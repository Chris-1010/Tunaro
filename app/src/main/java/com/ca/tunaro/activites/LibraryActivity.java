package com.ca.tunaro.activites;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.adapters.LibrarySongAdapter;
import com.ca.tunaro.interfaces.Library_RecyclerViewInterface;
import com.ca.tunaro.R;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.ca.tunaro.models.SongModel;

import java.util.ArrayList;
import java.util.List;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.requests.data.tracks.GetTrackRequest;

public class LibraryActivity extends BaseActivity implements Library_RecyclerViewInterface {
    private static final String TAG = "LibraryActivity";

    private MainActivity mainActivity;
    private SpotifyApi spotifyApi;
    private LibrarySongAdapter adapter;
    private DatabaseHelper dbHelper;
    private final List<SongModel> allSongs = new ArrayList<>();
    private EditText searchBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_library);

        Log.d(TAG, "LibraryActivity onCreate MainActivity.getInstance()");
        mainActivity = MainActivity.getInstance();

        if (mainActivity != null) {
            spotifyApi = mainActivity.getSpotifyApi();
        } else {
            showToast("Could not connect to Spotify");
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

    private void loadSongsWithNotes() {
        if (spotifyApi == null) {
            showToast("Spotify API not available yet. Please try again later.");
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
        loadSongsSequentially(songIds, index, null);
    }

    private void loadSongsSequentially(List<String> songIds, int index, Runnable onComplete) {
        if (index >= songIds.size()) {
            setLoadingState(false);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        String songId = songIds.get(index);
        GetTrackRequest getTrackRequest = spotifyApi.getTrack(songId).build();

        getTrackRequest.executeAsync()
                .thenAccept(track -> {
                    runOnUiThread(() -> {
                        AlbumSimplified trackAlbum = track.getAlbum();
                        String imageUrl = trackAlbum.getImages()[0].getUrl();

                        // Create Album object
                        SongModel.Album album = new SongModel.Album(
                                trackAlbum.getId(),
                                trackAlbum.getName(),
                                trackAlbum.getAlbumType().getType(),
                                trackAlbum.getReleaseDate(),
                                imageUrl
                        );

                        // Extract ISRC from external IDs
                        String isrc = "";
                        if (track.getExternalIds() != null && track.getExternalIds().getExternalIds() != null) {
                            java.util.Map<String, String> externalIds = track.getExternalIds().getExternalIds();
                            isrc = externalIds.getOrDefault("isrc", "");
                        }

                        SongModel songModel = new SongModel(
                                track.getId(),
                                track.getName(),
                                track.getArtists(),
                                track.getDurationMs(),
                                track.getUri(),
                                track.getPopularity(),
                                album,
                                isrc,
                                null
                        );

                        allSongs.add(songModel);
                        adapter.addSong(songModel);

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            loadSongsSequentially(songIds, index + 1, onComplete);
                        }, 100);
                    });
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        showToast("Error loading song: " + throwable.getMessage());
                        loadSongsSequentially(songIds, index + 1, onComplete);
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

                        AlbumSimplified trackAlbum = track.getAlbum();
                        String imageUrl = trackAlbum.getImages()[0].getUrl();

                        // Create Album object
                        SongModel.Album album = new SongModel.Album(
                                trackAlbum.getId(),
                                trackAlbum.getName(),
                                trackAlbum.getAlbumType().getType(),
                                trackAlbum.getReleaseDate(),
                                imageUrl
                        );

                        // Extract ISRC from external IDs
                        String isrc = "";
                        if (track.getExternalIds() != null && track.getExternalIds().getExternalIds() != null) {
                            java.util.Map<String, String> externalIds = track.getExternalIds().getExternalIds();
                            isrc = externalIds.getOrDefault("isrc", "");
                        }

                        // Create SongModel from Spotify Track
                        SongModel songModel = new SongModel(
                                track.getId(),
                                track.getName(),
                                track.getArtists(),
                                track.getDurationMs(),
                                track.getUri(),
                                track.getPopularity(),
                                album,
                                isrc,
                                null // Unnecessary for library view
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
                        showToast("Error loading song details: " + throwable.getMessage());
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

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}