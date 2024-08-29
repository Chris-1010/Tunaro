package com.ca.tunaro;

import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.hc.core5.http.ParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.data.playlists.GetListOfUsersPlaylistsRequest;
import se.michaelthelin.spotify.requests.data.playlists.GetPlaylistsItemsRequest;

public class PlaylistSetup {
    public static CompletableFuture<ArrayList<PlaylistModel>> getPlaylistData(String userID, SpotifyApi spotifyApi) {
        return getAllPlaylists(userID, spotifyApi, 0, new ArrayList<>());
    }

    private static CompletableFuture<ArrayList<PlaylistModel>> getAllPlaylists(String userID, SpotifyApi spotifyApi, int offset, ArrayList<PlaylistModel> accumulatedPlaylists) {
        return getListOfCurrentUsersPlaylists_Async(userID, spotifyApi, offset)
                .thenCompose(playlistSimplifiedPaging -> {
                    if (playlistSimplifiedPaging != null) {
                        for (PlaylistSimplified playlist : playlistSimplifiedPaging.getItems()) {
                            if (playlist.getTracks().getTotal() > 0) {
                                ArrayList<SongModel> playlistItems;
                                playlistItems = getPlaylistSongs(playlist.getId(), spotifyApi);
                                accumulatedPlaylists.add(new PlaylistModel(playlist.getId(), playlist.getName(), playlist.getTracks().getTotal(), playlist.getImages(), playlistItems));
                            }
                        }

                        if (playlistSimplifiedPaging.getNext() != null) {
                            // More playlists to fetch
                            return getAllPlaylists(userID, spotifyApi, offset + 50, accumulatedPlaylists);
                        }
                    }
                    return CompletableFuture.completedFuture(accumulatedPlaylists);
                })
                .exceptionally(throwable -> {
                    Log.e("Error in playlistRetrieval", "Error retrieving playlists: " + throwable.getMessage());
                    return accumulatedPlaylists;
                });
    }

    public static CompletableFuture<Paging<PlaylistSimplified>> getListOfCurrentUsersPlaylists_Async(String userID, SpotifyApi spotifyApi, int offset) {
        final GetListOfUsersPlaylistsRequest getListOfCurrentUsersPlaylistsRequest = spotifyApi
                .getListOfUsersPlaylists(userID)
                .offset(offset)
                .limit(50)
                .build();

        return getListOfCurrentUsersPlaylistsRequest.executeAsync()
                .exceptionally(throwable -> {
                    if (throwable instanceof CompletionException) {
                        System.out.println("getListOfCurrentUsersPlaylistsRequest: Error: " + Objects.requireNonNull(throwable.getCause()).getMessage());
                    } else if (throwable instanceof CancellationException) {
                        System.out.println("Async operation cancelled.");
                    } else {
                        System.out.println("An unexpected error occurred: " + throwable.getMessage());
                    }
                    return null;
                });
    }

    public static ArrayList<SongModel> getPlaylistSongs(String id, SpotifyApi spotifyApi) {
        return getAllSongs(id, spotifyApi, 0, new ArrayList<>());
    }

    private static ArrayList<SongModel> getAllSongs(String id, SpotifyApi spotifyApi, int offset, ArrayList<SongModel> accumulatedSongs) {
        final GetPlaylistsItemsRequest getPlaylistsItemsRequest = spotifyApi
                .getPlaylistsItems(id)
//                .fields("items(added_at,track(album(images,release_date),artists,duration_ms,id,name,popularity,uri))")
                .offset(offset)
                .limit(50)
                .build();

        try {
            Paging<PlaylistTrack> playlistTrackPaging = getPlaylistsItemsRequest.execute();

            for (PlaylistTrack playlistTrack : playlistTrackPaging.getItems()) {
                SongModel songModel = getSongModel(playlistTrack);
                accumulatedSongs.add(songModel);
            }

            if (playlistTrackPaging.getNext() != null) {
                // Time taken to retrieve playlist's songs is taking too long so capping it at 50 for now.
                if (accumulatedSongs.size() >= 10) return accumulatedSongs;
                // More songs to fetch
                return getAllSongs(id, spotifyApi, offset + 50, accumulatedSongs);
            } else {
                // No more songs, return the accumulated list
                return accumulatedSongs;
            }
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Error: " + e.getMessage());
            return accumulatedSongs;  // Return whatever we've accumulated so far
        }
    }

    private static @NonNull SongModel getSongModel(PlaylistTrack playlistTrack) {
        Track track = (Track) playlistTrack.getTrack();

        return new SongModel(
                track.getId(),
                track.getName(),
                track.getArtists(),
                track.getDurationMs(),
                track.getUri(),
                track.getPopularity(),
                track.getAlbum().getUri(),
                playlistTrack.getAddedAt(),
                track.getAlbum().getReleaseDate()
        );
    }
}
