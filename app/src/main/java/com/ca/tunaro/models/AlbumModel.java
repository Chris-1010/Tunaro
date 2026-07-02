package com.ca.tunaro.models;

import java.io.Serializable;

/**
 * A single entry in an artist's discography (album or single), used by ArtistView's Albums tab.
 * Base fields come from {@code getArtistsAlbums} (AlbumSimplified); {@code trackCount} and
 * {@code popularity} are enriched later via {@code getSeveralAlbums} and may be -1 until then.
 */
public class AlbumModel implements Serializable {
    private final String albumId;
    private final String name;
    private final String albumType;   // "album" | "single" | "compilation"
    private final String releaseDate;  // "YYYY", "YYYY-MM", or "YYYY-MM-DD"
    private final String coverImageUrl;
    private int trackCount = -1;
    private int popularity = -1;

    public AlbumModel(String albumId, String name, String albumType, String releaseDate, String coverImageUrl) {
        this.albumId = albumId;
        this.name = name;
        this.albumType = albumType;
        this.releaseDate = releaseDate;
        this.coverImageUrl = coverImageUrl;
    }

    public String getAlbumId() { return albumId; }
    public String getName() { return name; }
    public String getAlbumType() { return albumType; }
    public String getReleaseDate() { return releaseDate; }
    public String getCoverImageUrl() { return coverImageUrl; }

    public int getTrackCount() { return trackCount; }
    public void setTrackCount(int trackCount) { this.trackCount = trackCount; }

    public int getPopularity() { return popularity; }
    public void setPopularity(int popularity) { this.popularity = popularity; }

    /** Year as an int for sorting; 0 if the release date is missing/unparseable. */
    public int getReleaseYear() {
        if (releaseDate == null || releaseDate.isEmpty()) return 0;
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (Exception e) {
            return 0;
        }
    }
}
