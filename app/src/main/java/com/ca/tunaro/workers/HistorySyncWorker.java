package com.ca.tunaro.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.ca.tunaro.activites.MainActivity;
import com.ca.tunaro.utils.SpotifyHistoryFetcher;

public class HistorySyncWorker extends Worker {
    private static final String TAG = "HistorySyncWorker";

    public HistorySyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
                Log.w(TAG, "MainActivity or SpotifyApi not available for background sync");
                return Result.retry();
            }

            SpotifyHistoryFetcher fetcher = new SpotifyHistoryFetcher(getApplicationContext(), mainActivity.getSpotifyApi());

            // Always sync from last cursor in background
            fetcher.fetchHistory(new SpotifyHistoryFetcher.ProgressCallback() {
                @Override
                public void onProgress(int processed, int added) {
                    Log.d(TAG, "Background sync progress: Processed " + processed + " / Added " + added);
                }

                @Override
                public void onComplete(int totalProcessed, int totalAdded) {
                    Log.i(TAG, "Background sync complete: " + totalAdded + " new listens added");
                    // Save stats for settings display
                    com.ca.tunaro.activites.SettingsActivity.updateLastSyncStats(getApplicationContext(), totalProcessed, totalAdded);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Background sync error: " + error);
                }
            }).join();

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Background sync failed", e);
            return Result.retry();
        }
    }
}