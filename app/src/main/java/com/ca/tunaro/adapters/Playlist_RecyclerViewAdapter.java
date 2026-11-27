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
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.interfaces.Playlist_RecyclerViewInterface;
import com.ca.tunaro.R;

import java.util.ArrayList;
import java.util.List;
import com.ca.tunaro.utils.ImageLoads;

public class Playlist_RecyclerViewAdapter extends RecyclerView.Adapter<Playlist_RecyclerViewAdapter.ViewHolder> {
    public interface OnItemLongClickListener {
        void onItemLongClick(View itemView, int position);
    }

    private final Playlist_RecyclerViewInterface recyclerViewInterface;

    private final Context context;
    private final ArrayList<PlaylistModel> playlistModels;
    // Content key of each rendered row, parallel to playlistModels.
    private final ArrayList<String> renderedKeys = new ArrayList<>();

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
        View view = LayoutInflater.from(context).inflate(R.layout.playlist_recycler_view_row, parent, false);
        return new ViewHolder(view, recyclerViewInterface, longClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlaylistModel model = playlistModels.get(position);
        holder.playlistName.setSelected(true); // Enable marquee
        holder.playlistName.setText(model.getPlaylistName());
        holder.songCount.setText(context.getString(R.string.song_count, model.getSongCount()));
        holder.imageView.setTag(position);    // Store the model index in the tag of the ImageView, to be obtained later

        if (model.isFavourite()) {
            holder.favouriteIcon.setVisibility(View.VISIBLE);
        } else {
            holder.favouriteIcon.setVisibility(View.GONE);
        }

        // Load image using Glide
        Glide.with(context)
                .load(model.getImage())
                .apply(ImageLoads.thumbnail(context, 100))
                .placeholder(R.drawable.playlist_placeholder)
                .error(R.drawable.playlist_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return playlistModels.size();
    }

    /**
     * Replaces the rows (in the list shared with the host) via a diff keyed by
     * playlist ID. Content is compared against a snapshot of what each row last
     * displayed, since PlaylistModel instances are mutated in place.
     */
    public void submitPlaylists(List<PlaylistModel> newPlaylists) {
        final List<String> oldIds = new ArrayList<>();
        for (PlaylistModel p : playlistModels) oldIds.add(p.getId());
        final List<String> oldKeys = new ArrayList<>(renderedKeys);
        final List<PlaylistModel> next = new ArrayList<>(newPlaylists);
        final List<String> newKeys = new ArrayList<>();
        for (PlaylistModel p : next) newKeys.add(contentKey(p));

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldIds.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                return java.util.Objects.equals(oldIds.get(o), next.get(n).getId());
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                return o < oldKeys.size() && oldKeys.get(o).equals(newKeys.get(n));
            }
        });

        playlistModels.clear();
        playlistModels.addAll(next);
        renderedKeys.clear();
        renderedKeys.addAll(newKeys);
        diff.dispatchUpdatesTo(this);
    }

    /** Everything a playlist row displays; a change here rebinds the row. */
    private static String contentKey(PlaylistModel p) {
        return p.getPlaylistName() + '\u0000' + p.getSongCount() + '\u0000' + p.getImage()
                + '\u0000' + p.isFavourite();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageView favouriteIcon;
        TextView playlistName, songCount;

        public ViewHolder(@NonNull View itemView, Playlist_RecyclerViewInterface recyclerViewInterface, OnItemLongClickListener longClickListener) {
            super(itemView);
            playlistName = itemView.findViewById(R.id.songNameView);
            songCount = itemView.findViewById(R.id.songCountView);
            imageView = itemView.findViewById(R.id.albumCoverView);
            favouriteIcon = itemView.findViewById(R.id.favouriteIcon);

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