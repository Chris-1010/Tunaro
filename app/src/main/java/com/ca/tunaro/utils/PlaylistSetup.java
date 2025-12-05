package com.ca.tunaro.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.models.SongModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.data.playlists.GetListOfUsersPlaylistsRequest;
import se.michaelthelin.spotify.requests.data.playlists.GetPlaylistsItemsRequest;

public class PlaylistSetup {
    private static PlaylistCache playlistCache;
    private static SongCache songCache;
    private static final int MAX_BATCH_SIZE = 50;    // Spotify API maximum limit per request
    private static final Object lock = new Object();

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
        playlistCache = new PlaylistCache(context);
        songCache = new SongCache(context);

        // Clean up expired song cache entries on initialization
        songCache.clearExpiredEntries();
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable ex) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    public static CompletableFuture<ArrayList<PlaylistModel>> getPlaylistData(String userID, SpotifyApi spotifyApi) {
        // First try to get from cache
        ArrayList<PlaylistModel> cachedPlaylists = playlistCache.getCachedPlaylists();
        if (cachedPlaylists != null) {
            return CompletableFuture.completedFuture(cachedPlaylists);
        }

        // If not in cache, fetch from API and cache the result
        return getAllPlaylists(userID, spotifyApi, 0, new ArrayList<>())
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
            String userID, SpotifyApi spotifyApi, int offset, ArrayList<PlaylistModel> accumulatedPlaylists) {

        final GetListOfUsersPlaylistsRequest getListOfUsersPlaylistsRequest = spotifyApi
                .getListOfUsersPlaylists(userID)
                .limit(MAX_BATCH_SIZE)
                .offset(offset)
                .build();

        return getListOfUsersPlaylistsRequest.executeAsync()
                .thenCompose(playlistSimplifiedPaging -> {
                    try {
                        if (playlistSimplifiedPaging != null) {
                            for (PlaylistSimplified playlist : playlistSimplifiedPaging.getItems()) {
                                if (playlist.getTracks().getTotal() > 0) {
                                    accumulatedPlaylists.add(new PlaylistModel(
                                            playlist.getId(),
                                            playlist.getName(),
                                            playlist.getTracks().getTotal(),
                                            playlist.getImages(),
                                            new ArrayList<>()
                                    ));
                                }
                            }
                            if (playlistSimplifiedPaging.getNext() != null) {
                                return getAllPlaylists(userID, spotifyApi, offset + MAX_BATCH_SIZE, accumulatedPlaylists);
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

                // If all songs cached, return with needsCaching=false
                if (missingSongIds.isEmpty()) {
                    Log.d("PlaylistSetup", "All " + cachedSongs.size() + " songs found in cache");
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
            String id,
            SpotifyApi spotifyApi,
            int offset,
            ArrayList<SongModel> accumulatedSongs,
            SongLoadProgressListener progressListener, int totalCount) {

        final GetPlaylistsItemsRequest getPlaylistsItemsRequest = spotifyApi
                .getPlaylistsItems(id)
                .offset(offset)
                .limit(MAX_BATCH_SIZE)
                .build();

        return getPlaylistsItemsRequest.executeAsync()
                .thenCompose(playlistTrackPaging -> {
                    try {
                        for (PlaylistTrack playlistTrack : playlistTrackPaging.getItems()) {
                            if (playlistTrack.getTrack() instanceof Track) {
                                SongModel songModel = getSongModel(playlistTrack);
                                accumulatedSongs.add(songModel);
                            }
                        }

                        // Get total count on first request
                        int total = totalCount == -1 ? playlistTrackPaging.getTotal() : totalCount;

                        // Report progress for this batch
                        if (progressListener != null) {
                            progressListener.onSongsLoaded(new ArrayList<>(accumulatedSongs),
                                    accumulatedSongs.size(), total);
                        }

                        // Continue loading if there are more songs
                        if (playlistTrackPaging.getNext() != null) {
                            return getAllSongs(id, spotifyApi, offset + MAX_BATCH_SIZE,
                                    accumulatedSongs, progressListener, total);
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
                for (int i = 0; i < missingSongIds.size(); i += MAX_BATCH_SIZE) {
                    List<String> batchIds = missingSongIds.subList(i, Math.min(i + MAX_BATCH_SIZE, missingSongIds.size()));

                    se.michaelthelin.spotify.requests.data.tracks.GetSeveralTracksRequest getSeveralTracksRequest =
                            spotifyApi.getSeveralTracks(String.join(",", batchIds))
                                    .build();

                    se.michaelthelin.spotify.model_objects.specification.Track[] tracks = getSeveralTracksRequest.execute();

                    for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
                        if (tracks[trackIndex] != null) {
                            SongModel songModel = createSongModelFromTrack(tracks[trackIndex]);
                            allSongs.add(songModel);
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

    private static SongModel createSongModelFromTrack(se.michaelthelin.spotify.model_objects.specification.Track track) {
        se.michaelthelin.spotify.model_objects.specification.Image[] images = track.getAlbum().getImages();
        String imageUrl = images.length > 0 ? images[0].getUrl() : "";

        return new SongModel(
                track.getId(),
                track.getName(),
                track.getArtists(),
                track.getDurationMs(),
                track.getUri(),
                track.getPopularity(),
                track.getAlbum().getName(),
                imageUrl,
                null, // No playlist date for individual tracks
                track.getAlbum().getReleaseDate()
        );
    }

    private static @NonNull SongModel getSongModel(PlaylistTrack playlistTrack) {
        Track track = (Track) playlistTrack.getTrack();
        Image[] images = track.getAlbum().getImages();
        String imageUrl = images.length > 0 ? images[0].getUrl() : "";

        return new SongModel(
                track.getId(),
                track.getName(),
                track.getArtists(),
                track.getDurationMs(),
                track.getUri(),
                track.getPopularity(),
                track.getAlbum().getName(),
                imageUrl,
                playlistTrack.getAddedAt(),
                track.getAlbum().getReleaseDate()
        );
    }

    public static CompletableFuture<ArrayList<PlaylistModel>> refreshPlaylists(String userID, SpotifyApi spotifyApi) {
        synchronized (lock) {
            playlistCache.clearCache();
        }
        return getAllPlaylists(userID, spotifyApi, 0, new ArrayList<>())
                .thenApply(playlists -> {
                    synchronized (lock) {
                        playlistCache.cachePlaylists(playlists);
                    }
                    return playlists;
                })
                .exceptionally(throwable -> {
                    Log.e("PlaylistSetup", "Error refreshing playlists: " + throwable.getMessage());
                    return new ArrayList<>();
                });
    }
}