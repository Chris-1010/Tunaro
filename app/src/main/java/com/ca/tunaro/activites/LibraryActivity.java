package com.ca.tunaro.activites;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.adapters.LibrarySongAdapter;
import com.ca.tunaro.interfaces.Library_RecyclerViewInterface;
import com.ca.tunaro.R;
import com.ca.tunaro.utils.StateMessage;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.AppExecutors;

import java.util.ArrayList;
import java.util.List;

public class LibraryActivity extends BaseActivity implements Library_RecyclerViewInterface {
    private static final String TAG = "LibraryActivity";

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
        if (MainActivity.getInstance() == null) {
            showMessage("Couldn't connect to Spotify");
            finish();
            return;
        }

        dbHelper = DatabaseHelper.getInstance(this);

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
        setLoadingState(true);
        setEmptyState(false);
        AppExecutors.io().execute(() -> {
            List<String> songIds = dbHelper.getSongIdsWithNotes();
            List<SongModel> songs = dbHelper.getLeanSongs(songIds);
            if (songs.size() < songIds.size()) {
                // Songs missing from the DB are back-filled by the next playlist sync / orphan pass.
                Log.w(TAG, "Skipped " + (songIds.size() - songs.size()) + " library songs not in DB");
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                allSongs.clear();
                allSongs.addAll(songs);
                setLoadingState(false);
                filterSongs(searchBar.getText().toString());
            });
        });
    }

    private void filterSongs(String query) {
        if (query == null || query.isEmpty()) {
            adapter.updateSongs(allSongs);
            setEmptyState(allSongs.isEmpty());
            return;
        }

        List<SongModel> filteredList = new ArrayList<>();
        String lowercaseQuery = query.toLowerCase().trim();

        for (SongModel song : allSongs) {
            if (matches(song.getName(), lowercaseQuery) ||
                    matches(song.getArtist(), lowercaseQuery) ||
                    matches(song.getAlbumName(), lowercaseQuery)) {
                filteredList.add(song);
            }
        }

        adapter.updateSongs(filteredList);
        setEmptyState(filteredList.isEmpty());
    }

    // Lean rows come from LEFT JOINs, so artist/album can be null.
    private static boolean matches(String field, String lowercaseQuery) {
        return field != null && field.toLowerCase().contains(lowercaseQuery);
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

    // Shows the empty message: "no annotated songs" when the library is empty, "no matches" when
    // only the current search filters everything out.
    private void setEmptyState(boolean isEmpty) {
        View emptyView = findViewById(R.id.empty_view);
        if (!isEmpty) {
            StateMessage.hide(emptyView);
        } else if (allSongs.isEmpty()) {
            StateMessage.showEmpty(emptyView, R.drawable.ic_state_library,
                    R.string.library_empty, R.string.library_empty_hint);
        } else {
            StateMessage.showEmpty(emptyView, R.drawable.ic_search,
                    R.string.library_no_matches, R.string.library_no_matches_hint);
        }
    }

}
