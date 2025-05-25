package com.ca.tunaro;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
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
    private static final Object lock = new Object();

    public static void initialize(Context context) {
        playlistCache = new PlaylistCache(context);
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
                .limit(50)
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
                                return getAllPlaylists(userID, spotifyApi, offset + 50, accumulatedPlaylists);
                            }
                        }
                        return CompletableFuture.completedFuture(accumulatedPlaylists);
                    } catch (Exception e) {
                        return failedFuture(e);
                    }
                });
    }

    public static CompletableFuture<ArrayList<SongModel>> getPlaylistSongs(String playlistId, SpotifyApi spotifyApi) {
        // First try to get from cache
        ArrayList<SongModel> cachedSongs = playlistCache.getCachedSongsForPlaylist(playlistId);
        if (cachedSongs != null) {
            return CompletableFuture.completedFuture(cachedSongs);
        }

        // If not in cache, fetch from API and cache the result
        return getAllSongs(playlistId, spotifyApi, 0, new ArrayList<>())
                .thenApply(songs -> {
                    synchronized (lock) {
                        playlistCache.cacheSongsForPlaylist(playlistId, songs);
                    }
                    return songs;
                })
                .exceptionally(throwable -> {
                    Log.e("PlaylistSetup", "Error getting songs: " + throwable.getMessage());
                    return new ArrayList<>();
                });
    }

    private static CompletableFuture<ArrayList<SongModel>> getAllSongs(
            String id, SpotifyApi spotifyApi, int offset, ArrayList<SongModel> accumulatedSongs) {

        final GetPlaylistsItemsRequest getPlaylistsItemsRequest = spotifyApi
                .getPlaylistsItems(id)
                .offset(offset)
                .limit(50)  // Spotify API maximum limit per request
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

                        // Continue loading if there are more songs
                        if (playlistTrackPaging.getNext() != null) {
                            // Add a small delay to avoid rate limiting
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return getAllSongs(id, spotifyApi, offset + 50, accumulatedSongs);
                        }
                        return CompletableFuture.completedFuture(accumulatedSongs);
                    } catch (Exception e) {
                        return failedFuture(e);
                    }
                });
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