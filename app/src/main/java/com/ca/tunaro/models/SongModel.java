package com.ca.tunaro.models;

import com.ca.tunaro.activites.MainActivity;

import java.io.Serializable;
import java.util.Date;

import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;

public class SongModel implements Serializable {
    String id;
    String name;
    String[] artists;
    int duration;               // milliseconds
    String uri;                 // Spotify URI for playback
    int popularity;             // a score out of 100 which is based on how much the song has been played recently
    Album album;
    String isrc;                // International Standard Recording Code
    Date dateAddedToPlaylist;   // added_at (for sorting by date added)
    boolean isPlayable;         // whether the track is playable in the user's market

    //#region Nested class for Album

    // This is to avoid creating an extra unnecessary file for a simple model
    // which is only ever used in the context of a SongModel

    public static class Album implements Serializable {
        String id;
        String name;
        String albumType;       // single, album, or compilation
        String releaseDate;
        String coverImage;

        public Album(String id, String name, String albumType, String releaseDate, String coverImage) {
            this.id = id;
            this.name = name;
            this.albumType = albumType;
            this.releaseDate = releaseDate;
            this.coverImage = coverImage;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getAlbumType() {
            return albumType;
        }

        public String getReleaseDate() {
            return releaseDate;
        }

        public String getCoverImage() {
            return coverImage;
        }
    }

    //#endregion

    //#region Constructors

    public SongModel(String id, String name, String[] artists, int duration, String uri, int popularity, Album album, String isrc, Date dateAddedToPlaylist, boolean isPlayable) {
        this.id = id;
        this.name = name;
        this.artists = artists;
        this.duration = duration;
        this.uri = uri;
        this.popularity = popularity;
        this.album = album;
        this.isrc = isrc;
        this.dateAddedToPlaylist = dateAddedToPlaylist;
        this.isPlayable = isPlayable;
    }

    public SongModel(String id, String name, ArtistSimplified[] artists, int duration, String uri, int popularity, Album album, String isrc, Date dateAddedToPlaylist, boolean isPlayable) {
        this(id, name, extractArtistNames(artists), duration, uri, popularity, album, isrc, dateAddedToPlaylist, isPlayable);
    }

    //#endregion

    //#region Getters

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArtist() {
        return String.join(", ", artists);
    }

    public int getDuration() {
        return duration;
    }

    public String getDurationString() {
        // Convert milliseconds to seconds first
        int totalSeconds = duration / 1000;

        // Calculate minutes and remaining seconds
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        // Return formatted string
        if (seconds == 0) {
            return minutes + "m";
        } else {
            return minutes + "m " + seconds + "s";
        }
    }

    public String getUri() {
        return uri;
    }

    public int getPopularity() {
        return popularity;
    }

    public void fetchPopularityAsync(PopularityCallback callback) {
        // Check if popularity is already available
        if (popularity != 0) {
            if (callback != null) {
                callback.onPopularityFetched(popularity);
            }
            return;
        }

        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            if (callback != null) {
                callback.onError("Spotify API not available");
            }
            return;
        }

        mainActivity.getSpotifyApi().getTrack(this.id)
                .build()
                .executeAsync()
                .thenAccept(track -> {
                    this.popularity = track.getPopularity();
                    if (callback != null) {
                        callback.onPopularityFetched(this.popularity);
                    }
                })
                .exceptionally(throwable -> {
                    if (callback != null) {
                        callback.onError("Failed to fetch popularity: " + throwable.getMessage());
                    }
                    return null;
                });
    }

    public interface PopularityCallback {
        void onPopularityFetched(int popularity);
        void onError(String error);
    }

    // Album methods
    public String getAlbumId() {
        return album.getId();
    }

    public String getAlbumType() {
        return album.getAlbumType();
    }

    public String getAlbumName() {
        return album.getName();
    }

    public String getAlbumCoverUrl() {
        return album.getCoverImage();
    }

    public String getReleaseDate() {
        return album.getReleaseDate();
    }

    public String getIsrc() {
        return isrc;
    }

    public boolean isPlayable() {
        return isPlayable;
    }

    public Date getDateAddedToPlaylist() {
        return dateAddedToPlaylist;
    }

    public String getDateAddedToPlaylistString() {
        return dateAddedToPlaylist.toString();
    }

    //#endregion

    //#region Helper methods

    // Extract artists from ArtistSimplified[] to String[]
    private static String[] extractArtistNames(ArtistSimplified[] artists) {
        if (artists == null || artists.length == 0) {
            return new String[]{"Unknown Artist"};
        }

        String[] artistNames = new String[artists.length];
        for (int i = 0; i < artists.length; i++) {
            artistNames[i] = artists[i].getName();
        }

        return artistNames;
    }

    //#endregion
}
