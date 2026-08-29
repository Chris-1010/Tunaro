package com.ca.tunaro.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.R;
import com.ca.tunaro.interfaces.Library_RecyclerViewInterface;
import com.ca.tunaro.models.RankedSong;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RankingsLeaderboardAdapter extends RecyclerView.Adapter<RankingsLeaderboardAdapter.ViewHolder> {
    private final Context context;
    private final Library_RecyclerViewInterface recyclerViewInterface;
    // Ratings seed at this value, so scores are shown as a signed offset from it
    // (+0 = never ranked).
    private static final int ELO_BASELINE = 1500;
    private List<RankedSong> entries = new ArrayList<>();
    // uri -> [oldElo, newElo], shown as "+old → +new" for the just-finished game only.
    private Map<String, int[]> eloChanges = null;

    public RankingsLeaderboardAdapter(Context context, Library_RecyclerViewInterface recyclerViewInterface) {
        this.context = context;
        this.recyclerViewInterface = recyclerViewInterface;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_leaderboard_entry, parent, false);
        return new ViewHolder(view, recyclerViewInterface);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RankedSong entry = entries.get(position);
        holder.rankText.setText(String.valueOf(entry.rank));
        holder.songNameText.setText(entry.song.getName());
        holder.artistText.setText(entry.song.getArtist());

        Glide.with(context)
                .load(entry.song.getAlbumCoverUrl())
                .placeholder(R.drawable.playlist_placeholder)
                .error(R.drawable.playlist_placeholder)
                .into(holder.albumCoverImage);

        int[] change = eloChanges != null ? eloChanges.get(entry.song.getId()) : null;
        if (change != null) {
            holder.eloChangeText.setText(String.format(Locale.getDefault(), "%+d → %+d",
                    change[0] - ELO_BASELINE, change[1] - ELO_BASELINE));
            int colour = change[1] > change[0] ? Color.parseColor("#4CAF50")
                    : change[1] < change[0] ? Color.parseColor("#E57373")
                    : Color.parseColor("#AAAAAA");
            holder.eloChangeText.setTextColor(colour);
            holder.eloChangeText.setVisibility(View.VISIBLE);
        } else {
            holder.eloChangeText.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public void updateEntries(List<RankedSong> newEntries) {
        this.entries = newEntries;
        notifyDataSetChanged();
    }

    public void setEloChanges(Map<String, int[]> eloChanges) {
        this.eloChanges = eloChanges;
    }

    public List<RankedSong> getEntries() {
        return entries;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView rankText;
        ImageView albumCoverImage;
        TextView songNameText, artistText, eloChangeText;

        public ViewHolder(@NonNull View itemView, Library_RecyclerViewInterface recyclerViewInterface) {
            super(itemView);
            rankText = itemView.findViewById(R.id.rank_number);
            albumCoverImage = itemView.findViewById(R.id.album_cover);
            songNameText = itemView.findViewById(R.id.song_name);
            artistText = itemView.findViewById(R.id.artist_name);
            eloChangeText = itemView.findViewById(R.id.elo_change);

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
