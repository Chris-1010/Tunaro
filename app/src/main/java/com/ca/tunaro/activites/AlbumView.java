package com.ca.tunaro.activites;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.ca.tunaro.adapters.QueueLineDecoration;
import com.ca.tunaro.adapters.Song_RecyclerViewAdapter;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.interfaces.Song_RecyclerViewInterface;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.models.AlbumModel;
import com.ca.tunaro.models.Artist;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.AlbumFormat;
import com.ca.tunaro.utils.StateMessage;
import com.ca.tunaro.utils.AppExecutors;
import com.ca.tunaro.utils.ColorExtractor;
import com.ca.tunaro.utils.ImageLoads;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import se.michaelthelin.spotify.model_objects.specification.Album;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;

/**
 * Album detail screen. Paints instantly from the local DB (cached album row plus any locally-known
 * songs), then replaces that with the full Spotify album: header metadata, ordered track list and
 * tappable artists. Track rows behave as in PlaylistView: tap the cover to play from that track,
 * long-press it to play the single song, tap the row to open SongView.
 */
public class AlbumView extends BaseActivity implements Song_RecyclerViewInterface {
    private static final String TAG = "AlbumView";
    private static final int TRACK_PAGE_LIMIT = 50;
    private static final int MAX_TRACKS = 500;

    private String albumId;
    private String albumName;

    private RecyclerView recyclerView;
    private Song_RecyclerViewAdapter adapter;
    private QueueLineDecoration queueLineDecoration;
    private final ArrayList<SongModel> tracks = new ArrayList<>();
    private String highlightedUri;

    private String coverUrl;
    private boolean hasLocalData = false;
    private boolean hasRemoteData = false;
    // True while a Spotify request is in flight; placeholders only show during one.
    private boolean fetchPending = false;
    // Bumped per API load so a superseded (retried) request cannot overwrite a newer result.
    private int loadGeneration = 0;
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_album_view);

        albumId = bareId(getIntent().getStringExtra("album_id"));
        albumName = getIntent().getStringExtra("album_name");
        if (albumId == null || albumId.isEmpty()) {
            finish();
            return;
        }

        applyWindowInsets();

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.collapsed_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        setAlbumName(albumName);
        setupCollapsingHeader();
        setupTrackList();

        MaterialButton playButton = findViewById(R.id.album_play_button);
        playButton.setOnClickListener(v -> playFrom(0));
        setPlayEnabled(false);

        loadLocal();
        fetchAlbum();
    }

    private void applyWindowInsets() {
        final int listBasePadding = recyclerViewBottomPadding();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            View appBar = findViewById(R.id.app_bar);
            appBar.setPadding(appBar.getPaddingLeft(), systemBars.top,
                    appBar.getPaddingRight(), appBar.getPaddingBottom());
            View playbackBar = findViewById(R.id.playback_bar);
            if (playbackBar != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) playbackBar.getLayoutParams();
                params.bottomMargin = systemBars.bottom;
                playbackBar.setLayoutParams(params);
            }
            // Keep the last track scrollable clear of the playback bar and navigation bar.
            View list = findViewById(R.id.album_tracks);
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(),
                    listBasePadding + systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private int recyclerViewBottomPadding() {
        return findViewById(R.id.album_tracks).getPaddingBottom();
    }

    //#region Header

    // Fades the small collapsed-state cover + name in as the app bar collapses (as ArtistView).
    private void setupCollapsingHeader() {
        AppBarLayout appBar = findViewById(R.id.app_bar);
        View collapsedName = findViewById(R.id.collapsed_album_name);
        View collapsedCover = findViewById(R.id.collapsed_cover_card);
        appBar.addOnOffsetChangedListener((bar, verticalOffset) -> {
            int range = bar.getTotalScrollRange();
            float fraction = range == 0 ? 0f : Math.abs(verticalOffset) / (float) range;
            float alpha = Math.min(1f, Math.max(0f, (fraction - 0.6f) / 0.4f));
            collapsedName.setAlpha(alpha);
            collapsedCover.setAlpha(alpha);
        });
    }

    private void setAlbumName(String name) {
        if (name == null || name.isEmpty()) return;
        albumName = name;
        ((TextView) findViewById(R.id.album_name)).setText(name);
        ((TextView) findViewById(R.id.collapsed_album_name)).setText(name);
    }

    /**
     * Renders the header. {@code trackCount}/{@code totalMs} of 0 hide those parts of the meta line
     * (used for local data, where the song count would understate the album).
     */
    private void renderHeader(String name, String albumType, String releaseDate, String cover,
                              List<Artist> artists, int trackCount, long totalMs, String label) {
        setAlbumName(name);
        bindCover(cover);
        bindArtists(artists);

        TextView chip = findViewById(R.id.album_type_chip);
        String typeLabel = AlbumFormat.typeLabel(albumType);
        if (typeLabel != null) {
            chip.setText(typeLabel);
            chip.setTextColor(AlbumFormat.typeColor(albumType));
            chip.setVisibility(View.VISIBLE);
        } else {
            chip.setVisibility(View.GONE);
        }

        ((TextView) findViewById(R.id.album_meta)).setText(
                AlbumFormat.metaLine(releaseDate, trackCount, totalMs));

        TextView labelView = findViewById(R.id.album_label);
        if (label != null && !label.trim().isEmpty()) {
            labelView.setText(label.trim());
            labelView.setVisibility(View.VISIBLE);
        } else {
            labelView.setVisibility(View.GONE);
        }
    }

    // Loads the cover into both header images and re-themes the background, only when it changes,
    // so the remote refresh doesn't flash an already-shown local cover.
    private void bindCover(String url) {
        if (url == null || url.equals(coverUrl)) return;
        coverUrl = url;
        Glide.with(this)
                .load(url)
                .apply(ImageLoads.thumbnail(this, 220))
                .placeholder(R.drawable.song_placeholder)
                .error(R.drawable.song_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into((ImageView) findViewById(R.id.album_cover));
        Glide.with(this)
                .load(url)
                .apply(ImageLoads.thumbnail(this, 40))
                .placeholder(R.drawable.song_placeholder)
                .error(R.drawable.song_placeholder)
                .into((ImageView) findViewById(R.id.collapsed_cover));

        ColorExtractor.extractColors(this, url, new ColorExtractor.ColorExtractionCallback() {
            @Override
            public void onColorExtracted(int dominantColor, int vibrantColor) {
                if (isDestroyed() || !url.equals(coverUrl)) return;
                applyGradientBackground(ColorExtractor.pickBackgroundColor(dominantColor, vibrantColor));
            }

            @Override
            public void onError() {
                // Keep the plain black background.
            }
        });
    }

    private void applyGradientBackground(int color) {
        findViewById(R.id.main).setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TR_BL, new int[]{color, Color.BLACK}));
        // Collapsed toolbar: black under the back arrow and cover, ramping to a tint on the right.
        CollapsingToolbarLayout collapsing = findViewById(R.id.collapsing_toolbar);
        int tint = ColorUtils.blendARGB(color, Color.BLACK, 0.35f);
        GradientDrawable scrim = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.BLACK, Color.BLACK, tint});
        scrim.setGradientCenter(0.2f, 0.5f);
        collapsing.setContentScrim(scrim);
    }

    // Artist names as individually tappable spans, each opening that artist's page.
    private void bindArtists(List<Artist> artists) {
        TextView view = findViewById(R.id.album_artists);
        if (artists == null || artists.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        final int linkColor = ContextCompat.getColor(this, R.color.tanAccent);
        SpannableStringBuilder text = new SpannableStringBuilder();
        for (int i = 0; i < artists.size(); i++) {
            Artist artist = artists.get(i);
            if (artist.getName() == null) continue;
            if (text.length() > 0) text.append(", ");
            int start = text.length();
            text.append(artist.getName());
            if (artist.getArtistId() == null) continue;
            text.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    openArtist(artist);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setColor(linkColor);
                    ds.setUnderlineText(false);
                }
            }, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setText(text);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setHighlightColor(ContextCompat.getColor(this, R.color.faintTanAccent));
        view.setVisibility(text.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private void openArtist(Artist artist) {
        Intent intent = new Intent(this, ArtistView.class);
        intent.putExtra("artist_id", artist.getArtistId());
        intent.putExtra("artist_name", artist.getName());
        startActivity(intent);
    }

    //#endregion

    //#region Track list

    private void setupTrackList() {
        recyclerView = findViewById(R.id.album_tracks);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Song_RecyclerViewAdapter(this, this, tracks);
        adapter.updateSortContext(Song_RecyclerViewAdapter.CONTEXT_ALBUM_TRACK);
        recyclerView.setAdapter(adapter);
        queueLineDecoration = new QueueLineDecoration(adapter);
        recyclerView.addItemDecoration(queueLineDecoration);
        adapter.setOnQueueChangeListener((position, added) -> recyclerView.invalidateItemDecorations());
        SongModel current = PlaybackManager.getInstance().getCurrentSong();
        highlightedUri = current != null ? current.getUri() : null;
    }

    private void submitTracks(List<SongModel> songs, Map<String, Integer> trackNumbers,
                              boolean showArtist) {
        tracks.clear();
        tracks.addAll(songs);
        adapter.setShowArtist(showArtist);
        adapter.updateTrackNumbers(trackNumbers);
        adapter.updateSongs(tracks);
        recyclerView.invalidateItemDecorations();
        setPlayEnabled(!tracks.isEmpty());
    }

    private void setPlayEnabled(boolean enabled) {
        View play = findViewById(R.id.album_play_button);
        play.setEnabled(enabled);
        play.setAlpha(enabled ? 1f : 0.5f);
    }

    @Override
    public void onItemClick(int position, View itemView) {
        if (position < 0 || position >= tracks.size()) return;
        SelectedSongHolder.getInstance().setSelectedSong(tracks.get(position));
        startActivity(new Intent(this, SongView.class));
    }

    // Tap on a row's cover: start a queue of the album from that track (as PlaylistView).
    @Override
    public void onAlbumCoverClick(int position) {
        playFrom(position);
    }

    // Long press on a row's cover: play just that song, leaving the queue alone.
    @Override
    public void onAlbumCoverLongClick(int position) {
        if (position < 0 || position >= tracks.size()) return;
        SongModel song = tracks.get(position);
        PlaybackManager pm = PlaybackManager.getInstance();
        if (!pm.isConnected()) {
            pm.connectSpotify(this, () -> pm.playSong(song));
        } else {
            pm.playSong(song);
        }
    }

    private void playFrom(int position) {
        if (position < 0 || position >= tracks.size()) return;
        ArrayList<SongModel> queue = new ArrayList<>(tracks);
        PlaybackManager pm = PlaybackManager.getInstance();
        if (!pm.isConnected()) {
            pm.connectSpotify(this, () -> {
                pm.playQueue(queue, position);
                onQueueCreated();
            });
        } else {
            pm.playQueue(queue, position);
            onQueueCreated();
        }
    }

    private void onQueueCreated() {
        if (isDestroyed()) return;
        queueLineDecoration.setQueueMatchesDisplay(true);
        adapter.refreshPlaybackState();
        recyclerView.invalidateItemDecorations();
        SongModel current = PlaybackManager.getInstance().getCurrentSong();
        highlightedUri = current != null ? current.getUri() : null;
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying, SongModel currentSong) {
        super.onPlaybackStateChanged(isPlaying, currentSong);
        if (adapter == null) return;
        // Only a change of current song moves the highlight and the queued edges.
        String newUri = currentSong != null ? currentSong.getUri() : null;
        if (!Objects.equals(newUri, highlightedUri)) {
            adapter.refreshPlaybackState();
            recyclerView.invalidateItemDecorations();
            highlightedUri = newUri;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A note or snippet may have changed in SongView; refresh the row icons.
        if (firstResume) firstResume = false;
        else if (adapter != null) {
            adapter.refreshAnnotationFlags();
            loadListenCounts(new ArrayList<>(tracks));
        }
    }

    //#endregion

    //#region Loading

    // First paint from the DB: cached album row, locally-known songs and their artists.
    private void loadLocal() {
        DatabaseHelper db = DatabaseHelper.getInstance(getApplicationContext());
        final String id = albumId;
        AppExecutors.io().execute(() -> {
            AlbumModel album;
            List<SongModel> songs;
            List<Artist> artists;
            Set<String> added;
            Map<String, Integer> listens;
            try {
                album = db.getAlbum(id);
                songs = db.getAlbumLocalSongs(id);
                artists = db.getAlbumLocalArtists(id);
                added = db.getAlbumAddedSongUris(id);
                listens = db.getListenCountsBatch(uris(songs));
            } catch (RuntimeException e) {
                Log.e(TAG, "Failed to read local album", e);
                return;
            }
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                adapter.setAddedUris(added);
                if (hasRemoteData) return; // Spotify already answered with the full album.
                adapter.updateListenCounts(listens);
                if (album != null || !songs.isEmpty()) {
                    hasLocalData = true;
                    // A fast failure may have shown the error page before the DB answered.
                    findViewById(R.id.tracks_error).setVisibility(View.GONE);
                    String cover = album != null ? album.getCoverImageUrl() : null;
                    if (cover == null && !songs.isEmpty()) cover = songs.get(0).getAlbumCoverUrl();
                    renderHeader(album != null ? album.getName() : albumName,
                            album != null ? album.getAlbumType() : null,
                            album != null ? album.getReleaseDate() : null,
                            cover, artists, 0, 0, null);
                } else if (fetchPending) {
                    showHeaderShimmer(true); // First-ever visit: nothing cached yet.
                }
                if (!songs.isEmpty()) {
                    submitTracks(songs, Collections.emptyMap(), hasMultipleArtists(songs, artists));
                } else if (fetchPending) {
                    showTracksShimmer(true);
                }
            });
        });
    }

    private void fetchAlbum() {
        final int generation = ++loadGeneration;
        fetchPending = true;
        MainActivity main = MainActivity.getInstance();
        if (main == null || main.getSpotifyApi() == null) {
            onFetchFailed(generation, null);
            return;
        }
        final String id = albumId;
        main.executeWithTokenRefresh(() -> main.getSpotifyApi().getAlbum(id).build())
                .thenCompose(album -> {
                    if (album == null) throw new IllegalStateException("Empty album response");
                    Paging<TrackSimplified> first = album.getTracks();
                    List<TrackSimplified> items = new ArrayList<>();
                    int total = 0;
                    if (first != null) {
                        if (first.getItems() != null) Collections.addAll(items, first.getItems());
                        total = first.getTotal() != null ? first.getTotal() : items.size();
                    }
                    return fetchRemainingTracks(main, id, items, Math.min(total, MAX_TRACKS))
                            .thenApply(all -> new RemoteAlbum(album, all));
                })
                .thenAccept(remote -> {
                    persistAlbum(remote.album);
                    runOnUiThread(() -> {
                        if (generation != loadGeneration || isDestroyed()) return;
                        fetchPending = false;
                        renderRemote(remote);
                    });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to fetch album " + id, throwable);
                    onFetchFailed(generation, throwable);
                    return null;
                });
    }

    // Albums over one page (50 tracks) need follow-up track requests.
    private CompletableFuture<List<TrackSimplified>> fetchRemainingTracks(
            MainActivity main, String id, List<TrackSimplified> acc, int total) {
        if (acc.size() >= total) return CompletableFuture.completedFuture(acc);
        final int offset = acc.size();
        return main.executeWithTokenRefresh(() -> main.getSpotifyApi()
                        .getAlbumsTracks(id).offset(offset).limit(TRACK_PAGE_LIMIT).build())
                .thenCompose(page -> {
                    TrackSimplified[] items = page != null ? page.getItems() : null;
                    if (items == null || items.length == 0) return CompletableFuture.completedFuture(acc);
                    Collections.addAll(acc, items);
                    return fetchRemainingTracks(main, id, acc, total);
                });
    }

    // Keeps the cached album row fresh so the next visit paints the latest header instantly.
    private void persistAlbum(Album album) {
        try {
            DatabaseHelper.getInstance(getApplicationContext()).upsertAlbum(
                    albumId, album.getName(),
                    album.getAlbumType() != null ? album.getAlbumType().getType() : null,
                    album.getReleaseDate(), firstImageUrl(album.getImages()));
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to cache album", e);
        }
    }

    private void renderRemote(RemoteAlbum remote) {
        Album album = remote.album;
        String cover = firstImageUrl(album.getImages());
        if (cover == null) cover = coverUrl;
        String name = album.getName() != null ? album.getName() : albumName;

        List<Artist> artists = new ArrayList<>();
        if (album.getArtists() != null) {
            for (ArtistSimplified a : album.getArtists()) {
                if (a != null && a.getName() != null) artists.add(new Artist(a.getId(), a.getName()));
            }
        }

        boolean multiDisc = false;
        for (TrackSimplified t : remote.tracks) {
            if (t != null && t.getDiscNumber() != null && t.getDiscNumber() > 1) {
                multiDisc = true;
                break;
            }
        }

        List<SongModel> songs = new ArrayList<>();
        Map<String, Integer> trackNumbers = new HashMap<>();
        long totalMs = 0;
        for (TrackSimplified t : remote.tracks) {
            if (t == null || t.getUri() == null) continue;
            String primaryArtist = t.getArtists() != null && t.getArtists().length > 0
                    ? t.getArtists()[0].getName() : null;
            int duration = t.getDurationMs() != null ? t.getDurationMs() : 0;
            songs.add(new SongModel(t.getUri(), t.getName(), primaryArtist, duration, t.getUri(),
                    cover, 0, name, album.getReleaseDate()));
            // Per-disc numbering restarts, so multi-disc albums number tracks sequentially.
            int number = multiDisc || t.getTrackNumber() == null ? songs.size() : t.getTrackNumber();
            trackNumbers.put(t.getUri(), number);
            totalMs += duration;
        }

        int trackCount = album.getTracks() != null && album.getTracks().getTotal() != null
                ? album.getTracks().getTotal() : songs.size();

        hasRemoteData = true;
        renderHeader(name, album.getAlbumType() != null ? album.getAlbumType().getType() : null,
                album.getReleaseDate(), cover, artists, trackCount, totalMs, album.getLabel());
        showHeaderShimmer(false);
        submitTracks(songs, trackNumbers, hasMultipleArtists(songs, artists));
        showTracksShimmer(false);
        findViewById(R.id.tracks_error).setVisibility(View.GONE);
        loadListenCounts(songs);
    }

    private void loadListenCounts(List<SongModel> songs) {
        if (songs.isEmpty()) return;
        DatabaseHelper db = DatabaseHelper.getInstance(getApplicationContext());
        AppExecutors.io().execute(() -> {
            Map<String, Integer> listens;
            try {
                listens = db.getListenCountsBatch(uris(songs));
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to load listen counts", e);
                return;
            }
            runOnUiThread(() -> {
                if (!isDestroyed()) adapter.updateListenCounts(listens);
            });
        });
    }

    private void onFetchFailed(int generation, Throwable throwable) {
        runOnUiThread(() -> {
            if (generation != loadGeneration || isDestroyed()) return;
            fetchPending = false;
            if (hasRemoteData) return;
            showHeaderShimmer(false);
            showTracksShimmer(false);
            if (hasLocalData) {
                // Local data stays on screen; offer a quiet retry rather than an error page.
                Snackbar.make(findViewById(R.id.main), R.string.album_refresh_failed, Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.playback_bar)
                        .setAction(R.string.retry, v -> retry())
                        .setActionTextColor(ContextCompat.getColor(this, R.color.tanAccent))
                        .show();
            } else {
                StateMessage.showError(findViewById(R.id.tracks_error),
                        R.string.album_error_title, R.string.album_error_hint, this::retry);
            }
        });
    }

    private void retry() {
        findViewById(R.id.tracks_error).setVisibility(View.GONE);
        if (!hasLocalData) {
            showHeaderShimmer(true);
            showTracksShimmer(true);
        }
        fetchAlbum();
    }

    //#endregion

    //#region Helpers

    private static final class RemoteAlbum {
        final Album album;
        final List<TrackSimplified> tracks;

        RemoteAlbum(Album album, List<TrackSimplified> tracks) {
            this.album = album;
            this.tracks = tracks;
        }
    }

    // Show each row's artist only when the album mixes artists (features, compilations).
    private static boolean hasMultipleArtists(List<SongModel> songs, List<Artist> albumArtists) {
        String main = albumArtists != null && !albumArtists.isEmpty()
                ? albumArtists.get(0).getName() : null;
        for (SongModel s : songs) {
            String artist = s.getArtist();
            if (artist == null) continue;
            if (main == null) main = artist;
            else if (!main.equals(artist)) return true;
        }
        return false;
    }

    private void showHeaderShimmer(boolean show) {
        ShimmerFrameLayout shimmer = findViewById(R.id.header_meta_shimmer);
        View content = findViewById(R.id.header_meta_content);
        if (show) {
            shimmer.setVisibility(View.VISIBLE);
            shimmer.startShimmer();
            content.setVisibility(View.GONE);
        } else {
            shimmer.stopShimmer();
            shimmer.setVisibility(View.GONE);
            content.setVisibility(View.VISIBLE);
        }
    }

    private void showTracksShimmer(boolean show) {
        StateMessage.setShimmer(findViewById(R.id.tracks_shimmer), show);
    }

    private static List<String> uris(List<SongModel> songs) {
        List<String> uris = new ArrayList<>(songs.size());
        for (SongModel s : songs) {
            if (s.getUri() != null) uris.add(s.getUri());
        }
        return uris;
    }

    private static String firstImageUrl(Image[] images) {
        return images != null && images.length > 0 ? images[0].getUrl() : null;
    }

    private static String bareId(String idOrUri) {
        if (idOrUri == null) return null;
        return idOrUri.contains(":") ? idOrUri.substring(idOrUri.lastIndexOf(':') + 1) : idOrUri;
    }

    //#endregion
}
