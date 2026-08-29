package com.ca.tunaro.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.R;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.models.RankingGame;
import com.ca.tunaro.models.SongModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lists completed ranking games; a tap opens that game's final standings. */
public class RankingsHistoryAdapter extends RecyclerView.Adapter<RankingsHistoryAdapter.ViewHolder> {

    public interface OnGameClickListener {
        void onGameClick(RankingGame game);
    }

    // How many playlist covers to show before stopping (the count still reflects
    // the true totals).
    private static final int MAX_HISTORY_ICONS = 4;

    private final Context context;
    private final DatabaseHelper dbHelper;
    private final OnGameClickListener listener;
    private List<RankingGame> games = new ArrayList<>();
    // playlist_id → cover url, filled lazily so the same playlist isn't re-read
    // across rows.
    private final Map<String, String> coverCache = new HashMap<>();

    public RankingsHistoryAdapter(Context context, DatabaseHelper dbHelper, OnGameClickListener listener) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_ranking_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RankingGame game = games.get(position);

        String championName = "Champion";
        String coverUrl = null;
        if (!game.finalOrder.isEmpty()) {
            SongModel champion = dbHelper.getLeanSong(game.finalOrder.get(0));
            if (champion != null) {
                championName = champion.getName();
                coverUrl = champion.getAlbumCoverUrl();
            }
        }
        holder.champion.setText(championName);

        int songCount = game.finalOrder.size();
        String when = DatabaseHelper.getRelativeTimeDescription(game.completedAt);
        holder.when.setText(when);

        // Playlist covers between the "when" and the song count.
        holder.playlistIcons.removeAllViews();
        List<String> playlistIds = game.playlistIds != null ? game.playlistIds : new ArrayList<>();
        int iconSize = dpToPx(18);
        int iconGap = dpToPx(3);
        int shown = 0;
        for (String id : playlistIds) {
            if (shown >= MAX_HISTORY_ICONS) break;
            ImageView icon = new ImageView(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(iconSize, iconSize);
            params.setMargins(0, 0, iconGap, 0);
            icon.setLayoutParams(params);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(context)
                    .load(coverForPlaylist(id))
                    .placeholder(R.drawable.playlist_placeholder)
                    .error(R.drawable.playlist_placeholder)
                    .into(icon);
            holder.playlistIcons.addView(icon);
            shown++;
        }
        boolean hasIcons = shown > 0;
        holder.dot.setVisibility(hasIcons ? View.VISIBLE : View.GONE);
        holder.songCount.setText((hasIcons ? " · " : "") + songCount + " songs");

        Glide.with(context)
                .load(coverUrl)
                .placeholder(R.drawable.playlist_placeholder)
                .error(R.drawable.playlist_placeholder)
                .into(holder.cover);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onGameClick(game);
        });
    }

    @Override
    public int getItemCount() {
        return games.size();
    }

    public void updateGames(List<RankingGame> newGames) {
        this.games = newGames != null ? newGames : new ArrayList<>();
        notifyDataSetChanged();
    }

    private String coverForPlaylist(String id) {
        if (coverCache.containsKey(id)) return coverCache.get(id);
        PlaylistModel playlist = dbHelper.getPlaylistById(id);
        String cover = playlist != null ? playlist.getImage() : null;
        coverCache.put(id, cover);
        return cover;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView champion;
        final TextView when;
        final TextView dot;
        final LinearLayout playlistIcons;
        final TextView songCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.champion_cover);
            champion = itemView.findViewById(R.id.history_champion);
            when = itemView.findViewById(R.id.history_when);
            dot = itemView.findViewById(R.id.history_dot);
            playlistIcons = itemView.findViewById(R.id.history_playlist_icons);
            songCount = itemView.findViewById(R.id.history_song_count);
        }
    }
}
