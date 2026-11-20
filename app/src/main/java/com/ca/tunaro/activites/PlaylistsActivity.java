package com.ca.tunaro.activites;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.utils.PlaylistsEmptyState;
import com.ca.tunaro.utils.StateMessage;
import com.ca.tunaro.utils.AppExecutors;
import com.ca.tunaro.utils.PlaylistSetup;
import com.ca.tunaro.adapters.Playlist_RecyclerViewAdapter;
import com.ca.tunaro.interfaces.Playlist_RecyclerViewInterface;
import com.ca.tunaro.R;
import com.ca.tunaro.utils.SelectedPlaylistHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlaylistsActivity extends BaseActivity implements Playlist_RecyclerViewInterface {
    private static final String TAG = "PlaylistsActivity";

    private MainActivity mainActivity;
    private DatabaseHelper dbHelper;
    private Playlist_RecyclerViewAdapter adapter;
    private RecyclerView recyclerView;
    private ArrayList<PlaylistModel> playlistModels = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageView archiveToggleButton;
    private boolean showingArchived = false;
    private PopupMenu activePopupMenu;
    // Latest unfiltered playlist list (cached or fresh); null until something has loaded.
    private ArrayList<PlaylistModel> allPlaylists;
    private boolean fetchInFlight = false;
    // Discards filter results that a newer filter pass has superseded.
    private int filterGeneration = 0;
    // Content keys of the rendered rows, so a refresh can diff in instead of redrawing.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_playlists);

        mainActivity = MainActivity.getInstance();
        dbHelper = DatabaseHelper.getInstance(this);

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.playlists_recycler_view);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        archiveToggleButton = findViewById(R.id.archive_toggle_button);

        adapter = new Playlist_RecyclerViewAdapter(this, playlistModels, this, this::showPlaylistOptions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::refreshData);
        archiveToggleButton.setOnClickListener(v -> toggleArchivedView());

        // Load playlists
        if (mainActivity != null) {
            loadPlaylists();
        } else {
            showMessage("Couldn't connect to Spotify");
            finish();
        }
    }

    /** Show cached playlists immediately, then revalidate against Spotify and diff in changes. */
    private void loadPlaylists() {
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            StateMessage.showError(findViewById(R.id.playlists_state),
                    R.string.playlists_error_title, R.string.connection_error_hint,
                    this::retryFirstLoad);
            return;
        }

        PlaylistSetup.getCachedPlaylists().thenAccept(cached -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            if (cached != null && !cached.isEmpty()) {
                if (allPlaylists == null) showPlaylists(cached);
            } else if (fetchInFlight && allPlaylists == null) {
                // Nothing cached: show placeholder rows until the network answers.
                StateMessage.setShimmer(findViewById(R.id.playlists_shimmer), true);
            }
        }));
        fetchPlaylists(false);
    }

    private void refreshData() {
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        fetchPlaylists(true);
    }

    private void fetchPlaylists(boolean userInitiated) {
        fetchInFlight = true;
        PlaylistSetup.refreshPlaylists(mainActivity.getSpotifyApi())
                .thenAccept(playlists -> runOnUiThread(() -> {
                    fetchInFlight = false;
                    if (isDestroyed()) return;
                    showPlaylists(playlists);
                }))
                .exceptionally(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    Log.e(TAG, "Playlist refresh failed", cause);
                    runOnUiThread(() -> {
                        fetchInFlight = false;
                        if (isDestroyed()) return;
                        swipeRefreshLayout.setRefreshing(false);
                        StateMessage.setShimmer(findViewById(R.id.playlists_shimmer), false);
                        if (allPlaylists == null) {
                            // Nothing to show at all: full error state in place of the list.
                            StateMessage.showError(findViewById(R.id.playlists_state),
                                    R.string.playlists_error_title, R.string.connection_error_hint,
                                    this::retryFirstLoad);
                        } else if (userInitiated) {
                            // A background revalidation that fails quietly keeps the cached list.
                            showMessage(getString(R.string.playlists_refresh_failed),
                                    R.string.retry, () -> {
                                        if (!fetchInFlight) {
                                            swipeRefreshLayout.setRefreshing(true);
                                            refreshData();
                                        }
                                    });
                        }
                    });
                    return null;
                });
    }

    private void retryFirstLoad() {
        if (fetchInFlight) return;
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            showMessage("Not connected to Spotify yet");
            return;
        }
        StateMessage.hide(findViewById(R.id.playlists_state));
        StateMessage.setShimmer(findViewById(R.id.playlists_shimmer), true);
        fetchPlaylists(false);
    }

    private void showPlaylists(ArrayList<PlaylistModel> playlists) {
        allPlaylists = playlists;
        applyFilter();
    }

    private void showPlaylistOptions(View itemView, int position) {
        // Dismiss any active popup menu
        if (activePopupMenu != null) {
            activePopupMenu.dismiss();
        }

        PlaylistModel playlist = playlistModels.get(position);

        // Create and configure popup menu
        PopupMenu popupMenu = new PopupMenu(this, itemView.findViewById(R.id.options_anchor));
        popupMenu.getMenuInflater().inflate(R.menu.playlist_options_menu, popupMenu.getMenu());

        // Update menu item text based on favourite state
        MenuItem favouriteItem = popupMenu.getMenu().findItem(R.id.action_favourite);
        if (dbHelper.isPlaylistFavourited(playlist.getId())) {
            favouriteItem.setTitle("Unfavourite");
        } else {
            favouriteItem.setTitle("Favourite");
        }

        // Update menu item text based on archive state
        MenuItem archiveItem = popupMenu.getMenu().findItem(R.id.action_archive);
        if (showingArchived) {
            archiveItem.setTitle("Unarchive");
        } else {
            archiveItem.setTitle("Archive");
        }

        // Set click listeners
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_favourite) {
                if (dbHelper.isPlaylistFavourited(playlist.getId())) {
                    dbHelper.unfavouritePlaylist(playlist.getId());
                    showMessage("Playlist unfavourited");
                } else {
                    dbHelper.favouritePlaylist(playlist.getId());
                    showMessage("Playlist favourited");
                }
                applyFilter();
                return true;
            } else if (itemId == R.id.action_archive) {
                if (showingArchived) {
                    dbHelper.unarchivePlaylist(playlist.getId());
                    showMessage("Playlist unarchived");
                } else {
                    dbHelper.archivePlaylist(playlist.getId());
                    showMessage("Playlist archived");
                }
                applyFilter();
                return true;
            }
            return false;
        });

        // Track the active popup menu
        activePopupMenu = popupMenu;

        // Show the menu
        popupMenu.show();
    }

    private void toggleArchivedView() {
        showingArchived = !showingArchived;
        archiveToggleButton.setImageResource(showingArchived ?
                R.drawable.playlists :
                R.drawable.archived_playlists);
        applyFilter();
    }

    /** Re-filter the loaded playlists by archive/favourite state (read off the main thread). */
    private void applyFilter() {
        ArrayList<PlaylistModel> source = allPlaylists;
        if (source == null) return;
        boolean archived = showingArchived;
        int generation = ++filterGeneration;

        CompletableFuture.supplyAsync(() -> filterPlaylists(source, archived), AppExecutors.io())
                .thenAccept(filtered -> runOnUiThread(() -> {
                    if (isDestroyed() || generation != filterGeneration) return;
                    renderPlaylists(filtered);
                }))
                .exceptionally(e -> {
                    Log.e(TAG, "Filtering playlists failed", e);
                    runOnUiThread(() -> swipeRefreshLayout.setRefreshing(false));
                    return null;
                });
    }

    private ArrayList<PlaylistModel> filterPlaylists(ArrayList<PlaylistModel> playlists, boolean archived) {
        List<String> archivedIds = dbHelper.getArchivedPlaylistIds();
        List<String> favouritedIds = dbHelper.getFavouritedPlaylistIds();
        ArrayList<PlaylistModel> filteredPlaylists = new ArrayList<>();

        for (PlaylistModel playlist : playlists) {
            playlist.setFavourite(favouritedIds.contains(playlist.getId()));
            if (archivedIds.contains(playlist.getId()) == archived) {
                filteredPlaylists.add(playlist);
            }
        }

        // Favourite Playlists come first
        filteredPlaylists.sort((p1, p2) -> Boolean.compare(p2.isFavourite(), p1.isFavourite()));
        return filteredPlaylists;
    }

    private void renderPlaylists(ArrayList<PlaylistModel> filtered) {
        TextView titleView = findViewById(R.id.playlists_title);
        if (titleView != null) {
            titleView.setText(showingArchived ? "Archived Playlists" : "Your Playlists");
        }

        adapter.submitPlaylists(filtered);
        if (!fetchInFlight) swipeRefreshLayout.setRefreshing(false);
        StateMessage.setShimmer(findViewById(R.id.playlists_shimmer), false);
        renderEmptyState(filtered.size());
    }

    private void renderEmptyState(int visibleCount) {
        int total = allPlaylists != null ? allPlaylists.size() : 0;
        View state = findViewById(R.id.playlists_state);
        switch (PlaylistsEmptyState.resolve(showingArchived, total, visibleCount)) {
            case NO_PLAYLISTS:
                StateMessage.showEmpty(state, R.drawable.ic_state_playlist,
                        R.string.playlists_empty, R.string.playlists_empty_hint);
                break;
            case ALL_ARCHIVED:
                StateMessage.showEmpty(state, R.drawable.ic_state_playlist,
                        R.string.playlists_all_archived, R.string.playlists_all_archived_hint);
                break;
            case NO_ARCHIVED:
                StateMessage.showEmpty(state, R.drawable.ic_state_playlist,
                        R.string.playlists_archived_empty, R.string.playlists_archived_empty_hint);
                break;
            default:
                StateMessage.hide(state);
                break;
        }
    }


    @Override
    public void onItemClick(int position, View itemView) {
        PlaylistModel clickedPlaylist = playlistModels.get(position);

        if (clickedPlaylist.getSongCount() == 0) return;

        // Set the selected playlist in the singleton
        SelectedPlaylistHolder.getInstance().setSelectedPlaylist(
                clickedPlaylist,
                mainActivity
        );

        // Start the PlaylistView activity
        Intent intent = new Intent(this, PlaylistView.class);
        startActivity(intent);
    }

}
