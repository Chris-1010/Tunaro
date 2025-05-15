package com.ca.tunaro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class PlaylistsActivity extends AppCompatActivity implements Playlist_RecyclerViewInterface {
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Playlist_RecyclerViewAdapter adapter;
    private ArrayList<PlaylistModel> playlistModels = new ArrayList<>();
    private DatabaseHelper dbHelper;
    private MainActivity mainActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlists);

        mainActivity = MainActivity.getInstance();
        dbHelper = new DatabaseHelper(this);

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.playlists_recycler_view);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);

        adapter = new Playlist_RecyclerViewAdapter(this, playlistModels, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::refreshData);

        // Load playlists
        if (mainActivity != null) {
            loadPlaylists();
        } else {
            Toast.makeText(this, "Error: Could not connect to Spotify", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadPlaylists() {
        if (mainActivity == null || mainActivity.getSpotifyApi() == null || mainActivity.getUserID() == null) {
            Toast.makeText(this, "Spotify API not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        swipeRefreshLayout.setRefreshing(true);

        PlaylistSetup.getPlaylistData(mainActivity.getUserID(), mainActivity.getSpotifyApi())
                .thenAccept(playlists -> {
                    List<String> archivedIds = dbHelper.getArchivedPlaylistIds();
                    ArrayList<PlaylistModel> filteredPlaylists = new ArrayList<>();

                    for (PlaylistModel playlist : playlists) {
                        if (!archivedIds.contains(playlist.getId())) {
                            filteredPlaylists.add(playlist);
                        }
                    }

                    runOnUiThread(() -> {
                        playlistModels.clear();
                        playlistModels.addAll(filteredPlaylists);
                        adapter.notifyDataSetChanged();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        Toast.makeText(PlaylistsActivity.this,
                                "Error loading playlists: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
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
                .thenAccept(playlists -> {
                    List<String> archivedIds = dbHelper.getArchivedPlaylistIds();
                    ArrayList<PlaylistModel> filteredPlaylists = new ArrayList<>();

                    for (PlaylistModel playlist : playlists) {
                        if (!archivedIds.contains(playlist.getId())) {
                            filteredPlaylists.add(playlist);
                        }
                    }

                    runOnUiThread(() -> {
                        playlistModels.clear();
                        playlistModels.addAll(filteredPlaylists);
                        adapter.notifyDataSetChanged();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        Toast.makeText(PlaylistsActivity.this,
                                "Error refreshing playlists: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                    return null;
                });
    }

    @Override
    public void onItemClick(int position, View itemView) {
        PlaylistModel clickedPlaylist = playlistModels.get(position);

        if (clickedPlaylist.songCount == 0) return;

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
