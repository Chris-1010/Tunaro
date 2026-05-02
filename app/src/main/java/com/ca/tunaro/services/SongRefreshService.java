package com.ca.tunaro.services;

import android.content.Context;
import android.util.Log;

import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.SongModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.Track;

public class SongRefreshService {
    private static final String TAG = "SongRefreshService";
    private static final int BATCH_SIZE = 50;

    private final Context context;
    private final SpotifyApi spotifyApi;

    public SongRefreshService(Context context, SpotifyApi spotifyApi) {
        this.context = context;
        this.spotifyApi = spotifyApi;
    }

    public CompletableFuture<Void> refreshStaleSongs() {
        return CompletableFuture.runAsync(() -> {
            try {
                DatabaseHelper dbHelper = new DatabaseHelper(context);
                Map<String, String> songsToRefresh = dbHelper.getSongsNeedingRefresh();

                if (songsToRefresh.isEmpty()) {
                    Log.d(TAG, "No songs need refreshing");
                    return;
                }

                Log.d(TAG, "Refreshing " + songsToRefresh.size() + " stale songs");

                // Build parallel lists to preserve songId↔trackId correspondence
                List<String> songIds = new ArrayList<>(songsToRefresh.keySet());
                List<String> spotifyTrackIds = new ArrayList<>(songIds.size());
                for (String songId : songIds) {
                    String uri = songsToRefresh.get(songId);
                    if (uri == null || !uri.startsWith(SongModel.SPOTIFY_TRACK_URI_PREFIX)) {
                        spotifyTrackIds.add(null);
                        continue;
                    }
                    spotifyTrackIds.add(uri.substring(SongModel.SPOTIFY_TRACK_URI_PREFIX.length()));
                }

                int refreshed = 0;
                for (int i = 0; i < songIds.size(); i += BATCH_SIZE) {
                    int end = Math.min(i + BATCH_SIZE, songIds.size());
                    List<String> batchSongIds = songIds.subList(i, end);
                    List<String> batchTrackIds = spotifyTrackIds.subList(i, end);

                    // Collect valid track IDs for this batch
                    List<Integer> validIndices = new ArrayList<>();
                    List<String> validTrackIds = new ArrayList<>();
                    for (int j = 0; j < batchTrackIds.size(); j++) {
                        if (batchTrackIds.get(j) != null) {
                            validIndices.add(j);
                            validTrackIds.add(batchTrackIds.get(j));
                        }
                    }

                    if (validTrackIds.isEmpty()) continue;

                    try {
                        Track[] tracks = spotifyApi
                                .getSeveralTracks(String.join(",", validTrackIds))
                                .setQueryParameter("market", "from_token")
                                .build()
                                .execute();

                        for (int j = 0; j < tracks.length; j++) {
                            Track track = tracks[j];
                            if (track == null) continue;

                            String songId = batchSongIds.get(validIndices.get(j));
                            Boolean isPlayable = track.getIsPlayable();
                            dbHelper.refreshSong(songId, track.getPopularity(),
                                    isPlayable == null || isPlayable);
                            refreshed++;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Batch refresh failed for batch starting at " + i, e);
                    }
                }

                Log.d(TAG, "Refreshed " + refreshed + " songs");
            } catch (Exception e) {
                Log.e(TAG, "Song refresh failed", e);
            }
        });
    }
}
