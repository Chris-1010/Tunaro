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
import com.ca.tunaro.R;
import com.ca.tunaro.interfaces.Library_RecyclerViewInterface;
import com.ca.tunaro.utils.RankingsTournament;

import java.util.ArrayList;
import java.util.List;

public class RankingsLeaderboardAdapter extends RecyclerView.Adapter<RankingsLeaderboardAdapter.ViewHolder> {
    private final Context context;
    private final Library_RecyclerViewInterface recyclerViewInterface;
    private List<RankingsTournament.LeaderboardEntry> entries = new ArrayList<>();

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
        RankingsTournament.LeaderboardEntry entry = entries.get(position);
        holder.rankText.setText(String.valueOf(entry.rank));
        holder.songNameText.setText(entry.song.getName());
        holder.artistText.setText(entry.song.getArtist());

        Glide.with(context)
                .load(entry.song.getAlbumCoverUrl())
                .placeholder(R.drawable.playlist_placeholder)
                .error(R.drawable.playlist_placeholder)
                .into(holder.albumCoverImage);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public void updateEntries(List<RankingsTournament.LeaderboardEntry> newEntries) {
        this.entries = newEntries;
        notifyDataSetChanged();
    }

    public List<RankingsTournament.LeaderboardEntry> getEntries() {
        return entries;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView rankText;
        ImageView albumCoverImage;
        TextView songNameText, artistText;

        public ViewHolder(@NonNull View itemView, Library_RecyclerViewInterface recyclerViewInterface) {
            super(itemView);
            rankText = itemView.findViewById(R.id.rank_number);
            albumCoverImage = itemView.findViewById(R.id.album_cover);
            songNameText = itemView.findViewById(R.id.song_name);
            artistText = itemView.findViewById(R.id.artist_name);

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
