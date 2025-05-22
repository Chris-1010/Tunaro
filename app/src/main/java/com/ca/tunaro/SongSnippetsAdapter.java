package com.ca.tunaro;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.Locale;

public class SongSnippetsAdapter extends RecyclerView.Adapter<SongSnippetsAdapter.SnippetViewHolder> {
    private final Context context;
    private List<SongSnippet> snippets;
    private final OnSnippetActionListener listener;

    public interface OnSnippetActionListener {
        void onPlaySnippet(SongSnippet snippet);
        void onDetachSnippet(SongSnippet snippet);
        void onEditSnippet(SongSnippet snippet);
    }

    public SongSnippetsAdapter(Context context, List<SongSnippet> snippets, OnSnippetActionListener listener) {
        this.context = context;
        this.snippets = snippets;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SnippetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.snippet_item, parent, false);
        return new SnippetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SnippetViewHolder holder, int position) {
        SongSnippet snippet = snippets.get(position);

        // Set the snippet number
        holder.snippetNumber.setText(String.format(Locale.getDefault(), "# %d", snippet.getSnippetNo()));

        // Set the snippet title
        String title = snippet.getTitle();
        if (title == null || title.isEmpty()) {
            holder.snippetTitle.setVisibility(View.GONE);
        } else {
            holder.snippetTitle.setVisibility(View.VISIBLE);
            holder.snippetTitle.setText(title);
        }

        // Format and set the time range
        String timeRange = formatTime(snippet.getStartTime()) + " - " + formatTime(snippet.getEndTime());
        holder.snippetTimeRange.setText(timeRange);

        // Set up the seek bar
        long totalDuration = snippet.getEndTime() - snippet.getStartTime();
        holder.snippetSeekBar.setMax((int) totalDuration);
        holder.snippetSeekBar.setProgress(0); // Initial position

        // Set up play button click listener
        holder.snippetPlayButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlaySnippet(snippet);
            }
        });

        // Set up detach button click listener
        holder.detachButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDetachSnippet(snippet);
            }
        });

        // Set up long click for editing
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onEditSnippet(snippet);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return snippets.size();
    }

    public void updateSnippets(List<SongSnippet> newSnippets) {
        this.snippets = newSnippets;
        notifyDataSetChanged();
    }

    // Helper method to format milliseconds into MM:SS format
    private String formatTime(long timeMs) {
        int totalSeconds = (int) (timeMs / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    static class SnippetViewHolder extends RecyclerView.ViewHolder {
        TextView snippetNumber;
        TextView snippetTitle;
        TextView snippetTimeRange;
        SeekBar snippetSeekBar;
        FloatingActionButton snippetPlayButton;
        ImageButton detachButton;

        public SnippetViewHolder(@NonNull View itemView) {
            super(itemView);
            snippetNumber = itemView.findViewById(R.id.snippetNumber);
            snippetTitle = itemView.findViewById(R.id.snippetTitle);
            snippetTimeRange = itemView.findViewById(R.id.snippetTimeRange);
            snippetSeekBar = itemView.findViewById(R.id.snippetSeekBar);
            snippetPlayButton = itemView.findViewById(R.id.snippetPlayButton);
            detachButton = itemView.findViewById(R.id.detachButton);
        }
    }
}