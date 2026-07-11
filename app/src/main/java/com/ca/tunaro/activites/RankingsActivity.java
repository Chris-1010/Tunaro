package com.ca.tunaro.activites;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.ca.tunaro.adapters.RankingsLeaderboardAdapter;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.interfaces.Library_RecyclerViewInterface;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.models.SongSnippet;
import com.ca.tunaro.utils.RankingsTournament;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RankingsActivity extends BaseActivity implements Library_RecyclerViewInterface {
    private static final String TAG = "RankingsActivity";

    // Offered bracket sizes; the pool size itself is always offered as "All"
    private static final int[] BRACKET_SIZES = {4, 8, 16, 32, 64};
    private static final int DEFAULT_BRACKET_SIZE = 16;

    private DatabaseHelper dbHelper;
    private RankingsTournament tournament;
    private final List<SongModel> songPool = new ArrayList<>();
    private final List<Integer> sizeOptions = new ArrayList<>();

    // Phase containers
    private View setupPhase, matchPhase, resultsPhase;

    // Setup views
    private TextView poolSummary, sizeLabel;
    private Spinner sizeSpinner;
    private MaterialButton startButton;

    // Match views
    private TextView roundTitle, matchProgress;
    private CardView songCardA, songCardB;
    private ImageView coverA, coverB;
    private TextView songNameA, artistNameA, songNameB, artistNameB;

    private RankingsLeaderboardAdapter leaderboardAdapter;

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

        setupPhase = findViewById(R.id.setup_phase);
        matchPhase = findViewById(R.id.match_phase);
        resultsPhase = findViewById(R.id.results_phase);

        poolSummary = findViewById(R.id.pool_summary);
        sizeLabel = findViewById(R.id.size_label);
        sizeSpinner = findViewById(R.id.tournament_size_spinner);
        startButton = findViewById(R.id.start_button);

        roundTitle = findViewById(R.id.round_title);
        matchProgress = findViewById(R.id.match_progress);
        songCardA = findViewById(R.id.song_card_a);
        songCardB = findViewById(R.id.song_card_b);
        coverA = findViewById(R.id.cover_a);
        coverB = findViewById(R.id.cover_b);
        songNameA = findViewById(R.id.song_name_a);
        artistNameA = findViewById(R.id.artist_name_a);
        songNameB = findViewById(R.id.song_name_b);
        artistNameB = findViewById(R.id.artist_name_b);

        RecyclerView leaderboardRecyclerView = findViewById(R.id.leaderboard_recycler_view);
        leaderboardAdapter = new RankingsLeaderboardAdapter(this, this);
        leaderboardRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        leaderboardRecyclerView.setAdapter(leaderboardAdapter);

        startButton.setOnClickListener(v -> startTournament());
        songCardA.setOnClickListener(v -> chooseWinner(tournament.getContenderA()));
        songCardB.setOnClickListener(v -> chooseWinner(tournament.getContenderB()));
        findViewById(R.id.preview_button_a).setOnClickListener(v -> previewContender(tournament.getContenderA()));
        findViewById(R.id.preview_button_b).setOnClickListener(v -> previewContender(tournament.getContenderB()));
        findViewById(R.id.restart_button).setOnClickListener(v -> showSetupPhase());

        // Building the pool issues one DB query per annotated song, so load it off the
        // main thread and render the setup UI once it is ready. Show a loading state
        // in the meantime rather than a misleading "empty library" message.
        showLoadingPhase();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            loadSongPool();
            runOnUiThread(this::showSetupPhase);
        });
        executor.shutdown();
    }

    // The candidate pool is the library: every song with notes or snippets.
    // Runs off the main thread (N+2 synchronous DB reads).
    private void loadSongPool() {
        songPool.clear();
        Set<String> songIds = new LinkedHashSet<>(dbHelper.getSongIdsWithNotes());
        songIds.addAll(dbHelper.getSongIdsWithSnippets());

        for (String songId : songIds) {
            SongModel song = dbHelper.getLeanSong(songId);
            if (song != null) songPool.add(song);
        }
        Log.d(TAG, "Loaded ranking pool of " + songPool.size() + " songs");
    }

    private void showLoadingPhase() {
        setupPhase.setVisibility(View.VISIBLE);
        matchPhase.setVisibility(View.GONE);
        resultsPhase.setVisibility(View.GONE);

        poolSummary.setText("Loading your library…");
        sizeLabel.setVisibility(View.GONE);
        sizeSpinner.setVisibility(View.GONE);
        startButton.setVisibility(View.GONE);
    }

    private void showSetupPhase() {
        setupPhase.setVisibility(View.VISIBLE);
        matchPhase.setVisibility(View.GONE);
        resultsPhase.setVisibility(View.GONE);

        if (songPool.size() < 2) {
            poolSummary.setText("Add notes or snippets to at least two songs to play a ranking game");
            sizeLabel.setVisibility(View.GONE);
            sizeSpinner.setVisibility(View.GONE);
            startButton.setVisibility(View.GONE);
            return;
        }

        poolSummary.setText(songPool.size() + " songs in your library");
        sizeLabel.setVisibility(View.VISIBLE);
        sizeSpinner.setVisibility(View.VISIBLE);
        startButton.setVisibility(View.VISIBLE);

        sizeOptions.clear();
        List<String> labels = new ArrayList<>();
        for (int size : BRACKET_SIZES) {
            if (size < songPool.size()) {
                sizeOptions.add(size);
                labels.add(size + " random songs");
            }
        }
        sizeOptions.add(songPool.size());
        labels.add("All " + songPool.size() + " songs");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_dark, labels);
        adapter.setDropDownViewResource(R.layout.item_spinner_dark);
        sizeSpinner.setAdapter(adapter);

        int defaultIndex = sizeOptions.indexOf(DEFAULT_BRACKET_SIZE);
        sizeSpinner.setSelection(defaultIndex >= 0 ? defaultIndex : sizeOptions.size() - 1);
    }

    private void startTournament() {
        int size = sizeOptions.get(sizeSpinner.getSelectedItemPosition());
        List<SongModel> entrants = new ArrayList<>(songPool);
        Collections.shuffle(entrants);
        tournament = new RankingsTournament(new ArrayList<>(entrants.subList(0, size)));

        setupPhase.setVisibility(View.GONE);
        matchPhase.setVisibility(View.VISIBLE);
        resultsPhase.setVisibility(View.GONE);

        showCurrentMatch();
    }

    private void showCurrentMatch() {
        if (tournament.isFinished()) {
            showResults();
            return;
        }

        roundTitle.setText(tournament.getRoundName());
        matchProgress.setText("Match " + tournament.getMatchNumber() + " of " + tournament.getMatchesInRound());

        bindContender(tournament.getContenderA(), coverA, songNameA, artistNameA);
        bindContender(tournament.getContenderB(), coverB, songNameB, artistNameB);
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

    private void chooseWinner(SongModel winner) {
        tournament.reportWinner(winner);
        showCurrentMatch();
    }

    // Play the song's ranking snippet if one exists, otherwise the full song
    private void previewContender(SongModel song) {
        List<SongSnippet> snippets = dbHelper.getSongSnippets(song.getId());
        for (SongSnippet snippet : snippets) {
            if (snippet.getIncludeInRankings()) {
                playbackManager.playSnippet(snippet);
                return;
            }
        }

        if (!playbackManager.isConnected()) {
            showToast("Connecting to Spotify...");
            playbackManager.connectSpotify(getApplicationContext(), () -> playbackManager.playSong(song));
            return;
        }
        playbackManager.playSong(song);
    }

    private void showResults() {
        setupPhase.setVisibility(View.GONE);
        matchPhase.setVisibility(View.GONE);
        resultsPhase.setVisibility(View.VISIBLE);

        leaderboardAdapter.updateEntries(tournament.getLeaderboard());
    }

    @Override
    public void onItemClick(int position) {
        SongModel selectedSong = leaderboardAdapter.getEntries().get(position).song;
        SelectedSongHolder.getInstance().setSelectedSong(selectedSong);
        Intent intent = new Intent(this, SongView.class);
        intent.putExtra("source", "rankings");
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
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
