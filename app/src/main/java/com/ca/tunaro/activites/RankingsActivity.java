package com.ca.tunaro.activites;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.ca.tunaro.adapters.RankingsHistoryAdapter;
import com.ca.tunaro.adapters.RankingsLeaderboardAdapter;
import com.ca.tunaro.adapters.RankingsSnippetAdapter;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.fragments.RankingPlaylistSheet;
import com.ca.tunaro.interfaces.Library_RecyclerViewInterface;
import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.models.RankedSong;
import com.ca.tunaro.models.RankingGame;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.models.SongSnippet;
import com.ca.tunaro.utils.MaxHeightRecyclerView;
import com.ca.tunaro.utils.RotatingGradientBorderView;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.ca.tunaro.utils.SwissGraphView;
import com.ca.tunaro.utils.SwissTournament;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RankingsActivity extends BaseActivity implements Library_RecyclerViewInterface {
    private static final String TAG = "RankingsActivity";

    // Where a snippet-less preview starts — roughly into the song, not the intro.
    private static final double PREVIEW_START_FRACTION = 0.25;

    // A single game fields at most this many songs. When the chosen playlists hold
    // more, each game samples this many (weighted toward the least-ranked); repeated
    // games spread coverage across the whole pool and the cross-game Elo board is the
    // true full-catalogue ranking. Keeps any one game from running to hundreds of
    // matches.
    private static final int MAX_GAME_ENTRANTS = 128;

    // How many small cover thumbnails to draw before stopping (the count still
    // reflects the true total).
    private static final int MAX_RESUME_COVERS = 6;
    private static final int MAX_CARD_BADGES = 4;
    private static final int MAX_RESULTS_ICONS = 8;

    // Two helpers so background work never shares a connection with the main
    // thread: each DatabaseHelper call closes its connection when done, and a
    // cross-thread close on a shared instance would crash an in-flight query.
    private DatabaseHelper dbHelper;      // main thread only
    private DatabaseHelper bgDbHelper;    // executor thread only
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Random random = new Random();

    // Lazily-filled playlist_id → cover url cache. Entering the activity no longer
    // preloads the whole library; entrant songs are read only when a game starts,
    // for the chosen playlists alone.
    private final Map<String, String> playlistCoverById = new HashMap<>();

    // The live game, its persisted row id, the entrant songs (kept so an undo can
    // rebuild the bracket by replay), and the playlists it was built from (for the
    // on-card badges).
    private SwissTournament tournament;
    private long currentGameId = -1;
    private List<SongModel> currentEntrants = new ArrayList<>();
    private List<String> activeGamePlaylistIds = new ArrayList<>();
    private final Map<String, String> gameCoverById = new HashMap<>();

    // Entrants of the restored resumable game, kept so resuming can seed the undo.
    private List<SongModel> resumeEntrants = new ArrayList<>();

    // A resumable game restored during load, or null if none / unresumable.
    private SwissTournament resumeTournament;
    private RankingGame resumeGame;

    private List<RankingGame> completedGames = new ArrayList<>();
    private boolean viewingHistory = false;

    // Phase containers
    private View setupPhase, matchPhase, resultsPhase;

    // Setup views
    private View resumeCard, newGameSection, resumeCoversRow;
    private LinearLayout resumeCovers;
    private TextView resumeProgress, resumeCount, poolSummary, pastResultsLabel;
    private MaterialButton startButton, resumeButton;
    private RecyclerView historyRecyclerView;

    // Match views
    private TextView roundTitle, matchProgress;
    private SwissGraphView graphView;
    private CardView songCardA, songCardB;
    private ImageView coverA, coverB;
    private LinearLayout badgesA, badgesB;
    private TextView songNameA, artistNameA, songNameB, artistNameB;
    private MaxHeightRecyclerView snippetsA, snippetsB;
    private MaterialButton undoButton;
    // "Now playing" cues: a rotating green border around the playing card and a
    // matching glow from that side of the screen.
    private RotatingGradientBorderView borderA, borderB;
    private View edgeGlowLeft, edgeGlowRight;

    // Results views
    private TextView leaderboardTitle;
    private MaterialButton restartButton;
    private LinearLayout resultsPlaylistIcons;
    // Playlists to show above the standings for the result currently on screen.
    private List<String> resultsPlaylistIds = new ArrayList<>();

    private RankingsLeaderboardAdapter leaderboardAdapter;
    private RankingsHistoryAdapter historyAdapter;
    private RankingsSnippetAdapter snippetAdapterA, snippetAdapterB;

    // Mirror of the live playhead, fed to the under-card snippet lists.
    private String currentPlayingSongId;
    private boolean currentlyPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_rankings);

        if (MainActivity.getInstance() == null) {
            showToast("Could not connect to Spotify");
            finish();
            return;
        }

        dbHelper = new DatabaseHelper(this);
        bgDbHelper = new DatabaseHelper(this);

        bindViews();
        setupRecyclerViews();
        setupListeners();

        // Building the pool and restoring any saved game issue several DB reads,
        // so load off the main thread and show a loading state meanwhile.
        showLoadingPhase();
        executor.execute(() -> {
            loadData(bgDbHelper);
            runOnUiThread(this::showSetupPhase);
        });
    }

    private void bindViews() {
        setupPhase = findViewById(R.id.setup_phase);
        matchPhase = findViewById(R.id.match_phase);
        resultsPhase = findViewById(R.id.results_phase);

        resumeCard = findViewById(R.id.resume_card);
        newGameSection = findViewById(R.id.new_game_section);
        resumeProgress = findViewById(R.id.resume_progress);
        resumeCoversRow = findViewById(R.id.resume_covers_row);
        resumeCovers = findViewById(R.id.resume_covers);
        resumeCount = findViewById(R.id.resume_count);
        poolSummary = findViewById(R.id.pool_summary);
        pastResultsLabel = findViewById(R.id.past_results_label);
        startButton = findViewById(R.id.start_button);
        resumeButton = findViewById(R.id.resume_button);
        historyRecyclerView = findViewById(R.id.history_recycler_view);

        roundTitle = findViewById(R.id.round_title);
        matchProgress = findViewById(R.id.match_progress);
        graphView = findViewById(R.id.graph_view);
        songCardA = findViewById(R.id.song_card_a);
        songCardB = findViewById(R.id.song_card_b);
        coverA = findViewById(R.id.cover_a);
        coverB = findViewById(R.id.cover_b);
        badgesA = findViewById(R.id.badges_a);
        badgesB = findViewById(R.id.badges_b);
        songNameA = findViewById(R.id.song_name_a);
        artistNameA = findViewById(R.id.artist_name_a);
        songNameB = findViewById(R.id.song_name_b);
        artistNameB = findViewById(R.id.artist_name_b);
        snippetsA = findViewById(R.id.snippets_a);
        snippetsB = findViewById(R.id.snippets_b);
        undoButton = findViewById(R.id.undo_button);
        borderA = findViewById(R.id.border_a);
        borderB = findViewById(R.id.border_b);
        edgeGlowLeft = findViewById(R.id.edge_glow_left);
        edgeGlowRight = findViewById(R.id.edge_glow_right);

        leaderboardTitle = findViewById(R.id.leaderboard_title);
        restartButton = findViewById(R.id.restart_button);
        resultsPlaylistIcons = findViewById(R.id.results_playlist_icons);
    }

    private void setupRecyclerViews() {
        RecyclerView leaderboardRecyclerView = findViewById(R.id.leaderboard_recycler_view);
        leaderboardAdapter = new RankingsLeaderboardAdapter(this, this);
        leaderboardRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        leaderboardRecyclerView.setAdapter(leaderboardAdapter);

        historyAdapter = new RankingsHistoryAdapter(this, dbHelper, this::openHistoryDetail);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyRecyclerView.setAdapter(historyAdapter);

        snippetAdapterA = new RankingsSnippetAdapter(this, snippetPlayListener);
        snippetsA.setLayoutManager(new LinearLayoutManager(this));
        snippetsA.setAdapter(snippetAdapterA);

        snippetAdapterB = new RankingsSnippetAdapter(this, snippetPlayListener);
        snippetsB.setLayoutManager(new LinearLayoutManager(this));
        snippetsB.setAdapter(snippetAdapterB);
    }

    private final RankingsSnippetAdapter.OnSnippetPlayListener snippetPlayListener =
            new RankingsSnippetAdapter.OnSnippetPlayListener() {
                @Override
                public void onPlaySnippet(SongSnippet snippet) {
                    playSnippetPreview(snippet);
                }

                @Override
                public void onStopSnippet(SongSnippet snippet) {
                    playbackManager.pauseSnippet();
                }
            };

    private void setupListeners() {
        startButton.setOnClickListener(v -> confirmStartNewGame());
        resumeButton.setOnClickListener(v -> resumeSavedGame());
        songCardA.setOnClickListener(v -> previewContender(tournament.getContenderA()));
        songCardB.setOnClickListener(v -> previewContender(tournament.getContenderB()));
        // Long-press a contender to open its detail screen (a short tap still previews).
        songCardA.setOnLongClickListener(v -> {
            openSongView(tournament.getContenderA());
            return true;
        });
        songCardB.setOnLongClickListener(v -> {
            openSongView(tournament.getContenderB());
            return true;
        });
        graphView.setOnSongClickListener(this::openSongView);
        findViewById(R.id.choose_button_a).setOnClickListener(v -> chooseWinner(tournament.getContenderA()));
        findViewById(R.id.choose_button_b).setOnClickListener(v -> chooseWinner(tournament.getContenderB()));
        findViewById(R.id.save_exit_button).setOnClickListener(v -> finish());
        undoButton.setOnClickListener(v -> undoLastMatch());
        restartButton.setOnClickListener(v -> showSetupPhase());
    }

    // ---- Data loading ----

    // Runs off the main thread. Only restores any in-progress game (its own
    // entrants) and loads completed history — the full song pool is not touched
    // here; it is read per chosen playlist when a new game actually starts.
    private void loadData(DatabaseHelper db) {
        restoreInProgressGame(db);
        completedGames = db.getCompletedGames();
    }

    private void restoreInProgressGame(DatabaseHelper db) {
        resumeTournament = null;
        resumeGame = null;

        RankingGame game = db.getInProgressGame();
        if (game == null) return;

        List<SongModel> entrants = new ArrayList<>();
        for (String id : game.entrantIds) {
            SongModel song = db.getLeanSong(id);
            if (song == null) {
                // A saved entrant is gone (removed from every playlist) — the game
                // can no longer be reconstructed, so discard it.
                Log.w(TAG, "Discarding in-progress game: entrant " + id + " no longer exists");
                db.deleteGame(game.id);
                return;
            }
            entrants.add(song);
        }

        resumeTournament = SwissTournament.restore(entrants, game.decisions);
        resumeEntrants = entrants;
        resumeGame = game;
        if (resumeTournament.isFinished()) {
            // Already complete but never finalised (e.g. killed mid-finish): wrap it
            // up now rather than offering an already-decided game to resume.
            finaliseCompletedGame(db, game.id, resumeTournament, game.entrantIds);
            resumeTournament = null;
            resumeGame = null;
        }
    }

    // ---- Phases ----

    private void showLoadingPhase() {
        setupPhase.setVisibility(View.VISIBLE);
        matchPhase.setVisibility(View.GONE);
        resultsPhase.setVisibility(View.GONE);

        resumeCard.setVisibility(View.GONE);
        poolSummary.setText("Loading…");
        startButton.setVisibility(View.GONE);
        pastResultsLabel.setVisibility(View.GONE);
    }

    private void showSetupPhase() {
        setupPhase.setVisibility(View.VISIBLE);
        matchPhase.setVisibility(View.GONE);
        resultsPhase.setVisibility(View.GONE);
        viewingHistory = false;
        updatePlayingVisuals();

        // Resume card
        if (resumeTournament != null && resumeGame != null) {
            resumeCard.setVisibility(View.VISIBLE);
            resumeProgress.setText(progressLabel(resumeTournament));
            populateResumeCovers(resumeGame);
        } else {
            resumeCard.setVisibility(View.GONE);
        }

        // New-game controls. The picker sheet lists the playlists and reports if
        // there are none, so Start is always offered here.
        poolSummary.setText("Pick playlists to rank their songs head-to-head");
        startButton.setVisibility(View.VISIBLE);

        // Past results
        historyAdapter.updateGames(completedGames);
        pastResultsLabel.setVisibility(completedGames.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // "New game" opens the playlist picker sheet; warn first if it would throw away
    // a resumable game.
    private void confirmStartNewGame() {
        if (resumeTournament != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Start a new game?")
                    .setMessage("This discards your game in progress. Only one game can be saved at a time.")
                    .setPositiveButton("Continue", (d, w) -> openPlaylistPicker())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            openPlaylistPicker();
        }
    }

    private void openPlaylistPicker() {
        RankingPlaylistSheet sheet = new RankingPlaylistSheet();
        sheet.setOnPlaylistsConfirmedListener(this::startNewGame);
        sheet.show(getSupportFragmentManager(), "ranking_playlists");
    }

    // Build entrants from the union of the chosen playlists' songs, cap the field for
    // a huge pool (sampling the least-ranked), seed the opening order by past Elo, and
    // begin. The song reads happen off the main thread and are scoped to the picked
    // playlists only — entering the activity loads nothing.
    private void startNewGame(List<String> selectedIds) {
        showLoadingPhase();
        executor.execute(() -> {
            Set<String> unionUris = new LinkedHashSet<>();
            for (String id : selectedIds) {
                unionUris.addAll(bgDbHelper.getActiveSongUrisForPlaylist(id));
            }

            // Weight both the sampling and the seed decision by how many ranked
            // matches each song has already had.
            List<String> uriList = new ArrayList<>(unionUris);
            Map<String, Integer> matchesPlayed = bgDbHelper.getMatchesPlayed(uriList);
            if (uriList.size() > MAX_GAME_ENTRANTS) {
                uriList = sampleFewestRanked(uriList, matchesPlayed, MAX_GAME_ENTRANTS);
            }

            List<SongModel> entrants = new ArrayList<>();
            for (String uri : uriList) {
                SongModel song = bgDbHelper.getLeanSong(uri);
                if (song != null) entrants.add(song);
            }

            if (entrants.size() < 2) {
                runOnUiThread(() -> {
                    showToast("Those playlists have fewer than two songs between them");
                    showSetupPhase();
                });
                return;
            }
            seedOpeningOrder(entrants, matchesPlayed);

            SwissTournament newTournament = new SwissTournament(entrants);
            // Insert here so the id is known before the first choice persists.
            long gameId = bgDbHelper.startInProgressGame(
                    entrants.size(), newTournament.getEntrantIds(), selectedIds);

            runOnUiThread(() -> {
                tournament = newTournament;
                currentEntrants = entrants;
                currentGameId = gameId;
                setActiveGame(selectedIds);

                // Starting a new game supersedes any resumable one.
                resumeTournament = null;
                resumeGame = null;

                showMatchPhase();
            });
        });
    }

    // Pick a bounded field from a large pool, biased toward the songs with the fewest
    // ranked matches so far, so repeated games spread coverage rather than re-facing
    // the same songs. Uses weighted reservoir keys (u^(1/weight)); a smaller match
    // count means a larger weight and a better chance of selection, with enough
    // randomness that consecutive games field different songs.
    private List<String> sampleFewestRanked(List<String> uris, Map<String, Integer> matchesPlayed, int cap) {
        Map<String, Double> key = new HashMap<>();
        for (String uri : uris) {
            double weight = 1.0 / (1.0 + matchesPlayed.getOrDefault(uri, 0));
            key.put(uri, Math.pow(random.nextDouble(), 1.0 / weight));
        }
        List<String> pool = new ArrayList<>(uris);
        pool.sort((a, b) -> Double.compare(key.get(b), key.get(a)));
        return new ArrayList<>(pool.subList(0, Math.min(cap, pool.size())));
    }

    // Order the entrants for round one. When any entrant has a ranking history, sort
    // by cross-game Elo so the opening round pairs songs of similar strength;
    // otherwise shuffle, since a fresh pool has nothing to seed from.
    private void seedOpeningOrder(List<SongModel> entrants, Map<String, Integer> matchesPlayed) {
        boolean hasHistory = false;
        for (SongModel song : entrants) {
            if (matchesPlayed.getOrDefault(song.getId(), 0) > 0) {
                hasHistory = true;
                break;
            }
        }
        if (!hasHistory) {
            Collections.shuffle(entrants);
            return;
        }
        List<String> ids = new ArrayList<>();
        for (SongModel song : entrants) ids.add(song.getId());
        Map<String, Double> ratings = bgDbHelper.getRawRatings(ids);
        entrants.sort((x, y) -> Double.compare(
                ratings.getOrDefault(y.getId(), 0.0), ratings.getOrDefault(x.getId(), 0.0)));
    }

    private void resumeSavedGame() {
        tournament = resumeTournament;
        currentEntrants = resumeEntrants;
        currentGameId = resumeGame.id;
        setActiveGame(resumeGame.playlistIds);
        resumeTournament = null;
        resumeGame = null;
        showMatchPhase();
    }

    // Remember the game's playlists and ensure a cover url is on hand for each.
    private void setActiveGame(List<String> playlistIds) {
        activeGamePlaylistIds = playlistIds != null ? new ArrayList<>(playlistIds) : new ArrayList<>();
        gameCoverById.clear();
        for (String id : activeGamePlaylistIds) {
            gameCoverById.put(id, coverForPlaylist(id));
        }
    }

    // Playlist cover url, cached across calls (badges + resume strip reuse it).
    private String coverForPlaylist(String id) {
        if (playlistCoverById.containsKey(id)) return playlistCoverById.get(id);
        PlaylistModel playlist = dbHelper.getPlaylistById(id);
        String cover = playlist != null ? playlist.getImage() : null;
        playlistCoverById.put(id, cover);
        return cover;
    }

    private void showMatchPhase() {
        if (tournament.isFinished()) {
            finishCurrentGame();
            return;
        }

        setupPhase.setVisibility(View.GONE);
        matchPhase.setVisibility(View.VISIBLE);
        resultsPhase.setVisibility(View.GONE);

        showCurrentMatch();
    }

    private void showCurrentMatch() {
        if (tournament.isFinished()) {
            finishCurrentGame();
            return;
        }

        roundTitle.setText(tournament.getSegmentLabel());
        matchProgress.setText("Match " + tournament.getMatchNumber() + " of " + tournament.getTotalUserMatches());
        undoButton.setEnabled(tournament.getUserMatchesPlayed() > 0);

        SongModel a = tournament.getContenderA();
        SongModel b = tournament.getContenderB();
        bindContender(a, coverA, songNameA, artistNameA);
        bindContender(b, coverB, songNameB, artistNameB);

        bindBadges(a, badgesA);
        bindBadges(b, badgesB);

        bindSnippets(a, snippetAdapterA, snippetsA);
        bindSnippets(b, snippetAdapterB, snippetsB);

        graphView.render(tournament.getGraphRounds(), tournament.getStandings());

        updatePlayingVisuals();
    }

    // ---- "Now playing" cues ----

    // Light up the card (and its screen edge) whose song is currently sounding, and
    // fade its idle rival back so the active one stands out.
    private void updatePlayingVisuals() {
        boolean matchVisible = matchPhase != null && matchPhase.getVisibility() == View.VISIBLE;
        if (!matchVisible || tournament == null || tournament.isFinished()) {
            setSideActive(borderA, edgeGlowLeft, false);
            setSideActive(borderB, edgeGlowRight, false);
            songCardA.setAlpha(1f);
            songCardB.setAlpha(1f);
            return;
        }

        boolean aPlaying = currentlyPlaying
                && tournament.getContenderA().getId().equals(currentPlayingSongId);
        boolean bPlaying = currentlyPlaying
                && tournament.getContenderB().getId().equals(currentPlayingSongId);

        setSideActive(borderA, edgeGlowLeft, aPlaying);
        setSideActive(borderB, edgeGlowRight, bPlaying);
        songCardA.setAlpha(bPlaying ? 0.55f : 1f);
        songCardB.setAlpha(aPlaying ? 0.55f : 1f);
    }

    private void setSideActive(RotatingGradientBorderView border, View glow, boolean active) {
        if (active) {
            border.start();
        } else {
            border.stop();
        }
        fadeGlow(glow, active);
    }

    private void fadeGlow(View glow, boolean show) {
        if (show) {
            if (glow.getVisibility() == View.VISIBLE) return;
            glow.setVisibility(View.VISIBLE);
            glow.setAlpha(0f);
            glow.animate().alpha(1f).setDuration(280).start();
        } else {
            if (glow.getVisibility() != View.VISIBLE) return;
            glow.animate().alpha(0f).setDuration(220)
                    .withEndAction(() -> glow.setVisibility(View.GONE)).start();
        }
    }

    private void bindContender(SongModel song, ImageView cover, TextView name, TextView artist) {
        name.setText(song.getName());
        artist.setText(song.getArtist());
        Glide.with(this)
                .load(song.getAlbumCoverUrl())
                .placeholder(R.drawable.playlist_placeholder)
                .error(R.drawable.playlist_placeholder)
                .into(cover);
    }

    // Small playlist cover badges above the album art, shown only when a game
    // spans more than one playlist. Each badge marks a selected playlist the song
    // currently belongs to.
    private void bindBadges(SongModel song, LinearLayout container) {
        container.removeAllViews();
        if (activeGamePlaylistIds.size() <= 1) {
            container.setVisibility(View.GONE);
            return;
        }

        Set<String> songPlaylists = new LinkedHashSet<>(dbHelper.getActivePlaylistIdsForSong(song.getId()));
        int size = dpToPx(20);
        int gap = dpToPx(3);
        int added = 0;
        for (String id : activeGamePlaylistIds) {
            if (!songPlaylists.contains(id)) continue;
            if (added >= MAX_CARD_BADGES) break;
            ImageView badge = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(gap, 0, gap, 0);
            badge.setLayoutParams(params);
            badge.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this)
                    .load(gameCoverById.get(id))
                    .placeholder(R.drawable.playlist_placeholder)
                    .error(R.drawable.playlist_placeholder)
                    .into(badge);
            container.addView(badge);
            added++;
        }
        container.setVisibility(added > 0 ? View.VISIBLE : View.GONE);
    }

    private void bindSnippets(SongModel song, RankingsSnippetAdapter adapter, MaxHeightRecyclerView list) {
        List<SongSnippet> snippets = dbHelper.getSongSnippets(song.getId());
        adapter.updateSnippets(snippets);
        list.setVisibility(snippets.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void chooseWinner(SongModel winner) {
        tournament.reportWinner(winner);

        // Elo is only committed when the game completes, so a wrong pick can be
        // undone without unwinding a rating change. Only the resumable decision
        // log is persisted per match.
        final long gameId = currentGameId;
        final List<String> decisions = tournament.getDecisionWinnerIds();
        executor.execute(() -> bgDbHelper.updateGameDecisions(gameId, decisions));

        showCurrentMatch();
    }

    // Step back one match after a wrong pick: drop the last decision and rebuild
    // the bracket by replaying the rest. Because ratings aren't applied until the
    // game finishes, there is nothing else to revert.
    private void undoLastMatch() {
        if (tournament.getUserMatchesPlayed() == 0) return;

        List<String> decisions = tournament.getDecisionWinnerIds();
        decisions.remove(decisions.size() - 1);
        tournament = SwissTournament.restore(currentEntrants, decisions);

        final long gameId = currentGameId;
        final List<String> persisted = tournament.getDecisionWinnerIds();
        executor.execute(() -> bgDbHelper.updateGameDecisions(gameId, persisted));

        showCurrentMatch();
    }

    // ---- Preview playback ----

    // Keep a random preview away from the very start and very end of the track.
    private static final long PREVIEW_EDGE_PADDING_MS = 10_000;
    // A re-seek must land at least this far from the current spot so a second
    // listen is audibly a different part of the song.
    private static final long MIN_RESEEK_DELTA_MS = 5_000;

    private void previewContender(SongModel song) {
        // Re-tapping the card while this same song is already previewing jumps to a
        // fresh random spot, so a second listen samples a different part.
        if (song.getId().equals(currentPlayingSongId) && currentlyPlaying) {
            playSnippetPreview(randomSeekSnippet(song, playbackManager.getCurrentPositionMs()));
            return;
        }
        List<SongSnippet> snippets = dbHelper.getSongSnippets(song.getId());
        SongSnippet chosen = pickPreviewSnippet(song, snippets);
        playSnippetPreview(chosen);
    }

    // A pseudo-snippet that starts at a random offset and runs to the end, kept at
    // least PREVIEW_EDGE_PADDING_MS from both ends so it never lands a sliver
    // before the track finishes, and at least MIN_RESEEK_DELTA_MS from the current
    // position so a re-seek is clearly a different spot.
    private SongSnippet randomSeekSnippet(SongModel song, long avoidPositionMs) {
        long duration = song.getDuration();
        long start;
        if (duration <= 2 * PREVIEW_EDGE_PADDING_MS) {
            // Too short to pad both ends — just start partway in.
            start = duration / 4;
        } else {
            long span = duration - 2 * PREVIEW_EDGE_PADDING_MS;
            start = PREVIEW_EDGE_PADDING_MS + (long) (random.nextDouble() * span);
            // Re-roll until it clears the current spot, giving up after a few tries
            // when the usable span is too small to guarantee it.
            for (int attempt = 0; attempt < 10
                    && Math.abs(start - avoidPositionMs) < MIN_RESEEK_DELTA_MS; attempt++) {
                start = PREVIEW_EDGE_PADDING_MS + (long) (random.nextDouble() * span);
            }
        }
        return new SongSnippet(null, song.getId(), 1, "Preview", start, duration, false);
    }

    // Prefer a snippet flagged for rankings; among the candidates pick at random.
    // Falls back to a pseudo-snippet starting partway into the song.
    private SongSnippet pickPreviewSnippet(SongModel song, List<SongSnippet> snippets) {
        List<SongSnippet> flagged = new ArrayList<>();
        for (SongSnippet snippet : snippets) {
            if (snippet.getIncludeInRankings()) flagged.add(snippet);
        }
        List<SongSnippet> candidates = !flagged.isEmpty() ? flagged : snippets;
        if (!candidates.isEmpty()) {
            return candidates.get(random.nextInt(candidates.size()));
        }
        long start = (long) (song.getDuration() * PREVIEW_START_FRACTION);
        return new SongSnippet(null, song.getId(), 1, "Preview", start, song.getDuration(), false);
    }

    private void playSnippetPreview(SongSnippet snippet) {
        if (!playbackManager.isConnected()) {
            showToast("Connecting to Spotify…");
            playbackManager.connectSpotify(getApplicationContext(),
                    () -> playbackManager.playSnippet(snippet));
            return;
        }
        playbackManager.playSnippet(snippet);
    }

    // ---- Finishing / results ----

    private void finishCurrentGame() {
        Map<String, int[]> eloChanges =
                finaliseCompletedGame(dbHelper, currentGameId, tournament, tournament.getEntrantIds());
        completedGames = dbHelper.getCompletedGames();

        viewingHistory = false;
        leaderboardAdapter.setEloChanges(eloChanges);
        resultsPlaylistIds = new ArrayList<>(activeGamePlaylistIds);
        showResults(tournament.getFinalRanking(), "Final standings", "Play Again");
    }

    // Persist a completed game and roll its results into the Elo ratings. Safe to
    // call for a game restored already-finished. Returns each entrant's old→new Elo.
    private Map<String, int[]> finaliseCompletedGame(DatabaseHelper db, long gameId,
                                                     SwissTournament finished, List<String> entrantIds) {
        List<RankedSong> ranking = finished.getFinalRanking();
        List<String> finalOrder = new ArrayList<>();
        for (RankedSong ranked : ranking) finalOrder.add(ranked.song.getId());
        db.completeGame(gameId, finished.getDecisionWinnerIds(), finalOrder);

        // Elo is applied here — deferred to completion so a mid-game undo never has
        // to reverse a rating update. New entrants first seed at the current lowest
        // rating; snapshot before and after for the results screen.
        db.seedNewEntrants(entrantIds);
        Map<String, Double> before = db.getRawRatings(entrantIds);
        for (SwissTournament.Matchup matchup : finished.getUserMatchups()) {
            db.applyMatchResult(matchup.winner.getId(), matchup.loser.getId());
        }
        // Ratings ratchet upward only, so a better placement is enforced by lifting it
        // above a worse one — never by lowering — preserving order set in earlier games.
        db.enforceRankingOrder(finalOrder);
        db.incrementGamesPlayed(entrantIds);
        Map<String, Double> after = db.getRawRatings(entrantIds);

        Map<String, int[]> changes = new HashMap<>();
        for (String id : entrantIds) {
            changes.put(id, new int[]{
                    (int) Math.round(before.getOrDefault(id, 0.0)),
                    (int) Math.round(after.getOrDefault(id, 0.0))});
        }
        return changes;
    }

    private void openHistoryDetail(RankingGame game) {
        executor.execute(() -> {
            List<RankedSong> ranking = new ArrayList<>();
            int rank = 1;
            for (String id : game.finalOrder) {
                SongModel song = bgDbHelper.getLeanSong(id);
                if (song != null) ranking.add(new RankedSong(rank++, song));
            }
            runOnUiThread(() -> {
                viewingHistory = true;
                leaderboardAdapter.setEloChanges(null);
                resultsPlaylistIds = game.playlistIds != null
                        ? game.playlistIds : new ArrayList<>();
                showResults(ranking, "Final standings", "Back");
            });
        });
    }

    private void showResults(List<RankedSong> ranking, String title, String buttonText) {
        setupPhase.setVisibility(View.GONE);
        matchPhase.setVisibility(View.GONE);
        resultsPhase.setVisibility(View.VISIBLE);
        updatePlayingVisuals();

        leaderboardTitle.setText(title);
        restartButton.setText(buttonText);
        leaderboardAdapter.updateEntries(ranking);
        populateResultsIcons(resultsPlaylistIds);
    }

    // Playlist covers above the standings; long-pressing one toasts its name.
    private void populateResultsIcons(List<String> ids) {
        resultsPlaylistIcons.removeAllViews();
        if (ids == null || ids.isEmpty()) {
            resultsPlaylistIcons.setVisibility(View.GONE);
            return;
        }

        int size = dpToPx(34);
        int gap = dpToPx(4);
        int shown = 0;
        for (String id : ids) {
            if (shown >= MAX_RESULTS_ICONS) break;
            PlaylistModel playlist = dbHelper.getPlaylistById(id);
            String name = playlist != null ? playlist.getPlaylistName() : "Playlist";

            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(gap, 0, gap, 0);
            icon.setLayoutParams(params);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this)
                    .load(playlist != null ? playlist.getImage() : null)
                    .placeholder(R.drawable.playlist_placeholder)
                    .error(R.drawable.playlist_placeholder)
                    .into(icon);
            icon.setOnLongClickListener(v -> {
                showToast(name);
                return true;
            });
            resultsPlaylistIcons.addView(icon);
            shown++;
        }
        resultsPlaylistIcons.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
    }

    // ---- Resume card cover strip ----

    private void populateResumeCovers(RankingGame game) {
        resumeCovers.removeAllViews();
        List<String> ids = game.playlistIds != null ? game.playlistIds : new ArrayList<>();
        if (ids.isEmpty()) {
            resumeCoversRow.setVisibility(View.GONE);
            return;
        }

        int size = dpToPx(24);
        int gap = dpToPx(3);
        int shown = 0;
        for (String id : ids) {
            if (shown >= MAX_RESUME_COVERS) break;
            ImageView cover = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(0, 0, gap, 0);
            cover.setLayoutParams(params);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this)
                    .load(coverForPlaylist(id))
                    .placeholder(R.drawable.playlist_placeholder)
                    .error(R.drawable.playlist_placeholder)
                    .into(cover);
            resumeCovers.addView(cover);
            shown++;
        }
        resumeCount.setText(String.format(java.util.Locale.getDefault(),
                "%d playlist%s", ids.size(), ids.size() == 1 ? "" : "s"));
        resumeCoversRow.setVisibility(View.VISIBLE);
    }

    private String progressLabel(SwissTournament t) {
        return t.getUserMatchesPlayed() + "/" + t.getTotalUserMatches() + " matches · " + t.getSegmentLabel();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ---- Playback listener: feed the live playhead to snippet rows ----

    @Override
    public void onPlaybackStateChanged(boolean isPlaying, SongModel currentSong) {
        super.onPlaybackStateChanged(isPlaying, currentSong);
        currentPlayingSongId = currentSong != null ? currentSong.getId() : null;
        currentlyPlaying = isPlaying;
        runOnUiThread(this::updatePlayingVisuals);
    }

    @Override
    public void onPlaybackPositionChanged(long positionMs, long durationMs) {
        super.onPlaybackPositionChanged(positionMs, durationMs);
        if (matchPhase == null || matchPhase.getVisibility() != View.VISIBLE) return;
        runOnUiThread(() -> {
            snippetAdapterA.updatePlaybackPosition(positionMs, currentPlayingSongId, currentlyPlaying);
            snippetAdapterB.updatePlaybackPosition(positionMs, currentPlayingSongId, currentlyPlaying);
        });
    }

    // ---- Leaderboard row tap ----

    @Override
    public void onItemClick(int position) {
        openSongView(leaderboardAdapter.getEntries().get(position).song);
    }

    // Open a song's detail screen, the same way the leaderboard rows do: long-pressing
    // a contender card, or tapping a song in the standings ladder or a round's box.
    private void openSongView(SongModel song) {
        if (song == null) return;
        SelectedSongHolder.getInstance().setSelectedSong(song);
        Intent intent = new Intent(this, SongView.class);
        intent.putExtra("source", "rankings");
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.zoom_in, R.anim.zoom_out);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}
