package com.ca.tunaro.models;

import android.util.Log;

import com.ca.tunaro.activites.MainActivity;

import java.io.Serializable;
import java.util.Date;

import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;

public class SongModel implements Serializable, Playable {

    // --- Core fields (always populated) ---
    String id;           // Spotify URI (spotify:track:XXXX)
    String name;
    String primaryArtist;
    int duration;        // milliseconds
    String uri;          // Spotify URI for playback
    String coverImageUrl;

    // --- Lean-only extras (needed for PlaylistView sorting/filtering) ---
    int popularity;
    String albumName;
    String releaseDate;
    Date dateAddedToPlaylist; // playlist-context only, not persisted on SongModel

    // --- Full model extras (populated when opening SongView) ---
    String[] artists;    // full artist array including featuring
    String albumId;
    String albumType;
    String isrc;
    boolean isPlayable;
    String createdAt;    // when Tunaro first saw this song

    //#region Constructors

    // Lean constructor — for list views
    public SongModel(String id, String name, String primaryArtist, int duration, String uri,
                     String coverImageUrl, int popularity, String albumName, String releaseDate) {
        this.id = id;
        this.name = name;
        this.primaryArtist = primaryArtist;
        this.duration = duration;
        this.uri = uri;
        this.coverImageUrl = coverImageUrl;
        this.popularity = popularity;
        this.albumName = albumName;
        this.releaseDate = releaseDate;
    }

    // Full constructor — for SongView, assembled from DB joins
    public SongModel(String id, String name, String primaryArtist, String[] artists, int duration,
                     String uri, String coverImageUrl, int popularity, String albumId,
                     String albumName, String albumType, String releaseDate, String isrc,
                     boolean isPlayable, String createdAt) {
        this.id = id;
        this.name = name;
        this.primaryArtist = primaryArtist;
        this.artists = artists;
        this.duration = duration;
        this.uri = uri;
        this.coverImageUrl = coverImageUrl;
        this.popularity = popularity;
        this.albumId = albumId;
        this.albumName = albumName;
        this.albumType = albumType;
        this.releaseDate = releaseDate;
        this.isrc = isrc;
        this.isPlayable = isPlayable;
        this.createdAt = createdAt;
    }

    // Legacy full constructor using ArtistSimplified[] — used at Spotify API call sites
    public SongModel(String id, String name, ArtistSimplified[] artistSimplifieds, int duration,
                     String uri, int popularity, Album album, String isrc,
                     Date dateAddedToPlaylist, boolean isPlayable) {
        this.id = id;
        this.name = name;
        this.artists = extractArtistNames(artistSimplifieds);
        this.primaryArtist = getPrimaryArtistName(artistSimplifieds);
        this.duration = duration;
        this.uri = uri;
        this.coverImageUrl = album != null ? album.getCoverImage() : null;
        this.popularity = popularity;
        this.albumId = album != null ? album.getId() : null;
        this.albumName = album != null ? album.getName() : null;
        this.albumType = album != null ? album.getAlbumType() : null;
        this.releaseDate = album != null ? album.getReleaseDate() : null;
        this.isrc = isrc;
        this.dateAddedToPlaylist = dateAddedToPlaylist;
        this.isPlayable = isPlayable;
    }

    // Remote track constructor (App Remote — no album/ISRC metadata)
    public SongModel(String id, String name, String[] artistNames, int duration,
                     String uri, String coverImageUrl) {
        this.id = id;
        this.name = name;
        this.artists = artistNames;
        this.primaryArtist = (artistNames != null && artistNames.length > 0) ? artistNames[0] : null;
        this.duration = duration;
        this.uri = uri;
        this.coverImageUrl = coverImageUrl;
        this.isPlayable = true;
    }

    //#endregion

    //#region Nested Album class (used at Spotify API call sites)

    public static class Album implements Serializable {
        String id;
        String name;
        String albumType;
        String releaseDate;
        String coverImage;

        public Album(String id, String name, String albumType, String releaseDate, String coverImage) {
            this.id = id;
            this.name = name;
            this.albumType = albumType;
            this.releaseDate = releaseDate;
            this.coverImage = coverImage;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getAlbumType() { return albumType; }
        public String getReleaseDate() { return releaseDate; }
        public String getCoverImage() { return coverImage; }
    }

    //#endregion

    //#region Getters

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPrimaryArtist() { return primaryArtist; }
    public String[] getArtistArray() { return artists; }

    public String getArtist() {
        if (artists != null && artists.length > 0) return String.join(", ", artists);
        return primaryArtist != null ? primaryArtist : "";
    }

    public int getDuration() { return duration; }

    public String getDurationString() {
        int totalSeconds = duration / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return seconds == 0 ? minutes + "m" : minutes + "m " + seconds + "s";
    }

    public String getUri() { return uri; }
    public int getPopularity() { return popularity; }
    public String getAlbumId() { return albumId; }
    public String getAlbumName() { return albumName; }
    public String getAlbumType() { return albumType; }
    public String getReleaseDate() { return releaseDate; }
    public String getAlbumCoverUrl() { return coverImageUrl; }
    public String getIsrc() { return isrc; }
    public boolean isPlayable() { return isPlayable; }
    public String getCreatedAt() { return createdAt; }
    public Date getDateAddedToPlaylist() { return dateAddedToPlaylist; }
    public void setDateAddedToPlaylist(Date date) { this.dateAddedToPlaylist = date; }

    //#endregion

    //#region Popularity async fetch

    public interface PopularityCallback {
        void onPopularityFetched(int popularity);
        void onError(String error);
    }

    public void fetchPopularityAsync(PopularityCallback callback) {
        if (popularity != 0) {
            if (callback != null) callback.onPopularityFetched(popularity);
            return;
        }

        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            if (callback != null) callback.onError("Spotify API not available");
            return;
        }

        String[] parts = uri != null ? uri.split(":") : null;
        if (parts == null || parts.length < 3) {
            if (callback != null) callback.onError("No Spotify URI available");
            return;
        }

        Log.d("SongModel", "API: getTrack trackId=" + parts[2] + " song=" + name);
        final String trackId = parts[2];
        mainActivity.executeWithTokenRefresh(() -> mainActivity.getSpotifyApi().getTrack(trackId).build())
                .thenAccept(track -> {
                    this.popularity = track.getPopularity();
                    if (callback != null) callback.onPopularityFetched(this.popularity);
                })
                .exceptionally(throwable -> {
                    if (callback != null) callback.onError("Failed to fetch popularity: " + throwable.getMessage());
                    return null;
                });
    }

    //#endregion

    //#region Static utilities

    public static final String SPOTIFY_TRACK_URI_PREFIX = "spotify:track:";

    public static String getPrimaryArtistName(ArtistSimplified[] artists) {
        return (artists != null && artists.length > 0) ? artists[0].getName() : null;
    }

    private static String[] extractArtistNames(ArtistSimplified[] artists) {
        if (artists == null || artists.length == 0) return new String[]{"Unknown Artist"};
        String[] names = new String[artists.length];
        for (int i = 0; i < artists.length; i++) names[i] = artists[i].getName();
        return names;
    }

    //#endregion
}
