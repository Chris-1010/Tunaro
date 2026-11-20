package com.ca.tunaro.adapters;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.R;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.models.SongRankInfo;
import com.ca.tunaro.interfaces.Song_RecyclerViewInterface;
import com.ca.tunaro.utils.AppExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ca.tunaro.utils.ImageLoads;

public class Song_RecyclerViewAdapter extends RecyclerView.Adapter<Song_RecyclerViewAdapter.ViewHolder> {
    private static final String TAG = "SongAdapter";

    // Partial-rebind payloads: each refreshes one aspect of a row without
    // touching the rest (notably without reloading the album cover).
    private static final Object PAYLOAD_CONTEXT = new Object();
    private static final Object PAYLOAD_FLAGS = new Object();
    private static final Object PAYLOAD_PLAYBACK = new Object();

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Song_RecyclerViewInterface recyclerViewInterface;
    private ArrayList<SongModel> songModels;
    private final DatabaseHelper dbHelper;

    private int currentSortOption = -1;
    private boolean shouldShowContextualInfo = false;
    // Whether the artist-name row is shown. Hidden on the ArtistView Songs tab
    // (the page already belongs to that artist), so the layout collapses.
    private boolean showArtist = true;
    // When set (ArtistView Songs tab), rows whose URI is in this set show a green "added" tick.
    private java.util.Set<String> addedUris = null;
    private Map<String, Integer> listenCountMap = null;
    private Map<String, Integer> popularityMap = null;
    private Map<String, String> lastListenedMap = null;
    private Map<String, SongRankInfo> rankInfoMap = null;
    // Song IDs that have notes / snippets, preloaded off the main thread so
    // binding a row never queries the database.
    private Set<String> noteIds = Collections.emptySet();
    private Set<String> snippetIds = Collections.emptySet();
    private int flagsGeneration = 0;

    public Song_RecyclerViewAdapter(Context context, Song_RecyclerViewInterface recyclerViewInterface, ArrayList<SongModel> songModels) {
        this.context = context;
        this.recyclerViewInterface = recyclerViewInterface;
        // Own a copy: callers mutate and re-submit their list, which would
        // otherwise leave nothing for updateSongs to diff against.
        this.songModels = new ArrayList<>(songModels);
        this.dbHelper = DatabaseHelper.getInstance(context);
        refreshAnnotationFlags();
    }

    /**
     * Reloads which songs have notes/snippets on the I/O executor, then refreshes
     * the indicator icons of bound rows if anything changed. Call when returning
     * to the list, since a note may have been added or removed meanwhile.
     */
    public void refreshAnnotationFlags() {
        final int generation = ++flagsGeneration;
        AppExecutors.io().execute(() -> {
            Set<String> notes;
            Set<String> snippets;
            try {
                notes = new HashSet<>(dbHelper.getSongIdsWithNotes());
                snippets = new HashSet<>(dbHelper.getSongIdsWithSnippets());
            } catch (RuntimeException e) {
                Log.e(TAG, "Failed to load note/snippet flags", e);
                return;
            }
            mainHandler.post(() -> {
                if (generation != flagsGeneration) return; // A newer load superseded this one.
                boolean changed = !notes.equals(noteIds) || !snippets.equals(snippetIds);
                noteIds = notes;
                snippetIds = snippets;
                if (changed) notifyAllRows(PAYLOAD_FLAGS);
            });
        });
    }

    private void notifyAllRows(Object payload) {
        if (!songModels.isEmpty()) notifyItemRangeChanged(0, songModels.size(), payload);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.song_recycler_view_row, parent, false);
        return new ViewHolder(view, recyclerViewInterface, this);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SongModel model = songModels.get(position);
        holder.songNameView.setSelected(true); // Enable marquee
        holder.songNameView.setText(model.getName());
        if (showArtist) {
            holder.artistView.setVisibility(View.VISIBLE);
            holder.artistView.setSelected(true); // Enable marquee
            holder.artistView.setText(model.getArtist());
        } else {
            holder.artistView.setVisibility(View.GONE);
        }

        bindPlaybackState(holder, model);

        // Reset any in-progress swipe visuals when the row is (re)bound.
        holder.swipeConsumedClick = false;
        holder.queueSwipeOverlay.setVisibility(View.GONE);
        holder.queueSwipeOverlay.setClipBounds(null);
        holder.queueSwipeOverlay.setAlpha(1f);
        holder.queueSwipeIcon.setVisibility(View.GONE);

        // Load image using Glide
        Glide.with(context)
                .load(model.getAlbumCoverUrl())
                .apply(ImageLoads.thumbnail(context, 60))
                .placeholder(R.drawable.song_placeholder)
                .error(R.drawable.song_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.imageCoverView);

        bindFlags(holder, model);
        bindContextualInfo(holder, model);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        SongModel model = songModels.get(position);
        for (Object payload : payloads) {
            if (payload == PAYLOAD_PLAYBACK) bindPlaybackState(holder, model);
            else if (payload == PAYLOAD_FLAGS) bindFlags(holder, model);
            else if (payload == PAYLOAD_CONTEXT) bindContextualInfo(holder, model);
            else {
                onBindViewHolder(holder, position);
                return;
            }
        }
    }

    // Now-playing highlight and the lime queued edge on the album cover.
    private void bindPlaybackState(ViewHolder holder, SongModel model) {
        PlaybackManager pm = PlaybackManager.getInstance();
        SongModel currentSong = pm.getCurrentSong();
        boolean isPlaying = currentSong != null && currentSong.getUri().equals(model.getUri());
        holder.cardView.setForeground(isPlaying
                ? context.getDrawable(R.drawable.song_active_border)
                : null);
        holder.cardView.setCardBackgroundColor(isPlaying
                ? 0xFF162B1E  // dark green tint over blueBlack
                : 0xFF111f28);
        holder.showQueuedEdge(pm.isInQueue(model));
    }

    // "Added" tick plus the has-notes / has-snippets icons, all from preloaded sets.
    private void bindFlags(ViewHolder holder, SongModel model) {
        boolean added = addedUris != null && addedUris.contains(model.getId());
        holder.addedIcon.setVisibility(added ? View.VISIBLE : View.GONE);
        holder.hasNotesIcon.setVisibility(noteIds.contains(model.getId()) ? View.VISIBLE : View.GONE);
        holder.hasSnippetsIcon.setVisibility(snippetIds.contains(model.getId()) ? View.VISIBLE : View.GONE);
    }

    private void bindContextualInfo(ViewHolder holder, SongModel model) {
        // Handle contextual information display
        if (shouldShowContextualInfo) {
            holder.contextualInfoView.setVisibility(View.VISIBLE);

            switch (currentSortOption) {
                case 0: // Date Added
                    if (model.getDateAddedToPlaylist() != null) {
                        String dateAdded = formatDateForDisplay(model.getDateAddedToPlaylist());
                        holder.contextualInfoView.setText("Added: " + dateAdded);
                    } else {
                        holder.contextualInfoView.setText("Added: Unknown");
                    }
                    break;

                case 1: // Last Listened
                    if (lastListenedMap == null) {
                        // Timestamps are still loading; leave the line blank meanwhile.
                        holder.contextualInfoView.setText("");
                        break;
                    }
                    String lastListened = lastListenedMap.get(model.getId());
                    if (lastListened != null) {
                        String formattedTime = DatabaseHelper.getRelativeTimeDescription(lastListened);
                        holder.contextualInfoView.setText("Last listened: " + formattedTime);
                    } else {
                        holder.contextualInfoView.setText("Never listened");
                    }
                    break;

                case 3: // Length/Duration
                    holder.contextualInfoView.setText("Duration: " + model.getDurationString());
                    break;

                case 5: // Popularity
                    int popularity = popularityMap != null
                            ? popularityMap.getOrDefault(model.getId(), model.getPopularity())
                            : model.getPopularity();
                    holder.contextualInfoView.setText("Popularity: " + popularity + "/100");
                    break;

                case 6: // Listen Count
                    int listenCount = 0;
                    if (listenCountMap != null) {
                        listenCount = listenCountMap.getOrDefault(model.getId(), 0);
                    }
                    String listenText = listenCount == 1 ? "1 listen" : listenCount + " listens";
                    holder.contextualInfoView.setText(listenText);
                    break;

                case 7: // Release Date
                    String releaseDate = model.getReleaseDate();
                    String formattedReleaseDate = formatReleaseDateForDisplay(releaseDate);
                    holder.contextualInfoView.setText("Released: " + formattedReleaseDate);
                    break;

                case 8: // Rank (Elo)
                    SongRankInfo rankInfo = rankInfoMap != null ? rankInfoMap.get(model.getId()) : null;
                    if (rankInfo != null) {
                        // Rating shown as a signed offset from the 1500 seed.
                        long offset = Math.round(rankInfo.rating) - 1500;
                        String sign = offset >= 0 ? "+" : "";
                        holder.contextualInfoView.setText(
                                "#" + rankInfo.rank + " (" + sign + offset + ")");
                    } else {
                        holder.contextualInfoView.setText("Unranked");
                    }
                    break;
            }
        } else {
            holder.contextualInfoView.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return getSongModels().size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView imageCoverView;
        View coverClip;
        View queuedEdge;
        View queueSwipeOverlay;
        ImageView queueSwipeIcon;
        ImageView addedIcon;
        ImageView hasNotesIcon;
        ImageView hasSnippetsIcon;
        TextView songNameView, artistView;
        TextView contextualInfoView;
        final Song_RecyclerViewAdapter adapter;
        // Set true when a swipe gesture ends so the trailing click is ignored.
        boolean swipeConsumedClick;

        public ViewHolder(@NonNull View itemView, Song_RecyclerViewInterface recyclerViewInterface,
                          Song_RecyclerViewAdapter adapter) {
            super(itemView);
            this.adapter = adapter;
            cardView = itemView.findViewById(R.id.cardView);
            songNameView = itemView.findViewById(R.id.songNameView);
            artistView = itemView.findViewById(R.id.artistView);
            imageCoverView = itemView.findViewById(R.id.albumCoverView);
            coverClip = itemView.findViewById(R.id.coverClip);
            queuedEdge = itemView.findViewById(R.id.queuedEdge);
            queueSwipeOverlay = itemView.findViewById(R.id.queueSwipeOverlay);
            queueSwipeIcon = itemView.findViewById(R.id.queueSwipeIcon);
            addedIcon = itemView.findViewById(R.id.addedIcon);
            hasNotesIcon = itemView.findViewById(R.id.hasNotesIcon);
            hasSnippetsIcon = itemView.findViewById(R.id.hasSnippetsIcon);
            contextualInfoView = itemView.findViewById(R.id.contextualInfoView);

            // Clip overlays (lime edge, swipe overlay) to the cover's rounded
            // corners so nothing can draw outside the album art's bounds.
            final float radius = 8 * coverClip.getResources().getDisplayMetrics().density;
            coverClip.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            coverClip.setClipToOutline(true);

            attachQueueSwipe();

            // Open SongView on click
            itemView.setOnClickListener(view -> {
                if (swipeConsumedClick) { swipeConsumedClick = false; return; }
                if (recyclerViewInterface != null) {
                    int position = getAdapterPosition();

                    if (position != RecyclerView.NO_POSITION) {
                        recyclerViewInterface.onItemClick(position, itemView);
                    }
                }
            });

            // Tap on album cover — start queue from this position
            imageCoverView.setOnClickListener(view -> {
                if (swipeConsumedClick) { swipeConsumedClick = false; return; }
                if (recyclerViewInterface != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        recyclerViewInterface.onAlbumCoverClick(position);
                    }
                }
            });

            // Long press on album cover — play individually (no queue). Any host that
            // implements the interface handles it; the default is a no-op.
            imageCoverView.setOnLongClickListener(view -> {
                if (recyclerViewInterface != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        recyclerViewInterface.onAlbumCoverLongClick(position);
                        return true;
                    }
                }
                return false;
            });
        }

        // Swipe anywhere on the row to the right to toggle the song in the
        // queue. The album cover stays put; a green/red overlay wipes in from
        // the cover's left edge, tracking the swipe distance. The action
        // commits once the swipe passes a threshold, then the overlay eases out.
        private void attachQueueSwipe() {
            final int touchSlop = ViewConfiguration.get(itemView.getContext()).getScaledTouchSlop();
            final int addColor = ContextCompat.getColor(itemView.getContext(), R.color.queueAddGreen);
            final int removeColor = ContextCompat.getColor(itemView.getContext(), R.color.queueRemoveRed);

            View.OnTouchListener swipeListener = new View.OnTouchListener() {
                float downX, downY;
                boolean swiping;
                int rowWidth;

                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = e.getRawX();
                            downY = e.getRawY();
                            swiping = false;
                            rowWidth = itemView.getWidth();
                            // A new touch begins: clear any stale suppression flag
                            // so it can never outlive the gesture that set it.
                            swipeConsumedClick = false;
                            return false; // let click/long-press detection proceed

                        case MotionEvent.ACTION_MOVE: {
                            float dx = e.getRawX() - downX;
                            float dy = e.getRawY() - downY;
                            if (!swiping) {
                                if (dx > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                                    SongModel song = songAt();
                                    // The currently-playing song can't be queued or
                                    // removed, so ignore swipes on its row entirely.
                                    if (song != null && isCurrentSong(song)) {
                                        return false;
                                    }
                                    swiping = true;
                                    boolean removing = song != null
                                            && PlaybackManager.getInstance().isInQueue(song);
                                    queueSwipeOverlay.getBackground().mutate().setTint(removing ? removeColor : addColor);
                                    queueSwipeIcon.setImageResource(removing
                                            ? R.drawable.ic_queue_remove : R.drawable.ic_queue_add);
                                    queueSwipeOverlay.setAlpha(1f);
                                    // Start fully clipped so the overlay never
                                    // flashes across the whole cover on frame one.
                                    queueSwipeOverlay.setClipBounds(new Rect(0, 0, 0,
                                            queueSwipeOverlay.getHeight()));
                                    queueSwipeOverlay.setVisibility(View.VISIBLE);
                                    // Block the RecyclerView from stealing the gesture.
                                    v.getParent().requestDisallowInterceptTouchEvent(true);
                                } else {
                                    return false;
                                }
                            }
                            // Map the swipe distance across the row onto a 0..1
                            // reveal of the (stationary) cover-sized overlay.
                            float reveal = rowWidth > 0 ? Math.max(0f, Math.min(1f, dx / (rowWidth * 0.18f))) : 0f;
                            applyReveal(reveal);
                            return true;
                        }

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL: {
                            if (!swiping) return false;
                            // Only a real UP fires a trailing click to swallow;
                            // CANCEL produces no click, so leaving the flag set
                            // would silently eat the next genuine tap.
                            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                                swipeConsumedClick = true;
                            }
                            float dx = e.getRawX() - downX;
                            boolean committed = rowWidth > 0 && dx >= rowWidth * 0.18f
                                    && e.getActionMasked() == MotionEvent.ACTION_UP;
                            if (committed) {
                                SongModel song = songAt();
                                if (song != null) {
                                    PlaybackManager pm = PlaybackManager.getInstance();
                                    boolean added;
                                    if (pm.isInQueue(song)) {
                                        pm.removeFromQueue(song);
                                        added = false;
                                    } else {
                                        added = pm.addToQueue(song);
                                    }
                                    notifyQueueChange(added);
                                }
                            }
                            resetSwipe();
                            swiping = false;
                            return true;
                        }
                    }
                    return false;
                }
            };
            // Attach to the whole row and to the cover, so a swipe starting
            // anywhere (cover included) is captured while taps still work.
            itemView.setOnTouchListener(swipeListener);
            imageCoverView.setOnTouchListener(swipeListener);
        }

        private void showQueuedEdge(boolean show) {
            queuedEdge.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        // Reveal the overlay over the cover from the left, clipped to `progress`
        // of the cover's width. The cover itself never moves.
        private void applyReveal(float progress) {
            int w = queueSwipeOverlay.getWidth();
            int h = queueSwipeOverlay.getHeight();
            if (w == 0 || h == 0) return;
            int revealW = (int) (w * progress);
            queueSwipeOverlay.setClipBounds(new Rect(0, 0, revealW, h));
            // Fade the icon in across the swipe: 0% opacity at the start, full
            // opacity once the overlay covers half the cover.
            queueSwipeIcon.setVisibility(View.VISIBLE);
            queueSwipeIcon.setAlpha(Math.min(1f, progress / 0.5f));
        }

        private SongModel songAt() {
            int pos = getAdapterPosition();
            if (adapter == null || pos == RecyclerView.NO_POSITION) return null;
            ArrayList<SongModel> songs = adapter.getSongs();
            return pos < songs.size() ? songs.get(pos) : null;
        }

        private boolean isCurrentSong(SongModel song) {
            SongModel current = PlaybackManager.getInstance().getCurrentSong();
            return current != null && song != null
                    && current.getUri().equals(song.getUri());
        }

        private void notifyQueueChange(boolean added) {
            if (adapter != null) adapter.onQueueChanged(getAdapterPosition(), added);
        }

        // Fade the overlay out and refresh the queued indicator. The cover
        // never moved, so nothing to translate back.
        private void resetSwipe() {
            queueSwipeIcon.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                queueSwipeIcon.setVisibility(View.GONE);
                queueSwipeIcon.setAlpha(1f);
            }).start();
            queueSwipeOverlay.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                queueSwipeOverlay.setVisibility(View.GONE);
                queueSwipeOverlay.setAlpha(1f);
                queueSwipeOverlay.setClipBounds(null);
                SongModel song = songAt();
                boolean queued = song != null && PlaybackManager.getInstance().isInQueue(song);
                showQueuedEdge(queued);
            }).start();
        }
    }

    /**
     * Replaces the list, dispatching a URI-keyed diff so unchanged rows keep
     * their views (and their loaded covers) instead of all rebinding. The list
     * is copied, so callers may keep mutating their own instance.
     */
    public void updateSongs(List<SongModel> newSongs) {
        ArrayList<SongModel> next = new ArrayList<>(newSongs);
        ArrayList<SongModel> previous = this.songModels;
        this.songModels = next;
        if (previous.isEmpty()) {
            if (!next.isEmpty()) notifyItemRangeInserted(0, next.size());
            return;
        }
        if (!SongListDiff.shouldDiff(previous.size(), next.size())) {
            notifyDataSetChanged();
            return;
        }
        DiffUtil.calculateDiff(new SongListDiff(previous, next),
                        SongListDiff.shouldDetectMoves(previous.size(), next.size()))
                .dispatchUpdatesTo(this);
    }

    private ArrayList<SongModel> getSongModels() {
        return this.songModels;
    }

    public ArrayList<SongModel> getSongs() {
        return getSongModels();
    }

    public Context getContext() {
        return context;
    }

    /**
     * Re-evaluates the now-playing highlight and queued edge of every row after a
     * track change or new queue. Queue membership can shift across arbitrary rows,
     * so all rows are flagged, but only via a lightweight payload: just the bound
     * rows rebind, and only their playback indicators (no cover reload).
     */
    public void refreshPlaybackState() {
        notifyAllRows(PAYLOAD_PLAYBACK);
    }

    // Refresh the playback indicators of only the rows matching the given URIs
    // (every occurrence, since a playlist can hold the same song twice).
    public void refreshRowsForUris(String... uris) {
        for (String uri : uris) {
            if (uri == null) continue;
            for (int i = 0; i < songModels.size(); i++) {
                if (uri.equals(songModels.get(i).getUri())) {
                    notifyItemChanged(i, PAYLOAD_PLAYBACK);
                }
            }
        }
    }

    // Notified after a swipe toggles a song in the queue.
    public interface OnQueueChangeListener {
        void onQueueChanged(int position, boolean added);
    }

    private OnQueueChangeListener queueChangeListener;

    public void setOnQueueChangeListener(OnQueueChangeListener listener) {
        this.queueChangeListener = listener;
    }

    void onQueueChanged(int position, boolean added) {
        if (queueChangeListener != null) queueChangeListener.onQueueChanged(position, added);
    }

    public void updateSortContext(int sortOption) {
        this.currentSortOption = sortOption;
        // Show contextual info for Date Added (0), Last Listened (1), Length (3), Popularity (5), Listen Count (6), Release Date (7), and Rank (8)
        this.shouldShowContextualInfo = (sortOption == 0 || sortOption == 1 || sortOption == 3 || sortOption == 5 || sortOption == 6 || sortOption == 7 || sortOption == 8);
        notifyAllRows(PAYLOAD_CONTEXT);
    }

    // Hide the artist-name row (ArtistView Songs tab). Default true elsewhere.
    public void setShowArtist(boolean showArtist) {
        if (this.showArtist == showArtist) return;
        this.showArtist = showArtist;
        if (!songModels.isEmpty()) notifyItemRangeChanged(0, songModels.size());
    }

    // Marks which songs are locally added (their URI shows a green tick). Pass null to disable.
    public void setAddedUris(java.util.Set<String> addedUris) {
        this.addedUris = addedUris;
        notifyAllRows(PAYLOAD_FLAGS);
    }

    public void updateListenCounts(Map<String, Integer> listenCountMap) {
        this.listenCountMap = listenCountMap;
        notifyAllRows(PAYLOAD_CONTEXT);
    }

    public void updatePopularityMap(Map<String, Integer> popularityMap) {
        this.popularityMap = popularityMap;
        notifyAllRows(PAYLOAD_CONTEXT);
    }

    public void updateLastListenedMap(Map<String, String> lastListenedMap) {
        this.lastListenedMap = lastListenedMap;
        notifyAllRows(PAYLOAD_CONTEXT);
    }

    public void updateRankInfoMap(Map<String, SongRankInfo> rankInfoMap) {
        this.rankInfoMap = rankInfoMap;
        notifyAllRows(PAYLOAD_CONTEXT);
    }

    private String formatDateForDisplay(Date date) {
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
        return formatter.format(date);
    }

    private String formatReleaseDateForDisplay(String releaseDate) {
        if (releaseDate == null || releaseDate.isEmpty()) {
            return "Unknown";
        }

        try {
            // Parse the date string (Spotify format: "YYYY-MM-DD", "YYYY-MM", or "YYYY")
            String[] parts = releaseDate.split("-");

            if (parts.length == 1) {
                // Only year available
                return parts[0];
            } else if (parts.length == 2) {
                // Year and month available
                int month = Integer.parseInt(parts[1]);
                String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                return monthNames[month - 1] + " " + parts[0];
            } else if (parts.length == 3) {
                // Full date available
                int day = Integer.parseInt(parts[2]);
                int month = Integer.parseInt(parts[1]);
                String year = parts[0];
                String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

                // If January 1st, show only year
                if (month == 1 && day == 1) {
                    return year;
                }
                // If 1st of any month, show month and year only
                else if (day == 1) {
                    return monthNames[month - 1] + " " + year;
                }
                // Otherwise show full date
                else {
                    return day + " " + monthNames[month - 1] + " " + year;
                }
            }
        } catch (Exception e) {
            // If parsing fails, return the original string
            return releaseDate;
        }

        return releaseDate;
    }
}