package com.ca.tunaro;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
    protected ImageView albumCover;
    protected TextView songName;
    protected TextView artistName;
    protected ImageButton playPauseButton;
    protected ImageView deviceWarningIcon;
//    private TextView positionText;
//    private TextView durationText;

    private final Handler animationHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingAnimation;

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

    private void setupPlaybackBar() {
        // Find playback bar views
        playbackBar = findViewById(R.id.playback_bar);
        playbackBarBackground = findViewById(R.id.frameLayout);
        playbackSeekbar = findViewById(R.id.playback_seekbar);
        if (playbackBar == null) return;

        albumCover = findViewById(R.id.playback_album_cover);
        songName = findViewById(R.id.playback_song_name);
        artistName = findViewById(R.id.playback_artist_name);
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

        // Set click listener for the bar itself to open SongView
        if (playbackBar != null) {
            playbackBar.setOnClickListener(v -> {
                SongModel currentSong = playbackManager.getCurrentSong();
                if (currentSong != null) {
                    MainActivity mainActivity = MainActivity.getInstance();

                    if (mainActivity != null) {
                        // Check if the SongView for the clicked song is already open
                        SelectedSongHolder songHolder = SelectedSongHolder.getInstance();
                        if (songHolder.getSelectedSong() != null && Objects.equals(songHolder.getSelectedSong().getId(), currentSong.getId()))
                            return;

                        // Set the selected song
                        SelectedSongHolder.getInstance().setSelectedSong(currentSong);

                        // Open SongView activity
                        Intent intent = new Intent(this, SongView.class);
                        intent.putExtra("from_playback_bar", true);
                        startActivity(intent);
                        overridePendingTransition(R.anim.slide_up_in, R.anim.no_animation);
                    } else {
                        // Handle the case where no MainActivity reference is found
                        showToast("Unable to open song view");
                    }
                }
            });
        }
        if (playbackSeekbar != null) {
            // Block seeking while a snippet is playing: the snippet owns the
            // playhead (its range + end-timer), so a manual drag would fight it.
            playbackSeekbar.setOnTouchListener((v, e) ->
                    playbackManager != null && playbackManager.isSnippetMode());
            playbackSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                    if (fromUser && isSeeking) {
//                        // Update position text if it exists
//                        if (positionText != null) {
//                            positionText.setText(formatDuration(progress));
//                        }
//                    }
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

        int baseColor = getColor(R.color.playback_bar_base);
        // Vibrant at the left edge, reaching the base colour by 25% and holding it across
        // the rest of the bar.
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{startColor, baseColor, baseColor});
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            gradient.setColors(new int[]{startColor, baseColor, baseColor},
                    new float[]{0f, 0.25f, 1f});
        }
        gradient.setCornerRadius(0f);
        playbackBarBackground.setBackground(gradient);
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