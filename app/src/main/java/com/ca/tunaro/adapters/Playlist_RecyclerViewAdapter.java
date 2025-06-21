package com.ca.tunaro.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.interfaces.Playlist_RecyclerViewInterface;
import com.ca.tunaro.R;

import java.util.ArrayList;

public class Playlist_RecyclerViewAdapter extends RecyclerView.Adapter<Playlist_RecyclerViewAdapter.ViewHolder> {
    public interface OnItemLongClickListener {
        void onItemLongClick(View itemView, int position);
    }

    private final Playlist_RecyclerViewInterface recyclerViewInterface;

    private final Context context;
    private ArrayList<PlaylistModel> playlistModels;

    private final OnItemLongClickListener longClickListener;

    public Playlist_RecyclerViewAdapter(Context context, ArrayList<PlaylistModel> playlistModels, Playlist_RecyclerViewInterface recyclerViewInterface, OnItemLongClickListener longClickListener) {
        this.context = context;
        this.playlistModels = playlistModels;
        this.recyclerViewInterface = recyclerViewInterface;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.recycler_view_row, parent, false);
        return new ViewHolder(view, recyclerViewInterface, longClickListener);
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

        public ViewHolder(@NonNull View itemView, Playlist_RecyclerViewInterface recyclerViewInterface, OnItemLongClickListener longClickListener) {
            super(itemView);
            playlistName = itemView.findViewById(R.id.songNameView);
            songCount = itemView.findViewById(R.id.artistView);
            imageView = itemView.findViewById(R.id.albumCoverView);

            // Anchor view for popup menu if it doesn't exist
            if (itemView.findViewById(R.id.options_anchor) == null) {
                View anchor = new View(itemView.getContext());
                anchor.setId(R.id.options_anchor);
                ((ViewGroup) itemView).addView(anchor);
                anchor.setVisibility(View.INVISIBLE);
            }

            itemView.setOnClickListener(view -> {
                if (recyclerViewInterface != null) {
                    int position = getAdapterPosition();

                    if (position != RecyclerView.NO_POSITION) {
                        recyclerViewInterface.onItemClick(position, itemView);
                    }
                }
            });

            itemView.setOnLongClickListener(view -> {
                if (longClickListener != null) {
                    int position = getAdapterPosition();

                    if (position != RecyclerView.NO_POSITION) {
                        longClickListener.onItemLongClick(itemView, position);
                        return true;
                    }
                }
                return false;
            });
        }
    }

    public Context getContext() {
        return context;
    }
}