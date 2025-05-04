package com.ca.tunaro;

import java.util.Date;

import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;

public class SongModel {
    // See https://developer.spotify.com/documentation/web-api/reference/get-playlists-tracks to add more (also change the 'fields' parameter in the PlaylistSetup.java file)
    String id;
    String name;
    String[] artists;
    int duration;    // duration_ms
    String uri;
    int popularity;    // a score out of 100 which is based on how much the song is played and how recently (Dev Note: Seems to me more like a 'trending' score)
    String albumName;
    String albumCoverUrl;    // images[1]["url"] (1 is the second index in the list which holds image sizes of 300x300)
    Date dateAddedToPlaylist;    // added_at (a string in date-time format. i.e. "2021-09-14T22:45:17Z")
    String releaseDate;    // album["release_date"]    (most often in a format like YYYY-MM)
    Date lastListenDate;

    public SongModel(String id, String name, String[] artists, int duration, String uri, int popularity, String albumName, String albumCoverUrl, Date dateAddedToPlaylist, String releaseDate) {
        this.id = id;
        this.name = name;
        this.artists = artists;
        this.duration = duration;
        this.uri = uri;
        this.popularity = popularity;
        this.albumName = albumName;
        this.albumCoverUrl = albumCoverUrl;
        this.dateAddedToPlaylist = dateAddedToPlaylist;
        this.releaseDate = releaseDate;
    }

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

    public SongModel(String id, String name, ArtistSimplified[] artists, int duration, String uri, int popularity, String albumName, String albumCoverUrl, Date dateAddedToPlaylist, String releaseDate) {
        this(id, name, extractArtistNames(artists), duration, uri, popularity, albumName, albumCoverUrl, dateAddedToPlaylist, releaseDate);
    }

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

    public String getAlbumName() {
        return albumName;
    }

    public String getAlbumCoverUrl() {
        return albumCoverUrl;
    }

    public Date getDateAddedToPlaylist() {
        return dateAddedToPlaylist;
    }

    public String getDateAddedToPlaylistString() {
        return dateAddedToPlaylist.toString();
    }

    public String getReleaseDate() {
        return releaseDate;
    }
}
