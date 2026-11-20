package com.ca.tunaro.utils;

import static org.junit.Assert.assertEquals;

import com.ca.tunaro.models.SongModel;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LibrarySearchTest {

    private static SongModel song(String id, String name, String artist, String album) {
        return new SongModel(id, name, artist, 1000, "spotify:track:" + id, null, 0, album, null);
    }

    private final List<SongModel> songs = Arrays.asList(
            song("1", "Bohemian Rhapsody", "Queen", "A Night at the Opera"),
            song("2", "Idle Song", null, null),
            song("3", "Other", "Artist", "Opera Hits"));

    @Test
    public void blankQueryReturnsEverything() {
        assertEquals(3, LibrarySearch.filter(songs, null).size());
        assertEquals(3, LibrarySearch.filter(songs, "   ").size());
    }

    @Test
    public void matchesNameArtistAndAlbumCaseInsensitively() {
        assertEquals(Collections.singletonList(songs.get(0)), LibrarySearch.filter(songs, " QUEEN "));
        assertEquals(Arrays.asList(songs.get(0), songs.get(2)), LibrarySearch.filter(songs, "opera"));
        assertEquals(Collections.singletonList(songs.get(1)), LibrarySearch.filter(songs, "idle"));
    }

    @Test
    public void nullFieldsDoNotThrow() {
        assertEquals(0, LibrarySearch.filter(songs, "zzz").size());
    }

    @Test
    public void ignoresDefaultLocaleCaseRules() {
        Locale previous = Locale.getDefault();
        try {
            // Turkish lower-cases "I" to dotless "ı", which would break the "idle" match.
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals(Collections.singletonList(songs.get(1)), LibrarySearch.filter(songs, "IDLE"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
