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
import java.util.List;

public class Playlist_RecyclerViewAdapter extends RecyclerView.Adapter<Playlist_RecyclerViewAdapter.ViewHolder> {
    private final Playlist_RecyclerViewInterface recyclerViewInterface;

    private final Context context;
    private ArrayList<PlaylistModel> playlistModels;

    // Modified constructor that accepts a List of playlist models directly
    public Playlist_RecyclerViewAdapter(Context context, ArrayList<PlaylistModel> playlistModels, Playlist_RecyclerViewInterface recyclerViewInterface) {
        this.context = context;
        this.playlistModels = playlistModels;
        this.recyclerViewInterface = recyclerViewInterface;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.recycler_view_row, parent, false);
        return new ViewHolder(view, recyclerViewInterface);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlaylistModel model = playlistModels.get(position);
        holder.playlistName.setText(model.getPlaylistName());
        holder.songCount.setText(context.getString(R.string.song_count, model.getSongCount()));
        holder.imageView.setTag(position);    // Store the model index in the tag of the ImageView, to be obtained later

        // Load image using Glide
        Glide.with(context)
                .load(model.getImage())
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return playlistModels.size();
    }

    public void updatePlaylists(ArrayList<PlaylistModel> newPlaylists) {
        this.playlistModels = newPlaylists;
        notifyDataSetChanged();
    }

    // Important to have static here:
    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView playlistName, songCount;

        public ViewHolder(@NonNull View itemView, Playlist_RecyclerViewInterface recyclerViewInterface) {
            super(itemView);
            playlistName = itemView.findViewById(R.id.songNameView);
            songCount = itemView.findViewById(R.id.artistView);
            imageView = itemView.findViewById(R.id.albumCoverView);

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

    public Context getContext() {
        return context;
    }
}