package com.ca.tunaro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.requests.data.tracks.GetTrackRequest;

public class LibraryFragment extends Fragment implements Library_RecyclerViewInterface {
    private MainActivity mainActivity;
    private SpotifyApi spotifyApi;
    private View view;
    private LibrarySongAdapter adapter;
    private DatabaseHelper dbHelper;
    private List<SongModel> allSongs = new ArrayList<>();
    private EditText searchBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) requireActivity();
        spotifyApi = mainActivity.getSpotifyApi();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_library, container, false);

        // Initialize DatabaseHelper
        dbHelper = new DatabaseHelper(requireContext());

        // Initialize RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.library_recycler_view);
        adapter = new LibrarySongAdapter(requireContext(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // Initialize SearchBar
        searchBar = view.findViewById(R.id.search_bar);
        setupSearchBar();

        // Load songs with notes
        loadSongsWithNotes();

        return view;
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
            // Show a message to the user
            Toast.makeText(requireContext(), "Spotify API not available yet. Please try again later.", Toast.LENGTH_SHORT).show();
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
                    mainActivity.runOnUiThread(() -> {
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
                    mainActivity.runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
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

        for (SongModel song : allSongs) {  // Use allSongs instead of adapter.getSongs()
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
        // Maybe add a progress bar instead
        setLoadingState(true);

        // Get song details from Spotify API
        GetTrackRequest getTrackRequest = spotifyApi.getTrack(selectedSong.getId())
                .build();

        getTrackRequest.executeAsync()
                .thenAccept(track -> {
                    mainActivity.runOnUiThread(() -> {
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
                                null,
                                track.getAlbum().getReleaseDate()
                        );

                        // Set the selected song in the singleton
                        SelectedSongHolder.getInstance().setSelectedSong(songModel, mainActivity);

                        // Navigate to SongView
                        Intent intent = new Intent(requireContext(), SongView.class);
                        startActivity(intent);
                    });
                })
                .exceptionally(throwable -> {
                    mainActivity.runOnUiThread(() -> {
                        setLoadingState(false);
                        Toast.makeText(requireContext(),
                                "Error loading song details: " + throwable.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    private void setLoadingState(boolean isLoading) {
        // Implement loading state UI changes here
        // For example, show/hide a ProgressBar
        View loadingView = view.findViewById(R.id.loading_view);
        if (loadingView != null) {
            loadingView.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }
}