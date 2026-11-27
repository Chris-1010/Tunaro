package com.ca.tunaro.activites;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import com.ca.tunaro.utils.LibrarySearch;

import java.util.ArrayList;
import java.util.List;

public class LibraryActivity extends BaseActivity implements Library_RecyclerViewInterface {
    private static final String TAG = "LibraryActivity";

    private LibrarySongAdapter adapter;
    private DatabaseHelper dbHelper;
    private final List<SongModel> allSongs = new ArrayList<>();
    private EditText searchBar;
    // onCreate already loads once; onResume reloads only on later returns (e.g. back from SongView).
    private boolean skipNextResumeReload = true;

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
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard();
            }
        });

        searchBar = findViewById(R.id.search_bar);
        setupSearchBar();

        loadSongsWithNotes(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter == null) return; // onCreate bailed out early.
        if (skipNextResumeReload) {
            skipNextResumeReload = false;
            return;
        }
        // Notes may have been added or removed elsewhere; DiffUtil keeps the scroll position.
        loadSongsWithNotes(false);
    }

    private void setupSearchBar() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
        searchBar.clearFocus();
    }

    // showSpinner is false for silent refreshes so the current list stays on screen.
    private void loadSongsWithNotes(boolean showSpinner) {
        if (showSpinner) {
            setLoadingState(true);
            setEmptyState(false);
        }
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
        List<SongModel> filtered = LibrarySearch.filter(allSongs, query);
        adapter.updateSongs(filtered);
        setEmptyState(filtered.isEmpty());
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
