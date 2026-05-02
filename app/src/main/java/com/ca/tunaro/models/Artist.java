package com.ca.tunaro.models;

import java.io.Serializable;

public class Artist implements Serializable {
    private final String artistId;
    private final String name;

    public Artist(String artistId, String name) {
        this.artistId = artistId;
        this.name = name;
    }

    public String getArtistId() { return artistId; }
    public String getName() { return name; }
}
