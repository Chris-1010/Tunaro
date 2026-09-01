package com.ca.tunaro.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ca.tunaro.R;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.models.SongSnippet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compact snippet list shown under a ranking contender: title + play/stop only,
 * no seekbar. Playback shares the same behaviour as the snippet tab — tapping
 * plays the snippet; the row shows a stop icon and an active background while
 * its range is playing.
 */
public class RankingsSnippetAdapter extends RecyclerView.Adapter<RankingsSnippetAdapter.ViewHolder> {

    public interface OnSnippetPlayListener {
        void onPlaySnippet(SongSnippet snippet);
        void onStopSnippet(SongSnippet snippet);
    }

    private final Context context;
    private final OnSnippetPlayListener listener;
    private List<SongSnippet> snippets = new ArrayList<>();

    private long playbackPositionMs = -1;
    private String playingSongId;
    private boolean isPlaying;

    public RankingsSnippetAdapter(Context context, OnSnippetPlayListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_rankings_snippet, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SongSnippet snippet = snippets.get(position);

        String title = snippet.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = String.format(Locale.getDefault(), "Snippet %d", snippet.getSnippetNo());
        }
        holder.title.setText(title);

        boolean active = isActive(snippet);
        holder.playButton.setImageResource(
                active ? R.drawable.stop_circle_filled : R.drawable.play_circle_filled);
        holder.row.setBackground(rowBackground(active));

        // A tap anywhere on the row toggles play/stop. The play icon is a
        // non-clickable indicator, so its touches fall through to the row and the
        // whole row — not just the far edge — is a single hit target.
        holder.row.setOnClickListener(v -> {
            if (listener == null) return;
            if (isActive(snippet)) {
                listener.onStopSnippet(snippet);
            } else {
                listener.onPlaySnippet(snippet);
            }
        });
    }

    @Override
    public int getItemCount() {
        return snippets.size();
    }

    // The row is active — showing a stop icon — when its song is the one playing
    // and the playhead currently sits inside the snippet's range.
    private boolean isActive(SongSnippet snippet) {
        boolean thisSong = playingSongId != null && playingSongId.equals(snippet.getSongId());
        return thisSong && isPlaying
                && playbackPositionMs >= snippet.getStartTime()
                && playbackPositionMs <= snippet.getEndTime();
    }

    private GradientDrawable rowBackground(boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(context.getResources().getDisplayMetrics().density * 10);
        bg.setColor(active
                ? ContextCompat.getColor(context, R.color.tanAccent)
                : 0x22FFFFFF);
        return bg;
    }

    public void updateSnippets(List<SongSnippet> newSnippets) {
        this.snippets = newSnippets != null ? newSnippets : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void updatePlaybackPosition(long positionMs, String songId, boolean playing) {
        this.playbackPositionMs = positionMs;
        this.playingSongId = songId;
        this.isPlaying = playing;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View row;
        final ImageButton playButton;
        final TextView title;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.snippet_row);
            playButton = itemView.findViewById(R.id.snippet_play_button);
            title = itemView.findViewById(R.id.snippet_title);
        }
    }
}
