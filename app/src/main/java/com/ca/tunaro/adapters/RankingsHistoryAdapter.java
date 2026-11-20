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
import com.ca.tunaro.utils.ImageLoads;

/** Lists completed ranking games; a tap opens that game's final standings. */
public class RankingsHistoryAdapter extends RecyclerView.Adapter<RankingsHistoryAdapter.ViewHolder> {

    public interface OnGameClickListener {
        void onGameClick(RankingGame game);
    }

    // How many playlist covers to show before stopping (the count still reflects
    // the true totals).
    private static final int MAX_HISTORY_ICONS = 4;

    /**
     * Row data read from the database up front, so binding never touches the DB.
     * Build it with {@link #preload} on a background thread.
     */
    public static final class Preloaded {
        static final Preloaded EMPTY = new Preloaded(new HashMap<>(), new HashMap<>());

        // song uri → champion song (lean)
        final Map<String, SongModel> champions;
        // playlist_id → cover url (null when the playlist is gone)
        final Map<String, String> playlistCovers;

        Preloaded(Map<String, SongModel> champions, Map<String, String> playlistCovers) {
            this.champions = champions;
            this.playlistCovers = playlistCovers;
        }
    }

    private final Context context;
    private final OnGameClickListener listener;
    private List<RankingGame> games = new ArrayList<>();
    private Preloaded preloaded = Preloaded.EMPTY;

    public RankingsHistoryAdapter(Context context, OnGameClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    /**
     * Reads each game's champion and every referenced playlist cover once.
     * Blocking: call off the main thread.
     */
    public static Preloaded preload(DatabaseHelper db, List<RankingGame> games) {
        Map<String, SongModel> champions = new HashMap<>();
        Map<String, String> covers = new HashMap<>();
        if (games == null) return new Preloaded(champions, covers);
        for (RankingGame game : games) {
            if (!game.finalOrder.isEmpty()) {
                String championId = game.finalOrder.get(0);
                if (!champions.containsKey(championId)) {
                    champions.put(championId, db.getLeanSong(championId));
                }
            }
            if (game.playlistIds == null) continue;
            int seen = 0;
            for (String id : game.playlistIds) {
                if (seen++ >= MAX_HISTORY_ICONS) break;
                if (covers.containsKey(id)) continue;
                PlaylistModel playlist = db.getPlaylistById(id);
                covers.put(id, playlist != null ? playlist.getImage() : null);
            }
        }
        return new Preloaded(champions, covers);
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
            SongModel champion = preloaded.champions.get(game.finalOrder.get(0));
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
                    .load(preloaded.playlistCovers.get(id))
                    .apply(ImageLoads.thumbnailPx(iconSize))
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
                .apply(ImageLoads.thumbnail(context, 48))
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

    public void updateGames(List<RankingGame> newGames, Preloaded rowData) {
        this.games = newGames != null ? newGames : new ArrayList<>();
        this.preloaded = rowData != null ? rowData : Preloaded.EMPTY;
        notifyDataSetChanged();
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
