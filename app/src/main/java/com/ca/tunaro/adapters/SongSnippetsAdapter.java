package com.ca.tunaro.adapters;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ca.tunaro.R;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.models.SongSnippet;
import com.ca.tunaro.utils.SnippetTheme;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.Locale;

public class SongSnippetsAdapter extends RecyclerView.Adapter<SongSnippetsAdapter.SnippetViewHolder> {
    private final Context context;
    private List<SongSnippet> snippets;
    private final OnSnippetActionListener listener;

    // Live playback state used to drive each row's seekbar.
    private long playbackPositionMs = -1;
    private String playingSongId;
    private boolean isPlaying;

    // Ids of all variants of the song being viewed. A row reacts to playback
    // whenever the playing track is any of these (snippets are merged across
    // variants), not only the exact id stored on the snippet.
    private List<String> variantUris;

    // Album-derived theme; defaults to a neutral fallback until set.
    private SnippetTheme theme = SnippetTheme.fallback();

    public interface OnSnippetActionListener {
        void onPlaySnippet(SongSnippet snippet);
        void onPauseSnippet(SongSnippet snippet);
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

        // Set up the seek bar range. Position is driven by playback only.
        long totalDuration = snippet.getEndTime() - snippet.getStartTime();
        holder.snippetSeekBar.setMax((int) Math.max(0, totalDuration));

        // The seekbar reflects playback but is not user-draggable.
        holder.snippetSeekBar.setOnTouchListener((v, e) -> true);

        applyTheme(holder);
        bindSeekBarPosition(holder, snippet, false);

        // Set up play/pause button click listener. When the row is actively
        // playing, the button pauses; otherwise it (re)starts the snippet.
        holder.snippetPlayButton.setOnClickListener(v -> {
            if (listener == null) return;
            if (holder.active) {
                listener.onPauseSnippet(snippet);
            } else {
                listener.onPlaySnippet(snippet);
            }
        });

        // Mode button: cycles the end-behaviour of the currently-playing snippet
        // (Stop → Loop → Detach → Stop) and reflects the new mode immediately.
        holder.modeButton.setOnClickListener(v -> {
            PlaybackManager.getInstance().cycleSnippetEndMode();
            bindModeButton(holder, snippet);
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
    public void onBindViewHolder(@NonNull SnippetViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_POSITION)) {
            // Lightweight update: only move the seekbar, allowing animation.
            bindSeekBarPosition(holder, snippets.get(position), true);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    /**
     * Compute and apply the seekbar progress for a row from the live playhead.
     * Rows whose [start, end] range contains the playhead fill proportionally;
     * once the playhead leaves the range the bar holds its last position.
     * When playback restarts before the snippet's start, the bar animates back
     * down to 0 instead of snapping.
     */
    private void bindSeekBarPosition(SnippetViewHolder holder, SongSnippet snippet, boolean allowAnimation) {
        SeekBar bar = holder.snippetSeekBar;
        int max = bar.getMax();

        boolean isThisSong = playingSongId != null
                && (playingSongId.equals(snippet.getSongId())
                    || (variantUris != null && variantUris.contains(playingSongId)));
        long start = snippet.getStartTime();
        long end = snippet.getEndTime();

        // The row is "active" — show a pause icon — when its song is playing and
        // the playhead currently sits inside the snippet's range.
        boolean active = isThisSong && isPlaying
                && playbackPositionMs >= start && playbackPositionMs <= end;
        holder.active = active;
        holder.snippetPlayButton.setImageResource(
                active ? R.drawable.stop_circle_filled : R.drawable.play_circle_filled);

        bindModeButton(holder, snippet);

        if (!isThisSong || playbackPositionMs < 0) {
            // Not the playing song: leave whatever position the bar holds.
            cancelAnimator(holder);
            return;
        }

        if (playbackPositionMs < start) {
            // Playhead is before the snippet. If the bar is still filled (the
            // snippet just finished and the song looped/restarted), ease it back
            // to 0 rather than snapping. Let an already-running reverse animation
            // finish instead of restarting it on every 100ms tick.
            if (allowAnimation && bar.getProgress() > 0) {
                if (holder.seekAnimator == null || !holder.seekAnimator.isRunning()) {
                    animateSeekTo(holder, bar.getProgress(), 0);
                }
            } else {
                cancelAnimator(holder);
                bar.setProgress(0);
            }
            return;
        }

        // Within or past the range: fill proportionally, holding at full past end.
        cancelAnimator(holder);
        int target = (int) Math.min(max, playbackPositionMs - start);
        bar.setProgress(target);
    }

    /**
     * Drive the mode button. Two cases show it:
     *   - the snippet currently playing/paused: glyph reflects the live end-mode;
     *   - a natural passthrough — a normal song playing through this snippet's
     *     range (not snippet playback): show the continue/passthrough glyph,
     *     since that is exactly what is happening.
     * Otherwise it is hidden. At most one row shows it.
     */
    private void bindModeButton(SnippetViewHolder holder, SongSnippet snippet) {
        PlaybackManager pm = PlaybackManager.getInstance();
        SongSnippet current = pm.getCurrentSnippet();
        boolean isCurrentRow = pm.isSnippetPlaying() && current != null && current == snippet;

        int glyph;
        if (isCurrentRow) {
            switch (pm.getSnippetEndMode()) {
                case LOOP:
                    glyph = R.drawable.snippet_mode_loop;
                    break;
                case DETACH:
                    glyph = R.drawable.snippet_mode_detach;
                    break;
                case STOP:
                default:
                    glyph = R.drawable.snippet_mode_stop;
                    break;
            }
        } else if (isNaturalPassthrough(snippet)) {
            glyph = R.drawable.snippet_mode_detach;
        } else {
            holder.modeButton.setVisibility(View.GONE);
            return;
        }

        holder.modeButton.setVisibility(View.VISIBLE);
        holder.modeButton.setImageResource(glyph);
        // The glyph itself distinguishes the mode; keep it a constant white so
        // the mode isn't also signalled by colour.
        holder.modeButton.setColorFilter(android.graphics.Color.WHITE);
    }

    // True when a normal (non-snippet) playhead is currently inside this
    // snippet's range — the song is naturally passing through the snippet.
    private boolean isNaturalPassthrough(SongSnippet snippet) {
        if (PlaybackManager.getInstance().isSnippetMode()) return false;
        boolean isThisSong = playingSongId != null
                && (playingSongId.equals(snippet.getSongId())
                    || (variantUris != null && variantUris.contains(playingSongId)));
        return isThisSong && isPlaying
                && playbackPositionMs >= snippet.getStartTime()
                && playbackPositionMs <= snippet.getEndTime();
    }

    private void cancelAnimator(SnippetViewHolder holder) {
        if (holder.seekAnimator != null) {
            holder.seekAnimator.cancel();
            holder.seekAnimator = null;
        }
    }

    private void animateSeekTo(SnippetViewHolder holder, int from, int to) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(450);
        animator.addUpdateListener(a -> holder.snippetSeekBar.setProgress((int) a.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (holder.seekAnimator == animator) holder.seekAnimator = null;
            }
        });
        holder.seekAnimator = animator;
        animator.start();
    }

    private void applyTheme(SnippetViewHolder holder) {
        // Row background + border.
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setCornerRadius(dpToPx(holder.itemView, 25));
        rowBg.setColor(theme.rowBackground);
        rowBg.setStroke(dpToPx(holder.itemView, 1), theme.border);
        holder.rowContainer.setBackground(rowBg);

        // Text colours.
        holder.snippetTitle.setTextColor(theme.primaryText);
        holder.snippetTimeRange.setTextColor(theme.primaryText);
        holder.snippetNumber.setTextColor(theme.secondaryText);

        // Seekbar colours.
        holder.snippetSeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(theme.seekbarProgress));
        holder.snippetSeekBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.seekbarTrack));
        holder.snippetSeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(theme.seekbarThumb));

        // Mode button tint is set per-mode in bindModeButton; play button below.
        holder.snippetPlayButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.playButton));
        // Play/stop glyph: black or white depending on the play button's brightness.
        holder.snippetPlayButton.setImageTintList(android.content.res.ColorStateList.valueOf(theme.playButtonIcon));
    }

    private int dpToPx(View view, int dp) {
        return Math.round(dp * view.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return snippets.size();
    }

    public void updateSnippets(List<SongSnippet> newSnippets) {
        this.snippets = newSnippets;
        notifyDataSetChanged();
    }

    public void setTheme(SnippetTheme theme) {
        this.theme = theme;
        notifyDataSetChanged();
    }

    public void setVariantUris(List<String> variantUris) {
        this.variantUris = variantUris;
    }

    /**
     * Feed the live playhead in. {@code songId} is the id of the currently
     * playing track so each row can decide whether the playhead applies to it.
     */
    public void updatePlaybackPosition(long positionMs, String songId, boolean isPlaying) {
        this.playbackPositionMs = positionMs;
        this.playingSongId = songId;
        this.isPlaying = isPlaying;
        notifyItemRangePositionChanged();
    }

    private static final Object PAYLOAD_POSITION = new Object();

    private void notifyItemRangePositionChanged() {
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_POSITION);
    }

    // Helper method to format milliseconds into MM:SS format
    private String formatTime(long timeMs) {
        int totalSeconds = (int) (timeMs / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    static class SnippetViewHolder extends RecyclerView.ViewHolder {
        View rowContainer;
        TextView snippetNumber;
        TextView snippetTitle;
        TextView snippetTimeRange;
        SeekBar snippetSeekBar;
        FloatingActionButton snippetPlayButton;
        ImageButton modeButton;
        ValueAnimator seekAnimator;
        boolean active;

        public SnippetViewHolder(@NonNull View itemView) {
            super(itemView);
            rowContainer = itemView.findViewById(R.id.constraintLayout2);
            snippetNumber = itemView.findViewById(R.id.snippetNumber);
            snippetTitle = itemView.findViewById(R.id.snippetTitle);
            snippetTimeRange = itemView.findViewById(R.id.snippetTimeRange);
            snippetSeekBar = itemView.findViewById(R.id.snippetSeekBar);
            snippetPlayButton = itemView.findViewById(R.id.snippetPlayButton);
            modeButton = itemView.findViewById(R.id.modeButton);
        }
    }
}
