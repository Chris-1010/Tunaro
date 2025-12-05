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
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.R;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.interfaces.Song_RecyclerViewInterface;
import com.ca.tunaro.activites.PlaylistView;

import java.util.ArrayList;
import java.util.Date;

public class Song_RecyclerViewAdapter extends RecyclerView.Adapter<Song_RecyclerViewAdapter.ViewHolder> {
    private final Context context;
    private final Song_RecyclerViewInterface recyclerViewInterface;
    private ArrayList<SongModel> songModels;
    private final DatabaseHelper dbHelper;

    private int currentSortOption = -1; // Track current sort option
    private boolean shouldShowContextualInfo = false;

    public Song_RecyclerViewAdapter(Context context, Song_RecyclerViewInterface recyclerViewInterface, ArrayList<SongModel> songModels) {
        this.context = context;
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
        SongModel model = songModels.get(position);
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

        // Handle contextual information display
        if (shouldShowContextualInfo) {
            holder.contextualInfoView.setVisibility(View.VISIBLE);

            switch (currentSortOption) {
                case 0: // Date Added
                    if (model.getDateAddedToPlaylist() != null) {
                        String dateAdded = formatDateForDisplay(model.getDateAddedToPlaylist());
                        holder.contextualInfoView.setText("Added: " + dateAdded);
                    } else {
                        holder.contextualInfoView.setText("Added: Unknown");
                    }
                    break;

                case 1: // Last Listened
                    String lastListened = dbHelper.getMostRecentListenTimestamp(model.getId());
                    if (lastListened != null) {
                        String formattedTime = DatabaseHelper.getRelativeTimeDescription(lastListened);
                        holder.contextualInfoView.setText("Last listened: " + formattedTime);
                    } else {
                        holder.contextualInfoView.setText("Never listened");
                    }
                    break;

                case 3: // Length/Duration
                    holder.contextualInfoView.setText("Duration: " + model.getDurationString());
                    break;
            }
        } else {
            holder.contextualInfoView.setVisibility(View.GONE);
        }
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
        TextView contextualInfoView;

        public ViewHolder(@NonNull View itemView, Song_RecyclerViewInterface recyclerViewInterface) {
            super(itemView);
            songNameView = itemView.findViewById(R.id.songNameView);
            artistView = itemView.findViewById(R.id.artistView);
            imageCoverView = itemView.findViewById(R.id.albumCoverView);
            hasNotesIcon = itemView.findViewById(R.id.hasNotesIcon);
            hasSnippetsIcon = itemView.findViewById(R.id.hasSnippetsIcon);
            contextualInfoView = itemView.findViewById(R.id.contextualInfoView);

            // Open SongView on click
            itemView.setOnClickListener(view -> {
                if (recyclerViewInterface != null) {
                    int position = getAdapterPosition();

                    if (position != RecyclerView.NO_POSITION) {
                        recyclerViewInterface.onItemClick(position, itemView);
                    }
                }
            });

            // Long press on album cover for quick play
            imageCoverView.setOnLongClickListener(view -> {
                if (recyclerViewInterface != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        if (recyclerViewInterface instanceof PlaylistView) {
                            recyclerViewInterface.onAlbumCoverLongClick(position);
                        }
                        return true;
                    }
                }
                return false;
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

    public void updateSortContext(int sortOption) {
        this.currentSortOption = sortOption;
        // Show contextual info for Date Added (0) and Last Listened (1), and Length (3)
        this.shouldShowContextualInfo = (sortOption == 0 || sortOption == 1 || sortOption == 3);
        notifyDataSetChanged();
    }

    private String formatDateForDisplay(Date date) {
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
        return formatter.format(date);
    }
}