package com.ca.tunaro.utils;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.R;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.SwissTournament.GraphMatch;
import com.ca.tunaro.utils.SwissTournament.GraphRound;
import com.ca.tunaro.utils.SwissTournament.StandingRow;

import java.util.ArrayList;
import java.util.List;

/**
 * The live Swiss standings graphic. Two ways to watch the same tournament, chosen
 * by a pill toggle:
 *
 * <ul>
 *   <li><b>Standings</b> — the whole field as a ladder that reshuffles every round,
 *       each song showing its score; songs cut from contention fade back.</li>
 *   <li><b>Rounds</b> — one column of pairing boxes per round, the live match
 *       pulsing; horizontal swipes snap round-to-round.</li>
 * </ul>
 *
 * Fed from {@link SwissTournament#getStandings()} and
 * {@link SwissTournament#getGraphRounds()} on every state change.
 */
public class SwissGraphView extends LinearLayout {

    private static final int WINNER_TINT = 0x5541C77A;   // green wash behind the winner
    private static final int CURRENT_CARD = 0x59F0C36A;  // warm highlight on the live match
    private static final int CUT_DIM = 0x24FFFFFF;       // card wash for a song out of contention
    private static final float LOSER_DIM = 0.4f;
    private static final float CUT_ALPHA = 0.45f;

    private static final int BOX_W_DP = 150;
    private static final int V_GAP_DP = 10;    // between match boxes in a round column
    private static final int COL_GAP_DP = 12;  // between round columns

    private static final int MODE_STANDINGS = 0;
    private static final int MODE_ROUNDS = 1;

    private final LayoutInflater inflater;

    private LinearLayout pillRow;
    private FrameLayout contentFrame;

    private ScrollView standingsScrollY;
    private LinearLayout ladderColumn;

    private SnapHorizontalScrollView roundsScrollX;
    private ScrollView roundsScrollY;
    private LinearLayout columnsRow;
    private View currentBox;
    private AnimatorSet pulse;

    private List<GraphRound> rounds = new ArrayList<>();
    private List<StandingRow> standings = new ArrayList<>();
    private int mode = MODE_STANDINGS;

    /** Notified when a song is tapped in either the ladder or a round's pairing box. */
    public interface OnSongClickListener {
        void onSongClick(SongModel song);
    }

    private OnSongClickListener songClickListener;

    public void setOnSongClickListener(OnSongClickListener listener) {
        this.songClickListener = listener;
    }

    private void fireSongClick(SongModel song) {
        if (song != null && songClickListener != null) songClickListener.onSongClick(song);
    }

    public SwissGraphView(@NonNull Context context) {
        super(context);
        inflater = LayoutInflater.from(context);
        init();
    }

    public SwissGraphView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        inflater = LayoutInflater.from(context);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);

        pillRow = new LinearLayout(getContext());
        pillRow.setOrientation(HORIZONTAL);
        pillRow.setGravity(Gravity.CENTER_HORIZONTAL);
        pillRow.setPadding(dp(4), dp(4), dp(4), dp(8));
        addView(pillRow, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        contentFrame = new FrameLayout(getContext());
        addView(contentFrame, new LayoutParams(MATCH_PARENT, 0, 1f));

        // Standings ladder: a plain vertical scroll of rows.
        standingsScrollY = new ScrollView(getContext());
        standingsScrollY.setVerticalScrollBarEnabled(false);
        ladderColumn = new LinearLayout(getContext());
        ladderColumn.setOrientation(VERTICAL);
        ladderColumn.setPadding(dp(4), dp(2), dp(4), dp(8));
        standingsScrollY.addView(ladderColumn, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        contentFrame.addView(standingsScrollY, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        // Round columns: a horizontal pan that snaps to a round on release, wrapping
        // an inner vertical scroll so a tall round (many pairings) can be scrolled
        // down through as well as across.
        roundsScrollX = new SnapHorizontalScrollView(getContext());
        roundsScrollX.setHorizontalScrollBarEnabled(false);
        roundsScrollY = new ScrollView(getContext());
        roundsScrollY.setVerticalScrollBarEnabled(false);
        columnsRow = new LinearLayout(getContext());
        columnsRow.setOrientation(HORIZONTAL);
        columnsRow.setPadding(dp(4), dp(2), dp(16), dp(4));
        roundsScrollX.setSnapRow(columnsRow);
        roundsScrollY.addView(columnsRow, new ScrollView.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        roundsScrollX.addView(roundsScrollY, new LayoutParams(WRAP_CONTENT, MATCH_PARENT));
        contentFrame.addView(roundsScrollX, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    /** Redraw for the current tournament state. */
    public void render(List<GraphRound> newRounds, List<StandingRow> newStandings) {
        rounds = newRounds != null ? newRounds : new ArrayList<>();
        standings = newStandings != null ? newStandings : new ArrayList<>();
        if (rounds.isEmpty() && standings.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);

        buildPills();
        buildStandings();
        buildRounds();
        applyMode();
    }

    private void buildPills() {
        pillRow.removeAllViews();
        addPill("Standings", MODE_STANDINGS);
        addPill("Rounds", MODE_ROUNDS);
    }

    private void addPill(String label, int pillMode) {
        boolean isSelected = pillMode == mode;
        TextView pill = new TextView(getContext());
        pill.setText(label);
        pill.setTextSize(12f);
        pill.setPadding(dp(16), dp(6), dp(16), dp(6));
        pill.setTextColor(isSelected ? Color.BLACK : Color.WHITE);
        pill.setBackground(pillBackground(isSelected));

        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(dp(4), 0, dp(4), 0);
        pill.setLayoutParams(params);

        pill.setOnClickListener(v -> {
            if (mode == pillMode) return;
            popTap(v);
            mode = pillMode;
            buildPills();
            applyMode();
        });
        pillRow.addView(pill);
    }

    private GradientDrawable pillBackground(boolean isSelected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        if (isSelected) {
            bg.setColor(getColor(R.color.tanAccent));
        } else {
            bg.setColor(0x22FFFFFF);
            bg.setStroke(dp(1), 0x55FFFFFF);
        }
        return bg;
    }

    private void applyMode() {
        boolean standingsMode = mode == MODE_STANDINGS;
        standingsScrollY.setVisibility(standingsMode ? VISIBLE : GONE);
        roundsScrollX.setVisibility(standingsMode ? GONE : VISIBLE);
        // Bring the live mode's scroller to the front so it owns touches outright —
        // the two overlap in the frame, and only the top one should take gestures.
        if (standingsMode) {
            standingsScrollY.bringToFront();
            stopPulse();
        } else {
            roundsScrollX.bringToFront();
            scrollToCurrentRound();
        }
    }

    // ---- Standings ladder ----

    private void buildStandings() {
        ladderColumn.removeAllViews();
        for (StandingRow row : standings) {
            ladderColumn.addView(buildStandingRow(row));
        }
        // A soft fade so a reshuffle reads as a change rather than a flicker.
        ladderColumn.setAlpha(0f);
        ladderColumn.animate().alpha(1f).setDuration(200)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private View buildStandingRow(StandingRow row) {
        CardView card = (CardView) inflater.inflate(R.layout.item_standing_row, ladderColumn, false);
        ((TextView) card.findViewById(R.id.rank)).setText(String.valueOf(row.rank));
        ((TextView) card.findViewById(R.id.name)).setText(row.song.getName());
        ((TextView) card.findViewById(R.id.score)).setText(String.valueOf(row.score));
        Glide.with(getContext())
                .load(row.song.getAlbumCoverUrl())
                .placeholder(R.drawable.playlist_placeholder)
                .error(R.drawable.playlist_placeholder)
                .into((ImageView) card.findViewById(R.id.cover));

        // A cut song is faded and washed back so the contending block stands out.
        card.setAlpha(row.active ? 1f : CUT_ALPHA);
        card.setCardBackgroundColor(row.active ? 0x24FFFFFF : CUT_DIM);

        card.setOnClickListener(v -> fireSongClick(row.song));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(0, dp(3), 0, dp(3));
        card.setLayoutParams(params);
        return card;
    }

    // ---- Round columns ----

    private void buildRounds() {
        columnsRow.removeAllViews();
        currentBox = null;

        int boxW = dp(BOX_W_DP);
        for (GraphRound round : rounds) {
            LinearLayout columnView = new LinearLayout(getContext());
            columnView.setOrientation(VERTICAL);

            TextView header = new TextView(getContext());
            header.setText(round.label);
            header.setTextSize(12f);
            header.setTextColor(getColor(R.color.tanAccent));
            header.setPadding(dp(2), 0, dp(2), dp(6));
            columnView.addView(header, new LayoutParams(boxW, WRAP_CONTENT));

            for (GraphMatch match : round.matches) {
                View box = buildMatchBox(match);
                LayoutParams boxParams = new LayoutParams(boxW, WRAP_CONTENT);
                boxParams.bottomMargin = dp(V_GAP_DP);
                columnView.addView(box, boxParams);
                if (match.current) currentBox = box;
            }

            LayoutParams colParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            colParams.setMargins(dp(COL_GAP_DP), 0, dp(COL_GAP_DP), 0);
            columnsRow.addView(columnView, colParams);
        }
    }

    private View buildMatchBox(GraphMatch match) {
        CardView card = (CardView) inflater.inflate(R.layout.item_bracket_match, columnsRow, false);

        LinearLayout rowA = card.findViewById(R.id.row_a);
        LinearLayout rowB = card.findViewById(R.id.row_b);
        bindContender(match.a, card.findViewById(R.id.cover_a), card.findViewById(R.id.name_a));
        bindContender(match.b, card.findViewById(R.id.cover_b), card.findViewById(R.id.name_b));

        if (match.bye) {
            rowA.setBackgroundColor(WINNER_TINT);
            rowB.setBackgroundColor(Color.TRANSPARENT);
            rowA.setAlpha(1f);
            rowB.setAlpha(LOSER_DIM);
        } else if (match.decided) {
            boolean aWon = match.a != null && match.a.getId().equals(match.winnerId);
            rowA.setBackgroundColor(aWon ? WINNER_TINT : Color.TRANSPARENT);
            rowB.setBackgroundColor(aWon ? Color.TRANSPARENT : WINNER_TINT);
            rowA.setAlpha(aWon ? 1f : LOSER_DIM);
            rowB.setAlpha(aWon ? LOSER_DIM : 1f);
        } else {
            rowA.setBackgroundColor(Color.TRANSPARENT);
            rowB.setBackgroundColor(Color.TRANSPARENT);
            rowA.setAlpha(1f);
            rowB.setAlpha(1f);
        }

        if (match.current) {
            card.setCardBackgroundColor(CURRENT_CARD);
            card.setCardElevation(dp(6));
        }

        // Tapping either song in the box opens that song. A bye row (null b) is inert.
        rowA.setOnClickListener(v -> fireSongClick(match.a));
        if (match.b != null) rowB.setOnClickListener(v -> fireSongClick(match.b));
        return card;
    }

    private void bindContender(SongModel song, ImageView cover, TextView name) {
        if (song == null) {
            name.setText("Bye");
            name.setTextColor(0x73FFFFFF);
            cover.setImageResource(R.drawable.playlist_placeholder);
            return;
        }
        name.setText(song.getName());
        name.setTextColor(0xF2FFFFFF);
        Glide.with(getContext())
                .load(song.getAlbumCoverUrl())
                .placeholder(R.drawable.playlist_placeholder)
                .error(R.drawable.playlist_placeholder)
                .into(cover);
    }

    // Once the columns are laid out, snap the live match's round into view and pulse
    // it. Runs one-shot on the next layout pass, when positions and viewport are known.
    private void scrollToCurrentRound() {
        stopPulse();
        final View box = currentBox;
        if (box == null) return;
        columnsRow.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        columnsRow.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        revealColumn(box);
                        startPulse(box);
                    }
                });
    }

    private void revealColumn(View box) {
        View columnView = (View) box.getParent();
        if (columnView == null) return;
        int targetX = columnView.getLeft() - columnsRow.getPaddingLeft();
        roundsScrollX.smoothScrollTo(Math.max(0, targetX), 0);
    }

    private void startPulse(View box) {
        box.setPivotX(box.getWidth() / 2f);
        box.setPivotY(box.getHeight() / 2f);
        ObjectAnimator sx = ObjectAnimator.ofFloat(box, "scaleX", 1f, 1.05f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(box, "scaleY", 1f, 1.05f);
        for (ObjectAnimator a : new ObjectAnimator[]{sx, sy}) {
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setDuration(720);
        }
        pulse = new AnimatorSet();
        pulse.playTogether(sx, sy);
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
    }

    private void popTap(View v) {
        v.animate().cancel();
        v.setScaleX(0.9f);
        v.setScaleY(0.9f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(160)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private int getColor(int resId) {
        return getResources().getColor(resId, getContext().getTheme());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * A horizontal pan that snaps to a column (a round) on release instead of
     * free-scrolling: dragging settles onto the nearest round, and a flick advances
     * one round in the flick's direction.
     */
    private static class SnapHorizontalScrollView extends HorizontalScrollView {

        private LinearLayout snapRow;
        private int lastFlingVelocity;

        SnapHorizontalScrollView(Context context) {
            super(context);
        }

        void setSnapRow(LinearLayout row) {
            snapRow = row;
        }

        @Override
        public void fling(int velocityX) {
            lastFlingVelocity = velocityX;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            boolean handled = super.onTouchEvent(ev);
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                snapToNearestRound();
                lastFlingVelocity = 0;
            }
            return handled;
        }

        private void snapToNearestRound() {
            if (snapRow == null || snapRow.getChildCount() == 0) return;
            int scrollX = getScrollX();
            int nearest = 0;
            int nearestDistance = Integer.MAX_VALUE;
            for (int i = 0; i < snapRow.getChildCount(); i++) {
                int left = snapRow.getChildAt(i).getLeft() - snapRow.getPaddingLeft();
                int distance = Math.abs(left - scrollX);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = i;
                }
            }
            int threshold = dp(300);   // a firm flick, in px/s, advances a round
            if (lastFlingVelocity > threshold && nearest < snapRow.getChildCount() - 1) {
                nearest++;
            } else if (lastFlingVelocity < -threshold && nearest > 0) {
                nearest--;
            }
            int targetX = snapRow.getChildAt(nearest).getLeft() - snapRow.getPaddingLeft();
            smoothScrollTo(Math.max(0, targetX), getScrollY());
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }
}
