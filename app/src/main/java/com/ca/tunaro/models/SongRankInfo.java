package com.ca.tunaro.models;

/**
 * A song's standing in the global Elo ranking: its dense position among all
 * rated songs (1 = highest) and its raw rating. Only rated songs have one.
 */
public class SongRankInfo {
    public final int rank;
    public final double rating;

    public SongRankInfo(int rank, double rating) {
        this.rank = rank;
        this.rating = rating;
    }
}
