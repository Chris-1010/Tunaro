package com.ca.tunaro.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.ca.tunaro.R;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.models.SongModel;

/**
 * Foreground service acting purely as a lifetime anchor for a playback session
 * (ADR-0002). It holds no queue/snippet/poller state and runs none of that
 * logic: its jobs are to keep the process alive so PlaybackManager's playback
 * brain keeps steering Spotify while backgrounded, and to present a MediaStyle
 * media notification whose transport controls route back into PlaybackManager's
 * custom queue.
 *
 * <p>PlaybackManager owns the session lifecycle and starts/stops this service;
 * see {@code PlaybackManager.startPlaybackSession()} / {@code stopPlaybackSession()}.
 */
public class PlaybackService extends Service implements PlaybackManager.PlaybackListener {
    private static final String TAG = "PlaybackService";

    private static final String CHANNEL_ID = "tunaro_playback";
    private static final int NOTIFICATION_ID = 1001;

    private PlaybackManager playbackManager;
    private MediaSessionCompat mediaSession;
    private NotificationManagerCompat notificationManager;

    // The first notification is posted by startForeground in onStartCommand; until
    // then, listener callbacks update the session silently without posting.
    private boolean isForegroundStarted = false;

    // Cached album art and the song it belongs to, so metadata/notification can be
    // rebuilt synchronously while a fresh load runs asynchronously (re-posted on ready).
    private Bitmap currentArt;
    private String currentArtSongId;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service — pure anchor, no binder (ADR-0002).
    }

    @Override
    public void onCreate() {
        super.onCreate();
        playbackManager = PlaybackManager.getInstance();
        notificationManager = NotificationManagerCompat.from(this);
        createNotificationChannel();

        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setCallback(mediaSessionCallback);
        mediaSession.setActive(true);

        // Register for state/position updates. addListener immediately calls back
        // with current state, priming the session before startForeground posts.
        playbackManager.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isForegroundStarted = true;
        startForeground(NOTIFICATION_ID, buildNotification());
        // Route media-button / lock-screen / bluetooth transport events into the
        // session callback below.
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        // No recovery: a session killed under memory pressure ends and is not
        // resurrected (the queue is deliberately in-memory). See ADR-0002.
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (playbackManager != null) {
            playbackManager.removeListener(this);
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        isForegroundStarted = false;
        super.onDestroy();
    }

    //#region PlaybackListener

    @Override
    public void onPlaybackStateChanged(boolean isPlaying, SongModel currentSong) {
        updateMetadata(currentSong);
        updatePlaybackState();
        postNotification();
    }

    @Override
    public void onPlaybackPositionChanged(long positionMs, long durationMs) {
        // Position-only update: refresh the session state (cheap; the system
        // seekbar reads position from it) without re-posting the notification.
        updatePlaybackState();
    }

    @Override
    public void onConnectionStateChanged(boolean isConnected) {
        // Connection changes don't affect the notification; session lifecycle on
        // disconnect is handled by PlaybackManager stopping the service.
    }

    //#endregion

    //#region MediaSession → PlaybackManager routing

    private final MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            if (playbackManager != null && !playbackManager.isPlaying()) {
                playbackManager.togglePlayPause();
            }
        }

        @Override
        public void onPause() {
            if (playbackManager != null && playbackManager.isPlaying()) {
                playbackManager.togglePlayPause();
            }
        }

        @Override
        public void onSkipToNext() {
            if (playbackManager != null) playbackManager.next();
        }

        @Override
        public void onSkipToPrevious() {
            if (playbackManager != null) playbackManager.previous();
        }

        @Override
        public void onSeekTo(long pos) {
            // Gated in the PlaybackState actions: ACTION_SEEK_TO is only advertised
            // outside snippet mode, so the notification seekbar is non-draggable
            // while a snippet owns the playhead. Guard here too for safety.
            if (playbackManager != null && !playbackManager.isSnippetMode()) {
                playbackManager.seekTo(pos);
            }
        }
    };

    //#endregion

    //#region MediaSession + notification building

    private void updateMetadata(SongModel song) {
        if (mediaSession == null) return;
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        if (song != null) {
            builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getName());
            builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist());
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, playbackManager.getDurationMs());
            loadArtIfNeeded(song);
            if (currentArt != null && song.getId().equals(currentArtSongId)) {
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArt);
            }
        }
        mediaSession.setMetadata(builder.build());
    }

    private void updatePlaybackState() {
        if (mediaSession == null || playbackManager == null) return;
        boolean isPlaying = playbackManager.isPlaying();
        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        // Draggable seekbar in normal playback; suppressed during snippet mode,
        // mirroring the in-app rule (CONTEXT.md, Tunaro media session).
        if (!playbackManager.isSnippetMode()) {
            actions |= PlaybackStateCompat.ACTION_SEEK_TO;
        }
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                        isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        playbackManager.getCurrentPositionMs(),
                        isPlaying ? 1f : 0f)
                .build();
        mediaSession.setPlaybackState(state);
    }

    private void postNotification() {
        if (!isForegroundStarted || notificationManager == null) return;
        notificationManager.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        boolean isPlaying = playbackManager != null && playbackManager.isPlaying();
        SongModel song = playbackManager != null ? playbackManager.getCurrentSong() : null;

        androidx.core.app.NotificationCompat.Builder builder =
                new androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(song != null ? song.getName() : getString(R.string.app_name))
                        .setContentText(song != null ? song.getArtist() : null)
                        .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                        .setOngoing(true)
                        .setContentIntent(buildContentIntent())
                        .setOnlyAlertOnce(true);

        if (currentArt != null && song != null && song.getId().equals(currentArtSongId)) {
            builder.setLargeIcon(currentArt);
        }

        builder.addAction(R.drawable.ic_notif_skip_previous, getString(R.string.notif_previous),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));
        builder.addAction(
                isPlaying ? R.drawable.ic_notif_pause : R.drawable.ic_notif_play,
                getString(isPlaying ? R.string.notif_pause : R.string.notif_play),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE));
        builder.addAction(R.drawable.ic_notif_skip_next, getString(R.string.notif_next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT));

        builder.setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));

        return builder.build();
    }

    private PendingIntent buildContentIntent() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch == null) {
            launch = new Intent();
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 0, launch, flags);
    }

    // Load album art for the current song asynchronously, then cache it and
    // re-post so the notification and lock screen pick up the artwork.
    private void loadArtIfNeeded(SongModel song) {
        if (song == null || song.getId().equals(currentArtSongId)) {
            return; // already loaded (or loading) for this song
        }
        currentArtSongId = song.getId();
        currentArt = null;
        String url = song.getAlbumCoverUrl();
        if (url == null || url.isEmpty()) return;
        final String requestedId = song.getId();
        Glide.with(getApplicationContext())
                .asBitmap()
                .load(url)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        // Ignore a stale load whose song is no longer current.
                        if (!requestedId.equals(currentArtSongId)) return;
                        currentArt = resource;
                        SongModel current = playbackManager != null ? playbackManager.getCurrentSong() : null;
                        if (current != null) {
                            updateMetadata(current);
                        }
                        postNotification();
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                        // No-op: the cached bitmap is dropped when the next song loads.
                    }
                });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.playback_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.playback_channel_description));
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    //#endregion
}
