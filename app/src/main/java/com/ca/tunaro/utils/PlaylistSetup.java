package com.ca.tunaro.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.ca.tunaro.activites.MainActivity;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.models.SongModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.data.playlists.GetListOfCurrentUsersPlaylistsRequest;
import se.michaelthelin.spotify.requests.data.playlists.GetPlaylistsItemsRequest;

public class PlaylistSetup {
    private static PlaylistCache playlistCache;
    private static SongCache songCache;
    private static Context appContext;
    private static final int MAX_BATCH_SIZE = 50;    // Spotify API maximum limit per request
    private static final Object lock = new Object();
    private static final AtomicBoolean scanInProgress = new AtomicBoolean(false);
    // Playlist track counts, kept from the last DB read so opening a playlist does not
    // re-read the whole table. Refreshed on every scan.
    private static volatile Map<String, DatabaseHelper.PlaylistScanInfo> trackCountSnapshot;

    /** Throttle key and interval shared by the launch scan and the Playlists screen rescan. */
    public static final String PREF_LAST_PLAYLIST_SCAN = "last_launch_playlist_scan_ms";
    public static final long PLAYLIST_SCAN_INTERVAL_MS = 6L * 60L * 60L * 1000L;

    public static class SongLoadResult {
        public final ArrayList<SongModel> songs;
        public final boolean needsCaching;

        public SongLoadResult(ArrayList<SongModel> songs, boolean needsCaching) {
            this.songs = songs;
            this.needsCaching = needsCaching;
        }
    }

    public interface SongLoadProgressListener {
        void onSongsLoaded(ArrayList<SongModel> loadedSongs, int currentCount, int totalCount);
    }

    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
        playlistCache = new PlaylistCache(appContext);
        songCache = SongCache.getInstance(appContext);

        // Parse the song cache and prune expired entries off the main thread, so the
        // first playlist open reads straight from memory.
        SongCache cache = songCache;
        AppExecutors.io().execute(cache::clearExpiredEntries);
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable ex) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    // Proactively refreshes the access token if it's near expiry and applies it to the given
    // SpotifyApi, so the (partly synchronous) request chains below don't fail with an expired
    // token. Returns a future that completes once the token is ready; failures are swallowed so
    // the work still proceeds with the existing token.
    private static CompletableFuture<Void> ensureFreshToken(SpotifyApi spotifyApi) {
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null || spotifyApi == null) {
            return CompletableFuture.completedFuture(null);
        }
        return mainActivity.getValidAccessToken()
                .thenAccept(token -> {
                    if (token != null) spotifyApi.setAccessToken(token);
                })
                .exceptionally(e -> {
                    Log.w("PlaylistSetup", "Could not ensure fresh token, proceeding with existing", e);
                    return null;
                });
    }

    /**
     * The last cached playlist list, read off the main thread. Completes with null when nothing
     * is cached yet. Callers show this immediately and then revalidate with
     * {@link #refreshPlaylists}.
     */
    public static CompletableFuture<ArrayList<PlaylistModel>> getCachedPlaylists() {
        return CompletableFuture.supplyAsync(() -> playlistCache.getCachedPlaylists(), AppExecutors.io());
    }

    /** Fetch every playlist from Spotify and write it to the DB and the playlist cache. */
    private static CompletableFuture<ArrayList<PlaylistModel>> fetchAndCachePlaylists(SpotifyApi spotifyApi) {
        return ensureFreshToken(spotifyApi)
                .thenCompose(ignored -> getAllPlaylists(spotifyApi, 0, new ArrayList<>()))
                .thenApply(playlists -> {
                    synchronized (lock) {
                        playlistCache.cachePlaylists(playlists);
                    }
                    // Remote totals just changed in the DB; reload them on next use.
                    trackCountSnapshot = null;
                    return playlists;
                });
    }

    private static CompletableFuture<ArrayList<PlaylistModel>> getAllPlaylists(
            SpotifyApi spotifyApi, int offset, ArrayList<PlaylistModel> accumulatedPlaylists) {

        final GetListOfCurrentUsersPlaylistsRequest request = spotifyApi
                .getListOfCurrentUsersPlaylists()
                .limit(MAX_BATCH_SIZE)
                .offset(offset)
                .build();

        Log.d("PlaylistSetup", "API: getListOfCurrentUsersPlaylists offset=" + offset);
        return request.executeAsync()
                .thenCompose(playlistSimplifiedPaging -> {
                    try {
                        if (playlistSimplifiedPaging != null) {
                            DatabaseHelper dbHelper = appContext != null ? DatabaseHelper.getInstance(appContext) : null;
                            for (PlaylistSimplified playlist : playlistSimplifiedPaging.getItems()) {
                                if (playlist.getTracks().getTotal() > 0) {
                                    Image[] images = playlist.getImages();
                                    String imageUrl = images != null && images.length > 0 ? images[0].getUrl() : null;
                                    String owner = playlist.getOwner() != null ? playlist.getOwner().getId() : null;

                                    accumulatedPlaylists.add(new PlaylistModel(
                                            playlist.getId(),
                                            playlist.getName(),
                                            playlist.getTracks().getTotal(),
                                            images,
                                            new ArrayList<>()
                                    ));

                                    if (dbHelper != null) {
                                        dbHelper.upsertPlaylist(
                                                playlist.getId(),
                                                playlist.getName(),
                                                null,
                                                imageUrl,
                                                playlist.getTracks().getTotal(),
                                                owner,
                                                playlist.getSnapshotId()
                                        );
                                    }
                                }
                            }
                            if (playlistSimplifiedPaging.getNext() != null) {
                                return getAllPlaylists(spotifyApi, offset + MAX_BATCH_SIZE, accumulatedPlaylists);
                            }
                        }
                        return CompletableFuture.completedFuture(accumulatedPlaylists);
                    } catch (Exception e) {
                        return failedFuture(e);
                    }
                });
    }

    public static CompletableFuture<SongLoadResult> getPlaylistSongs(
            String playlistId, SpotifyApi spotifyApi, SongLoadProgressListener progressListener) {

        // Read and emit the cache first; the token is only awaited once the network is needed.
        // The io task returns the rest of the work as a future instead of joining on it, so a
        // slow token refresh or network fetch never pins a thread of the shared io pool.
        return CompletableFuture.supplyAsync(() -> {
            List<String> cachedSongIds = playlistCache.getCachedPlaylistSongIds(playlistId);
            if (cachedSongIds != null && !cachedSongIds.isEmpty()
                    && isCachedSongListComplete(playlistId, cachedSongIds.size())) {
                Map<String, SongModel> cachedSongsMap = songCache.getCachedSongsMap(cachedSongIds);

                List<String> missingSongIds = new ArrayList<>();
                ArrayList<SongModel> cachedSongs = SongCache.orderedHits(cachedSongIds, cachedSongsMap, missingSongIds);

                // Cache misses (expired or never cached) usually still live in the songs table:
                // read them in one batch before resorting to the network, and re-cache the hits.
                if (!missingSongIds.isEmpty() && appContext != null) {
                    try {
                        Map<String, SongModel> dbSongs = DatabaseHelper.getInstance(appContext)
                                .getFullSongs(missingSongIds);
                        if (!dbSongs.isEmpty()) {
                            songCache.cacheSongs(new ArrayList<>(dbSongs.values()));
                            cachedSongsMap.putAll(dbSongs);
                            missingSongIds.clear();
                            cachedSongs = SongCache.orderedHits(cachedSongIds, cachedSongsMap, missingSongIds);
                            Log.d("PlaylistSetup", "Recovered " + dbSongs.size() + " songs from DB");
                        }
                    } catch (Exception e) {
                        Log.e("PlaylistSetup", "DB fallback for cache misses failed", e);
                    }
                }

                // Report cached songs immediately
                if (!cachedSongs.isEmpty() && progressListener != null) {
                    progressListener.onSongsLoaded(new ArrayList<>(cachedSongs),
                            cachedSongs.size(), cachedSongIds.size());
                }

                // Back-fill dateAddedToPlaylist from DB — the cached SongModel may carry
                // a stale date from a different playlist's scan.
                if (appContext != null) {
                    DatabaseHelper dbHelper = DatabaseHelper.getInstance(appContext);
                    Map<String, java.util.Date> addedAtMap = dbHelper.getAddedAtMapForPlaylist(playlistId);
                    for (SongModel song : cachedSongs) {
                        java.util.Date addedAt = addedAtMap.get(song.getId());
                        if (addedAt != null) song.setDateAddedToPlaylist(addedAt);
                    }
                }

                // If all songs cached, return with needsCaching=false
                if (missingSongIds.isEmpty()) {
                    Log.d("PlaylistSetup", "All " + cachedSongs.size() + " songs found in cache");
                    ensurePlaylistLinksInDb(cachedSongIds, playlistId);
                    return CompletableFuture.completedFuture(new SongLoadResult(cachedSongs, false));
                }

                // Fetch missing songs - needs caching
                final ArrayList<SongModel> cachedResult = cachedSongs;
                final int expectedTotal = cachedSongs.size() + missingSongIds.size();
                final int totalExpected = cachedSongIds.size();
                return ensureFreshToken(spotifyApi)
                        .thenCompose(ignored -> fetchMissingSongs(missingSongIds, spotifyApi, cachedResult,
                                progressListener, totalExpected))
                        .handle((allSongs, e) -> {
                            if (e != null) {
                                Log.e("PlaylistSetup", "Error fetching missing songs", e);
                                return new SongLoadResult(cachedResult, false);
                            }
                            // A partial recovery must not be written back to the cache: doing so drops the
                            // unrecovered IDs permanently and shrinks the playlist a little on every launch.
                            boolean complete = allSongs.size() == expectedTotal;
                            if (!complete) {
                                Log.w("PlaylistSetup", "Recovered only " + allSongs.size() + " of " + expectedTotal
                                        + " songs for playlist " + playlistId + " — leaving cache untouched");
                            }
                            return new SongLoadResult(allSongs, complete);
                        });
            }

            // Not in cache - fetch from API, needs caching
            return ensureFreshToken(spotifyApi)
                    .thenCompose(ignored -> getAllSongs(playlistId, spotifyApi, 0, new ArrayList<>(),
                            progressListener, -1))
                    .handle((songs, e) -> {
                        if (e != null) {
                            Log.e("PlaylistSetup", "Error getting songs", e);
                            return new SongLoadResult(new ArrayList<>(), false);
                        }
                        return new SongLoadResult(songs, true);
                    });
        }, AppExecutors.io()).thenCompose(next -> next);
    }

    private static CompletableFuture<ArrayList<SongModel>> getAllSongs(
            String playlistId,
            SpotifyApi spotifyApi,
            int offset,
            ArrayList<SongModel> accumulatedSongs,
            SongLoadProgressListener progressListener, int totalCount) {

        final GetPlaylistsItemsRequest getPlaylistsItemsRequest = spotifyApi
                .getPlaylistsItems(playlistId)
                .setQueryParameter("market", "from_token")
                .offset(offset)
                .limit(MAX_BATCH_SIZE)
                .build();

        return getPlaylistsItemsRequest.executeAsync()
                .thenCompose(playlistTrackPaging -> {
                    try {
                        DatabaseHelper dbHelper = appContext != null ? DatabaseHelper.getInstance(appContext) : null;
                        List<String> batchSongIds = new ArrayList<>();

                        Runnable processPage = () -> {
                            for (PlaylistTrack playlistTrack : playlistTrackPaging.getItems()) {
                                if (playlistTrack.getTrack() instanceof Track) {
                                    SongModel songModel = getSongModel(playlistTrack);
                                    accumulatedSongs.add(songModel);
                                    batchSongIds.add(songModel.getId());

                                    if (dbHelper != null) {
                                        upsertTrackToDb(dbHelper, (Track) playlistTrack.getTrack(), songModel);
                                        String addedAtStr = formatDate(playlistTrack.getAddedAt());
                                        dbHelper.upsertSongPlaylistLink(songModel.getId(), playlistId, addedAtStr);
                                    }
                                }
                            }
                        };
                        // One transaction per page instead of one per statement.
                        if (dbHelper != null) dbHelper.runInTransaction(processPage);
                        else processPage.run();

                        // Reconcile removed songs once per batch (only on last page)
                        int total = totalCount == -1 ? playlistTrackPaging.getTotal() : totalCount;

                        // Report progress for this batch
                        if (progressListener != null) {
                            progressListener.onSongsLoaded(new ArrayList<>(accumulatedSongs),
                                    accumulatedSongs.size(), total);
                        }

                        // Continue loading if there are more songs
                        if (playlistTrackPaging.getNext() != null) {
                            return getAllSongs(playlistId, spotifyApi, offset + MAX_BATCH_SIZE,
                                    accumulatedSongs, progressListener, total);
                        }

                        // All pages fetched — reconcile removals
                        if (dbHelper != null) {
                            List<String> allSongIds = new ArrayList<>();
                            for (SongModel s : accumulatedSongs) allSongIds.add(s.getId());
                            dbHelper.reconcilePlaylistSongs(playlistId, allSongIds);
                        }

                        return CompletableFuture.completedFuture(accumulatedSongs);
                    } catch (Exception e) {
                        return failedFuture(e);
                    }
                });
    }

    public static void cacheSongsInBackground(String playlistId, ArrayList<SongModel> songs) {
        Log.d("PlaylistSetup", "Starting background cache for " + songs.size() + " songs");
        int remoteTotal = remoteTotalFor(playlistId);

        synchronized (lock) {
            // SongCache: Cache all songs
            songCache.cacheSongs(songs);

            // PlaylistCache: Update with song IDs
            List<String> songIds = new ArrayList<>();
            for (SongModel song : songs) {
                songIds.add(song.getId());
            }
            playlistCache.updatePlaylistSongs(playlistId, songIds, remoteTotal);
        }

        Log.d("PlaylistSetup", "Background cache completed for playlist " + playlistId);
    }

    private static CompletableFuture<ArrayList<SongModel>> fetchMissingSongs(
            List<String> missingSongIds, SpotifyApi spotifyApi, ArrayList<SongModel> existingSongs,
            SongLoadProgressListener progressListener, int totalExpected) {

        return CompletableFuture.supplyAsync(() -> {
            ArrayList<SongModel> allSongs = new ArrayList<>(existingSongs);

            try {
                DatabaseHelper dbHelper = appContext != null ? DatabaseHelper.getInstance(appContext) : null;

                for (int i = 0; i < missingSongIds.size(); i += MAX_BATCH_SIZE) {
                    List<String> batchIds = missingSongIds.subList(i, Math.min(i + MAX_BATCH_SIZE, missingSongIds.size()));

                    // The endpoint takes bare base62 IDs, not the "spotify:track:" URIs held in the cache.
                    List<String> batchTrackIds = new ArrayList<>();
                    List<String> requestedIds = new ArrayList<>();
                    for (String songId : batchIds) {
                        String trackId = toBareTrackId(songId);
                        if (trackId == null) {
                            Log.w("PlaylistSetup", "Skipping malformed song ID: " + songId);
                            continue;
                        }
                        batchTrackIds.add(trackId);
                        requestedIds.add(songId);
                    }

                    if (batchTrackIds.isEmpty()) continue;

                    se.michaelthelin.spotify.requests.data.tracks.GetSeveralTracksRequest getSeveralTracksRequest =
                            spotifyApi.getSeveralTracks(String.join(",", batchTrackIds))
                                    .setQueryParameter("market", "from_token")
                                    .build();

                    Track[] tracks = getSeveralTracksRequest.execute();

                    for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
                        if (tracks[trackIndex] != null) {
                            SongModel songModel = createSongModelFromTrack(tracks[trackIndex]);
                            allSongs.add(songModel);
                            if (dbHelper != null) {
                                upsertTrackToDb(dbHelper, tracks[trackIndex], songModel);
                            }
                        } else {
                            String requested = trackIndex < requestedIds.size()
                                    ? requestedIds.get(trackIndex) : "index " + trackIndex;
                            Log.w("PlaylistSetup", "Track " + requested + " was not found");
                        }
                    }

                    // Report progress after each batch
                    if (progressListener != null) {
                        progressListener.onSongsLoaded(new ArrayList<>(allSongs),
                                allSongs.size(), totalExpected);
                    }
                }
            } catch (Exception e) {
                Log.e("PlaylistSetup", "Error fetching missing songs", e);
            }

            return allSongs;
        });
    }

    /**
     * Convert a cached song ID ("spotify:track:<id>") to the bare base62 ID the Web API expects.
     * Returns null when the ID is not a track URI.
     */
    private static String toBareTrackId(String songId) {
        if (songId == null) return null;
        if (songId.startsWith(SongModel.SPOTIFY_TRACK_URI_PREFIX)) {
            String trackId = songId.substring(SongModel.SPOTIFY_TRACK_URI_PREFIX.length());
            return trackId.isEmpty() ? null : trackId;
        }
        return null;
    }

    /**
     * Guard against a truncated cache entry. When the cached ID list is shorter than the track
     * count Spotify last reported, the cache is stale and the playlist must be refetched in full —
     * otherwise the missing songs are never recovered.
     */
    private static boolean isCachedSongListComplete(String playlistId, int cachedCount) {
        if (appContext == null) return true;
        Map<String, DatabaseHelper.PlaylistScanInfo> counts = trackCountSnapshot;
        if (counts == null) counts = loadTrackCounts();
        DatabaseHelper.PlaylistScanInfo info = counts.get(playlistId);
        if (info == null) return true;
        int remoteCount = info.remoteCount;
        int cachedTotal = playlistCache.getCachedSongTotal(playlistId);
        if (!PlaylistScanPolicy.isCacheComplete(cachedCount, cachedTotal, remoteCount)) {
            Log.w("PlaylistSetup", "Cached song list for playlist " + playlistId + " has " + cachedCount
                    + " of " + remoteCount + " tracks — refetching from Spotify");
            return false;
        }
        return true;
    }

    /** Read every playlist's track counts and keep them for later cache checks. */
    private static Map<String, DatabaseHelper.PlaylistScanInfo> loadTrackCounts() {
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(appContext);
        Map<String, DatabaseHelper.PlaylistScanInfo> counts = dbHelper.getPlaylistTrackCounts();
        trackCountSnapshot = counts;
        return counts;
    }

    /** Spotify's latest track total for a playlist as stored in the DB, or -1 when unknown. */
    private static int remoteTotalFor(String playlistId) {
        if (appContext == null) return -1;
        Map<String, DatabaseHelper.PlaylistScanInfo> counts = trackCountSnapshot;
        if (counts == null) counts = loadTrackCounts();
        DatabaseHelper.PlaylistScanInfo info = counts.get(playlistId);
        return info != null && info.remoteCount > 0 ? info.remoteCount : -1;
    }

    /** Number of song IDs currently cached for a playlist; 0 when nothing is cached. */
    private static int cachedSongIdCount(String playlistId) {
        List<String> cached = playlistCache.getCachedPlaylistSongIds(playlistId);
        return cached == null ? 0 : cached.size();
    }

    private static void ensurePlaylistLinksInDb(List<String> songIds, String playlistId) {
        if (appContext == null) return;
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(appContext);
        dbHelper.runInTransaction(() -> {
            for (String songId : songIds) {
                dbHelper.upsertSongPlaylistLink(songId, playlistId, null);
            }
        });
    }

    private static void upsertTrackToDb(DatabaseHelper dbHelper, Track track, SongModel songModel) {
        dbHelper.upsertFullTrack(track, songModel);
    }

    private static SongModel createSongModelFromTrack(Track track) {
        AlbumSimplified trackAlbum = track.getAlbum();
        Image[] images = trackAlbum != null ? trackAlbum.getImages() : null;
        String imageUrl = images != null && images.length > 0 ? images[0].getUrl() : "";

        SongModel.Album album = trackAlbum != null ? new SongModel.Album(
                trackAlbum.getId(),
                trackAlbum.getName(),
                trackAlbum.getAlbumType() != null ? trackAlbum.getAlbumType().getType() : null,
                trackAlbum.getReleaseDate(),
                imageUrl
        ) : null;

        String isrc = extractIsrc(track);
        Boolean playable = track.getIsPlayable();
        ArtistSimplified[] artists = track.getArtists();
        return new SongModel(
                track.getUri(),
                track.getName(),
                artists,
                track.getDurationMs(),
                track.getUri(),
                track.getPopularity(),
                album,
                isrc,
                null,
                playable == null || playable
        );
    }

    private static @NonNull SongModel getSongModel(PlaylistTrack playlistTrack) {
        SongModel song = createSongModelFromTrack((Track) playlistTrack.getTrack());
        song.setDateAddedToPlaylist(playlistTrack.getAddedAt());
        return song;
    }

    private static String extractIsrc(Track track) {
        if (track.getExternalIds() != null && track.getExternalIds().getExternalIds() != null) {
            Map<String, String> externalIds = track.getExternalIds().getExternalIds();
            return externalIds.getOrDefault("isrc", "");
        }
        return "";
    }

    private static String formatDate(Date date) {
        if (date == null) return null;
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                .format(date);
    }

    /**
     * Launch scan: refresh playlist metadata (DB and playlist cache), then rescan any playlist
     * whose contents changed. Completes exceptionally when the metadata fetch or any playlist
     * scan fails, so the caller does not record the run and retries on the next launch.
     */
    public static CompletableFuture<Void> scanAllPlaylistSongs(SpotifyApi spotifyApi) {
        if (appContext == null || spotifyApi == null) return CompletableFuture.completedFuture(null);

        return fetchAndCachePlaylists(spotifyApi)
                .thenCompose(ignored -> runScan(spotifyApi));
    }

    private static CompletableFuture<Void> runScan(SpotifyApi spotifyApi) {
        if (appContext == null) return CompletableFuture.completedFuture(null);
        if (!scanInProgress.compareAndSet(false, true)) {
            Log.d("PlaylistSetup", "Scan already in progress — skipping duplicate scan");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            int failures = 0;
            try {
                Map<String, DatabaseHelper.PlaylistScanInfo> trackCounts = loadTrackCounts();
                DatabaseHelper dbHelper = DatabaseHelper.getInstance(appContext);
                for (Map.Entry<String, DatabaseHelper.PlaylistScanInfo> entry : trackCounts.entrySet()) {
                    String pid = entry.getKey();
                    DatabaseHelper.PlaylistScanInfo info = entry.getValue();
                    String label = info.name != null ? info.name : pid;
                    int cachedCount = cachedSongIdCount(pid);
                    PlaylistScanPolicy.Decision decision = PlaylistScanPolicy.decide(
                            info.remoteCount, info.scannedCount, info.snapshotId,
                            info.scannedSnapshotId, cachedCount, playlistCache.getCachedSongTotal(pid));
                    if (decision == PlaylistScanPolicy.Decision.ADOPT_SNAPSHOT) {
                        dbHelper.adoptPlaylistSnapshot(pid, info.snapshotId);
                        continue;
                    }
                    if (decision == PlaylistScanPolicy.Decision.SKIP) {
                        Log.v("PlaylistSetup", "Skipping scan for \"" + label + "\" (" + info.remoteCount + " tracks up to date)");
                        continue;
                    }
                    try {
                        ArrayList<SongModel> songs = scanPlaylistSync(pid, spotifyApi);
                        Log.d("PlaylistSetup", "Scanned \"" + label + "\": fetched " + songs.size() + " tracks (was scanned=" + info.scannedCount + ", spotify=" + info.remoteCount + ", cached=" + cachedCount + ")");
                        cacheSongsInBackground(pid, songs);
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        failures++;
                        break;
                    } catch (se.michaelthelin.spotify.exceptions.detailed.NotFoundException e) {
                        Log.w("PlaylistSetup", "Playlist \"" + label + "\" not found on Spotify — removing from DB");
                        dbHelper.deletePlaylist(pid);
                    } catch (Exception e) {
                        failures++;
                        boolean isRateLimit = e.getMessage() != null && e.getMessage().contains("429");
                        Log.e("PlaylistSetup", "Scan failed for \"" + label + "\"" + (isRateLimit ? " (rate limited)" : ""), e);
                        if (isRateLimit) {
                            try { Thread.sleep(10000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                        }
                    }
                }
            } finally {
                scanInProgress.set(false);
            }
            if (failures > 0) {
                throw new IllegalStateException(failures + " playlist scan(s) failed");
            }
        }, AppExecutors.io());
    }

    private static ArrayList<SongModel> scanPlaylistSync(String playlistId, SpotifyApi spotifyApi) throws Exception {
        ArrayList<SongModel> accumulated = new ArrayList<>();
        int offset = 0;
        int total = -1;
        DatabaseHelper dbHelper = appContext != null ? DatabaseHelper.getInstance(appContext) : null;
        do {
            se.michaelthelin.spotify.model_objects.specification.Paging<se.michaelthelin.spotify.model_objects.specification.PlaylistTrack> page =
                    spotifyApi.getPlaylistsItems(playlistId)
                            .setQueryParameter("market", "from_token")
                            .offset(offset)
                            .limit(MAX_BATCH_SIZE)
                            .build()
                            .execute();
            if (total == -1) total = page.getTotal();
            Runnable processPage = () -> {
                for (se.michaelthelin.spotify.model_objects.specification.PlaylistTrack pt : page.getItems()) {
                    if (pt.getTrack() instanceof se.michaelthelin.spotify.model_objects.specification.Track) {
                        SongModel song = getSongModel(pt);
                        accumulated.add(song);
                        if (dbHelper != null) {
                            upsertTrackToDb(dbHelper, (se.michaelthelin.spotify.model_objects.specification.Track) pt.getTrack(), song);
                            dbHelper.upsertSongPlaylistLink(song.getId(), playlistId, formatDate(pt.getAddedAt()));
                        }
                    }
                }
            };
            // One transaction per page instead of one per statement.
            if (dbHelper != null) dbHelper.runInTransaction(processPage);
            else processPage.run();
            offset += MAX_BATCH_SIZE;
            if (page.getNext() == null) break;
        } while (true);

        if (dbHelper != null) {
            List<String> allIds = new ArrayList<>();
            for (SongModel s : accumulated) allIds.add(s.getId());
            int distinctCount = (int) allIds.stream().distinct().count();
            dbHelper.runInTransaction(() -> {
                dbHelper.reconcilePlaylistSongs(playlistId, allIds);
                dbHelper.updatePlaylistTrackCount(playlistId, distinctCount);
            });

        }
        return accumulated;
    }

    /**
     * Fetch fresh playlist metadata and update the cache. Completes as soon as the list is known;
     * changed playlists are rescanned in the background. Completes exceptionally on failure so
     * callers can keep showing what they already have.
     *
     * @param forceRescan rescan changed playlists now (pull-to-refresh). Otherwise the rescan
     *                    shares the launch scan's throttle, so merely opening the screen does not
     *                    page through playlist contents on every visit.
     */
    public static CompletableFuture<ArrayList<PlaylistModel>> refreshPlaylists(
            SpotifyApi spotifyApi, boolean forceRescan) {
        return fetchAndCachePlaylists(spotifyApi)
                .thenApply(playlists -> {
                    Context context = appContext;
                    boolean due = forceRescan || (context != null && AppExecutors.isJobDue(
                            context, PREF_LAST_PLAYLIST_SCAN, PLAYLIST_SCAN_INTERVAL_MS));
                    if (due) {
                        // remote counts and snapshots are now fresh; rescan any changed playlists
                        runScan(spotifyApi)
                                .thenRun(() -> {
                                    if (context != null) AppExecutors.markJobRun(context, PREF_LAST_PLAYLIST_SCAN);
                                })
                                .exceptionally(e -> {
                                    Log.w("PlaylistSetup", "Background playlist rescan incomplete", e);
                                    return null;
                                });
                    }
                    return playlists;
                });
    }
}
