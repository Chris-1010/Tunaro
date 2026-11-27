package com.ca.tunaro.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure formatting helpers for album metadata, shared by the album page and the song details tab.
 * Kept free of Android types so they can be unit tested on the JVM.
 */
public final class AlbumFormat {
    public static final int COLOR_SINGLE = 0xFF4CAF50;
    public static final int COLOR_ALBUM = 0xFF2196F3;
    public static final int COLOR_COMPILATION = 0xFFFF9800;
    public static final int COLOR_UNKNOWN = 0xB3FFFFFF;

    private AlbumFormat() {
    }

    /** Text colour for an album type ("single" green, "album" blue, "compilation" orange). */
    public static int typeColor(String albumType) {
        if (albumType == null) return COLOR_UNKNOWN;
        switch (albumType.toLowerCase(Locale.ROOT)) {
            case "single": return COLOR_SINGLE;
            case "album": return COLOR_ALBUM;
            case "compilation": return COLOR_COMPILATION;
            default: return COLOR_UNKNOWN;
        }
    }

    /** "album" -> "Album"; null or blank stays null. */
    public static String typeLabel(String albumType) {
        if (albumType == null || albumType.trim().isEmpty()) return null;
        String t = albumType.trim();
        return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Four-digit year from a Spotify release date ("YYYY", "YYYY-MM" or "YYYY-MM-DD"), else null. */
    public static String releaseYear(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) return null;
        String year = releaseDate.substring(0, 4);
        for (int i = 0; i < 4; i++) {
            if (!Character.isDigit(year.charAt(i))) return null;
        }
        return year;
    }

    /** "1 song" / "12 songs". */
    public static String trackCountLabel(int count) {
        return count == 1 ? "1 song" : count + " songs";
    }

    /** Total running time: "45 sec", "43 min", "1 hr", "1 hr 12 min". Rounds to the nearest minute. */
    public static String totalDurationLabel(long totalMs) {
        if (totalMs <= 0) return null;
        long totalSec = Math.round(totalMs / 1000.0);
        if (totalSec < 60) return totalSec + " sec";
        long totalMin = Math.round(totalMs / 60000.0);
        long hours = totalMin / 60;
        long minutes = totalMin % 60;
        if (hours == 0) return minutes + " min";
        return minutes == 0 ? hours + " hr" : hours + " hr " + minutes + " min";
    }

    /** Header metadata line, e.g. "2019 · 12 songs · 43 min"; parts that are unknown are skipped. */
    public static String metaLine(String releaseDate, int trackCount, long totalMs) {
        List<String> parts = new ArrayList<>();
        String year = releaseYear(releaseDate);
        if (year != null) parts.add(year);
        if (trackCount > 0) parts.add(trackCountLabel(trackCount));
        String duration = totalDurationLabel(totalMs);
        if (duration != null) parts.add(duration);
        return String.join(" · ", parts);
    }

    /** Track length as a clock, e.g. 252000 -> "4:12"; null when unknown. */
    public static String clockDuration(long ms) {
        if (ms <= 0) return null;
        long totalSec = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", totalSec / 60, totalSec % 60);
    }

    /** Per-row line on the album page, e.g. "3 · 4:12 · 7 listens"; a track number of 0 is omitted. */
    public static String trackLine(int trackNumber, String duration, int listens) {
        List<String> parts = new ArrayList<>();
        if (trackNumber > 0) parts.add(String.valueOf(trackNumber));
        if (duration != null && !duration.isEmpty()) parts.add(duration);
        if (listens > 0) parts.add(listens == 1 ? "1 listen" : listens + " listens");
        return String.join(" · ", parts);
    }
}
