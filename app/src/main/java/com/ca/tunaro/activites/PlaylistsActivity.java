package com.ca.tunaro.activites;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.utils.PlaylistSetup;
import com.ca.tunaro.adapters.Playlist_RecyclerViewAdapter;
import com.ca.tunaro.interfaces.Playlist_RecyclerViewInterface;
import com.ca.tunaro.R;
import com.ca.tunaro.utils.SelectedPlaylistHolder;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_playlists);

        mainActivity = MainActivity.getInstance();
        dbHelper = new DatabaseHelper(this);

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.playlists_recycler_view);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        archiveToggleButton = findViewById(R.id.archive_toggle_button);

        adapter = new Playlist_RecyclerViewAdapter(this, playlistModels, this, this::showPlaylistOptions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::refreshData);
        archiveToggleButton.setOnClickListener(v -> toggleArchivedView());

        // Load playlists
        if (mainActivity != null) {
            loadPlaylists();
        } else {
            showToast("Error: Could not connect to Spotify");
            finish();
        }
    }

    private void loadPlaylists() {
        if (mainActivity == null || mainActivity.getSpotifyApi() == null || mainActivity.getUserID() == null) {
            showToast("Spotify API not ready");
            return;
        }

        swipeRefreshLayout.setRefreshing(true);

        PlaylistSetup.getPlaylistData(mainActivity.getUserID(), mainActivity.getSpotifyApi())
                .thenAccept(this::updateUIWithFilteredPlaylists)
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        showToast("Error loading playlists: " + e.getMessage());
                        swipeRefreshLayout.setRefreshing(false);
                    });
                    return null;
                });
    }

    private void refreshData() {
        if (mainActivity == null || mainActivity.getSpotifyApi() == null || mainActivity.getUserID() == null) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        PlaylistSetup.refreshPlaylists(mainActivity.getUserID(), mainActivity.getSpotifyApi())
                .thenAccept(this::updateUIWithFilteredPlaylists)
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        showToast("Error refreshing playlists: " + e.getMessage());
                        swipeRefreshLayout.setRefreshing(false);
                    });
                    return null;
                });
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
                    showToast("Playlist unfavourited");
                } else {
                    dbHelper.favouritePlaylist(playlist.getId());
                    showToast("Playlist favourited");
                }
                refreshPlaylists();
                return true;
            } else if (itemId == R.id.action_archive) {
                if (showingArchived) {
                    dbHelper.unarchivePlaylist(playlist.getId());
                    showToast("Playlist unarchived");
                } else {
                    dbHelper.archivePlaylist(playlist.getId());
                    showToast("Playlist archived");
                }
                refreshPlaylists();
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
        refreshPlaylists();
    }

    private void refreshPlaylists() {
        if (mainActivity == null || mainActivity.getSpotifyApi() == null || mainActivity.getUserID() == null) {
            return;
        }

        // Show loading indicator
        swipeRefreshLayout.setRefreshing(true);

        // Only refresh from API if coming from archived view to normal view
        if (showingArchived) {
            // Use cached data since archived playlists don't need fresh API data
            PlaylistSetup.getPlaylistData(mainActivity.getUserID(), mainActivity.getSpotifyApi())
                    .thenAccept(this::updateUIWithFilteredPlaylists)
                    .exceptionally(e -> {
                        runOnUiThread(() -> {
                            showToast("Error loading playlists: " + e.getMessage());
                            swipeRefreshLayout.setRefreshing(false);
                        });
                        return null;
                    });
        } else {
            // If showing main view, do a full refresh
            PlaylistSetup.refreshPlaylists(mainActivity.getUserID(), mainActivity.getSpotifyApi())
                    .thenAccept(this::updateUIWithFilteredPlaylists)
                    .exceptionally(e -> {
                        runOnUiThread(() -> {
                            showToast("Error refreshing playlists: " + e.getMessage());
                            swipeRefreshLayout.setRefreshing(false);
                        });
                        return null;
                    });
        }
    }

    private void updateUIWithFilteredPlaylists(ArrayList<PlaylistModel> playlists) {
        List<String> archivedIds = dbHelper.getArchivedPlaylistIds();
        List<String> favouritedIds = dbHelper.getFavouritedPlaylistIds();
        ArrayList<PlaylistModel> filteredPlaylists = new ArrayList<>();

        for (PlaylistModel playlist : playlists) {
            boolean isArchived = archivedIds.contains(playlist.getId());
            boolean isFavourited = favouritedIds.contains(playlist.getId());

            playlist.setFavourite(isFavourited);

            if (isArchived == showingArchived) {
                filteredPlaylists.add(playlist);
            }
        }

        // Favourite Playlists come first
        filteredPlaylists.sort((p1, p2) -> {
            if (p1.isFavourite() && !p2.isFavourite()) return -1;
            if (!p1.isFavourite() && p2.isFavourite()) return 1;
            return 0;
        });

        runOnUiThread(() -> {
            // Update title to show if in archived mode
            TextView titleView = findViewById(R.id.playlists_title);
            if (titleView != null) {
                titleView.setText(showingArchived ? "Archived Playlists" : "Your Playlists");
            }

            playlistModels.clear();
            playlistModels.addAll(filteredPlaylists);
            adapter.notifyDataSetChanged();
            swipeRefreshLayout.setRefreshing(false);
        });
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

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}
