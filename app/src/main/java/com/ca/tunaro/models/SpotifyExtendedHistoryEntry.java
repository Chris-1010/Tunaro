package com.ca.tunaro.models;

import com.google.gson.annotations.SerializedName;

/**
 * Minimal model for Spotify extended streaming history entries.
 * Only captures fields needed for importing into listen_history table.
 */
public class SpotifyExtendedHistoryEntry {
    @SerializedName("ts")
    private String timestamp;

    @SerializedName("spotify_track_uri")
    private String spotifyTrackUri;

    @SerializedName("ms_played")
    private int msPlayed;

    public String getTimestamp() {
        return timestamp;
    }

    public String getSpotifyTrackUri() {
        return spotifyTrackUri;
    }

    public int getMsPlayed() {
        return msPlayed;
    }

    /**
     * Extracts the Spotify track ID from the spotify_track_uri.
     * URI format: "spotify:track:{id}"
     * @return Track ID or null if URI is invalid/missing
     */
    public String extractTrackId() {
        if (spotifyTrackUri == null || !spotifyTrackUri.startsWith("spotify:track:")) {
            return null;
        }
        return spotifyTrackUri.substring("spotify:track:".length());
    }

    /**
     * Checks if this entry is valid for import (has track URI and meaningful play time).
     * @return true if valid for import
     */
    public boolean isValid() {
        return spotifyTrackUri != null &&
               spotifyTrackUri.startsWith("spotify:track:") &&
               msPlayed > 0;
    }
}