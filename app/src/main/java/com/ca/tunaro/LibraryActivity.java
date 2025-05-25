package com.ca.tunaro;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.requests.data.tracks.GetTrackRequest;

public class LibraryActivity extends AppCompatActivity implements Library_RecyclerViewInterface {
    private MainActivity mainActivity;
    private SpotifyApi spotifyApi;
    private LibrarySongAdapter adapter;
    private DatabaseHelper dbHelper;
    private List<SongModel> allSongs = new ArrayList<>();
    private EditText searchBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        mainActivity = MainActivity.getInstance();

        if (mainActivity != null) {
            spotifyApi = mainActivity.getSpotifyApi();
        } else {
            Toast.makeText(this, "Could not connect to Spotify", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize DatabaseHelper
        dbHelper = new DatabaseHelper(this);

        // Initialize RecyclerView
        RecyclerView recyclerView = findViewById(R.id.library_recycler_view);
        adapter = new LibrarySongAdapter(this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Initialize SearchBar
        searchBar = findViewById(R.id.search_bar);
        setupSearchBar();

        Button importButton = findViewById(R.id.import_button);
        importButton.setOnClickListener(v -> importData());

        Button exportButton = findViewById(R.id.export_button);
        exportButton.setOnClickListener(v -> exportData());

        // Load songs with notes
        loadSongsWithNotes();
    }

    private void setupSearchBar() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadSongsWithNotes() {
        if (spotifyApi == null) {
            Toast.makeText(this, "Spotify API not available yet. Please try again later.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get all song IDs that have notes
        List<String> songIds = dbHelper.getSongIdsWithNotes();

        // Show loading state
        setLoadingState(true);

        // Clear existing songs
        allSongs.clear();
        adapter.clearSongs();

        // Load songs one at a time sequentially
        loadSongsSequentially(songIds, 0);
    }

    private void loadSongsSequentially(List<String> songIds, int index) {
        if (index >= songIds.size()) {
            setLoadingState(false);
            return;
        }

        String songId = songIds.get(index);
        GetTrackRequest getTrackRequest = spotifyApi.getTrack(songId).build();

        getTrackRequest.executeAsync()
                .thenAccept(track -> {
                    runOnUiThread(() -> {
                        SongModel songModel = new SongModel(
                                track.getId(),
                                track.getName(),
                                track.getArtists(),
                                track.getDurationMs(),
                                track.getUri(),
                                track.getPopularity(),
                                track.getAlbum().getName(),
                                track.getAlbum().getImages()[0].getUrl(),
                                null,
                                track.getAlbum().getReleaseDate()
                        );

                        allSongs.add(songModel);  // Add to our stored list
                        adapter.addSong(songModel);

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            loadSongsSequentially(songIds, index + 1);
                        }, 100);
                    });
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                "Error loading song: " + throwable.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        loadSongsSequentially(songIds, index + 1);
                    });
                    return null;
                });
    }

    private void filterSongs(String query) {
        if (query == null || query.isEmpty()) {
            adapter.updateSongs(allSongs);
            return;
        }

        List<SongModel> filteredList = new ArrayList<>();
        String lowercaseQuery = query.toLowerCase().trim();

        for (SongModel song : allSongs) {
            if (song.getName().toLowerCase().contains(lowercaseQuery) ||
                    song.getArtist().toLowerCase().contains(lowercaseQuery) ||
                    song.getAlbumName().toLowerCase().contains(lowercaseQuery)) {
                filteredList.add(song);
            }
        }

        adapter.updateSongs(filteredList);
    }

    @Override
    public void onItemClick(int position) {
        SongModel selectedSong = adapter.getSongs().get(position);

        // Show loading state if needed
        setLoadingState(true);

        // Get song details from Spotify API
        GetTrackRequest getTrackRequest = spotifyApi.getTrack(selectedSong.getId())
                .build();

        getTrackRequest.executeAsync()
                .thenAccept(track -> {
                    runOnUiThread(() -> {
                        setLoadingState(false);

                        // Create SongModel from Spotify Track
                        SongModel songModel = new SongModel(
                                track.getId(),
                                track.getName(),
                                track.getArtists(),
                                track.getDurationMs(),
                                track.getUri(),
                                track.getPopularity(),
                                track.getAlbum().getName(),
                                track.getAlbum().getImages()[0].getUrl(),
                                null, // We don't have dateAddedToPlaylist for library view
                                track.getAlbum().getReleaseDate()
                        );

                        // Set the selected song in the singleton
                        SelectedSongHolder.getInstance().setSelectedSong(songModel, mainActivity);

                        // Navigate to SongView
                        Intent intent = new Intent(this, SongView.class);
                        intent.putExtra("source", "library");
                        startActivity(intent);
                    });
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        setLoadingState(false);
                        Toast.makeText(this,
                                "Error loading song details: " + throwable.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    private void setLoadingState(boolean isLoading) {
        // Implement loading state UI changes here
        View loadingView = findViewById(R.id.loading_view);
        if (loadingView != null) {
            loadingView.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }

    // Handle the import button click
    public void importData() {
        importLauncher.launch(new String[]{"application/json"});
    }

    private ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        DatabaseHelper.ImportStats stats = DatabaseHelper.importFromUri(this, uri);
                        Toast.makeText(this, stats.getSummary(), Toast.LENGTH_LONG).show();

                        // Reload the songs list to reflect any imported data
                        loadSongsWithNotes();
                    } catch (Exception e) {
                        Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    // Handle the export button click
    public void exportData() {
        String fileName = "tunaro_backup_" +
                new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date()) +
                ".json";
        exportLauncher.launch(fileName);
    }

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    try {
                        String jsonData = DatabaseHelper.generateExportJson(this);
                        DatabaseHelper.writeExportToUri(this, uri, jsonData);
                        Toast.makeText(this, "Data exported successfully!", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );
}