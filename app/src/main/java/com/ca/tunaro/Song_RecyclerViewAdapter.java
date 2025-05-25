package com.ca.tunaro;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class Song_RecyclerViewAdapter extends RecyclerView.Adapter<Song_RecyclerViewAdapter.ViewHolder> {
    private final Context context;
    private final PlaylistView activity;
    private final Song_RecyclerViewInterface recyclerViewInterface;
    private ArrayList<SongModel> songModels;
    private final DatabaseHelper dbHelper;

    public Song_RecyclerViewAdapter(Context context, PlaylistView playlistView, Song_RecyclerViewInterface recyclerViewInterface, ArrayList<SongModel> songModels) {
        this.context = context;
        this.activity = playlistView;
        this.recyclerViewInterface = recyclerViewInterface;
        this.songModels = songModels;
        this.dbHelper = new DatabaseHelper(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.song_recycler_view_row, parent, false);
        return new ViewHolder(view, recyclerViewInterface);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SongModel model = getSongModels().get(position);
        holder.songNameView.setText(model.getName());
        holder.artistView.setText(model.getArtist());

        // Load image using Glide
        Glide.with(context)
                .load(model.getAlbumCoverUrl())
                .into(holder.imageCoverView);

        // Check if the song has notes/snippets and show/hide the corresponding icon accordingly
        if (dbHelper.hasSongNotes(model.getId())) holder.hasNotesIcon.setVisibility(View.VISIBLE);
        else holder.hasNotesIcon.setVisibility(View.GONE);
        if (dbHelper.hasSongSnippets(model.getId())) holder.hasSnippetsIcon.setVisibility(View.VISIBLE);
        else holder.hasSnippetsIcon.setVisibility(View.GONE);

    }

    @Override
    public int getItemCount() {
        return getSongModels().size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageCoverView;
        ImageView hasNotesIcon;
        ImageView hasSnippetsIcon;
        TextView songNameView, artistView;

        public ViewHolder(@NonNull View itemView, Song_RecyclerViewInterface recyclerViewInterface) {
            super(itemView);
            songNameView = itemView.findViewById(R.id.songNameView);
            artistView = itemView.findViewById(R.id.artistView);
            imageCoverView = itemView.findViewById(R.id.albumCoverView);
            hasNotesIcon = itemView.findViewById(R.id.hasNotesIcon);
            hasSnippetsIcon = itemView.findViewById(R.id.hasSnippetsIcon);

            itemView.setOnClickListener(view -> {
                if (recyclerViewInterface != null) {
                    int position = getAdapterPosition();

                    if (position != RecyclerView.NO_POSITION) {
                        recyclerViewInterface.onItemClick(position, itemView);
                    }
                }
            });
        }
    }

    public void updateSongs(ArrayList<SongModel> newSongs) {
        this.songModels = newSongs;
        notifyDataSetChanged();
    }

    private ArrayList<SongModel> getSongModels() {
        return this.songModels;
    }

    public ArrayList<SongModel> getSongs() {
        return getSongModels();
    }
}