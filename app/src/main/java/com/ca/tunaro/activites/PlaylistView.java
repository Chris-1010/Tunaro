package com.ca.tunaro.activites;

import android.content.Intent;
import android.content.SharedPreferences;
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
import com.ca.tunaro.utils.PlaylistSetup;
import com.ca.tunaro.R;
import com.ca.tunaro.utils.SelectedPlaylistHolder;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.adapters.Song_RecyclerViewAdapter;
import com.ca.tunaro.interfaces.Song_RecyclerViewInterface;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import se.michaelthelin.spotify.SpotifyApi;

public class PlaylistView extends BaseActivity implements Song_RecyclerViewInterface {
    private static final String TAG = "PlaylistView";

    private PlaylistModel selectedPlaylist;
    private Song_RecyclerViewAdapter adapter;

    // Searching
    private ImageView searchIcon;
    private EditText searchBar;
    private ArrayList<SongModel> allSongs = new ArrayList<>();

    // Sorting
    private ImageView sortIcon;
    private ImageView sortDirectionIcon;
    private Spinner sortSpinner;
    private boolean isAscending = false;
    private SharedPreferences prefs;
    private static final String SORT_PREF_KEY = "playlist_sort_option";
    private static final String SORT_DIRECTION_KEY = "playlist_sort_direction";

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
        RecyclerView recyclerView = findViewById(R.id.song_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize adapter, empty for now
        adapter = new Song_RecyclerViewAdapter(this, this, this, new ArrayList<>());
        recyclerView.setAdapter(adapter);

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
    }

    private void setupSearch() {
        searchIcon = findViewById(R.id.search_icon);
        searchBar = findViewById(R.id.search_bar);

        searchIcon.setVisibility(View.VISIBLE);

        searchIcon.setOnClickListener(v -> {
            if (searchBar.getVisibility() == View.GONE) {
                // Show search bar
                searchBar.setVisibility(View.VISIBLE);
                searchBar.requestFocus();
            } else {
                // Hide search bar and clear search
                searchBar.setVisibility(View.GONE);
                searchBar.setText("");
                adapter.updateSongs(allSongs);
            }
        });

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
            adapter.updateSongs(allSongs);
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

        adapter.updateSongs(filteredList);
    }

    private void setupSorting() {
        sortIcon = findViewById(R.id.sort_icon);
        sortDirectionIcon = findViewById(R.id.sort_direction_icon);
        sortSpinner = findViewById(R.id.sort_spinner);

        sortIcon.setVisibility(View.VISIBLE);

        // Initialize SharedPreferences
        prefs = getSharedPreferences("PlaylistPrefs", MODE_PRIVATE);

        // Set up spinner adapter
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Date Added", "Last Listened", "Title", "Length", "Artist"}
        );
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);

        // Load saved preferences
        int savedSortOption = prefs.getInt(SORT_PREF_KEY, 0); // Default to Date Added
        isAscending = prefs.getBoolean(SORT_DIRECTION_KEY, false); // Default to descending

        sortSpinner.setSelection(savedSortOption);

        // Apply initial sort with contextual info
        sortSongs(savedSortOption);

        updateSortDirectionIcon();

        // Set up click listeners
        sortIcon.setOnClickListener(v -> {
            if (sortSpinner.getVisibility() == View.GONE) {
                sortSpinner.setVisibility(View.VISIBLE);
                sortDirectionIcon.setVisibility(View.VISIBLE);
            } else {
                sortSpinner.setVisibility(View.GONE);
                sortDirectionIcon.setVisibility(View.GONE);
            }
        });

        sortDirectionIcon.setOnClickListener(v -> {
            isAscending = !isAscending;
            updateSortDirectionIcon();
            prefs.edit().putBoolean(SORT_DIRECTION_KEY, isAscending).apply();
            sortSongs(sortSpinner.getSelectedItemPosition());
        });

        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(SORT_PREF_KEY, position).apply();
                sortSongs(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void updateSortDirectionIcon() {
        sortDirectionIcon.setImageResource(isAscending ?
                R.drawable.ic_arrow_upward :
                R.drawable.ic_arrow_downward);
    }

    private void sortSongs(int sortOption) {
        if (adapter == null || selectedPlaylist == null) return;

        ArrayList<SongModel> songs = new ArrayList<>(allSongs);

        Comparator<SongModel> comparator = null;
        switch (sortOption) {
            case 0: // Date Added
                comparator = Comparator.comparing(SongModel::getDateAddedToPlaylist);
                break;
            case 1: // Last Listened
                DatabaseHelper dbHelper = new DatabaseHelper(this);
                java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
                inputFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

                comparator = (song1, song2) -> {
                    // Get timestamps for both songs
                    String timestamp1 = dbHelper.getMostRecentListenTimestamp(song1.getId());
                    String timestamp2 = dbHelper.getMostRecentListenTimestamp(song2.getId());

                    Date date1 = null;
                    Date date2 = null;

                    if (timestamp1 != null) {
                        try {
                            date1 = inputFormat.parse(timestamp1);
                        } catch (java.text.ParseException e) {
                            // Leave as null
                        }
                    }
                    if (timestamp2 != null) {
                        try {
                            date2 = inputFormat.parse(timestamp2);
                        } catch (java.text.ParseException e) {
                            // Leave as null
                        }
                    }

                    // Compare dates
                    if (date1 != null && date2 != null) {
                        return date1.compareTo(date2);
                    } else if (date1 != null) {
                        return 1; // date1 comes after null dates
                    } else if (date2 != null) {
                        return -1; // date2 comes after null dates
                    } else {
                        return 0; // both null, equal
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
        }

        if (comparator != null) {
            if (!isAscending) {
                comparator = comparator.reversed();
            }
            songs.sort(comparator);
            adapter.updateSongs(songs);

            adapter.updateSortContext(sortOption);
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
     * Launch a new activity (SongView) that shows a detailed display (last listened to, popularity, release date, etc.), very similar to the PlaylistView for the top half (showing the album cover and the name underneath.
     * The user can add details about the song like where they heard the song first, favourite parts of the song, ratings, and general notes.
     */
    @Override
    public void onItemClick(int position, View itemView) {
        // Change over to the PlaylistView Activity

        SongModel clickedSong = adapter.getSongs().get(position);

        // Set the selected song in the singleton
        MainActivity mainActivity = MainActivity.getInstance();
        SelectedSongHolder.getInstance().setSelectedSong(clickedSong, mainActivity);

        // Start the SongView activity
        Intent intent = new Intent(this, SongView.class);
        startActivity(intent);
    }

    // Quick play functionality
    public void onAlbumCoverLongClick(int position) {
        SongModel clickedSong = adapter.getSongs().get(position);

        // Play the song immediately using PlaybackManager
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