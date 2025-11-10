package com.ca.tunaro.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.ca.tunaro.models.SongModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SongCache {
    //#region Constants and Fields
    private static final String TAG = "SongCache";
    private static final String PREF_NAME = "SongCache";
    private static final String SONGS_KEY = "cached_songs";
    private static final String CACHE_TIMESTAMPS_KEY = "cache_timestamps";
    private static final long CACHE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private final SharedPreferences prefs;
    private final Gson gson;
    //#endregion

    public SongCache(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new GsonBuilder()
                .serializeNulls()
                .create();
    }

    //#region Cache Operations
    public void cacheSongs(List<SongModel> songs) {
        if (songs == null || songs.isEmpty()) {
            Log.w(TAG, "Cannot cache null or empty song list");
            return;
        }

        try {
            // Parse JSON once
            Map<String, SongModel> cachedSongs = getCachedSongsMap();
            Map<String, Long> timestamps = getCacheTimestamps();

            long currentTime = System.currentTimeMillis();
            int addedCount = 0;

            // Add all songs to the maps
            for (SongModel song : songs) {
                if (song != null && song.getId() != null) {
                    cachedSongs.put(song.getId(), song);
                    timestamps.put(song.getId(), currentTime);
                    addedCount++;
                }
            }

            // Write JSON once
            saveCachedSongs(cachedSongs);
            saveCacheTimestamps(timestamps);

            Log.d(TAG, "Batch cached " + addedCount + " songs");
        } catch (Exception e) {
            Log.e(TAG, "Error batch caching songs", e);
        }
    }

    public Map<String, SongModel> getCachedSongsMap(List<String> songIds) {
        if (songIds == null || songIds.isEmpty()) return new HashMap<>();

        try {
            Map<String, SongModel> cachedSongsMap = getCachedSongsMap();
            Map<String, Long> timestamps = getCacheTimestamps();
            Map<String, SongModel> result = new HashMap<>();

            for (String songId : songIds) {
                Long cacheTime = timestamps.get(songId);

                // Check if cache entry exists and is not expired
                if (cacheTime != null && !isExpired(cacheTime)) {
                    SongModel song = cachedSongsMap.get(songId);
                    if (song != null) {
                        result.put(songId, song);
                    }
                }
            }

            Log.d(TAG, "Batch retrieved " + result.size() + " songs from cache");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error batch retrieving cached songs", e);
            return new HashMap<>();
        }
    }

    public SongModel getCachedSong(String songId) {
        if (songId == null) return null;

        try {
            Map<String, Long> timestamps = getCacheTimestamps();
            Long cacheTime = timestamps.get(songId);

            // Check if cache entry exists and is not expired
            if (cacheTime == null || isExpired(cacheTime)) {
                if (cacheTime != null) {
                    Log.d(TAG, "Cache entry expired for song: " + songId);
                    removeCachedSong(songId);
                }
                return null;
            }

            Map<String, SongModel> cachedSongs = getCachedSongsMap();
            SongModel song = cachedSongs.get(songId);

            if (song != null) {
                Log.d(TAG, "Cache hit for song: " + songId);
            }

            return song;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving cached song: " + songId, e);
            return null;
        }
    }

    public void removeCachedSong(String songId) {
        if (songId == null) return;

        try {
            Map<String, SongModel> cachedSongs = getCachedSongsMap();
            Map<String, Long> timestamps = getCacheTimestamps();

            cachedSongs.remove(songId);
            timestamps.remove(songId);

            saveCachedSongs(cachedSongs);
            saveCacheTimestamps(timestamps);

            Log.d(TAG, "Removed cached song: " + songId);
        } catch (Exception e) {
            Log.e(TAG, "Error removing cached song: " + songId, e);
        }
    }

    public void clearExpiredEntries() {
        try {
            Map<String, SongModel> cachedSongs = getCachedSongsMap();
            Map<String, Long> timestamps = getCacheTimestamps();

            int removedCount = 0;
            for (String songId : timestamps.keySet().toArray(new String[0])) {
                Long cacheTime = timestamps.get(songId);
                if (cacheTime != null && isExpired(cacheTime)) {
                    cachedSongs.remove(songId);
                    timestamps.remove(songId);
                    removedCount++;
                }
            }

            if (removedCount > 0) {
                saveCachedSongs(cachedSongs);
                saveCacheTimestamps(timestamps);
                Log.d(TAG, "Cleared " + removedCount + " expired cache entries");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing expired entries", e);
        }
    }

    public void clearCache() {
        Log.d(TAG, "Clearing entire song cache");
        prefs.edit().clear().apply();
    }
    //#endregion

    //#region Private Helper Methods
    private Map<String, SongModel> getCachedSongsMap() {
        try {
            String songsJson = prefs.getString(SONGS_KEY, null);
            if (songsJson == null) {
                return new HashMap<>();
            }

            Type mapType = new TypeToken<Map<String, SongModel>>(){}.getType();
            Map<String, SongModel> songs = gson.fromJson(songsJson, mapType);
            return songs != null ? songs : new HashMap<>();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing cached songs", e);
            return new HashMap<>();
        }
    }

    private Map<String, Long> getCacheTimestamps() {
        try {
            String timestampsJson = prefs.getString(CACHE_TIMESTAMPS_KEY, null);
            if (timestampsJson == null) {
                return new HashMap<>();
            }

            Type mapType = new TypeToken<Map<String, Long>>(){}.getType();
            Map<String, Long> timestamps = gson.fromJson(timestampsJson, mapType);
            return timestamps != null ? timestamps : new HashMap<>();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing cache timestamps", e);
            return new HashMap<>();
        }
    }

    private void saveCachedSongs(Map<String, SongModel> songs) {
        try {
            String songsJson = gson.toJson(songs);
            prefs.edit().putString(SONGS_KEY, songsJson).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving cached songs", e);
        }
    }

    private void saveCacheTimestamps(Map<String, Long> timestamps) {
        try {
            String timestampsJson = gson.toJson(timestamps);
            prefs.edit().putString(CACHE_TIMESTAMPS_KEY, timestampsJson).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving cache timestamps", e);
        }
    }

    private boolean isExpired(long cacheTime) {
        return System.currentTimeMillis() - cacheTime > CACHE_EXPIRY_MS;
    }
    //#endregion

    //#region Cache Statistics
    public int getCacheSize() {
        return getCachedSongsMap().size();
    }

    public long getCacheMemoryUsage() {
        try {
            String songsJson = prefs.getString(SONGS_KEY, "");
            String timestampsJson = prefs.getString(CACHE_TIMESTAMPS_KEY, "");
            return songsJson.length() + timestampsJson.length();
        } catch (Exception e) {
            return 0;
        }
    }
    //#endregion
}