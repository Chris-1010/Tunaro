package com.ca.tunaro.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.ca.tunaro.models.SongModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide song metadata cache. The SharedPreferences blob is parsed once into memory; reads are
 * served from the in-memory maps and writes are flushed back on the I/O pool, debounced so a burst of
 * cacheSongs calls costs a single serialisation.
 *
 * Returned SongModels are copies, so callers may mutate playlist-context fields (date added) without
 * leaking them into other playlists.
 */
public final class SongCache {
    //#region Constants and Fields
    private static final String TAG = "SongCache";
    private static final String PREF_NAME = "SongCache";
    private static final String SONGS_KEY = "cached_songs";
    private static final String CACHE_TIMESTAMPS_KEY = "cache_timestamps";
    static final long CACHE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days
    private static final long WRITE_DEBOUNCE_MS = 1000L;

    private static volatile SongCache instance;

    private final SharedPreferences prefs;
    private final Gson gson;
    private final Map<String, SongModel> songs = new ConcurrentHashMap<>();
    private final Map<String, Long> timestamps = new ConcurrentHashMap<>();
    private final Object loadLock = new Object();
    private final Object writeLock = new Object();
    private final AtomicBoolean writeScheduled = new AtomicBoolean(false);
    private volatile boolean loaded = false;
    //#endregion

    public static SongCache getInstance(Context context) {
        SongCache local = instance;
        if (local == null) {
            synchronized (SongCache.class) {
                local = instance;
                if (local == null) {
                    local = new SongCache(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    private SongCache(Context appContext) {
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new GsonBuilder()
                .serializeNulls()
                .create();
    }

    //#region Cache Operations

    /** Parses the persisted cache on the I/O pool so the first blocking read finds it ready. */
    public void preloadAsync() {
        if (!loaded) AppExecutors.io().execute(this::ensureLoaded);
    }

    public void cacheSongs(List<SongModel> newSongs) {
        if (newSongs == null || newSongs.isEmpty()) {
            Log.w(TAG, "Cannot cache null or empty song list");
            return;
        }
        ensureLoaded();
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (SongModel song : newSongs) {
            if (song != null && song.getId() != null) {
                songs.put(song.getId(), new SongModel(song));
                timestamps.put(song.getId(), now);
                changed = true;
            }
        }
        if (changed) scheduleWrite();
    }

    /** Fresh (unexpired) cached songs for {@code songIds}, keyed by ID. Blocks until the cache is loaded. */
    public Map<String, SongModel> getCachedSongsMap(List<String> songIds) {
        if (songIds == null || songIds.isEmpty()) return new HashMap<>();
        ensureLoaded();
        Map<String, SongModel> result = selectFresh(songs, timestamps, songIds, System.currentTimeMillis());
        for (Map.Entry<String, SongModel> entry : result.entrySet()) {
            entry.setValue(new SongModel(entry.getValue()));
        }
        Log.d(TAG, "Batch retrieved " + result.size() + " songs from cache");
        return result;
    }

    /** Fresh cached song, or null. Blocks until the cache is loaded. */
    public SongModel getCachedSong(String songId) {
        if (songId == null) return null;
        ensureLoaded();
        return freshCopy(songId);
    }

    /**
     * Non-blocking variant of {@link #getCachedSong}: returns null (and starts loading) when the
     * persisted cache has not been parsed yet. Safe to call on the main thread.
     */
    public SongModel peekCachedSong(String songId) {
        if (songId == null) return null;
        if (!loaded) {
            preloadAsync();
            return null;
        }
        return freshCopy(songId);
    }

    public void removeCachedSong(String songId) {
        if (songId == null) return;
        ensureLoaded();
        boolean removed = songs.remove(songId) != null;
        removed |= timestamps.remove(songId) != null;
        if (removed) scheduleWrite();
    }

    public void clearExpiredEntries() {
        ensureLoaded();
        int removedCount = pruneExpired(songs, timestamps, System.currentTimeMillis());
        if (removedCount > 0) {
            scheduleWrite();
            Log.d(TAG, "Cleared " + removedCount + " expired cache entries");
        }
    }

    public void clearCache() {
        Log.d(TAG, "Clearing entire song cache");
        synchronized (loadLock) {
            songs.clear();
            timestamps.clear();
            loaded = true;
        }
        synchronized (writeLock) {
            prefs.edit().clear().apply();
        }
    }
    //#endregion

    //#region Private Helper Methods
    private SongModel freshCopy(String songId) {
        Long cacheTime = timestamps.get(songId);
        if (cacheTime == null) return null;
        if (isExpired(cacheTime, System.currentTimeMillis())) return null;
        SongModel song = songs.get(songId);
        return song != null ? new SongModel(song) : null;
    }

    private void ensureLoaded() {
        if (loaded) return;
        synchronized (loadLock) {
            if (loaded) return;
            Map<String, SongModel> storedSongs = parse(SONGS_KEY,
                    new TypeToken<Map<String, SongModel>>(){}.getType());
            Map<String, Long> storedTimestamps = parse(CACHE_TIMESTAMPS_KEY,
                    new TypeToken<Map<String, Long>>(){}.getType());
            // putIfAbsent: anything cached in memory meanwhile is newer than the stored copy.
            for (Map.Entry<String, SongModel> e : storedSongs.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) songs.putIfAbsent(e.getKey(), e.getValue());
            }
            for (Map.Entry<String, Long> e : storedTimestamps.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) timestamps.putIfAbsent(e.getKey(), e.getValue());
            }
            loaded = true;
            Log.d(TAG, "Loaded " + songs.size() + " cached songs");
        }
    }

    private <T> Map<String, T> parse(String key, Type mapType) {
        try {
            String json = prefs.getString(key, null);
            if (json == null) return new HashMap<>();
            Map<String, T> parsed = gson.fromJson(json, mapType);
            return parsed != null ? parsed : new HashMap<>();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing " + key, e);
            return new HashMap<>();
        }
    }

    private void scheduleWrite() {
        if (writeScheduled.compareAndSet(false, true)) {
            AppExecutors.ioDelayed(this::flush, WRITE_DEBOUNCE_MS);
        }
    }

    private void flush() {
        // Reset before snapshotting so a change racing the snapshot schedules another flush.
        writeScheduled.set(false);
        synchronized (writeLock) {
            try {
                String songsJson = gson.toJson(new HashMap<>(songs));
                String timestampsJson = gson.toJson(new HashMap<>(timestamps));
                prefs.edit()
                        .putString(SONGS_KEY, songsJson)
                        .putString(CACHE_TIMESTAMPS_KEY, timestampsJson)
                        .apply();
            } catch (Exception e) {
                Log.e(TAG, "Error saving song cache", e);
            }
        }
    }
    //#endregion

    //#region Pure logic (unit-tested)
    static boolean isExpired(long cacheTime, long now) {
        return now - cacheTime > CACHE_EXPIRY_MS;
    }

    /** Entries of {@code ids} present in both maps and not expired at {@code now}. */
    static Map<String, SongModel> selectFresh(Map<String, SongModel> songs, Map<String, Long> timestamps,
                                              List<String> ids, long now) {
        Map<String, SongModel> result = new HashMap<>();
        for (String id : ids) {
            if (id == null) continue;
            Long cacheTime = timestamps.get(id);
            if (cacheTime == null || isExpired(cacheTime, now)) continue;
            SongModel song = songs.get(id);
            if (song != null) result.put(id, song);
        }
        return result;
    }

    /** Removes expired entries (and orphaned songs with no timestamp); returns how many were dropped. */
    static int pruneExpired(Map<String, SongModel> songs, Map<String, Long> timestamps, long now) {
        int removed = 0;
        for (String id : new ArrayList<>(timestamps.keySet())) {
            Long cacheTime = timestamps.get(id);
            if (cacheTime != null && isExpired(cacheTime, now)) {
                timestamps.remove(id);
                songs.remove(id);
                removed++;
            }
        }
        for (String id : new ArrayList<>(songs.keySet())) {
            if (!timestamps.containsKey(id)) {
                songs.remove(id);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Songs for {@code orderedIds} in order, taken from {@code found}; IDs with no entry are appended
     * to {@code missingOut}.
     */
    public static ArrayList<SongModel> orderedHits(List<String> orderedIds, Map<String, SongModel> found,
                                                   List<String> missingOut) {
        ArrayList<SongModel> hits = new ArrayList<>();
        for (String id : orderedIds) {
            SongModel song = found.get(id);
            if (song != null) {
                hits.add(song);
            } else {
                missingOut.add(id);
            }
        }
        return hits;
    }
    //#endregion

    //#region Cache Statistics
    public int getCacheSize() {
        ensureLoaded();
        return songs.size();
    }
    //#endregion
}
