package com.ca.tunaro;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

public class PlaylistView extends AppCompatActivity implements Song_RecyclerViewInterface {
    private PlaylistModel selectedPlaylist;
    private Song_RecyclerViewAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_playlist_view);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Retrieve the selected playlist
        selectedPlaylist = SelectedPlaylistHolder.getInstance().getSelectedPlaylist();

        if (selectedPlaylist == null) {
            // Handle error - playlist not found
            finish();
            return;
        }

        String playlistName = selectedPlaylist.getPlaylistName();
        int songCount = selectedPlaylist.getSongCount();
        String playlistImage = selectedPlaylist.getImage();

        TextView nameView = this.findViewById(R.id.detailed_playlistName);
        TextView countView = this.findViewById(R.id.detailed_songCount);
        ImageView imageView = this.findViewById(R.id.detailed_playlistCover);
        nameView.setText(playlistName);
        countView.setText(getString(R.string.song_count, songCount));
        Glide.with(this)
                .load(playlistImage)
                .into(imageView);

        // Set up RecyclerView
        RecyclerView recyclerView = findViewById(R.id.song_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new Song_RecyclerViewAdapter(this, this, this, selectedPlaylist.getSongs());
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear the selected playlist when the activity is destroyed
        SelectedPlaylistHolder.getInstance().clearSelectedPlaylist();
    }

    /**
     * Launch a new activity (SongView) that shows a detailed display (last listened to, popularity, release date, etc.), very similar to the PlaylistView for the top half (showing the album cover and the name underneath.
     * The user can add details about the song like where they heard the song first, favourite parts of the song, ratings, and general notes.
     */
    @Override
    public void onItemClick(int position, View itemView) {

    }
}