package com.ca.tunaro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

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
}