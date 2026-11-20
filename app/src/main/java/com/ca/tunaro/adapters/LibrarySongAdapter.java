package com.ca.tunaro.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.interfaces.Library_RecyclerViewInterface;
import com.ca.tunaro.R;
import com.ca.tunaro.models.SongModel;

import java.util.ArrayList;
import java.util.List;
import com.ca.tunaro.utils.ImageLoads;

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
                .apply(ImageLoads.thumbnail(context, 56))
                .into(holder.albumCoverImage);

        // Show note indicator
        holder.hasNotesIcon.setVisibility(View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    /**
     * Replaces the list via a URI-keyed diff so unchanged rows are not rebound.
     * The list is copied, so later changes to the caller's list never alias the adapter's.
     */
    public void updateSongs(List<SongModel> newSongs) {
        List<SongModel> next = new ArrayList<>(newSongs);
        List<SongModel> previous = this.songs;
        this.songs = next;
        if (!SongListDiff.shouldDiff(previous.size(), next.size())) {
            notifyDataSetChanged();
            return;
        }
        DiffUtil.calculateDiff(new SongListDiff(previous, next),
                        SongListDiff.shouldDetectMoves(previous.size(), next.size()))
                .dispatchUpdatesTo(this);
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