package com.ca.tunaro.models;

import java.io.Serializable;
import java.util.List;

public class SongVariant implements Serializable {
    private final long variantId;
    private final String songId;
    private final String spotifyUri;
    private final String albumId;
    private final String albumName;
    private final String albumCoverUrl;
    private final int popularity;
    private final String firstSeenAt;
    private List<Artist> artists;

    public SongVariant(long variantId, String songId, String spotifyUri, String albumId,
                       String albumName, String albumCoverUrl, int popularity, String firstSeenAt) {
        this.variantId = variantId;
        this.songId = songId;
        this.spotifyUri = spotifyUri;
        this.albumId = albumId;
        this.albumName = albumName;
        this.albumCoverUrl = albumCoverUrl;
        this.popularity = popularity;
        this.firstSeenAt = firstSeenAt;
    }

    public long getVariantId() { return variantId; }
    public String getSongId() { return songId; }
    public String getSpotifyUri() { return spotifyUri; }
    public String getAlbumId() { return albumId; }
    public String getAlbumName() { return albumName; }
    public String getAlbumCoverUrl() { return albumCoverUrl; }
    public int getPopularity() { return popularity; }
    public String getFirstSeenAt() { return firstSeenAt; }
    public List<Artist> getArtists() { return artists; }
    public void setArtists(List<Artist> artists) { this.artists = artists; }
}
