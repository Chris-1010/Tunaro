package com.ca.tunaro;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.ca.tunaro.activites.MainActivity;
import com.ca.tunaro.activites.PlaylistView;
import com.ca.tunaro.activites.SongView;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.ColorExtractor;
import com.ca.tunaro.utils.SelectedSongHolder;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class BaseActivity extends AppCompatActivity implements PlaybackManager.PlaybackListener {
    private static final String TAG = "Base Activity";

    protected PlaybackManager playbackManager;
    private SongModel currentDisplayedSong;
    private static SongModel lastGlobalSong;

    // Playback bar views
    protected View playbackBar;
    protected View playbackBarBackground;
    protected SeekBar playbackSeekbar;
    private boolean isSeeking = false;
    // Magnet-to-start window (ms): dragging the seekbar within this fixed
    // distance of the beginning snaps to 0. Fixed, not a percentage, so it never
    // swallows the intro of a long track (#105).
    private static final int SEEK_SNAP_TO_START_MS = 2000;
    protected ImageView albumCover;
    protected TextView songName;
    protected TextView artistName;
    protected ImageButton playPauseButton;
    protected ImageView deviceWarningIcon;
//    private TextView positionText;
//    private TextView durationText;

    private final Handler animationHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingAnimation;

    // Carousel (swipe Previous/Next) state. The resting cover (albumCover) and the
    // title/artist column translate together as the "current" panel; the incoming
    // song rides a transient panel inside the clipped viewport. See
    // docs/impl-notes-47.md §"Gesture mechanics".
    private View trackInfoColumn;
    private FrameLayout carouselViewport;
    private GestureDetector carouselDetector;
    private int touchSlop;
    private float carouselDownX;
    private float carouselDownY;
    private boolean carouselDragging;
    private boolean carouselCommitting;
    private int carouselDirection;      // +1 = Previous (drag right), -1 = Next (drag left)
    private boolean carouselDeadEnd;    // rubber-band this drag (no committable neighbour)
    private boolean carouselFlingCommit;
    private View carouselIncomingPanel;
    private boolean carouselAwaitingTrack; // a placeholder commit is waiting for the real track

    // Commit when the drag passes this fraction of the viewport width, or on a
    // fling faster than this velocity (px/s). Rubber-band dead-ends resist by this
    // factor. Gradient cross-fade duration for an optimistic commit.
    private static final float CAROUSEL_COMMIT_FRACTION = 0.4f;
    private static final float CAROUSEL_FLING_VELOCITY = 1200f;
    private static final float CAROUSEL_RUBBER_BAND = 0.35f;
    private static final int CAROUSEL_GRADIENT_CROSSFADE_MS = 250;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playbackManager = PlaybackManager.getInstance();
    }

    @Override
    protected void onStart() {
        super.onStart();
        playbackManager.addListener(this);
        syncPlaybackBarState();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume");

        if (checkForRecovery()) return;

        if (playbackManager == null) {
            PlaybackManager.getInstance().initialize(
                    getApplicationContext(),
                    getString(R.string.spotify_client_id),
                    getString(R.string.redirect_uri)
            );
            playbackManager = PlaybackManager.getInstance();
        }

        // Re-sync playback bar state
        syncPlaybackBarState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        playbackManager.removeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cancel any pending animations
        if (pendingAnimation != null) {
            animationHandler.removeCallbacks(pendingAnimation);
            pendingAnimation = null;
        }

        // Clear animation references
        if (albumCover != null) {
            albumCover.clearAnimation();
        }
        if (songName != null) {
            songName.clearAnimation();
        }
        if (artistName != null) {
            artistName.clearAnimation();
        }
    }

    protected boolean checkForRecovery() {
        MainActivity mainActivity = MainActivity.getInstance();
        boolean needsRecovery = mainActivity == null ||
                mainActivity.getSpotifyApi() == null ||
                mainActivity.getUserID() == null;

        if (needsRecovery) {
            Log.d("BaseActivity", "Recovery needed, clearing task and restarting");
            // Clear everything and restart from MainActivity
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true; // Recovery initiated
        }

        return false; // No recovery needed
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        setupPlaybackBar();
    }

    // Seekbar touch is consumed (returns true, no performClick) to block seeking
    // during snippet playback; the accessibility warning does not apply here.
    @SuppressLint("ClickableViewAccessibility")
    private void setupPlaybackBar() {
        // Find playback bar views
        playbackBar = findViewById(R.id.playback_bar);
        playbackBarBackground = findViewById(R.id.frameLayout);
        playbackSeekbar = findViewById(R.id.playback_seekbar);
        if (playbackBar == null) return;

        albumCover = findViewById(R.id.playback_album_cover);
        songName = findViewById(R.id.playback_song_name);
        artistName = findViewById(R.id.playback_artist_name);
        trackInfoColumn = findViewById(R.id.linearLayout);
        carouselViewport = findViewById(R.id.playback_carousel_viewport);
        playPauseButton = findViewById(R.id.playback_play_pause);
        deviceWarningIcon = findViewById(R.id.playback_device_warning);
//        positionText = findViewById(R.id.playback_position);
//        durationText = findViewById(R.id.playback_duration);

        // Set initial visibility
        updatePlaybackBarVisibility();

        // Set click listener for play/pause button
        if (playPauseButton != null) {
            playPauseButton.setOnClickListener(v -> {
                playbackManager.togglePlayPause();
            });
        }

        // The bar body is a carousel: horizontal drag = Previous/Next, tap = open
        // SongView (folded into the gesture detector's onSingleTapUp). The seekbar
        // strip and play/pause button are children and win touches in their own
        // bounds, so only body touches reach this handler.
        setupCarouselGesture();
        if (playbackSeekbar != null) {
            // Block seeking while a snippet is playing: the snippet owns the
            // playhead (its range + end-timer), so a manual drag would fight it.
            playbackSeekbar.setOnTouchListener((v, e) ->
                    playbackManager != null && playbackManager.isSnippetMode());
            playbackSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && progress > 0 && progress <= SEEK_SNAP_TO_START_MS) {
                        // Magnet-to-start: while the drag is within the window of the
                        // beginning, pull the thumb to 0 live so releasing restarts the
                        // track. Snapping the progress here makes onStopTrackingTouch
                        // read 0 and seek accordingly. The resulting fromUser=false
                        // callback lands at progress 0, so this does not re-fire.
                        seekBar.setProgress(0);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isSeeking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int progress = seekBar.getProgress();
                    playbackManager.seekTo(progress);
                    isSeeking = false;
                }
            });
        }
    }

    //#region Carousel gesture (Previous/Next swipe)

    @SuppressLint("ClickableViewAccessibility")
    private void setupCarouselGesture() {
        if (playbackBar == null) return;
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        carouselDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                openCurrentSongView();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // Record a decisive horizontal fling; ACTION_UP decides the commit.
                if (carouselDragging && !carouselDeadEnd
                        && Math.abs(velocityX) > Math.abs(velocityY)
                        && Math.abs(velocityX) > CAROUSEL_FLING_VELOCITY
                        && Integer.signum((int) velocityX) == carouselDirection) {
                    carouselFlingCommit = true;
                }
                return false;
            }
        });
        playbackBar.setOnTouchListener((v, e) -> handleCarouselTouch(e));
    }

    // A new touch mid-commit (or while a placeholder commit awaits its real track)
    // is ignored so the settle animation isn't interrupted.
    private boolean carouselBusy() {
        return carouselCommitting || carouselAwaitingTrack;
    }

    private boolean handleCarouselTouch(MotionEvent e) {
        boolean detectorHandled = carouselDetector.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                carouselDownX = e.getRawX();
                carouselDownY = e.getRawY();
                carouselDragging = false;
                carouselDirection = 0;
                carouselFlingCommit = false;
                return !carouselBusy();
            case MotionEvent.ACTION_MOVE:
                if (carouselBusy()) return true;
                float dx = e.getRawX() - carouselDownX;
                float dy = e.getRawY() - carouselDownY;
                if (!carouselDragging) {
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        beginCarouselDrag(dx > 0 ? 1 : -1);
                    }
                    if (!carouselDragging) return true;
                }
                updateCarouselDrag(dx);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (carouselDragging) {
                    finishCarouselDrag(e);
                    return true;
                }
                return detectorHandled;
            default:
                return detectorHandled;
        }
    }

    private void beginCarouselDrag(int direction) {
        if (carouselViewport == null || carouselViewport.getWidth() == 0) {
            return; // not laid out yet; leave it to the tap detector
        }
        carouselDragging = true;
        carouselDirection = direction;
        cancelPendingTrackAnimation();
        boolean committable = direction > 0 ? playbackManager.hasPrevious() : playbackManager.hasNext();
        carouselDeadEnd = !committable;
        removeIncomingPanel();
        if (!carouselDeadEnd) {
            SongModel incoming = direction > 0
                    ? playbackManager.peekPreviousSong()
                    : playbackManager.peekNextSong();
            carouselIncomingPanel = createIncomingPanel(incoming, direction);
        }
    }

    // Build the incoming neighbour panel off to the incoming side of the viewport.
    // A null song (Recommendations past the end of the queue) renders a neutral
    // placeholder that resolves when the real track change lands.
    private View createIncomingPanel(SongModel incoming, int direction) {
        View panel = LayoutInflater.from(this)
                .inflate(R.layout.playback_bar_panel, carouselViewport, false);
        ImageView cover = panel.findViewById(R.id.panel_album_cover);
        TextView title = panel.findViewById(R.id.panel_song_name);
        TextView artist = panel.findViewById(R.id.panel_artist_name);
        if (incoming != null) {
            title.setText(incoming.getName());
            artist.setText(incoming.getArtist());
            if (!isDestroyed() && !isFinishing()) {
                Glide.with(this)
                        .load(incoming.getAlbumCoverUrl())
                        .placeholder(R.drawable.song_placeholder)
                        .error(R.drawable.song_placeholder)
                        .into(cover);
            }
        } else {
            title.setText("");
            artist.setText("");
            cover.setImageResource(R.drawable.song_placeholder);
        }
        float w = carouselViewport.getWidth();
        panel.setTranslationX(direction > 0 ? -w : w);
        carouselViewport.addView(panel);
        return panel;
    }

    private void updateCarouselDrag(float dx) {
        float w = carouselViewport.getWidth();
        if (carouselDeadEnd) {
            // Resist and cap: there is no neighbour to reveal.
            float resisted = clamp(dx * CAROUSEL_RUBBER_BAND, -w * 0.25f, w * 0.25f);
            setCurrentPanelTranslation(resisted);
            return;
        }
        float clamped = carouselDirection > 0 ? clamp(dx, 0f, w) : clamp(dx, -w, 0f);
        setCurrentPanelTranslation(clamped);
        if (carouselIncomingPanel != null) {
            float base = carouselDirection > 0 ? -w : w;
            carouselIncomingPanel.setTranslationX(base + clamped);
        }
        setCurrentPanelAlpha(1f - 0.3f * (Math.abs(clamped) / w));
    }

    private void finishCarouselDrag(MotionEvent e) {
        float dx = e.getRawX() - carouselDownX;
        float w = carouselViewport.getWidth();
        boolean commit = Math.abs(dx) >= w * CAROUSEL_COMMIT_FRACTION || carouselFlingCommit;
        carouselDragging = false;

        if (carouselDeadEnd) {
            // No neighbour to reveal (start of history, or Stop mode at the end of
            // the queue): rubber-band back with no action.
            springCurrentPanelHome();
            return;
        }
        if (commit) {
            commitCarousel();
        } else {
            cancelCarousel();
        }
    }

    // Optimistic commit: fire playback and cross-fade the gradient now, slide the
    // current panel out and the incoming panel to rest in parallel, then reconcile
    // to the confirmed track once the slide settles.
    private void commitCarousel() {
        carouselCommitting = true;
        float w = carouselViewport.getWidth();
        int direction = carouselDirection;
        SongModel incoming = direction > 0
                ? playbackManager.peekPreviousSong()
                : playbackManager.peekNextSong();

        if (direction > 0) {
            playbackManager.previous();
        } else {
            playbackManager.next();
        }
        if (incoming != null) {
            crossFadeGradient(incoming.getAlbumCoverUrl());
        }

        springTranslation(albumCover, direction > 0 ? w : -w);
        springTranslation(trackInfoColumn, direction > 0 ? w : -w);
        fadeCurrentPanel(0f);

        final View panel = carouselIncomingPanel;
        SpringAnimation slideIn = springTranslation(panel, 0f);
        if (slideIn != null) {
            slideIn.addEndListener((a, canceled, value, velocity) -> onCommitSettled(incoming, panel));
        } else {
            onCommitSettled(incoming, panel);
        }
    }

    private void onCommitSettled(SongModel incoming, View panel) {
        if (incoming != null) {
            // Teleport the real current panel onto the incoming song (invisibly,
            // since it sits above the viewport), then drop the transient panel.
            // Suppress the default track-change animation for the player-state
            // callback that will confirm this same song.
            cancelCurrentPanelAnimators();
            updateTrackInfo(incoming);
            setCurrentPanelTranslation(0f);
            setCurrentPanelAlpha(1f);
            currentDisplayedSong = incoming;
            lastGlobalSong = incoming;
            removeIncomingPanel();
            carouselCommitting = false;
        } else {
            // Placeholder commit: keep it showing until the real track lands, then
            // reconcile in updatePlaybackBarUI.
            carouselCommitting = false;
            carouselAwaitingTrack = true;
        }
    }

    private void cancelCarousel() {
        carouselCommitting = true; // brief settle window; ignore new touches
        springCurrentPanelHome();
        final View panel = carouselIncomingPanel;
        if (panel != null) {
            float w = carouselViewport.getWidth();
            SpringAnimation out = springTranslation(panel, carouselDirection > 0 ? -w : w);
            if (out != null) {
                out.addEndListener((a, canceled, value, velocity) -> {
                    removeIncomingPanel();
                    carouselCommitting = false;
                });
                return;
            }
        }
        carouselCommitting = false;
    }

    private void springCurrentPanelHome() {
        springTranslation(albumCover, 0f);
        springTranslation(trackInfoColumn, 0f);
        fadeCurrentPanel(1f);
    }

    private SpringAnimation springTranslation(View view, float target) {
        if (view == null) return null;
        SpringAnimation anim = new SpringAnimation(view, DynamicAnimation.TRANSLATION_X, target);
        anim.getSpring()
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY);
        anim.start();
        return anim;
    }

    private void setCurrentPanelTranslation(float tx) {
        if (albumCover != null) albumCover.setTranslationX(tx);
        if (trackInfoColumn != null) trackInfoColumn.setTranslationX(tx);
    }

    private void setCurrentPanelAlpha(float alpha) {
        if (albumCover != null) albumCover.setAlpha(alpha);
        if (trackInfoColumn != null) trackInfoColumn.setAlpha(alpha);
    }

    private void fadeCurrentPanel(float target) {
        if (albumCover != null) {
            albumCover.animate().alpha(target).setDuration(CAROUSEL_GRADIENT_CROSSFADE_MS).start();
        }
        if (trackInfoColumn != null) {
            trackInfoColumn.animate().alpha(target).setDuration(CAROUSEL_GRADIENT_CROSSFADE_MS).start();
        }
    }

    private void cancelCurrentPanelAnimators() {
        if (albumCover != null) albumCover.animate().cancel();
        if (trackInfoColumn != null) trackInfoColumn.animate().cancel();
    }

    private void removeIncomingPanel() {
        if (carouselIncomingPanel != null && carouselViewport != null) {
            carouselViewport.removeView(carouselIncomingPanel);
        }
        carouselIncomingPanel = null;
    }

    private void cancelPendingTrackAnimation() {
        if (pendingAnimation != null) {
            animationHandler.removeCallbacks(pendingAnimation);
            pendingAnimation = null;
        }
    }

    private void openCurrentSongView() {
        SongModel currentSong = playbackManager.getCurrentSong();
        if (currentSong == null) return;
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null) {
            showToast("Unable to open song view");
            return;
        }
        // Skip if the SongView for this song is already the selected one.
        SelectedSongHolder songHolder = SelectedSongHolder.getInstance();
        if (songHolder.getSelectedSong() != null
                && Objects.equals(songHolder.getSelectedSong().getId(), currentSong.getId())) {
            return;
        }
        SelectedSongHolder.getInstance().setSelectedSong(currentSong);
        Intent intent = new Intent(this, SongView.class);
        intent.putExtra("from_playback_bar", true);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_up_in, R.anim.no_animation);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    //#endregion

    private void updatePlaybackBarVisibility() {
        if (playbackBar != null) {
            boolean shouldShowBar = playbackManager.getCurrentSong() != null;
            playbackBar.setVisibility(shouldShowBar ? View.VISIBLE : View.GONE);

            // Adjust main content padding if needed
            View mainContent = findViewById(android.R.id.content);
            if (mainContent instanceof ViewGroup && (BaseActivity.this instanceof SongView || BaseActivity.this instanceof PlaylistView)) {
                // Add bottom padding to main content if bar is visible
                // This is a simplified approach - might need to adjust based on layouts
                int bottomPadding = shouldShowBar ?
                        getResources().getDimensionPixelSize(R.dimen.playback_bar_height) : 0;
//                mainContent.setPadding(
//                        mainContent.getPaddingLeft(),
//                        mainContent.getPaddingTop(),
//                        mainContent.getPaddingRight(),
//                        bottomPadding
//                );
            }
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying, SongModel currentSong) {
        // Update playback bar UI
        updatePlaybackBarUI(isPlaying, currentSong);

        // Update visibility of the bar
        updatePlaybackBarVisibility();
    }

    @Override
    public void onPlaybackPositionChanged(long positionMs, long durationMs) {
        runOnUiThread(() -> {
            if (playbackSeekbar != null && !isSeeking) {
                // Update seekbar max and progress
                playbackSeekbar.setMax((int) durationMs);
                playbackSeekbar.setProgress((int) positionMs);

                // Update text views if they exist
//                if (positionText != null) {
//                    positionText.setText(formatDuration(positionMs));
//                }
//                if (durationText != null) {
//                    durationText.setText(formatDuration(durationMs));
//                }
            }
        });
    }

    private void updatePlaybackBarUI(boolean isPlaying, SongModel currentSong) {
        if (playbackBar == null) return;

        runOnUiThread(() -> {
            if (playPauseButton != null) {
                // Update play/pause button icon
                playPauseButton.setImageResource(
                        isPlaying ? R.drawable.pause_circle_filled : R.drawable.play_circle_filled);
            }

            // A carousel commit owns the current panel while it settles; let its own
            // reconciliation set the final track rather than double-animating here.
            if (carouselCommitting && currentSong != null) {
                currentDisplayedSong = currentSong;
                lastGlobalSong = currentSong;
                return;
            }

            // A placeholder commit (Recommendations past the end) resolves here when
            // the real track change arrives: swap the parked current panel onto it.
            if (carouselAwaitingTrack && currentSong != null) {
                carouselAwaitingTrack = false;
                cancelCurrentPanelAnimators();
                updateTrackInfo(currentSong);
                setCurrentPanelTranslation(0f);
                setCurrentPanelAlpha(1f);
                removeIncomingPanel();
                currentDisplayedSong = currentSong;
                lastGlobalSong = currentSong;
                return;
            }

            if (currentSong != null) {
                // Check if this is a different song to trigger animation (not just activity recreation)
                boolean isNewSong = lastGlobalSong == null ||
                        !currentSong.getId().equals(lastGlobalSong.getId());

                // Only animate if it's a new song AND there was a previous song displayed
                boolean shouldAnimate = isNewSong &&
                        lastGlobalSong != null &&
                        currentDisplayedSong != null;

                if (shouldAnimate) {
                    // Cancel any pending animation
                    if (pendingAnimation != null) {
                        animationHandler.removeCallbacks(pendingAnimation);
                    }

                    // Delay animation slightly to avoid false triggers during rapid updates
                    pendingAnimation = () -> {
                        if (currentSong.equals(playbackManager.getCurrentSong())) {
                            animateTrackChange(currentSong);
                        }
                    };
                    animationHandler.postDelayed(pendingAnimation, 800); // 800ms delay
                } else {
                    updateTrackInfo(currentSong);
                }

                this.currentDisplayedSong = currentSong;
                lastGlobalSong = currentSong;
            }
        });
    }

    private void syncPlaybackBarState() {
        SongModel currentSong = playbackManager.getCurrentSong();
        boolean isPlaying = playbackManager.isPlaying();
        long currentPosition = playbackManager.getCurrentPositionMs();
        long duration = playbackManager.getDurationMs();

        // Update UI immediately without waiting for callbacks
        if (currentSong != null) {
            updatePlaybackBarUI(isPlaying, currentSong);
            onPlaybackPositionChanged(currentPosition, duration);
            updatePlaybackBarVisibility();
        }
    }

    private void animateTrackChange(SongModel newSong) {
        if (songName == null || artistName == null || albumCover == null) {
            updateTrackInfo(newSong);
            return;
        }

        int animationDuration = 1350;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        songName.animate()
                .translationX(-screenWidth * 0.3f)
                .alpha(0f)
                .scaleX(0.8f)
                .setDuration(animationDuration)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .start();

        artistName.animate()
                .translationX(-screenWidth * 0.3f)
                .alpha(0f)
                .scaleX(0.8f)
                .setDuration(animationDuration)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .start();

        albumCover.animate()
                .translationX(-screenWidth * 0.4f)
                .alpha(0f)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .rotation(15f)
                .setDuration(animationDuration)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    if (isDestroyed() || isFinishing()) {
                        return;
                    }

                    updateTrackInfo(newSong);

                    // Reset positions for slide-in
                    songName.setTranslationX(screenWidth * 0.3f);
                    songName.setScaleX(0.8f);
                    songName.setAlpha(0f);

                    artistName.setTranslationX(screenWidth * 0.3f);
                    artistName.setScaleX(0.8f);
                    artistName.setAlpha(0f);

                    albumCover.setTranslationX(screenWidth * 0.4f);
                    albumCover.setScaleX(0.7f);
                    albumCover.setScaleY(0.7f);
                    albumCover.setRotation(-15f);
                    albumCover.setAlpha(0f);

                    // Check again before starting second animation
                    if (isDestroyed() || isFinishing()) {
                        return;
                    }

                    // Animate in with spring-like effect
                    songName.animate()
                            .translationX(0)
                            .alpha(1f)
                            .scaleX(1f)
                            .setDuration(animationDuration)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();

                    artistName.animate()
                            .translationX(0)
                            .alpha(1f)
                            .scaleX(1f)
                            .setDuration(animationDuration)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();

                    albumCover.animate()
                            .translationX(0)
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(animationDuration)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    private void updateTrackInfo(SongModel song) {
        if (isDestroyed() || isFinishing()) {
            return;
        }

        // Update song info without animation
        if (songName != null) {
            songName.setText(song.getName());
        }

        if (artistName != null) {
            artistName.setText(song.getArtist());
        }

        // Load album artwork
        if (albumCover != null && !isDestroyed() && !isFinishing()) {
            Glide.with(this)
                    .load(song.getAlbumCoverUrl())
                    .placeholder(R.drawable.playlist_placeholder)
                    .error(R.drawable.playlist_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(albumCover);
        }

        // Tint the bar with a left-to-right gradient: the album's vibrant colour on the
        // left, fading into the base colour by 25% and holding it to the right.
        applyPlaybackBarGradient(song.getAlbumCoverUrl());
    }

    private void applyPlaybackBarGradient(String albumCoverUrl) {
        if (playbackBarBackground == null) return;

        if (albumCoverUrl == null || albumCoverUrl.isEmpty()) {
            setPlaybackBarGradient(getColor(R.color.playback_bar_base));
            return;
        }

        ColorExtractor.extractColors(this, albumCoverUrl, new ColorExtractor.ColorExtractionCallback() {
            @Override
            public void onColorExtracted(int dominantColor, int vibrantColor) {
                if (isDestroyed() || isFinishing()) return;
                setPlaybackBarGradient(vibrantColor);
            }

            @Override
            public void onError() {
                if (isDestroyed() || isFinishing()) return;
                setPlaybackBarGradient(getColor(R.color.playback_bar_base));
            }
        });
    }

    private void setPlaybackBarGradient(int startColor) {
        if (playbackBarBackground == null) return;
        playbackBarBackground.setBackground(buildBarGradient(startColor));
    }

    // Vibrant at the left edge, reaching the base colour by 25% and holding it
    // across the rest of the bar.
    private GradientDrawable buildBarGradient(int startColor) {
        int baseColor = getColor(R.color.playback_bar_base);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{startColor, baseColor, baseColor});
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            gradient.setColors(new int[]{startColor, baseColor, baseColor},
                    new float[]{0f, 0.25f, 1f});
        }
        gradient.setCornerRadius(0f);
        return gradient;
    }

    // Cross-fade the bar gradient to the incoming song's colour (optimistic
    // carousel commit). Colour extraction is async, so the fade starts once the
    // vibrant colour is ready.
    private void crossFadeGradient(String albumCoverUrl) {
        if (playbackBarBackground == null) return;
        if (albumCoverUrl == null || albumCoverUrl.isEmpty()) {
            crossFadeGradientToColor(getColor(R.color.playback_bar_base));
            return;
        }
        ColorExtractor.extractColors(this, albumCoverUrl, new ColorExtractor.ColorExtractionCallback() {
            @Override
            public void onColorExtracted(int dominantColor, int vibrantColor) {
                if (isDestroyed() || isFinishing()) return;
                crossFadeGradientToColor(vibrantColor);
            }

            @Override
            public void onError() {
                if (isDestroyed() || isFinishing()) return;
                crossFadeGradientToColor(getColor(R.color.playback_bar_base));
            }
        });
    }

    private void crossFadeGradientToColor(int startColor) {
        if (playbackBarBackground == null) return;
        Drawable from = playbackBarBackground.getBackground();
        Drawable to = buildBarGradient(startColor);
        if (from == null) {
            playbackBarBackground.setBackground(to);
            return;
        }
        TransitionDrawable transition = new TransitionDrawable(new Drawable[]{from, to});
        transition.setCrossFadeEnabled(true);
        playbackBarBackground.setBackground(transition);
        transition.startTransition(CAROUSEL_GRADIENT_CROSSFADE_MS);
    }

    @Override
    public void onConnectionStateChanged(boolean isConnected) {
        // might want to show some UI feedback when connection state changes
    }

    public void setDeviceWarningVisible(boolean visible) {
        if (deviceWarningIcon != null) {
            runOnUiThread(() -> deviceWarningIcon.setVisibility(visible ? View.VISIBLE : View.GONE));
        }
    }

    private String formatDuration(long durationMs) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) -
                TimeUnit.MINUTES.toSeconds(minutes);
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}