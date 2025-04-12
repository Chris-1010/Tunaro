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

public class LibrarySongAdapter extends RecyclerView.Adapter<LibrarySongAdapter.ViewHolder> {
    private final Context context;
    private final Library_RecyclerViewInterface recyclerViewInterface;
    private List<SongModel> songs = new ArrayList<>();

    public LibrarySongAdapter(Context context, Library_RecyclerViewInterface recyclerViewInterface) {
        this.context = context;
        this.recyclerViewInterface = recyclerViewInterface;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.library_song_item, parent, false);
        return new ViewHolder(view, recyclerViewInterface);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SongModel model = songs.get(position);
        holder.songNameText.setText(model.getName());
        holder.artistText.setText(model.getArtist());

        // Load album cover using Glide
        Glide.with(context)
                .load(model.getAlbumCoverUrl())
                .into(holder.albumCoverImage);

        // Show note indicator
        holder.hasNotesIcon.setVisibility(View.VISIBLE);
    }

    public void addSong(SongModel song) {
        this.songs.add(song);
        notifyItemInserted(songs.size() - 1);
    }

    public void clearSongs() {
        int size = songs.size();
        songs.clear();
        notifyItemRangeRemoved(0, size);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public void updateSongs(List<SongModel> newSongs) {
        this.songs = newSongs;
        notifyDataSetChanged();
    }

    public List<SongModel> getSongs() {
        return songs;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView albumCoverImage;
        ImageView hasNotesIcon;
        TextView songNameText, artistText;

        public ViewHolder(@NonNull View itemView, Library_RecyclerViewInterface recyclerViewInterface) {
            super(itemView);
            albumCoverImage = itemView.findViewById(R.id.album_cover);
            songNameText = itemView.findViewById(R.id.song_name);
            artistText = itemView.findViewById(R.id.artist_name);
            hasNotesIcon = itemView.findViewById(R.id.has_notes_icon);

            itemView.setOnClickListener(v -> {
                if (recyclerViewInterface != null) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        recyclerViewInterface.onItemClick(pos);
                    }
                }
            });
        }
    }
}