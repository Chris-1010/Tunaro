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

        dbHelper = new DatabaseHelper(this);

        RecyclerView recyclerView = findViewById(R.id.library_recycler_view);
        adapter = new LibrarySongAdapter(this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        searchBar = findViewById(R.id.search_bar);
        setupSearchBar();

        loadSongsWithNotes();
    }

    private void setupSearchBar() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadSongsWithNotes() {
        List<String> songIds = dbHelper.getSongIdsWithNotes();

        setLoadingState(true);
        allSongs.clear();
        adapter.clearSongs();

        // Load from DB first, fall back to Spotify API for any unknown songs
        loadSongsSequentially(songIds, 0);
    }

    private void loadSongsSequentially(List<String> songIds, int index) {
        if (index >= songIds.size()) {
            setLoadingState(false);
            return;
        }

        String songId = songIds.get(index);

        // Try DB first
        SongModel dbSong = dbHelper.getLeanSong(songId);
        if (dbSong != null) {
            runOnUiThread(() -> {
                allSongs.add(dbSong);
                adapter.addSong(dbSong);
                loadSongsSequentially(songIds, index + 1);
            });
            return;
        }

        // Not in DB — should not happen in normal flow, but fall back to Spotify API
        if (spotifyApi == null) {
            loadSongsSequentially(songIds, index + 1);
            return;
        }

        // songId is a composite key and can't be passed to Spotify API directly
        // Log a warning and skip; song data will be populated on next playlist sync
        Log.w(TAG, "Song not in DB, skipping library entry: " + songId);
        new Handler(Looper.getMainLooper()).post(() -> loadSongsSequentially(songIds, index + 1));
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
        SelectedSongHolder.getInstance().setSelectedSong(selectedSong);
        Intent intent = new Intent(this, SongView.class);
        intent.putExtra("source", "library");
        startActivity(intent);
    }

    private void setLoadingState(boolean isLoading) {
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
