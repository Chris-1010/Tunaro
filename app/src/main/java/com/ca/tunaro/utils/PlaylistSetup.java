package com.ca.tunaro.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

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
        songCache = new SongCache(appContext);

        // Clean up expired song cache entries on initialization
        songCache.clearExpiredEntries();
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable ex) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    public static CompletableFuture<ArrayList<PlaylistModel>> getPlaylistData(SpotifyApi spotifyApi) {
        ArrayList<PlaylistModel> cachedPlaylists = playlistCache.getCachedPlaylists();
        if (cachedPlaylists != null) {
            return CompletableFuture.completedFuture(cachedPlaylists);
        }

        return getAllPlaylists(spotifyApi, 0, new ArrayList<>())
                .thenApply(playlists -> {
                    synchronized (lock) {
                        playlistCache.cachePlaylists(playlists);
                    }
                    return playlists;
                })
                .exceptionally(throwable -> {
                    Log.e("PlaylistSetup", "Error getting playlists: " + throwable.getMessage());
                    return new ArrayList<>();
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
                            DatabaseHelper dbHelper = appContext != null ? new DatabaseHelper(appContext) : null;
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
                                                owner
                                        );
                                    }
                                }
                            }
                            if (dbHelper != null) dbHelper.close();
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

        return CompletableFuture.supplyAsync(() -> {
            List<String> cachedSongIds = playlistCache.getCachedPlaylistSongIds(playlistId);
            if (cachedSongIds != null && !cachedSongIds.isEmpty()) {
                Map<String, SongModel> cachedSongsMap = songCache.getCachedSongsMap(cachedSongIds);

                ArrayList<SongModel> cachedSongs = new ArrayList<>();
                List<String> missingSongIds = new ArrayList<>();

                // Preserve order from cached song IDs
                for (String songId : cachedSongIds) {
                    SongModel song = cachedSongsMap.get(songId);
                    if (song != null) {
                        cachedSongs.add(song);
                    } else {
                        missingSongIds.add(songId);
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
                    DatabaseHelper dbHelper = new DatabaseHelper(appContext);
                    Map<String, java.util.Date> addedAtMap = dbHelper.getAddedAtMapForPlaylist(playlistId);
                    dbHelper.close();
                    for (SongModel song : cachedSongs) {
                        java.util.Date addedAt = addedAtMap.get(song.getId());
                        if (addedAt != null) song.setDateAddedToPlaylist(addedAt);
                    }
                }

                // If all songs cached, return with needsCaching=false
                if (missingSongIds.isEmpty()) {
                    Log.d("PlaylistSetup", "All " + cachedSongs.size() + " songs found in cache");
                    ensurePlaylistLinksInDb(cachedSongIds, playlistId);
                    return new SongLoadResult(cachedSongs, false);
                }

                // Fetch missing songs - needs caching
                try {
                    ArrayList<SongModel> allSongs = fetchMissingSongs(missingSongIds, spotifyApi, cachedSongs,
                            progressListener, cachedSongIds.size()).get();
                    return new SongLoadResult(allSongs, true);
                } catch (Exception e) {
                    Log.e("PlaylistSetup", "Error fetching missing songs", e);
                    return new SongLoadResult(cachedSongs, false);
                }
            }

            // Not in cache - fetch from API, needs caching
            try {
                ArrayList<SongModel> songs = getAllSongs(playlistId, spotifyApi, 0, new ArrayList<>(),
                        progressListener, -1).get();
                return new SongLoadResult(songs, true);
            } catch (Exception e) {
                Log.e("PlaylistSetup", "Error getting songs", e);
                return new SongLoadResult(new ArrayList<>(), false);
            }
        });
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
                        DatabaseHelper dbHelper = appContext != null ? new DatabaseHelper(appContext) : null;
                        List<String> batchSongIds = new ArrayList<>();

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

        synchronized (lock) {
            // SongCache: Cache all songs
            songCache.cacheSongs(songs);

            // PlaylistCache: Update with song IDs
            List<String> songIds = new ArrayList<>();
            for (SongModel song : songs) {
                songIds.add(song.getId());
            }
            playlistCache.updatePlaylistSongs(playlistId, songIds);
        }

        Log.d("PlaylistSetup", "Background cache completed for playlist " + playlistId);
    }

    private static CompletableFuture<ArrayList<SongModel>> fetchMissingSongs(
            List<String> missingSongIds, SpotifyApi spotifyApi, ArrayList<SongModel> existingSongs,
            SongLoadProgressListener progressListener, int totalExpected) {

        return CompletableFuture.supplyAsync(() -> {
            ArrayList<SongModel> allSongs = new ArrayList<>(existingSongs);

            try {
                DatabaseHelper dbHelper = appContext != null ? new DatabaseHelper(appContext) : null;

                for (int i = 0; i < missingSongIds.size(); i += MAX_BATCH_SIZE) {
                    List<String> batchIds = missingSongIds.subList(i, Math.min(i + MAX_BATCH_SIZE, missingSongIds.size()));

                    se.michaelthelin.spotify.requests.data.tracks.GetSeveralTracksRequest getSeveralTracksRequest =
                            spotifyApi.getSeveralTracks(String.join(",", batchIds))
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
                            Log.w("PlaylistSetup", "Track " + trackIndex + " was not found");
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

    private static void ensurePlaylistLinksInDb(List<String> songIds, String playlistId) {
        if (appContext == null) return;
        DatabaseHelper dbHelper = new DatabaseHelper(appContext);
        for (String songId : songIds) {
            dbHelper.upsertSongPlaylistLink(songId, playlistId, null);
        }
        dbHelper.close();
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

    public static CompletableFuture<Void> scanAllPlaylistSongs(SpotifyApi spotifyApi) {
        if (appContext == null || spotifyApi == null) return CompletableFuture.completedFuture(null);

        // Fetch fresh remote track counts from Spotify before checking what needs scanning
        return getAllPlaylists(spotifyApi, 0, new ArrayList<>())
                .thenCompose(ignored -> runScan(spotifyApi))
                .exceptionally(e -> {
                    Log.e("PlaylistSetup", "Failed to fetch remote track counts before scan", e);
                    // Fall back to scanning with whatever counts are already in the DB
                    return null;
                });
    }

    private static CompletableFuture<Void> runScan(SpotifyApi spotifyApi) {
        if (appContext == null) return CompletableFuture.completedFuture(null);
        if (!scanInProgress.compareAndSet(false, true)) {
            Log.d("PlaylistSetup", "Scan already in progress — skipping duplicate scan");
            return CompletableFuture.completedFuture(null);
        }
        DatabaseHelper dbHelper = new DatabaseHelper(appContext);
        Map<String, DatabaseHelper.PlaylistScanInfo> trackCounts = dbHelper.getPlaylistTrackCounts();
        dbHelper.close();

        return CompletableFuture.runAsync(() -> {
            try {
                for (Map.Entry<String, DatabaseHelper.PlaylistScanInfo> entry : trackCounts.entrySet()) {
                    String pid = entry.getKey();
                    DatabaseHelper.PlaylistScanInfo info = entry.getValue();
                    String label = info.name != null ? info.name : pid;
                    if (info.remoteCount > 0 && info.scannedCount == info.remoteCount) {
                        Log.v("PlaylistSetup", "Skipping scan for \"" + label + "\" (" + info.remoteCount + " tracks up to date)");
                        continue;
                    }
                    try {
                        ArrayList<SongModel> songs = scanPlaylistSync(pid, spotifyApi);
                        Log.d("PlaylistSetup", "Scanned \"" + label + "\": fetched " + songs.size() + " tracks (was scanned=" + info.scannedCount + ", spotify=" + info.remoteCount + ")");
                        cacheSongsInBackground(pid, songs);
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (se.michaelthelin.spotify.exceptions.detailed.NotFoundException e) {
                        Log.w("PlaylistSetup", "Playlist \"" + label + "\" not found on Spotify — removing from DB");
                        DatabaseHelper deleteHelper = new DatabaseHelper(appContext);
                        deleteHelper.deletePlaylist(pid);
                        deleteHelper.close();
                    } catch (Exception e) {
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
        });
    }

    private static ArrayList<SongModel> scanPlaylistSync(String playlistId, SpotifyApi spotifyApi) throws Exception {
        ArrayList<SongModel> accumulated = new ArrayList<>();
        int offset = 0;
        int total = -1;
        DatabaseHelper dbHelper = appContext != null ? new DatabaseHelper(appContext) : null;
        try {
            do {
                se.michaelthelin.spotify.model_objects.specification.Paging<se.michaelthelin.spotify.model_objects.specification.PlaylistTrack> page =
                        spotifyApi.getPlaylistsItems(playlistId)
                                .setQueryParameter("market", "from_token")
                                .offset(offset)
                                .limit(MAX_BATCH_SIZE)
                                .build()
                                .execute();
                if (total == -1) total = page.getTotal();
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
                offset += MAX_BATCH_SIZE;
                if (page.getNext() == null) break;
            } while (true);

            if (dbHelper != null) {
                List<String> allIds = new ArrayList<>();
                for (SongModel s : accumulated) allIds.add(s.getId());
                dbHelper.reconcilePlaylistSongs(playlistId, allIds);
                int distinctCount = (int) allIds.stream().distinct().count();
                dbHelper.updatePlaylistTrackCount(playlistId, distinctCount);

            }
        } finally {
            if (dbHelper != null) dbHelper.close();
        }
        return accumulated;
    }

    public static CompletableFuture<ArrayList<PlaylistModel>> refreshPlaylists(SpotifyApi spotifyApi) {
        return getAllPlaylists(spotifyApi, 0, new ArrayList<>())
                .thenCompose(playlists -> {
                    synchronized (lock) {
                        playlistCache.cachePlaylists(playlists);
                    }
                    // remote_track_count is now fresh; run scan to pick up any changed playlists
                    return runScan(spotifyApi).thenApply(v -> playlists);
                })
                .exceptionally(throwable -> {
                    Log.e("PlaylistSetup", "Error refreshing playlists: " + throwable.getMessage());
                    return new ArrayList<>();
                });
    }
}
