package com.ca.tunaro;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class PlayFragment extends Fragment implements Playlist_RecyclerViewInterface {
    private View view;
    private Playlist_RecyclerViewAdapter adapter;
    private final ArrayList<PlaylistModel> playlistModels = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private MainActivity mainActivity;
    private boolean isRefreshing = false;
    private DatabaseHelper dbHelper;
    private boolean showingArchived = false;
    private ImageView archiveToggleButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) requireActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_play, container, false);

        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.mRecyclerView);
        adapter = new Playlist_RecyclerViewAdapter(getContext(), this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (!isRefreshing) {
                refreshData();
            }
        });

        dbHelper = new DatabaseHelper(requireContext());
        archiveToggleButton = view.findViewById(R.id.archiveToggleButton);
        archiveToggleButton.setOnClickListener(v -> toggleArchivedView());

        SwipeToArchiveCallback.OnSwipeListener archiveListener = new SwipeToArchiveCallback.OnSwipeListener() {
            @Override
            public void onArchive(int position) {
                PlaylistModel playlist = getPlaylistModels().get(position);
                if (showingArchived) {
                    dbHelper.unarchivePlaylist(playlist.getId());
                    Toast.makeText(requireContext(), "Playlist unarchived", Toast.LENGTH_SHORT).show();
                } else {
                    dbHelper.archivePlaylist(playlist.getId());
                    Toast.makeText(requireContext(), "Playlist archived", Toast.LENGTH_SHORT).show();
                }
                refreshPlaylists();
            }
        };

        SwipeToArchiveCallback swipeHandler = new SwipeToArchiveCallback(adapter, archiveListener, showingArchived);
        new ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View v, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    private void refreshData() {
        if (getActivity() == null) return;

        MainActivity activity = (MainActivity) getActivity();
        isRefreshing = true;

        PlaylistSetup.refreshPlaylists(activity.getUserID(), activity.getSpotifyApi())
                .thenAccept(playlists -> {
                    if (getActivity() == null) return;

                    getActivity().runOnUiThread(() -> {
                        updatePlaylists(playlists);
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                        isRefreshing = false;
                    });
                })
                .exceptionally(throwable -> {
                    if (getActivity() == null) return null;

                    getActivity().runOnUiThread(() -> {
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                        isRefreshing = false;
                        Toast.makeText(getContext(),
                                "Error refreshing playlists: " + throwable.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    public void updatePlaylists(ArrayList<PlaylistModel> playlists) {
        if (playlists == null || playlists.isEmpty()) {
            return;
        }

        List<String> archivedIds = dbHelper.getArchivedPlaylistIds();
        ArrayList<PlaylistModel> filteredPlaylists = new ArrayList<>();

        for (PlaylistModel playlist : playlists) {
            boolean isArchived = archivedIds.contains(playlist.getId());
            if (isArchived == showingArchived) {
                filteredPlaylists.add(playlist);
            }
        }

        playlistModels.clear();
        playlistModels.addAll(filteredPlaylists);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        // Update playlist count if the view exists
        if (getView() != null) {
            TextView playlistCountIndicator = getActivity().findViewById(R.id.playlistCount);
            if (playlistCountIndicator != null) {
                String countText = getString(R.string.playlist_count, filteredPlaylists.size());
                if (showingArchived) {
                    countText += " (Archived)";
                }
                playlistCountIndicator.setText(countText);
            }
        }
    }

    public ArrayList<PlaylistModel> getPlaylistModels() {
        return playlistModels;
    }

    @Override
    public void onItemClick(int position, View itemView) {
        // Change over to the PlaylistView Activity

        PlaylistModel clickedPlaylist = getPlaylistModels().get(position);

        if (clickedPlaylist.songCount == 0) return;

        // Set the selected playlist in the singleton
        SelectedPlaylistHolder.getInstance().setSelectedPlaylist(
                clickedPlaylist,
                mainActivity.getSpotifyApi(),
                mainActivity.getSpotifyAppRemote()
        );

        // Start the PlaylistView activity
        Intent intent = new Intent(getContext(), PlaylistView.class);
        startActivity(intent);
    }

    private void toggleArchivedView() {
        showingArchived = !showingArchived;
        archiveToggleButton.setImageResource(showingArchived ?
                R.drawable.playlists :
                R.drawable.archived_playlists);
        refreshPlaylists();
    }

    private void refreshPlaylists() {
        if (getActivity() == null) return;
        MainActivity activity = (MainActivity) getActivity();

        // Only refresh from API if we're coming from archived view to normal view
        if (showingArchived) {
            // Use cached data since archived playlists don't need fresh API data
            PlaylistSetup.getPlaylistData(activity.getUserID(), activity.getSpotifyApi())
                    .thenAccept(playlists -> {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> updatePlaylists(playlists));
                    });
        } else {
            // If showing main view, do a full refresh
            PlaylistSetup.refreshPlaylists(activity.getUserID(), activity.getSpotifyApi())
                    .thenAccept(playlists -> {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            updatePlaylists(playlists);
                            if (swipeRefreshLayout != null) {
                                swipeRefreshLayout.setRefreshing(false);
                            }
                        });
                    })
                    .exceptionally(throwable -> {
                        if (getActivity() == null) return null;
                        getActivity().runOnUiThread(() -> {
                            if (swipeRefreshLayout != null) {
                                swipeRefreshLayout.setRefreshing(false);
                            }
                            Toast.makeText(getContext(),
                                    "Error refreshing playlists: " + throwable.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
                        return null;
                    });
        }
    }

//    public void toggleAPI(View v) {
//        Button b = (Button) v;
//        String currentState = b.getText().toString();
//        if (currentState.equals("Play")) {mSpotifyAppRemote.getPlayerApi().resume(); b.setText(R.string.pause);}
//        else {mSpotifyAppRemote.getPlayerApi().pause(); b.setText(R.string.play);}
////        if (b.getText().toString().equals("Play")) {
////
////        }
//    }
}