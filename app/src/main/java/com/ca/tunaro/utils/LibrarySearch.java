package com.ca.tunaro.utils;

import com.ca.tunaro.models.SongModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure search filter for the Library list: case-insensitive match on name, artist or album. */
public final class LibrarySearch {
    private LibrarySearch() {}

    /** Returns the songs matching {@code query}; a null/blank query returns every song. */
    public static List<SongModel> filter(List<SongModel> songs, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return new ArrayList<>(songs);

        List<SongModel> result = new ArrayList<>();
        for (SongModel song : songs) {
            if (matches(song.getName(), needle)
                    || matches(song.getArtist(), needle)
                    || matches(song.getAlbumName(), needle)) {
                result.add(song);
            }
        }
        return result;
    }

    // Lean rows come from LEFT JOINs, so artist/album can be null.
    private static boolean matches(String field, String needle) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needle);
    }
}
