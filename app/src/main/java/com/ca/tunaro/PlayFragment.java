package com.ca.tunaro;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class PlayFragment extends Fragment implements Playlist_RecyclerViewInterface {
    private View view;
    private Playlist_RecyclerViewAdapter adapter;
    private final ArrayList<PlaylistModel> playlistModels = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_play, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View v, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new Playlist_RecyclerViewAdapter(getContext(), this, this);

        // Set the LayoutManager that this RecyclerView will use.
        RecyclerView recyclerView = view.findViewById(R.id.mRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    public void updatePlaylists(ArrayList<PlaylistModel> playlists) {
        int oldSize = getPlaylistModels().size();

        getPlaylistModels().clear();
        getPlaylistModels().addAll(playlists);
        adapter.notifyItemRangeRemoved(0, oldSize);
        adapter.notifyItemRangeInserted(0, playlists.size());
    }

    public ArrayList<PlaylistModel> getPlaylistModels() {
        return playlistModels;
    }

    @Override
    public void onItemClick(int position, View itemView) {
        // Change over to the PlaylistView Activity

        PlaylistModel clickedPlaylist = getPlaylistModels().get(position);

        // Set the selected playlist in the singleton
        SelectedPlaylistHolder.getInstance().setSelectedPlaylist(clickedPlaylist);

        // Start the PlaylistView activity
        Intent intent = new Intent(getContext(), PlaylistView.class);
        startActivity(intent);
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