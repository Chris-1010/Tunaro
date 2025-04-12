package com.ca.tunaro;

import java.util.Date;

import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;

public class SongModel {
    // See https://developer.spotify.com/documentation/web-api/reference/get-playlists-tracks to add more (also change the 'fields' parameter in the PlaylistSetup.java file)
    String id;
    String name;
    ArtistSimplified[] artists;
    int duration;    // duration_ms
    String uri;
    int popularity;    // a score out of 100 which is based on how much the song is played and how recently (Dev Note: Seems to me more like a 'trending' score)
    String albumCoverUrl;    // images[1]["url"] (1 is the second index in the list which holds image sizes of 300x300)
    Date dateAddedToPlaylist;    // added_at (a string in date-time format. i.e. "2021-09-14T22:45:17Z")
    String releaseDate;    // album["release_date"]    (most often in a format like YYYY-MM)

    public SongModel(String id, String name, ArtistSimplified[] artists, int duration, String uri, int popularity, String albumCoverUrl, Date dateAddedToPlaylist, String releaseDate) {
        this.id = id;
        this.name = name;
        this.artists = artists;
        this.duration = duration;
        this.uri = uri;
        this.popularity = popularity;
        this.albumCoverUrl = albumCoverUrl;
        this.dateAddedToPlaylist = dateAddedToPlaylist;
        this.releaseDate = releaseDate;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArtist() {
        return artists[0].getName();
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

    public String getAlbumCoverUrl() {
        return albumCoverUrl;
    }

    public String getDateAddedToPlaylist() {
        return dateAddedToPlaylist.toString();
    }

    public String getReleaseDate() {
        return releaseDate;
    }
}
