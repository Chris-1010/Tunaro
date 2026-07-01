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
import com.ca.tunaro.models.SongSnippet;
import com.ca.tunaro.utils.DeviceChecker;
import com.ca.tunaro.utils.SongCache;
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

    // Connection retry / watchdog
    private static final int MAX_CONNECT_RETRIES = 3;
    private static final long CONNECT_TIMEOUT_MS = 12000; // give up on a single attempt after this
    private int connectRetryCount = 0;
    private final Handler connectHandler = new Handler(Looper.getMainLooper());
    private Runnable connectTimeoutRunnable;

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
    private final long LISTEN_THRESHOLD_MS = 10000; // fallback when duration unknown
    private String currentListenTrackId = null;
    private boolean hasRecordedListen = false;
    private boolean isDeviceWarningActive = false;
    private final Handler listenHandler = new Handler(Looper.getMainLooper());
    private Runnable listenRunnable;

    // Queues, Spotify-style:
    //  - primaryQueue: songs the user explicitly added (swipe-to-queue). FIFO.
    //    Played before the playlist flow and removed once consumed.
    //  - secondaryQueue + secondaryIndex: the playlist flow seeded by tapping a
    //    song. secondaryIndex points at the playlist song the playback is
    //    anchored to; it only advances when a secondary song actually plays, so
    //    primary tracks slot in "between" playlist songs.
    //  - history: every song that finished or was advanced past, newest last.
    //    Plumbing for a future previous-song control.
    private List<SongModel> primaryQueue = new ArrayList<>();
    private List<SongModel> secondaryQueue = new ArrayList<>();
    private int secondaryIndex = -1;
    private final List<SongModel> history = new ArrayList<>();
    private static final int MAX_HISTORY = 200;
    // Guard: only fire advanceQueue() once per song-end
    private boolean queueAdvancePending = false;
    // Timestamp of last advance — suppress end-of-song check for 3s after an advance
    // to avoid Spotify returning stale position data for the newly-started track.
    private long lastAdvanceTimeMs = 0;

    //#region Snippet Playback Fields
    // The behaviour applied when a snippet's end-timer fires. Transient: held in
    // memory for the live session only, never persisted. See ADR 0001.
    public enum SnippetEndMode { STOP, LOOP, DETACH }

    // The persisted default mode a snippet starts in (set in SettingsActivity).
    public static final String PREFS_NAME = "TunaroPrefs";
    public static final String PREF_SNIPPET_DEFAULT_MODE = "snippet_default_end_mode";

    private boolean isSnippetMode = false;
    private boolean isSnippetPlaying = false;
    private SongSnippet currentSnippet;
    private SnippetEndMode snippetEndMode = SnippetEndMode.STOP;
    private final Handler snippetHandler = new Handler(Looper.getMainLooper());
    private Runnable snippetEndRunnable;
    private int activeSnippetTimers = 0;
    // Wall-clock bookkeeping so the end-timer can be paused with the song and
    // resumed from the time that was left, instead of firing while paused.
    private long snippetTimerDueAtMs = 0;   // when the pending timer would fire
    private long snippetTimerRemainingMs = 0; // time left while paused; 0 = running
    //#endregion

    // Listeners
    private final List<PlaybackListener> listeners = new ArrayList<>();

    public interface PlaybackListener {
        void onPlaybackStateChanged(boolean isPlaying, SongModel currentSong);

        void onConnectionStateChanged(boolean isConnected);

        void onPlaybackPositionChanged(long positionMs, long durationMs);
    }

    private PlaybackManager() {
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
        // Fresh user-initiated connection: reset the retry counter.
        connectRetryCount = 0;
        attemptConnect(onSuccess);
    }

    private void attemptConnect(Runnable onSuccess) {
        // Already connected, just run the success callback
        if (isConnected && spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
            Log.d(TAG, "Already connected to Spotify remote");
            cancelConnectTimeout();
            isConnecting = false;
            if (onSuccess != null) onSuccess.run();
            return;
        }

        // Avoid multiple connection attempts while one is genuinely in progress.
        // A watchdog (cancelConnectTimeout/connectTimeoutRunnable) guarantees this
        // flag can never stay stuck true forever, which previously deadlocked all
        // future connection attempts after the app sat idle.
        if (isConnecting) {
            Log.d(TAG, "Connection attempt already in progress, ignoring request");
            return;
        }

        // Disconnect first if there's a stale connection
        if (spotifyAppRemote != null) {
            Log.d(TAG, "Disconnecting previous remote connection before reconnecting");
            SpotifyAppRemote.disconnect(spotifyAppRemote);
            spotifyAppRemote = null;
        }

        if (applicationContext == null) {
            Log.e(TAG, "Cannot connect: PlaybackManager not initialized with a context");
            return;
        }

        isConnecting = true;
        isConnected = false;
        startConnectTimeout(onSuccess);

        ConnectionParams connectionParams = new ConnectionParams.Builder(clientId)
                .setRedirectUri(redirectUri)
                .showAuthView(true)
                .build();

        Log.d(TAG, "Attempting to connect to Spotify remote (attempt " + (connectRetryCount + 1) + "/" + (MAX_CONNECT_RETRIES + 1) + ")");
        // Always connect via the application context so a destroyed Activity can't
        // leave the binding in a broken state.
        SpotifyAppRemote.connect(applicationContext, connectionParams, new Connector.ConnectionListener() {
            @Override
            public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                cancelConnectTimeout();
                PlaybackManager.this.spotifyAppRemote = spotifyAppRemote;
                isConnected = true;
                isConnecting = false;
                connectRetryCount = 0;

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
                cancelConnectTimeout();
                isConnected = false;
                isConnecting = false;
                spotifyAppRemote = null;

                Log.w(TAG, "Spotify remote connection failed: " + throwable.getMessage(), throwable);
                notifyConnectionStateChanged();
                scheduleRetryOrGiveUp(onSuccess);
            }
        });
    }

    // Watchdog: if a connection attempt neither succeeds nor fails within the
    // timeout, treat it as a failure so isConnecting can never stay stuck.
    private void startConnectTimeout(Runnable onSuccess) {
        cancelConnectTimeout();
        connectTimeoutRunnable = () -> {
            if (isConnecting) {
                Log.w(TAG, "Connection attempt timed out after " + CONNECT_TIMEOUT_MS + "ms");
                isConnecting = false;
                if (spotifyAppRemote != null) {
                    SpotifyAppRemote.disconnect(spotifyAppRemote);
                    spotifyAppRemote = null;
                }
                isConnected = false;
                scheduleRetryOrGiveUp(onSuccess);
            }
        };
        connectHandler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS);
    }

    private void cancelConnectTimeout() {
        if (connectTimeoutRunnable != null) {
            connectHandler.removeCallbacks(connectTimeoutRunnable);
            connectTimeoutRunnable = null;
        }
    }

    private void scheduleRetryOrGiveUp(Runnable onSuccess) {
        if (connectRetryCount < MAX_CONNECT_RETRIES) {
            connectRetryCount++;
            // Exponential-ish backoff: 1s, 2s, 4s. Gives the Spotify app time to
            // wake up instead of hammering it synchronously.
            long backoff = 1000L * (1L << (connectRetryCount - 1));
            Log.d(TAG, "Retrying Spotify connection in " + backoff + "ms (retry " + connectRetryCount + "/" + MAX_CONNECT_RETRIES + ")");
            connectHandler.postDelayed(() -> attemptConnect(onSuccess), backoff);
        } else {
            connectRetryCount = 0;
            Log.e(TAG, "Failed to connect to Spotify after " + (MAX_CONNECT_RETRIES + 1) + " attempts");
            showToast("Couldn't connect to Spotify. Make sure Spotify is open, then try again.");
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
            String[] artistNames = new String[remoteTrack.artists.size()];
            for (int i = 0; i < remoteTrack.artists.size(); i++) {
                artistNames[i] = remoteTrack.artists.get(i).name;
            }

            boolean trackChanged = false;
            if (currentSong == null || !remoteTrack.uri.equals(currentSong.getUri())) {
                currentSong = createSongModelFromRemoteTrack(remoteTrack, remoteTrack.uri, artistNames);
                trackChanged = true;
                checkPlaybackDevice();
                if (applicationContext != null) {
                    new DatabaseHelper(applicationContext).upsertSong(currentSong);
                }
            }

            // Set duration before listen tracking starts: the listen threshold
            // is a third of the track duration, so it must reflect the new track.
            currentPositionMs = playerState.playbackPosition;
            durationMs = remoteTrack.duration;

            handleListenTracking(currentSong.getId(), !playerState.isPaused);

            // Update playing state
            boolean wasPlaying = isPlaying;
            isPlaying = !playerState.isPaused;

            // Notify if state or track changed
            if (wasPlaying != isPlaying || trackChanged) {
                notifyPlaybackStateChanged();
            }

            // Always notify position change when getting player state
            notifyPlaybackPositionChanged();

            // Update position tracking state when play state changes
            if (isPlaying && !isTrackingPosition) {
                startPositionTracking();
            } else if (!isPlaying && isTrackingPosition) {
                stopPositionTracking();
            }
        } else {
            // No track reported. This happens transiently during track transitions
            // (manual switch or queue auto-advance) as well as on a genuine stop.
            // The playback bar is never torn down here: a transient null would blank
            // the bar mid-transition, and on a real stop keeping the last song visible
            // (so it can be resumed) is better UX than an empty gap. The bar only
            // changes when a real track arrives to replace the current one.
            //
            // Tracking, however, should stop — there is no live track to poll.
            stopListenTracking();

            if (isPlaying) {
                isPlaying = false;
                // Keep currentSong; just reflect that nothing is actively playing so
                // the bar shows a resumable (paused) state.
                notifyPlaybackStateChanged();
            }

            if (isTrackingPosition) {
                stopPositionTracking();
            }
        }
    }

    // Helper method to create SongModel
    private SongModel createSongModelFromRemoteTrack(Track remoteTrack, String songId, String[] artistNames) {
        SongCache songCache = new SongCache(this.applicationContext);
        SongModel cachedSong = songCache.getCachedSong(songId);
        if (cachedSong != null) {
            return cachedSong;
        }

        // Convert Spotify URI image format to web URL format
        String imageUrl = remoteTrack.imageUri.raw;
        if (imageUrl != null && imageUrl.startsWith("spotify:image:")) {
            // Extract the image ID (the part after the last colon)
            String imageId = imageUrl.substring(imageUrl.lastIndexOf(":") + 1);
            // Construct the proper web URL
            imageUrl = "https://i.scdn.co/image/" + imageId;
        }

        return new SongModel(
                songId,
                remoteTrack.name,
                artistNames,
                (int) remoteTrack.duration,
                remoteTrack.uri,
                imageUrl
        );
    }

    public void playSong(SongModel song) {
        if (spotifyAppRemote != null && isConnected) {
            // Starting a normal song leaves any active snippet: tear down snippet
            // state so listen-tracking, auto-advance and seekbar seeking all resume.
            if (isSnippetPlaying || isSnippetMode) {
                stopSnippetPlayback();
            }
            spotifyAppRemote.getPlayerApi().play(song.getUri())
                    .setResultCallback(empty -> {
                        currentSong = song;
                        isPlaying = true;
                        queueAdvancePending = false;
                        notifyPlaybackStateChanged();
                        checkPlaybackDevice();
                        Log.i(TAG, "Now playing: " + song.getName() + " by " + String.join(", ", song.getArtist()));
                    })
                    .setErrorCallback(throwable -> {
                        Log.e(TAG, "Error playing song: " + throwable.getMessage());
                    });
        }
    }

    // Seed the secondary (playlist) queue from a tapped position and start
    // playing it. Leaves the primary (explicit) queue untouched, so explicit
    // adds survive switching playlists and still play next.
    public void playQueue(List<SongModel> songs, int startIndex) {
        if (songs == null || songs.isEmpty() || startIndex < 0 || startIndex >= songs.size())
            return;
        secondaryQueue = new ArrayList<>(songs);

        // Skip unplayable songs at the start of the queue
        while (startIndex < secondaryQueue.size() && !secondaryQueue.get(startIndex).isPlayable()) {
            Log.w(TAG, "playQueue: Skipping unplayable song '" + secondaryQueue.get(startIndex).getName() + "' at index " + startIndex);
            startIndex++;
        }

        if (startIndex >= secondaryQueue.size()) {
            Log.w(TAG, "playQueue: No playable songs in queue");
            secondaryQueue.clear();
            secondaryIndex = -1;
            queueAdvancePending = false;
            return;
        }

        secondaryIndex = startIndex;
        Log.i(TAG, "Created secondary queue with " + songs.size() + " songs. Starting at index " + startIndex + ": " + secondaryQueue.get(secondaryIndex).getName());
        pushHistory(currentSong);
        playSong(secondaryQueue.get(secondaryIndex));
    }

    public void skipToSong(SongModel song) {
        if (secondaryQueue.isEmpty()) {
            // No active playlist queue — play individually
            playSong(song);
            return;
        }
        for (int i = 0; i < secondaryQueue.size(); i++) {
            if (secondaryQueue.get(i).getUri().equals(song.getUri())) {
                secondaryIndex = i;
                pushHistory(currentSong);
                playSong(secondaryQueue.get(secondaryIndex));
                return;
            }
        }
        // Song not found in queue — play individually without touching the queue
        playSong(song);
    }

    public void clearQueue() {
        primaryQueue.clear();
        secondaryQueue.clear();
        secondaryIndex = -1;
        queueAdvancePending = false;
    }

    // Add a song to the primary (explicit) queue. With nothing playing, start it
    // immediately. No-op if the song is already queued.
    public boolean addToQueue(SongModel song) {
        if (song == null) return false;
        if (isInQueue(song)) return false;
        if (currentSong != null && currentSong.getUri().equals(song.getUri())) return false;

        if (currentSong == null) {
            // Nothing playing yet — just play it.
            playSong(song);
            Log.i(TAG, "addToQueue: nothing playing, starting '" + song.getName() + "'");
            return true;
        }

        primaryQueue.add(song);
        Log.i(TAG, "addToQueue: '" + song.getName() + "' (primary queue size " + primaryQueue.size() + ")");
        return true;
    }

    // Remove a song from whichever queue holds it. The currently-playing song
    // cannot be removed. secondaryIndex is fixed up to stay anchored.
    public boolean removeFromQueue(SongModel song) {
        if (song == null) return false;
        if (currentSong != null && currentSong.getUri().equals(song.getUri())) return false;

        for (int i = 0; i < primaryQueue.size(); i++) {
            if (primaryQueue.get(i).getUri().equals(song.getUri())) {
                primaryQueue.remove(i);
                Log.i(TAG, "removeFromQueue: '" + song.getName() + "' from primary (size " + primaryQueue.size() + ")");
                return true;
            }
        }

        for (int i = 0; i < secondaryQueue.size(); i++) {
            if (secondaryQueue.get(i).getUri().equals(song.getUri())) {
                secondaryQueue.remove(i);
                if (i < secondaryIndex) secondaryIndex--;
                if (secondaryQueue.isEmpty()) {
                    secondaryIndex = -1;
                    queueAdvancePending = false;
                }
                Log.i(TAG, "removeFromQueue: '" + song.getName() + "' from secondary (size " + secondaryQueue.size() + ")");
                return true;
            }
        }
        return false;
    }

    // True when the song is upcoming: queued in the primary queue, or ahead of
    // the anchor in the secondary (playlist) queue. The currently-playing song
    // is never "in queue".
    public boolean isInQueue(SongModel song) {
        if (song == null) return false;
        if (currentSong != null && currentSong.getUri().equals(song.getUri())) return false;

        for (SongModel s : primaryQueue) {
            if (s.getUri().equals(song.getUri())) return true;
        }
        for (int i = secondaryIndex + 1; i >= 0 && i < secondaryQueue.size(); i++) {
            if (secondaryQueue.get(i).getUri().equals(song.getUri())) return true;
        }
        return false;
    }

    public boolean hasActiveQueue() {
        return !primaryQueue.isEmpty() || !secondaryQueue.isEmpty();
    }

    // The secondary (playlist) queue and its anchor, used by QueueLineDecoration
    // to draw the connecting line down upcoming playlist songs.
    public int getQueueIndex() {
        return secondaryIndex;
    }

    public List<SongModel> getQueue() {
        return secondaryQueue;
    }

    public void togglePlayPause() {
        if (spotifyAppRemote != null && isConnected) {
            checkPlaybackDevice();
            if (isPlaying) {
                spotifyAppRemote.getPlayerApi().pause()
                        .setResultCallback(empty -> {
                            isPlaying = false;
                            // Hold the end-timer once the pause has actually taken effect,
                            // so it can't fire while paused; resuming continues from the
                            // time that was left.
                            if (isSnippetMode) {
                                holdSnippetEndTimer();
                            }
                            notifyPlaybackStateChanged();
                        });
            } else {
                spotifyAppRemote.getPlayerApi().resume()
                        .setResultCallback(empty -> {
                            isPlaying = true;
                            if (isSnippetMode) {
                                resumeSnippetEndTimer();
                            }
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
                                if (!isCorrectDevice && PlaybackManager.instance.isPlaying() && DeviceChecker.isDeviceCheckEnabled(applicationContext)) {
                                    showToast(message);
                                }
                            }

                            @Override
                            public void onDeviceWarningStateChanged(boolean showWarning) {
                                setDeviceWarningState(showWarning);
                            }
                        });
            }
        }
    }

    // Whether advancing would land on a real next song (primary or secondary).
    private boolean hasNextSong() {
        for (SongModel s : primaryQueue) {
            if (s.isPlayable()) return true;
        }
        for (int i = secondaryIndex + 1; i >= 0 && i < secondaryQueue.size(); i++) {
            if (secondaryQueue.get(i).isPlayable()) return true;
        }
        return false;
    }

    // Advance to the next song: explicit (primary) adds take priority, then the
    // playlist (secondary) flow. The song just finished is pushed to history.
    private void advanceQueue() {
        // Primary queue first: pull the next playable explicit add off the front.
        while (!primaryQueue.isEmpty() && !primaryQueue.get(0).isPlayable()) {
            Log.w(TAG, "advanceQueue: Skipping unplayable primary song '" + primaryQueue.get(0).getName() + "'");
            primaryQueue.remove(0);
        }
        if (!primaryQueue.isEmpty()) {
            SongModel next = primaryQueue.remove(0);
            lastAdvanceTimeMs = System.currentTimeMillis();
            pushHistory(currentSong);
            Log.i(TAG, "advanceQueue: Last Song: " + (currentSong != null ? currentSong.getName() : "none") + ", Current Song (primary): " + next.getName());
            playSong(next);
            return;
        }

        // Otherwise advance the playlist flow.
        if (secondaryQueue.isEmpty()) return;
        int nextIndex = secondaryIndex + 1;
        while (nextIndex < secondaryQueue.size() && !secondaryQueue.get(nextIndex).isPlayable()) {
            Log.w(TAG, "advanceQueue: Skipping unplayable song '" + secondaryQueue.get(nextIndex).getName() + "' at index " + nextIndex);
            nextIndex++;
        }
        if (nextIndex < secondaryQueue.size()) {
            secondaryIndex = nextIndex;
            SongModel next = secondaryQueue.get(secondaryIndex);
            lastAdvanceTimeMs = System.currentTimeMillis();
            pushHistory(currentSong);
            Log.i(TAG, "advanceQueue: Last Song: " + (currentSong != null ? currentSong.getName() : "none") + ", Current Song: " + next.getName() + ", Queue Position: " + (secondaryIndex + 1) + "/" + secondaryQueue.size());
            playSong(next);
        } else {
            // End of playlist flow.
            pushHistory(currentSong);
            secondaryQueue.clear();
            secondaryIndex = -1;
            queueAdvancePending = false;
        }
    }

    // Record a song as played, for a future previous-song control. Collapses
    // consecutive duplicates so repeated state callbacks don't bloat history.
    private void pushHistory(SongModel song) {
        if (song == null) return;
        if (!history.isEmpty() && history.get(history.size() - 1).getUri().equals(song.getUri())) {
            return;
        }
        history.add(song);
        // Cap the stack so a long session can't grow it without bound.
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    public List<SongModel> getHistory() {
        return history;
    }

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

                // Sync device warning state for new activities
                if (listener instanceof BaseActivity) {
                    ((BaseActivity) listener).setDeviceWarningVisible(isDeviceWarningActive);
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

            listenRunnable = () -> {
                if (isTrackingListen && !hasRecordedListen && !isSnippetMode) {
                    recordListen();
                    hasRecordedListen = true;
                }
            };

            // Register a listen once a third of the song has played. This scales
            // with the track and avoids counting accidental skim-throughs. Fall
            // back to a fixed threshold when the duration isn't known yet.
            long thresholdMs = durationMs > 0 ? durationMs / 3 : LISTEN_THRESHOLD_MS;
            listenHandler.postDelayed(listenRunnable, thresholdMs);
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
    }

    private void recordListen() {
        if (applicationContext != null && !isDeviceWarningActive) {
            DatabaseHelper dbHelper = new DatabaseHelper(applicationContext);
            String songId = currentSong.getId();

            if (dbHelper.hasListenWithinDuration(currentSong.getId(), System.currentTimeMillis(), currentSong.getDuration())) {
                Log.d(TAG, "Recent listen found for song '" + currentSong.getName() + "'. Skipping recording.");
            } else {
                dbHelper.addListenRecord(songId);
                showToast("Recorded listen for song");
                Log.d(TAG, "Recorded listen for song: " + currentSong.getName() + " (" + currentSong.getArtist() + ")");
            }
        }
    }

    //#endregion

    //#region Snippet Playback Methods
    public void playSnippet(SongSnippet snippet) {
        if (!isConnected || spotifyAppRemote == null) {
            showToast("Connecting to Spotify...");
            connectSpotify(applicationContext, () -> playSnippet(snippet));
            return;
        }

        // Cancel any existing snippet timer
        cancelCurrentSnippetTimer();

        // Starting a (different) snippet resets the end-mode to the user's
        // configured default. Replaying the same snippet — e.g. resuming after a
        // pause — preserves the mode the user had selected.
        if (currentSnippet == null || snippet != currentSnippet) {
            snippetEndMode = getDefaultSnippetEndMode(applicationContext);
        }

        isSnippetPlaying = true;
        currentSnippet = snippet;
        setSnippetMode(true);

        // Check if correct song is playing
        if (currentSong == null || !snippet.getSongId().equals(currentSong.getId())) {
            // Need to play the correct song first
            playSongForSnippet(snippet);
        } else {
            // Correct song is already playing, just seek and set timer
            seekToSnippetStart(snippet);
        }
    }

    private void playSongForSnippet(SongSnippet snippet) {
        DatabaseHelper dbHelper = new DatabaseHelper(applicationContext);
        SongModel song = dbHelper.getLeanSong(snippet.getSongId());
        String uri = song != null ? song.getUri() : null;
        if (uri == null) {
            showToast("Song URI not found");
            stopSnippetPlayback();
            return;
        }
        spotifyAppRemote.getPlayerApi().play(uri)
                .setResultCallback(empty -> {
                    // Add delay to ensure song loads
                    new Handler(Looper.getMainLooper()).postDelayed(() ->
                            spotifyAppRemote.getPlayerApi().pause()
                                    .setResultCallback(pauseResult ->
                                            new Handler(Looper.getMainLooper()).postDelayed(() ->
                                                    seekToSnippetStart(snippet), 100)), 100);
                })
                .setErrorCallback(throwable -> {
                    showToast("Error playing song: " + throwable.getMessage());
                    stopSnippetPlayback();
                });
    }

    private void seekToSnippetStart(SongSnippet snippet) {
        spotifyAppRemote.getPlayerApi().seekTo(snippet.getStartTime())
                .setResultCallback(seekResult -> {
                    spotifyAppRemote.getPlayerApi().resume();
                    startSnippetEndTimer(snippet.getEndTime() - snippet.getStartTime());
                });
    }

    private void startSnippetEndTimer(long duration) {
        activeSnippetTimers++;
        snippetTimerRemainingMs = 0;
        snippetTimerDueAtMs = System.currentTimeMillis() + duration;

        snippetEndRunnable = () -> {
            activeSnippetTimers--;

            if (activeSnippetTimers <= 0) {
                activeSnippetTimers = 0;
                handleSnippetEnd();
            }
        };

        snippetHandler.postDelayed(snippetEndRunnable, duration);
    }

    // Hold the end-timer while the song is paused mid-snippet: cancel the pending
    // callback and remember how much of the snippet was left so resuming can pick
    // up from there rather than restarting or firing during the pause.
    private void holdSnippetEndTimer() {
        if (snippetEndRunnable == null || snippetTimerRemainingMs > 0) {
            return; // nothing pending, or already held
        }
        snippetTimerRemainingMs = Math.max(0, snippetTimerDueAtMs - System.currentTimeMillis());
        snippetHandler.removeCallbacks(snippetEndRunnable);
        snippetEndRunnable = null;
        activeSnippetTimers = Math.max(0, activeSnippetTimers - 1);
    }

    // Re-arm the held end-timer for the remaining slice of the snippet.
    private void resumeSnippetEndTimer() {
        if (snippetTimerRemainingMs <= 0) {
            return; // not held
        }
        long remaining = snippetTimerRemainingMs;
        snippetTimerRemainingMs = 0;
        startSnippetEndTimer(remaining);
    }

    // Evaluate the end-behaviour when the snippet's end-timer fires. The mode is
    // the single source of truth, read here at fire-time (ADR 0001).
    private void handleSnippetEnd() {
        SongSnippet snippet = currentSnippet;

        switch (snippetEndMode) {
            case LOOP:
                // Seek back to the snippet start and re-arm a fresh timer.
                // Playback is already running, so seeking keeps it playing.
                if (snippet != null && spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
                    long start = snippet.getStartTime();
                    spotifyAppRemote.getPlayerApi().seekTo(start)
                            .setResultCallback(seekResult -> {
                                // Guard against a stale seek callback: the snippet may have
                                // been stopped or swapped out while the seek was in flight.
                                if (isSnippetMode && currentSnippet == snippet) {
                                    startSnippetEndTimer(snippet.getEndTime() - start);
                                }
                            });
                }
                break;

            case DETACH:
                // Let playback continue into the rest of the song. Clearing
                // snippet mode resumes listen-tracking and queue auto-advance.
                isSnippetPlaying = false;
                currentSnippet = null;
                setSnippetMode(false);
                break;

            case STOP:
            default:
                if (spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
                    spotifyAppRemote.getPlayerApi().pause();
                }
                stopSnippetPlayback();
                break;
        }
    }

    private void cancelCurrentSnippetTimer() {
        if (snippetEndRunnable != null) {
            snippetHandler.removeCallbacks(snippetEndRunnable);
            snippetEndRunnable = null;
        }
        activeSnippetTimers = 0;
        snippetTimerRemainingMs = 0;
        snippetTimerDueAtMs = 0;
    }

    public void stopSnippetPlayback() {
        isSnippetPlaying = false;
        currentSnippet = null;
        setSnippetMode(false);
        cancelCurrentSnippetTimer();
    }

    /**
     * Set the end-behaviour for the snippet currently playing/paused. The timer
     * is NOT cancelled — the mode is just a flag read when the end-timer fires
     * (ADR 0001), so switching modes mid-play stays coherent.
     */
    public void setSnippetEndMode(SnippetEndMode mode) {
        if (mode != null) {
            snippetEndMode = mode;
        }
    }

    public SnippetEndMode getSnippetEndMode() {
        return snippetEndMode;
    }

    /**
     * The user's configured default end-mode for a freshly-started snippet,
     * from TunaroPrefs. Falls back to STOP when unset or unrecognised.
     */
    public static SnippetEndMode getDefaultSnippetEndMode(Context context) {
        if (context == null) return SnippetEndMode.STOP;
        String name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_SNIPPET_DEFAULT_MODE, SnippetEndMode.STOP.name());
        try {
            return SnippetEndMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return SnippetEndMode.STOP;
        }
    }

    /** Advance the end-mode one step: STOP → LOOP → DETACH → STOP. */
    public SnippetEndMode cycleSnippetEndMode() {
        switch (snippetEndMode) {
            case STOP:   snippetEndMode = SnippetEndMode.LOOP;   break;
            case LOOP:   snippetEndMode = SnippetEndMode.DETACH; break;
            case DETACH:
            default:     snippetEndMode = SnippetEndMode.STOP;   break;
        }
        return snippetEndMode;
    }

    /**
     * Pause an in-progress snippet. The pending end timer is cancelled so it
     * can't fire (and stop playback) while paused; tapping play again restarts
     * the snippet from its start.
     */
    public void pauseSnippet() {
        cancelCurrentSnippetTimer();
        if (spotifyAppRemote != null && isConnected) {
            spotifyAppRemote.getPlayerApi().pause()
                    .setResultCallback(empty -> {
                        isPlaying = false;
                        notifyPlaybackStateChanged();
                    });
        }
    }

    public boolean isSnippetPlaying() {
        return isSnippetPlaying;
    }

    public SongSnippet getCurrentSnippet() {
        return currentSnippet;
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

                            // Queue auto-advance: when within 1500ms of the end, wait a bit before advancing so can be closer to the actual finish without cutting it off.
                            boolean inGracePeriod = (System.currentTimeMillis() - lastAdvanceTimeMs) < 3000;
                            if (hasNextSong() && !isSnippetMode && !playerState.isPaused
                                    && durationMs > 0 && !queueAdvancePending && !inGracePeriod
                                    && (durationMs - currentPositionMs) <= 1500) {
                                Log.d(TAG, "updatePlaybackPosition: Caught end of song at " + (durationMs - currentPositionMs) + "ms remaining. Queueing advance.");
                                queueAdvancePending = true;
                                positionHandler.postDelayed(PlaybackManager.this::advanceQueue, 300);
                            }
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

    public boolean isDeviceWarningActive() {
        return isDeviceWarningActive;
    }

    //#endregion

    //#region Setters

    public void setSnippetMode(boolean isSnippetMode) {
        this.isSnippetMode = isSnippetMode;
        if (isSnippetMode) {
            stopListenTracking();
        }
    }

    public void setDeviceWarningState(boolean showWarning) {
        this.isDeviceWarningActive = showWarning;
        // Notify all listeners immediately
        for (PlaybackListener listener : listeners) {
            if (listener instanceof BaseActivity) {
                ((BaseActivity) listener).setDeviceWarningVisible(showWarning);
            }
        }
    }

    //#endregion

    private void showToast(String message) {
        if (applicationContext != null) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show();
            Log.v(TAG, "showed Toast: " + message);
        }
    }

    public void disconnect() {
        stopPositionTracking();
        stopListenTracking();
        cancelCurrentSnippetTimer();
        cancelConnectTimeout();
        connectHandler.removeCallbacksAndMessages(null);
        isConnecting = false;
        connectRetryCount = 0;
        if (spotifyAppRemote != null) {
            SpotifyAppRemote.disconnect(spotifyAppRemote);
            spotifyAppRemote = null;
            isConnected = false;
            notifyConnectionStateChanged();
        }
    }
}