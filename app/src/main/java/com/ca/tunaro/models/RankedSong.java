package com.ca.tunaro.models;

/**
 * A song paired with its final placement in a ranking game. Ranks are dense and
 * unique (1, 2, 3, …) — no two songs share a rank.
 */
public class RankedSong {
    public final int rank;
    public final SongModel song;

    public RankedSong(int rank, SongModel song) {
        this.rank = rank;
        this.song = song;
    }
}
