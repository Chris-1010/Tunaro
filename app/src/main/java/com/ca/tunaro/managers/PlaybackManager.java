package com.ca.tunaro.managers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.activites.MainActivity;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.utils.DeviceChecker;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.types.PlayerState;
import com.spotify.protocol.types.Track;

import java.util.ArrayList;
import java.util.List;

public class PlaybackManager {
    private static final String TAG = "PlaybackManager";
    private static PlaybackManager instance;

    private Context applicationContext;

    // Spotify connection params
    private String clientId;
    private String redirectUri;
    private SpotifyAppRemote spotifyAppRemote;

    // Current playback state
    private SongModel currentSong;
    private boolean isPlaying = false;
    private boolean isConnecting = false;
    private boolean isConnected = false;

    // Seeking state
    private long currentPositionMs = 0;
    private long durationMs = 0;
    private final Handler positionHandler = new Handler(Looper.getMainLooper());
    private final Runnable positionUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updatePlaybackPosition();
            positionHandler.postDelayed(this, 100);
        }
    };
    private boolean isTrackingPosition = false;

    // Track Listening
    private boolean isTrackingListen = false;
    private long listenStartTime = 0;
    private final long LISTEN_THRESHOLD_MS = 10000; // 10 seconds
    private String currentListenTrackId = null;
    private boolean hasRecordedListen = false;
    private boolean isSnippetMode = false;
    private final Handler listenHandler = new Handler(Looper.getMainLooper());
    private Runnable listenRunnable;

    // Listeners
    private final List<PlaybackListener> listeners = new ArrayList<>();

    public interface PlaybackListener {
        void onPlaybackStateChanged(boolean isPlaying, SongModel currentSong);

        void onConnectionStateChanged(boolean isConnected);

        void onPlaybackPositionChanged(long positionMs, long durationMs);
    }

    private PlaybackManager() {
        // Private constructor for singleton
    }

    public static synchronized PlaybackManager getInstance() {
        if (instance == null) {
            instance = new PlaybackManager();
        }
        return instance;
    }

    public void initialize(Context context, String clientId, String redirectUri) {
        this.applicationContext = context.getApplicationContext();
        this.clientId = clientId;
        this.redirectUri = redirectUri;
    }

    public void connectSpotify(Context context, Runnable onSuccess) {
        // Avoid multiple connection attempts while one is in progress
        if (isConnecting) {
            Log.d(TAG, "Connection attempt already in progress, ignoring request");
            return;
        }

        // Already connected, just run the success callback
        if (isConnected && spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
            Log.d(TAG, "Already connected to Spotify remote");
            if (onSuccess != null) onSuccess.run();
            return;
        }

        // Disconnect first if there's a stale connection
        if (spotifyAppRemote != null) {
            Log.d(TAG, "Disconnecting previous remote connection before reconnecting");
            SpotifyAppRemote.disconnect(spotifyAppRemote);
            spotifyAppRemote = null;
        }

        isConnecting = true;
        isConnected = false;

        ConnectionParams connectionParams = new ConnectionParams.Builder(clientId)
                .setRedirectUri(redirectUri)
                .showAuthView(true)
                .build();

        Log.d(TAG, "Attempting to connect to Spotify remote");
        SpotifyAppRemote.connect(context, connectionParams, new Connector.ConnectionListener() {
            @Override
            public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                PlaybackManager.this.spotifyAppRemote = spotifyAppRemote;
                isConnected = true;
                isConnecting = false;

                // Subscribe to player state to monitor playback
                subscribeToPlayerState();

                // Notify listeners
                notifyConnectionStateChanged();

                // Run success callback if provided
                if (onSuccess != null) {
                    onSuccess.run();
                }

                Log.d(TAG, "Connected to Spotify remote");
            }

            @Override
            public void onFailure(Throwable throwable) {
                isConnected = false;
                isConnecting = false;
                spotifyAppRemote = null;

                Log.e(TAG, "Failed to connect to Spotify: " + throwable.getMessage(), throwable);
                Toast.makeText(context.getApplicationContext(),
                        "Failed to connect to Spotify. Please ensure the Spotify app is installed.",
                        Toast.LENGTH_LONG).show();

                // Notify listeners
                notifyConnectionStateChanged();
            }
        });
    }

    private void subscribeToPlayerState() {
        if (spotifyAppRemote != null) {
            spotifyAppRemote.getPlayerApi()
                    .subscribeToPlayerState()
                    .setEventCallback(this::processPlayerState);
        }
    }

    private void processPlayerState(PlayerState playerState) {
        Track remoteTrack = playerState.track;

        if (remoteTrack != null) {
            String trackId = remoteTrack.uri.split(":")[2];
            handleListenTracking(trackId, !playerState.isPaused);

            String[] artistNames = new String[remoteTrack.artists.size()];
            for (int i = 0; i < remoteTrack.artists.size(); i++) {
                artistNames[i] = remoteTrack.artists.get(i).name;
            }

            boolean trackChanged = false;
            if (currentSong == null || !remoteTrack.uri.equals(currentSong.getUri())) {
                // Extract ID from URI (format: spotify:track:id)

                // Create a simplified SongModel from track with string artist names
                currentSong = createSongModelFromRemoteTrack(remoteTrack, trackId, artistNames);
                trackChanged = true;

                // Check device when track changes
                checkPlaybackDevice();
            }

            // Update playing state
            boolean wasPlaying = isPlaying;
            isPlaying = !playerState.isPaused;

            // Notify if state or track changed
            if (wasPlaying != isPlaying || trackChanged) {
                notifyPlaybackStateChanged();
            }

            currentPositionMs = playerState.playbackPosition;
            durationMs = remoteTrack.duration;

            // Always notify position change when getting player state
            notifyPlaybackPositionChanged();

            // Update position tracking state when play state changes
            if (isPlaying && !isTrackingPosition) {
                startPositionTracking();
            } else if (!isPlaying && isTrackingPosition) {
                stopPositionTracking();
            }
        } else {
            // No track playing

            stopListenTracking();

            if (isPlaying || currentSong != null) {
                isPlaying = false;
                currentSong = null;
                notifyPlaybackStateChanged();
            }

            if (isTrackingPosition) {
                stopPositionTracking();
            }
        }
    }

    // Helper method to create SongModel
    private SongModel createSongModelFromRemoteTrack(Track remoteTrack, String id, String[] artistNames) {
        // Convert Spotify URI image format to web URL format
        String imageUrl = remoteTrack.imageUri.raw;
        if (imageUrl != null && imageUrl.startsWith("spotify:image:")) {
            // Extract the image ID (the part after the last colon)
            String imageId = imageUrl.substring(imageUrl.lastIndexOf(":") + 1);
            // Construct the proper web URL
            imageUrl = "https://i.scdn.co/image/" + imageId;
        }

        return new SongModel(
                id,
                remoteTrack.name,
                artistNames,
                (int) remoteTrack.duration,
                remoteTrack.uri,
                0, // Don't have popularity from playback
                remoteTrack.album.name,
                imageUrl,
                null,
                null
        );
    }

    public void playSong(SongModel song) {
        if (spotifyAppRemote != null && isConnected) {
            spotifyAppRemote.getPlayerApi().play(song.getUri())
                    .setResultCallback(empty -> {
                        currentSong = song;
                        isPlaying = true;
                        notifyPlaybackStateChanged();
                        checkPlaybackDevice();
                    })
                    .setErrorCallback(throwable -> {
                        Log.e(TAG, "Error playing song: " + throwable.getMessage());
                    });
        }
    }

    public void togglePlayPause() {
        if (spotifyAppRemote != null && isConnected) {
            if (isPlaying) {
                spotifyAppRemote.getPlayerApi().pause()
                        .setResultCallback(empty -> {
                            isPlaying = false;
                            notifyPlaybackStateChanged();
                        });
            } else {
                spotifyAppRemote.getPlayerApi().resume()
                        .setResultCallback(empty -> {
                            isPlaying = true;
                            notifyPlaybackStateChanged();
                        });
            }
        }
    }

    private void checkPlaybackDevice() {
        if (applicationContext != null) {
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null && mainActivity.getSpotifyApi() != null) {
                DeviceChecker.checkPlaybackDevice(applicationContext, mainActivity.getSpotifyApi(),
                        new DeviceChecker.DeviceCheckCallback() {
                            @Override
                            public void onDeviceCheckResult(boolean isCorrectDevice, String message) {
                                if (!isCorrectDevice && DeviceChecker.isDeviceCheckEnabled(applicationContext)) {
                                    showToast("Device warning: " + message);
                                }
                            }

                            @Override
                            public void onDeviceWarningStateChanged(boolean showWarning) {
                                // Notify all BaseActivity instances about the warning state
                                for (PlaybackListener listener : listeners) {
                                    if (listener instanceof BaseActivity) {
                                        ((BaseActivity) listener).setDeviceWarningVisible(showWarning);
                                    }
                                }
                            }
                        });
            }
        }
    }

    // TODO Methods for next/previous track switching

    //#region Listeners

    public void addListener(PlaybackListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);

            // Update new listener with current state
            if (listener != null) {
                listener.onConnectionStateChanged(isConnected);
                listener.onPlaybackStateChanged(isPlaying, currentSong);

                if (currentSong != null && durationMs > 0) {
                    listener.onPlaybackPositionChanged(currentPositionMs, durationMs);
                }
            }
        }
    }

    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    //#endregion

    //#region Listen Tracking Methods

    private void handleListenTracking(String trackId, boolean isPlaying) {
        if (isSnippetMode) {
            return; // Don't track during snippet playback
        }

        // Check if this is a different track
        if (currentListenTrackId != null && !trackId.equals(currentListenTrackId)) {
            // New track started - reset listen state for the previous track
            resetListenState();
        }

        if (isPlaying) {
            if (!isTrackingListen || !trackId.equals(currentListenTrackId)) {
                // New track or resuming tracking
                startListenTracking(trackId);
            }
        } else {
            // Paused - stop tracking but don't reset the recorded state
            stopListenTracking();
        }
    }

    private void startListenTracking(String trackId) {
        // If it's a different track, reset
        if (!trackId.equals(currentListenTrackId)) {
            resetListenState();
            currentListenTrackId = trackId;
        }

        // Only start tracking if a listen for this track hasn't already been recorded
        if (!hasRecordedListen && !isTrackingListen) {
            isTrackingListen = true;
            listenStartTime = System.currentTimeMillis();

            listenRunnable = () -> {
                if (isTrackingListen && !hasRecordedListen && !isSnippetMode) {
                    recordListen(trackId);
                    hasRecordedListen = true;
                }
            };

            listenHandler.postDelayed(listenRunnable, LISTEN_THRESHOLD_MS);
        }
    }

    private void stopListenTracking() {
        isTrackingListen = false;
        if (listenRunnable != null) {
            listenHandler.removeCallbacks(listenRunnable);
            listenRunnable = null;
        }
    }

    private void resetListenState() {
        stopListenTracking();
        hasRecordedListen = false;
        currentListenTrackId = null;
        listenStartTime = 0;
    }

    private void recordListen(String songId) {
        if (applicationContext != null) {
            DatabaseHelper dbHelper = new DatabaseHelper(applicationContext);
            dbHelper.addListenRecord(songId);

            showToast("Recorded listen for song");
            Log.d(TAG, "Recorded listen for song: " + songId);
        }
    }

    //#endregion

    private void notifyPlaybackStateChanged() {
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackStateChanged(isPlaying, currentSong);
        }
    }

    private void notifyPlaybackPositionChanged() {
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackPositionChanged(currentPositionMs, durationMs);
        }
    }

    private void notifyConnectionStateChanged() {
        for (PlaybackListener listener : listeners) {
            listener.onConnectionStateChanged(isConnected);
        }
    }

    public void startPositionTracking() {
        if (!isTrackingPosition && isConnected && isPlaying) {
            isTrackingPosition = true;
            positionHandler.post(positionUpdateRunnable);
        }
    }

    public void stopPositionTracking() {
        isTrackingPosition = false;
        positionHandler.removeCallbacks(positionUpdateRunnable);
    }

    private void updatePlaybackPosition() {
        if (spotifyAppRemote != null && isConnected) {
            spotifyAppRemote.getPlayerApi().getPlayerState()
                    .setResultCallback(playerState -> {
                        if (playerState.track != null) {
                            currentPositionMs = playerState.playbackPosition;
                            durationMs = playerState.track.duration;

                            // Notify listeners with updated position
                            notifyPlaybackPositionChanged();
                        }
                    });
        }
    }

    public void seekTo(long positionMs) {
        if (spotifyAppRemote != null && isConnected) {
            spotifyAppRemote.getPlayerApi().seekTo(positionMs);
        }
    }

    //#region Getters

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isSnippetMode() {
        return isSnippetMode;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public SongModel getCurrentSong() {
        return currentSong;
    }

    public long getCurrentPositionMs() {
        return currentPositionMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public SpotifyAppRemote getSpotifyAppRemote() {
        return spotifyAppRemote;
    }

    //#endregion

    //#region Setters

    public void setSnippetMode(boolean isSnippetMode) {
        this.isSnippetMode = isSnippetMode;
        if (isSnippetMode) {
            stopListenTracking();
        }
    }

    //#endregion

    private void showToast(String message) {
        if (applicationContext != null) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show();
        }
    }

    public void disconnect() {
        stopPositionTracking();
        stopListenTracking();
        if (spotifyAppRemote != null) {
            SpotifyAppRemote.disconnect(spotifyAppRemote);
            spotifyAppRemote = null;
            isConnected = false;
            notifyConnectionStateChanged();
        }
    }
}