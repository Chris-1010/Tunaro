package com.ca.tunaro.utils;

import android.content.Context;
import android.util.Log;

import com.ca.tunaro.database.DatabaseHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.PagingCursorbased;
import se.michaelthelin.spotify.model_objects.specification.PlayHistory;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.data.player.GetCurrentUsersRecentlyPlayedTracksRequest;

public class SpotifyHistoryFetcher {
    private static final String TAG = "SpotifyHistoryFetcher";
    private static final int ITEMS_PER_REQUEST = 50;

    private final SpotifyApi spotifyApi;
    private final DatabaseHelper dbHelper;
    private final SimpleDateFormat utcFormat;

    //#region Progress Tracking
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalAdded = new AtomicInteger(0);
    private volatile boolean isCancelled = false;
    //#endregion

    public interface ProgressCallback {
        void onProgress(int processed, int added);

        void onComplete(int totalProcessed, int totalAdded);

        void onError(String error);
    }

    public SpotifyHistoryFetcher(Context context, SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
        this.dbHelper = new DatabaseHelper(context);
        this.utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        this.utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public CompletableFuture<Void> fetchHistory(ProgressCallback callback) {
        return CompletableFuture.runAsync(() -> {
            try {
                resetCounters();

                // Start from current time and go backwards
                String currentTime = String.valueOf(System.currentTimeMillis());
                Log.d(TAG, "Fetching history from present going backwards");

                fetchHistoryRecursive(currentTime, callback);

                callback.onComplete(totalProcessed.get(), totalAdded.get());

            } catch (Exception e) {
                Log.e(TAG, "Error fetching history", e);
                callback.onError("Failed to fetch history: " + e.getMessage());
            }
        });
    }

    private void fetchHistoryRecursive(String before, ProgressCallback callback) {
        if (isCancelled) {
            Log.d(TAG, "Fetch cancelled by user");
            return;
        }

        try {
            GetCurrentUsersRecentlyPlayedTracksRequest.Builder requestBuilder =
                    spotifyApi.getCurrentUsersRecentlyPlayedTracks()
                            .limit(ITEMS_PER_REQUEST);

            if (before != null) {
                Date beforeDate = new Date(Long.parseLong(before));
                requestBuilder.before(beforeDate); // Go backwards from this timestamp
            }

            GetCurrentUsersRecentlyPlayedTracksRequest request = requestBuilder.build();

            PagingCursorbased<PlayHistory> response = request.execute();
            PlayHistory[] items = response.getItems();

            if (items == null || items.length == 0) {
                Log.d(TAG, "No more items to fetch - reached end of available history");
                return;
            }

            Log.d(TAG, "Processing " + items.length + " items");

            int batchAdded = processHistoryItems(items);
            totalProcessed.addAndGet(items.length);
            totalAdded.addAndGet(batchAdded);

            // Update progress callback
            callback.onProgress(totalProcessed.get(), totalAdded.get());

            // Continue fetching if there's more
            String nextBefore = null;
            try {
                // Extract before parameter from URL like:
                // https://api.spotify.com/v1/me/player/recently-played?before=1757775505838&limit=50
                String[] parts = response.getNext().split("before=");
                if (parts.length > 1) {
                    String beforePart = parts[1];
                    String[] beforeValue = beforePart.split("&");
                    nextBefore = beforeValue[0];
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to extract before parameter from URL: " + response.getNext(), e);
            }

            if (nextBefore != null && !nextBefore.equals(before) && !isCancelled) {
                fetchHistoryRecursive(nextBefore, callback);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in recursive fetch", e);
            throw new RuntimeException(e);
        }
    }

    private int processHistoryItems(PlayHistory[] items) {
        int addedCount = 0;

        for (PlayHistory item : items) {
            if (isCancelled) break;

            Track track = item.getTrack();
            if (track == null) continue;

            String songId = track.getId();
            long playedAt = item.getPlayedAt().getTime();
            int durationMs = track.getDurationMs();

            // Check for duplicates within song duration window
            if (dbHelper.hasListenWithinDuration(songId, playedAt, durationMs)) {
                Log.d(TAG, "Recent listen found for '" + track.getName() + "'. Skipping recording.");
            } else {
                String timestamp = utcFormat.format(new Date(playedAt));
                dbHelper.addListenRecordWithTimestamp(songId, timestamp);
                addedCount++;

                Log.d(TAG, "Added listen for: " + track.getName() + " at " + timestamp);
            }
        }

        return addedCount;
    }

    /**
     * Resets progress counters and cancellation flag
     **/
    private void resetCounters() {
        totalProcessed.set(0);
        totalAdded.set(0);
        isCancelled = false;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void cancel() {
        isCancelled = true;
    }
}