package com.ca.tunaro.activites;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.utils.ColorExtractor;
import com.ca.tunaro.utils.PlaylistSetup;
import com.ca.tunaro.R;
import com.ca.tunaro.utils.SelectedPlaylistHolder;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.adapters.QueueLineDecoration;
import com.ca.tunaro.adapters.Song_RecyclerViewAdapter;
import com.ca.tunaro.interfaces.Song_RecyclerViewInterface;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import se.michaelthelin.spotify.SpotifyApi;

public class PlaylistView extends BaseActivity implements Song_RecyclerViewInterface {
    private static final String TAG = "PlaylistView";

    private PlaylistModel selectedPlaylist;
    private Song_RecyclerViewAdapter adapter;
    private RecyclerView recyclerView;
    private QueueLineDecoration queueLineDecoration;
    // URI of the song currently highlighted as "now playing", to avoid
    // refreshing rows on every play/pause when the song hasn't changed.
    private String highlightedUri;

    // Searching
    private EditText searchBar;
    private ArrayList<SongModel> allSongs = new ArrayList<>();

    // Sorting
    private ImageView sortDirectionIcon;
    private Spinner sortSpinner;
    private boolean isAscending = false;
    private SharedPreferences prefs;
    private static final String SORT_PREF_KEY = "playlist_sort_option";
    private static final String SORT_DIRECTION_KEY = "playlist_sort_direction";

    // Filter toggle
    private ImageView filterToggleIcon;
    private View controlsContainer;
    private static final String CONTROLS_VISIBLE_KEY = "controls_visible";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_playlist_view);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Retrieve the selected playlist
        selectedPlaylist = SelectedPlaylistHolder.getInstance().getSelectedPlaylist();

        if (selectedPlaylist == null) {
            // Handle error - playlist not found
            showToast("Error: No playlist selected");
            finish();
            return;
        }

        // Set up initial UI with playlist details
        setupInitialUI();

        // Set up RecyclerView
        recyclerView = findViewById(R.id.song_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize adapter, empty for now
        adapter = new Song_RecyclerViewAdapter(this, this, new ArrayList<>());
        recyclerView.setAdapter(adapter);
        queueLineDecoration = new QueueLineDecoration(adapter);
        recyclerView.addItemDecoration(queueLineDecoration);

        // Swipe an album cover right to add/remove the song from the queue.
        adapter.setOnQueueChangeListener((position, added) -> {
            showToast(added ? "Added to queue" : "Removed from queue");
            recyclerView.invalidateItemDecorations();
        });

        // Show loading state while fetching songs
        showShimmerLoading(true);

        // Get SpotifyApi instance from MainActivity
        SpotifyApi spotifyApi;
        try {
            spotifyApi = MainActivity.getInstance().getSpotifyApi();
            if (spotifyApi == null) throw new Exception("SpotifyApi not available");
        } catch (Exception e) {
            finish();
            return;
        }

        // Load songs with progress updates
        PlaylistSetup.getPlaylistSongs(selectedPlaylist.getId(), spotifyApi,
                        (loadedSongs, currentCount, totalCount) -> runOnUiThread(() -> {
                            // Update adapter progressively as songs load
                            allSongs = new ArrayList<>(loadedSongs);
                            adapter.updateSongs(loadedSongs);

                            // Hide shimmer when songs start loading in
                            if (currentCount > 0) {
                                showShimmerLoading(false);
                            }

                            Log.d(TAG, "Loaded " + currentCount + " / " + totalCount + " songs");
                        }))
                .thenAccept(result -> {
                    if (result.songs == null) {
                        throw new CompletionException(new Exception("No songs retrieved"));
                    }
                    selectedPlaylist.setSongs(result.songs);
                    allSongs = new ArrayList<>(result.songs);

                    runOnUiThread(() -> {
                        showShimmerLoading(false);
                        adapter.updateSongs(result.songs);

                        // Setup search and sorting after songs are loaded
                        setupSearch();
                        setupSorting();
                        setupFilterToggle();

                        // Only cache if needed
                        if (result.needsCaching) {
                            CompletableFuture.runAsync(() -> {
                                PlaylistSetup.cacheSongsInBackground(selectedPlaylist.getId(), result.songs);
                            });
                        } else {
                            Log.d(TAG, "Skipping cache - all songs already cached");
                        }
                    });
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        showShimmerLoading(false);
                        showToast("Error loading songs: " + throwable.getMessage());
                    });
                    return null;
                });
    }

    private void setupInitialUI() {
        String playlistName = selectedPlaylist.getPlaylistName();
        int songCount = selectedPlaylist.getSongCount();
        String playlistImage = selectedPlaylist.getImage();

        TextView nameView = findViewById(R.id.detailed_playlistName);
        TextView countView = findViewById(R.id.detailed_songCount);
        ImageView imageView = findViewById(R.id.detailed_playlistCover);

        // Enable marquee for long playlist names
        nameView.setSelected(true);
        nameView.setText(playlistName);
        countView.setText(getString(R.string.song_count, songCount));

        // Load main playlist image
        Glide.with(this)
                .load(playlistImage)
                .placeholder(R.drawable.playlist_placeholder)
                .error(R.drawable.playlist_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView);

        // Tap playlist cover to play from the first listed song
        imageView.setOnClickListener(v -> {
            ArrayList<SongModel> currentList = adapter.getSongs();
            if (currentList == null || currentList.isEmpty()) {
                showToast("No songs loaded yet");
                return;
            }
            if (!playbackManager.isConnected()) {
                showToast("Connecting to Spotify...");
                playbackManager.connectSpotify(this, () -> {
                    playbackManager.playQueue(currentList, 0);
                    onQueueCreated();
                    showToast("Playing from " + currentList.get(0).getName());
                });
            } else {
                playbackManager.playQueue(currentList, 0);
                onQueueCreated();
                showToast("Playing from " + currentList.get(0).getName());
            }
        });

        // Set up Collapsible Header
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Handle toolbar navigation click
        toolbar.setNavigationOnClickListener(v -> finish());

        // Get references to views
        ImageView collapsedImage = findViewById(R.id.collapsed_playlist_image);
        AppBarLayout appBarLayout = findViewById(R.id.app_bar);
        CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsing_toolbar);

        // Set playlist name as toolbar title
        collapsingToolbar.setTitle(selectedPlaylist.getPlaylistName());
        collapsingToolbar.setCollapsedTitleTextColor(getResources().getColor(android.R.color.white, getTheme()));
        collapsingToolbar.setExpandedTitleColor(getResources().getColor(android.R.color.transparent, getTheme()));

        // Load the same image into collapsed view
        Glide.with(this)
                .load(selectedPlaylist.getImage())
                .placeholder(R.drawable.playlist_placeholder)
                .error(R.drawable.playlist_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(collapsedImage);

        // Add AppBarLayout offset listener to fade in/out the collapsed image. The card
        // wraps the image for rounded corners + border, so fade the card to keep them in sync.
        View collapsedImageCard = findViewById(R.id.collapsed_playlist_image_card);
        appBarLayout.addOnOffsetChangedListener((appBar, verticalOffset) -> {
            float percentage = Math.abs(verticalOffset) / (float) appBar.getTotalScrollRange();
            collapsedImageCard.setAlpha(percentage);
        });

        // Extract colors from the playlist image and apply dynamic background
        ColorExtractor.extractColors(this, playlistImage, new ColorExtractor.ColorExtractionCallback() {
            @Override
            public void onColorExtracted(int dominantColor, int vibrantColor) {
                applyGradientBackground(ColorExtractor.pickBackgroundColor(dominantColor, vibrantColor));
            }

            @Override
            public void onError() {
                applyGradientBackground(Color.parseColor("#424242"));
            }
        });
    }

    private void applyGradientBackground(int dominantColor) {
        CoordinatorLayout mainLayout = findViewById(R.id.main);

        if (mainLayout != null) {
            GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TR_BL, // Top-right to bottom-left
                    new int[]{dominantColor, Color.BLACK});

            mainLayout.setBackground(gradient);
        }
    }

    private void setupSearch() {
        searchBar = findViewById(R.id.search_bar);

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void filterSongs(String query) {
        if (query == null || query.isEmpty()) {
            // Re-apply current sort when clearing search
            sortSongs(sortSpinner.getSelectedItemPosition());
            return;
        }

        ArrayList<SongModel> filteredList = new ArrayList<>();
        String lowercaseQuery = query.toLowerCase().trim();

        for (SongModel song : allSongs) {  // Use allSongs for filtering
            if (song.getName().toLowerCase().contains(lowercaseQuery) ||
                    song.getArtist().toLowerCase().contains(lowercaseQuery) ||
                    song.getAlbumName().toLowerCase().contains(lowercaseQuery)) {
                filteredList.add(song);
            }
        }

        // Apply current sort to filtered results
        int currentSortOption = sortSpinner.getSelectedItemPosition();
        applySortToList(filteredList, currentSortOption);
        adapter.updateSongs(filteredList);
        queueLineDecoration.setQueueMatchesDisplay(false);

        adapter.updateSortContext(currentSortOption);
        pushContextualMapsToAdapter(filteredList, currentSortOption);

    }

    private void setupSorting() {
        sortDirectionIcon = findViewById(R.id.sort_direction_icon);
        sortSpinner = findViewById(R.id.sort_spinner);

        // Initialize SharedPreferences
        prefs = getSharedPreferences("PlaylistPrefs", MODE_PRIVATE);

        // Set up spinner adapter with custom view
        String[] sortOptions = new String[]{"Date Added", "Last Listened", "Title", "Length", "Artist", "Popularity", "Listen Count", "Release Date"};
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<String>(this, R.layout.spinner_dropdown_item, sortOptions) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                // Create a completely custom TextView programmatically for selected item
                TextView textView = new TextView(getContext());
                textView.setText(getItem(position));
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(16);
                textView.setPadding(0, 0, 0, 0);  // No padding - spinner already has padding
                textView.setGravity(android.view.Gravity.CENTER_VERTICAL);
                textView.setSingleLine(true);
                textView.setEllipsize(null);

                // Set layout parameters to ensure text isn't clipped
                android.view.ViewGroup.LayoutParams params = new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                );
                textView.setLayoutParams(params);

                return textView;
            }
        };
        sortAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);

        // Load saved preferences
        int savedSortOption = prefs.getInt(SORT_PREF_KEY, 0); // Default to Date Added
        isAscending = prefs.getBoolean(SORT_DIRECTION_KEY, false); // Default to descending

        sortSpinner.setSelection(savedSortOption);

        updateSortDirectionIcon();

        // Set up click listener for sort direction
        sortDirectionIcon.setOnClickListener(v -> {
            isAscending = !isAscending;
            updateSortDirectionIcon();
            prefs.edit().putBoolean(SORT_DIRECTION_KEY, isAscending).apply();

            // If there's an active search, re-filter to maintain search results
            String currentQuery = searchBar.getText().toString();
            if (!currentQuery.isEmpty()) {
                filterSongs(currentQuery);
            } else {
                sortSongs(sortSpinner.getSelectedItemPosition());
            }
        });

        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(SORT_PREF_KEY, position).apply();

                // If there's an active search, re-filter to maintain search results
                String currentQuery = searchBar.getText().toString();
                if (!currentQuery.isEmpty()) {
                    filterSongs(currentQuery);
                } else {
                    sortSongs(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupFilterToggle() {
        filterToggleIcon = findViewById(R.id.filter_toggle_icon);
        controlsContainer = findViewById(R.id.controls_container);

        // Load saved visibility state
        boolean controlsVisible = prefs.getBoolean(CONTROLS_VISIBLE_KEY, true); // Default to visible
        controlsContainer.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);

        // Toggle controls visibility when filter icon is clicked
        filterToggleIcon.setOnClickListener(v -> {
            boolean isVisible = controlsContainer.getVisibility() == View.VISIBLE;
            controlsContainer.setVisibility(isVisible ? View.GONE : View.VISIBLE);

            // Save the state
            prefs.edit().putBoolean(CONTROLS_VISIBLE_KEY, !isVisible).apply();

            // When hiding controls, reset to Date Added (Newest first) and clear search
            if (isVisible) {
                searchBar.setText("");
                isAscending = false; // Newest first
                sortSpinner.setSelection(0); // Date Added
                prefs.edit().putInt(SORT_PREF_KEY, 0).apply();
                prefs.edit().putBoolean(SORT_DIRECTION_KEY, false).apply();
                updateSortDirectionIcon();
                sortSongs(0);
            }
        });
    }

    private void updateSortDirectionIcon() {
        sortDirectionIcon.setImageResource(isAscending ?
                R.drawable.ic_arrow_upward :
                R.drawable.ic_arrow_downward);
    }

    private void applySortToList(ArrayList<SongModel> songs, int sortOption) {
        Comparator<SongModel> comparator = null;

        switch (sortOption) {
            case 0: // Date Added
                comparator = Comparator.comparing(SongModel::getDateAddedToPlaylist, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case 1: // Last Listened
                DatabaseHelper dbHelper = new DatabaseHelper(this);
                List<String> songIds = new ArrayList<>();
                for (SongModel song : songs) {
                    songIds.add(song.getId());
                }

                Map<String, String> timestampMap = dbHelper.getVariantMostRecentListenTimestampsBatch(songIds);
                dbHelper.close();

                // Support both timestamp formats (milliseconds included for Tunaro records, not for Spotify)
                java.text.SimpleDateFormat formatWithMillis = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
                java.text.SimpleDateFormat formatWithoutMillis = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault());
                formatWithMillis.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                formatWithoutMillis.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

                comparator = (song1, song2) -> {
                    String timestamp1 = timestampMap.get(song1.getId());
                    String timestamp2 = timestampMap.get(song2.getId());

                    Date date1 = null;
                    Date date2 = null;

                    if (timestamp1 != null) {
                        try {
                            try {
                                date1 = formatWithMillis.parse(timestamp1);
                            } catch (java.text.ParseException e) {
                                date1 = formatWithoutMillis.parse(timestamp1);
                            }
                        } catch (java.text.ParseException e) {
                            // Leave as null
                        }
                    }
                    if (timestamp2 != null) {
                        try {
                            try {
                                date2 = formatWithMillis.parse(timestamp2);
                            } catch (java.text.ParseException e) {
                                date2 = formatWithoutMillis.parse(timestamp2);
                            }
                        } catch (java.text.ParseException e) {
                            // Leave as null
                        }
                    }

                    if (date1 != null && date2 != null) {
                        return date1.compareTo(date2);
                    } else if (date1 != null) {
                        return 1;
                    } else if (date2 != null) {
                        return -1;
                    } else {
                        return 0;
                    }
                };
                break;
            case 2: // Title
                comparator = Comparator.comparing(SongModel::getName, String.CASE_INSENSITIVE_ORDER);
                break;
            case 3: // Length
                comparator = Comparator.comparingInt(SongModel::getDuration);
                break;
            case 4: // Artist
                comparator = Comparator.comparing(SongModel::getArtist, String.CASE_INSENSITIVE_ORDER);
                break;
            case 5: // Popularity (variant-aware: uses max across ISRC siblings)
                DatabaseHelper popularityDbHelper = new DatabaseHelper(this);
                List<String> popularitySongIds = new ArrayList<>();
                for (SongModel song : songs) {
                    popularitySongIds.add(song.getId());
                }
                Map<String, Integer> popularityMap = popularityDbHelper.getVariantPopularityBatch(popularitySongIds);
                popularityDbHelper.close();
                comparator = (song1, song2) -> Integer.compare(
                        popularityMap.getOrDefault(song1.getId(), song1.getPopularity()),
                        popularityMap.getOrDefault(song2.getId(), song2.getPopularity()));
                break;
            case 6: // Listen Count (variant-aware: sums across ISRC siblings)
                DatabaseHelper listenCountDbHelper = new DatabaseHelper(this);
                List<String> listenCountSongIds = new ArrayList<>();
                for (SongModel song : songs) {
                    listenCountSongIds.add(song.getId());
                }

                Map<String, Integer> listenCountMap = listenCountDbHelper.getVariantListenCountsBatch(listenCountSongIds);
                listenCountDbHelper.close();

                comparator = (song1, song2) -> {
                    int count1 = listenCountMap.getOrDefault(song1.getId(), 0);
                    int count2 = listenCountMap.getOrDefault(song2.getId(), 0);
                    return Integer.compare(count1, count2);
                };
                break;
            case 7: // Release Date
                comparator = (song1, song2) -> {
                    String date1 = song1.getReleaseDate();
                    String date2 = song2.getReleaseDate();

                    if (date1 != null && date2 != null) {
                        return date1.compareTo(date2);
                    } else if (date1 != null) {
                        return 1;
                    } else if (date2 != null) {
                        return -1;
                    } else {
                        return 0;
                    }
                };
                break;
        }

        if (comparator != null) {
            if (!isAscending) {
                comparator = comparator.reversed();
            }
            songs.sort(comparator);
        }
    }

    private void sortSongs(int sortOption) {
        if (adapter == null || selectedPlaylist == null) return;

        ArrayList<SongModel> songs = new ArrayList<>(allSongs);

        applySortToList(songs, sortOption);
        adapter.updateSongs(songs);
        queueLineDecoration.setQueueMatchesDisplay(false);

        adapter.updateSortContext(sortOption);
        pushContextualMapsToAdapter(songs, sortOption);
    }

    private void pushContextualMapsToAdapter(List<SongModel> songs, int sortOption) {
        List<String> ids = new ArrayList<>();
        for (SongModel song : songs) ids.add(song.getId());

        if (sortOption == 1) {
            DatabaseHelper db = new DatabaseHelper(this);
            adapter.updateLastListenedMap(db.getVariantMostRecentListenTimestampsBatch(ids));
            db.close();
        } else if (sortOption == 5) {
            DatabaseHelper db = new DatabaseHelper(this);
            adapter.updatePopularityMap(db.getVariantPopularityBatch(ids));
            db.close();
        } else if (sortOption == 6) {
            DatabaseHelper db = new DatabaseHelper(this);
            adapter.updateListenCounts(db.getVariantListenCountsBatch(ids));
            db.close();
        }
    }

    private void showShimmerLoading(boolean isLoading) {
        View shimmerView = findViewById(R.id.shimmer_view_container);
        RecyclerView recyclerView = findViewById(R.id.song_recycler_view);

        if (shimmerView != null) {
            shimmerView.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (isLoading) {
                ((com.facebook.shimmer.ShimmerFrameLayout) shimmerView).startShimmer();
            } else {
                ((com.facebook.shimmer.ShimmerFrameLayout) shimmerView).stopShimmer();
            }
        }

        recyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear the selected playlist when the activity is destroyed
        SelectedPlaylistHolder.getInstance().clearSelectedPlaylist();
    }

    /**
     * Launch SongView activity when selected
     */
    @Override
    public void onItemClick(int position, View itemView) {
        SongModel clickedSong = adapter.getSongs().get(position);

        // Set the selected song in the singleton
        MainActivity mainActivity = MainActivity.getInstance();
        SelectedSongHolder.getInstance().setSelectedSong(clickedSong);

        // Start the SongView activity
        Intent intent = new Intent(this, SongView.class);
        intent.putExtra("playlist_name", selectedPlaylist.getPlaylistName());
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // A queue was just created from this screen: queued rows need their lime
    // edge, so refresh the whole list once and reset the highlight tracker.
    private void onQueueCreated() {
        queueLineDecoration.setQueueMatchesDisplay(true);
        if (adapter != null) adapter.notifyDataSetChanged();
        SongModel current = playbackManager.getCurrentSong();
        highlightedUri = current != null ? current.getUri() : null;
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying, SongModel currentSong) {
        super.onPlaybackStateChanged(isPlaying, currentSong);
        if (adapter == null) return;
        // The row highlight only depends on which song is current, not on the
        // play/paused state. A pure play<->pause toggle needs no rebind; only
        // when the current song changes do the old and new rows update.
        String newUri = currentSong != null ? currentSong.getUri() : null;
        if (!java.util.Objects.equals(newUri, highlightedUri)) {
            // The now-playing highlight moved, but the queue membership of rows
            // shifts too: the song that just started playing is no longer
            // "upcoming", so its lime queued edge must clear. Rebind the whole
            // list so every row re-evaluates its queued state, and refresh the
            // connecting line decoration to match.
            adapter.notifyDataSetChanged();
            recyclerView.invalidateItemDecorations();
            highlightedUri = newUri;
        }
    }

    // Start queue from this position
    @Override
    public void onAlbumCoverClick(int position) {
        ArrayList<SongModel> currentList = adapter.getSongs();
        if (!playbackManager.isConnected()) {
            showToast("Connecting to Spotify...");
            playbackManager.connectSpotify(this, () -> {
                playbackManager.playQueue(currentList, position);
                onQueueCreated();
            });
        } else {
            playbackManager.playQueue(currentList, position);
            onQueueCreated();
        }
    }

    // Play song individually (no queue)
    @Override
    public void onAlbumCoverLongClick(int position) {
        SongModel clickedSong = adapter.getSongs().get(position);

        if (!playbackManager.isConnected()) {
            showToast("Connecting to Spotify...");
            playbackManager.connectSpotify(this, () -> {
                playbackManager.playSong(clickedSong);
                showToast("Playing " + clickedSong.getName());
            });
        } else {
            playbackManager.playSong(clickedSong);
            showToast("Playing " + clickedSong.getName());
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}