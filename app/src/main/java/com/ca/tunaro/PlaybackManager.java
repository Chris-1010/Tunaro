package com.ca.tunaro;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

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
    private boolean isConnected = false;

    // Listeners
    private final List<PlaybackListener> listeners = new ArrayList<>();

    public interface PlaybackListener {
        void onPlaybackStateChanged(boolean isPlaying, SongModel currentSong);
        void onConnectionStateChanged(boolean isConnected);
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
        if (isConnected && spotifyAppRemote != null) {
            if (onSuccess != null) onSuccess.run();
            return;
        }

        ConnectionParams connectionParams = new ConnectionParams.Builder(clientId)
                .setRedirectUri(redirectUri)
                .showAuthView(true)
                .build();

        SpotifyAppRemote.connect(context, connectionParams, new Connector.ConnectionListener() {
            @Override
            public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                PlaybackManager.this.spotifyAppRemote = spotifyAppRemote;
                isConnected = true;

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
                Log.e(TAG, "Failed to connect to Spotify: " + throwable.getMessage());
                Toast.makeText(context,
                        "Failed to connect to Spotify. Please ensure the Spotify app is installed.",
                        Toast.LENGTH_LONG).show();

                // Notify listeners
                notifyConnectionStateChanged();
            }
        });
    }

    public void disconnect() {
        if (spotifyAppRemote != null) {
            SpotifyAppRemote.disconnect(spotifyAppRemote);
            spotifyAppRemote = null;
            isConnected = false;
            notifyConnectionStateChanged();
        }
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
            // Extract artist names as strings
            String[] artistNames = new String[remoteTrack.artists.size()];
            for (int i = 0; i < remoteTrack.artists.size(); i++) {
                artistNames[i] = remoteTrack.artists.get(i).name;
            }

            // Only update current song if it's different
            if (currentSong == null || !remoteTrack.uri.equals(currentSong.getUri())) {
                // Extract ID from URI (format: spotify:track:id)
                String id = remoteTrack.uri.split(":")[2];

                // Create a simplified SongModel from track with string artist names
                currentSong = createSongModelFromRemoteTrack(remoteTrack, id, artistNames);

                // Now you can safely show toast with context
                showToast("Now playing: " + remoteTrack.name);
            }

            // Update playing state
            boolean wasPlaying = isPlaying;
            isPlaying = !playerState.isPaused;

            // Only notify if state actually changed
            if (wasPlaying != isPlaying) {
                notifyPlaybackStateChanged();
            }
        } else {
            // No track playing
            if (isPlaying) {
                isPlaying = false;
                notifyPlaybackStateChanged();
            }
        }
    }

    // Helper method to create SongModel
    private SongModel createSongModelFromRemoteTrack(Track remoteTrack, String id, String[] artistNames) {
        // This will need to be updated based on your modified SongModel
        // This is just an example assuming you'll update SongModel to accept String[] for artists
        return new SongModel(
                id,
                remoteTrack.name,
                artistNames,
                (int) remoteTrack.duration,
                remoteTrack.uri,
                0, // We don't have popularity from playback
                remoteTrack.album.name,
                remoteTrack.imageUri.raw,
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

    // Add methods for next/previous if needed

    public void addListener(PlaybackListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);

            // Update new listener with current state
            if (listener != null) {
                listener.onConnectionStateChanged(isConnected);
                listener.onPlaybackStateChanged(isPlaying, currentSong);
            }
        }
    }

    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    private void notifyPlaybackStateChanged() {
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackStateChanged(isPlaying, currentSong);
        }
    }

    private void notifyConnectionStateChanged() {
        for (PlaybackListener listener : listeners) {
            listener.onConnectionStateChanged(isConnected);
        }
    }

    // Getters
    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public SongModel getCurrentSong() {
        return currentSong;
    }

    public SpotifyAppRemote getSpotifyAppRemote() {
        return spotifyAppRemote;
    }

    private void showToast(String message) {
        if (applicationContext != null) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show();
        }
    }
}