package com.ca.tunaro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AlbumFormatTest {

    @Test
    public void typeColorMapsKnownTypesCaseInsensitively() {
        assertEquals(AlbumFormat.COLOR_SINGLE, AlbumFormat.typeColor("SINGLE"));
        assertEquals(AlbumFormat.COLOR_ALBUM, AlbumFormat.typeColor("album"));
        assertEquals(AlbumFormat.COLOR_COMPILATION, AlbumFormat.typeColor("Compilation"));
        assertEquals(AlbumFormat.COLOR_UNKNOWN, AlbumFormat.typeColor("appears_on"));
        assertEquals(AlbumFormat.COLOR_UNKNOWN, AlbumFormat.typeColor(null));
    }

    @Test
    public void typeLabelCapitalises() {
        assertEquals("Album", AlbumFormat.typeLabel("ALBUM"));
        assertNull(AlbumFormat.typeLabel(" "));
        assertNull(AlbumFormat.typeLabel(null));
    }

    @Test
    public void releaseYearHandlesAllPrecisions() {
        assertEquals("2019", AlbumFormat.releaseYear("2019"));
        assertEquals("2019", AlbumFormat.releaseYear("2019-03"));
        assertEquals("2019", AlbumFormat.releaseYear("2019-03-08"));
        assertNull(AlbumFormat.releaseYear("0"));
        assertNull(AlbumFormat.releaseYear("abcd-01"));
        assertNull(AlbumFormat.releaseYear(null));
    }

    @Test
    public void totalDurationLabelRoundsToMinutes() {
        assertNull(AlbumFormat.totalDurationLabel(0));
        assertEquals("45 sec", AlbumFormat.totalDurationLabel(45_000));
        assertEquals("43 min", AlbumFormat.totalDurationLabel(43 * 60_000 + 10_000));
        assertEquals("1 hr", AlbumFormat.totalDurationLabel(60 * 60_000));
        assertEquals("1 hr 12 min", AlbumFormat.totalDurationLabel(72 * 60_000));
    }

    @Test
    public void metaLineSkipsUnknownParts() {
        assertEquals("2019 · 12 songs · 43 min",
                AlbumFormat.metaLine("2019-03-08", 12, 43 * 60_000));
        assertEquals("1 song", AlbumFormat.metaLine(null, 1, 0));
        assertEquals("", AlbumFormat.metaLine(null, 0, 0));
    }

    @Test
    public void clockDurationPadsSeconds() {
        assertEquals("4:05", AlbumFormat.clockDuration(245_900));
        assertEquals("0:59", AlbumFormat.clockDuration(59_000));
        assertNull(AlbumFormat.clockDuration(0));
    }

    @Test
    public void trackLineOmitsMissingParts() {
        assertEquals("3 · 4:12 · 7 listens", AlbumFormat.trackLine(3, "4:12", 7));
        assertEquals("3 · 4:12 · 1 listen", AlbumFormat.trackLine(3, "4:12", 1));
        assertEquals("4:12", AlbumFormat.trackLine(0, "4:12", 0));
    }
}
