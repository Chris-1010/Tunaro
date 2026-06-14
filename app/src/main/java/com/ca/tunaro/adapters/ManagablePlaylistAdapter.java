package com.ca.tunaro.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid of playlists for the "Add to playlist" sheet. Each cell is tap-to-toggle:
 * selected cells get a green border + tick badge. The adapter tracks the current
 * selection against each playlist's original membership and reports whether any
 * change is pending so the sheet can show/hide its Save button.
 */
public class ManagablePlaylistAdapter extends RecyclerView.Adapter<ManagablePlaylistAdapter.ViewHolder> {

    public interface SelectionChangeListener {
        /** Called after any toggle, with whether the selection now differs from the original. */
        void onSelectionChanged(boolean hasPendingChanges);
    }

    private final Context context;
    private final List<DatabaseHelper.ManagablePlaylist> playlists;
    private final boolean[] selected;
    private final SelectionChangeListener listener;

    public ManagablePlaylistAdapter(Context context,
                                    List<DatabaseHelper.ManagablePlaylist> playlists,
                                    SelectionChangeListener listener) {
        this.context = context;
        this.playlists = playlists;
        this.listener = listener;
        this.selected = new boolean[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            this.selected[i] = playlists.get(i).containsSong;
        }
    }

    /** Playlists newly ticked (to add) — selected now but not originally a member. */
    public List<DatabaseHelper.ManagablePlaylist> getAdditions() {
        List<DatabaseHelper.ManagablePlaylist> result = new ArrayList<>();
        for (int i = 0; i < playlists.size(); i++) {
            if (selected[i] && !playlists.get(i).containsSong) result.add(playlists.get(i));
        }
        return result;
    }

    /** Playlists newly unticked (to remove) — originally a member but no longer selected. */
    public List<DatabaseHelper.ManagablePlaylist> getRemovals() {
        List<DatabaseHelper.ManagablePlaylist> result = new ArrayList<>();
        for (int i = 0; i < playlists.size(); i++) {
            if (!selected[i] && playlists.get(i).containsSong) result.add(playlists.get(i));
        }
        return result;
    }

    private boolean hasPendingChanges() {
        for (int i = 0; i < playlists.size(); i++) {
            if (selected[i] != playlists.get(i).containsSong) return true;
        }
        return false;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_managable_playlist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DatabaseHelper.ManagablePlaylist playlist = playlists.get(position);

        holder.name.setText(playlist.name);
        Glide.with(context)
                .load(playlist.imageUrl)
                .placeholder(R.drawable.playlist_placeholder)
                .error(R.drawable.playlist_placeholder)
                .into(holder.image);

        // Keep the image frame square: set its height to its measured width once laid out.
        holder.imageFrame.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        holder.imageFrame.getViewTreeObserver().removeOnPreDrawListener(this);
                        int width = holder.imageFrame.getWidth();
                        if (width > 0 && holder.imageFrame.getLayoutParams().height != width) {
                            holder.imageFrame.getLayoutParams().height = width;
                            holder.imageFrame.requestLayout();
                        }
                        return true;
                    }
                });

        applySelectionVisual(holder, selected[position]);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            selected[pos] = !selected[pos];
            applySelectionVisual(holder, selected[pos]);
            if (listener != null) listener.onSelectionChanged(hasPendingChanges());
        });
    }

    private void applySelectionVisual(ViewHolder holder, boolean isSelected) {
        holder.border.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.tick.setVisibility(isSelected ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout imageFrame;
        final ImageView image;
        final View border;
        final ImageView tick;
        final TextView name;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageFrame = itemView.findViewById(R.id.managable_image_frame);
            image = itemView.findViewById(R.id.managable_image);
            border = itemView.findViewById(R.id.managable_border);
            tick = itemView.findViewById(R.id.managable_tick);
            name = itemView.findViewById(R.id.managable_name);
        }
    }
}
