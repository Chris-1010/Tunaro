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
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.ca.tunaro.R;
import com.ca.tunaro.models.AlbumModel;

import java.util.ArrayList;

/**
 * Renders an artist's discography in ArtistView's Albums tab. The contextual third line shows
 * whichever metric the current sort is keyed on (track count or popularity), mirroring how
 * Song_RecyclerViewAdapter surfaces its sort context.
 */
public class Album_RecyclerViewAdapter extends RecyclerView.Adapter<Album_RecyclerViewAdapter.ViewHolder> {

    public interface OnAlbumClickListener {
        // Tapping/holding the album cover plays the album (discarding the current queue).
        void onAlbumPlay(AlbumModel album);
        // Tapping elsewhere on the row opens the album's detail view.
        void onAlbumOpen(AlbumModel album);
    }

    // Mirrors the Albums tab sort spinner positions.
    public static final int SORT_RELEASE_DATE = 0;
    public static final int SORT_TYPE = 1;
    public static final int SORT_NAME = 2;
    public static final int SORT_TRACK_COUNT = 3;
    public static final int SORT_POPULARITY = 4;

    private final Context context;
    private ArrayList<AlbumModel> albums;
    private final OnAlbumClickListener clickListener;
    private int sortOption = SORT_RELEASE_DATE;

    public Album_RecyclerViewAdapter(Context context, ArrayList<AlbumModel> albums, OnAlbumClickListener clickListener) {
        this.context = context;
        this.albums = albums;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.album_recycler_view_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AlbumModel album = albums.get(position);

        holder.nameView.setText(album.getName());
        holder.nameView.setSelected(true); // Enable marquee on long names.

        // The release type is shown as the coloured chip, so the meta line carries the release date
        // and, when sorting by track count, the track count alongside it.
        StringBuilder meta = new StringBuilder();
        String releaseDate = formatReleaseDateForDisplay(album.getReleaseDate());
        if (releaseDate != null) meta.append(releaseDate);
        if (sortOption == SORT_TRACK_COUNT && album.getTrackCount() >= 0) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(album.getTrackCount())
                    .append(album.getTrackCount() == 1 ? " track" : " tracks");
        }
        holder.metaView.setText(meta.toString());

        holder.typeBadge.setText(capitalise(album.getAlbumType()));
        holder.typeBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(typeColor(album.getAlbumType())));

        // Contextual line: only for popularity. Track count now rides in the meta line above.
        if (sortOption == SORT_POPULARITY && album.getPopularity() >= 0) {
            holder.contextualView.setVisibility(View.VISIBLE);
            holder.contextualView.setText("Popularity: " + album.getPopularity() + "/100");
        } else {
            holder.contextualView.setVisibility(View.GONE);
        }

        Glide.with(context)
                .load(album.getCoverImageUrl())
                .placeholder(R.drawable.song_placeholder)
                .error(R.drawable.song_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.coverView);

        // Cover tap/hold plays the album; the rest of the row opens the album detail view.
        holder.coverView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onAlbumPlay(album);
        });
        holder.coverView.setOnLongClickListener(v -> {
            if (clickListener != null) clickListener.onAlbumPlay(album);
            return true;
        });
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onAlbumOpen(album);
        });
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public void updateAlbums(ArrayList<AlbumModel> newAlbums) {
        this.albums = newAlbums;
        notifyDataSetChanged();
    }

    public void setSortOption(int sortOption) {
        this.sortOption = sortOption;
        notifyDataSetChanged();
    }

    private static int typeColor(String albumType) {
        if (albumType == null) return 0xFF2196F3;
        switch (albumType.toLowerCase()) {
            case "single": return 0xFF2E7D32;
            case "compilation": return 0xFFEF6C00;
            default: return 0xFF1565C0;
        }
    }

    private static String capitalise(String text) {
        if (text == null || text.isEmpty()) return "Album";
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    // Formats a Spotify release date ("YYYY", "YYYY-MM", or "YYYY-MM-DD") for display, including
    // the month where available. Mirrors Song_RecyclerViewAdapter's formatting.
    private static final String[] MONTH_NAMES =
            {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private static String formatReleaseDateForDisplay(String releaseDate) {
        if (releaseDate == null || releaseDate.isEmpty()) return null;
        try {
            String[] parts = releaseDate.split("-");
            if (parts.length == 1) {
                return parts[0];
            } else if (parts.length == 2) {
                int month = Integer.parseInt(parts[1]);
                return MONTH_NAMES[month - 1] + " " + parts[0];
            } else if (parts.length == 3) {
                int day = Integer.parseInt(parts[2]);
                int month = Integer.parseInt(parts[1]);
                String year = parts[0];
                if (month == 1 && day == 1) return year;
                if (day == 1) return MONTH_NAMES[month - 1] + " " + year;
                return day + " " + MONTH_NAMES[month - 1] + " " + year;
            }
        } catch (Exception e) {
            return releaseDate;
        }
        return releaseDate;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView coverView;
        TextView nameView, metaView, contextualView, typeBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            coverView = itemView.findViewById(R.id.albumCover);
            nameView = itemView.findViewById(R.id.albumName);
            metaView = itemView.findViewById(R.id.albumMeta);
            contextualView = itemView.findViewById(R.id.albumContextual);
            typeBadge = itemView.findViewById(R.id.albumTypeBadge);
        }
    }
}
