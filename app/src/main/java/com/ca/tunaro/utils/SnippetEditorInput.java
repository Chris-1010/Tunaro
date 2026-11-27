package com.ca.tunaro.utils;

import androidx.annotation.Nullable;

import com.ca.tunaro.models.SongSnippet;

import java.util.Locale;
import java.util.Objects;

/** Pure parsing, validation and diff helpers for the snippet editor, kept free of Android views. */
public final class SnippetEditorInput {
    /** Validation results for a typed time. */
    public static final int VALID = 0;
    public static final int BAD_FORMAT = 1;
    public static final int PAST_END = 2;
    public static final int NOT_AFTER_START = 3;

    private SnippetEditorInput() {
    }

    /**
     * Parse {@code M:SS.mmm} (or {@code M:SS}, or {@code M.SS.mmm}) into milliseconds. Fractions shorter than three
     * digits are scaled ({@code .5} is 500ms) and longer ones truncated. Returns -1 if malformed.
     */
    public static long parseTime(@Nullable String text) {
        if (text == null) return -1;
        String s = text.trim();
        if (s.isEmpty()) return -1;
        // Some numeric keypads lack ':'; accept "M.SS.mmm" as the same time.
        if (s.indexOf(':') < 0 && s.indexOf('.') != s.lastIndexOf('.')) {
            int firstDot = s.indexOf('.');
            s = s.substring(0, firstDot) + ':' + s.substring(firstDot + 1);
        }
        String[] parts = s.split(":", -1);
        if (parts.length != 2 || parts[0].isEmpty()) return -1;
        try {
            int minutes = Integer.parseInt(parts[0]);
            String[] secParts = parts[1].split("\\.", -1);
            if (secParts.length > 2 || secParts[0].isEmpty()) return -1;
            int seconds = Integer.parseInt(secParts[0]);
            if (minutes < 0 || seconds < 0 || seconds > 59) return -1;
            int millis = 0;
            if (secParts.length == 2) {
                String frac = secParts[1];
                if (frac.isEmpty()) return -1;
                if (frac.length() > 3) frac = frac.substring(0, 3);
                millis = Integer.parseInt(frac);
                if (frac.length() == 1) millis *= 100;
                else if (frac.length() == 2) millis *= 10;
            }
            return minutes * 60_000L + seconds * 1000L + millis;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Format milliseconds as {@code M:SS.mmm}. */
    public static String formatTime(long timeMs) {
        long minutes = timeMs / 60_000L;
        long seconds = (timeMs % 60_000L) / 1000L;
        long millis = timeMs % 1000L;
        return String.format(Locale.getDefault(), "%d:%02d.%03d", minutes, seconds, millis);
    }

    /** Check a parsed start time ({@code -1} if malformed) against the song duration. */
    public static int validateStart(long startMs, long durationMs) {
        if (startMs < 0) return BAD_FORMAT;
        if (startMs >= durationMs) return PAST_END;
        return VALID;
    }

    /** Check a parsed end time against the duration and, if set ({@code != -1}), the start. */
    public static int validateEnd(long endMs, long startMs, long durationMs) {
        if (endMs < 0) return BAD_FORMAT;
        if (endMs >= durationMs) return PAST_END;
        if (startMs != -1 && endMs <= startMs) return NOT_AFTER_START;
        return VALID;
    }

    /** Field error text for a validation result, or null when valid. */
    @Nullable
    public static String errorText(int result) {
        switch (result) {
            case BAD_FORMAT:
                return "Use M:SS.mmm";
            case PAST_END:
                return "Past the end of the song";
            case NOT_AFTER_START:
                return "Must be after the start";
            default:
                return null;
        }
    }

    /** The next free snippet number: one past the highest in use, and at least size + 1. */
    public static long nextSnippetNo(Iterable<SongSnippet> existing, int size) {
        long next = size + 1L;
        for (SongSnippet s : existing) {
            if (s.getSnippetNo() >= next) next = s.getSnippetNo() + 1;
        }
        return next;
    }

    /** A detached copy, so edits do not mutate the instance an adapter has bound. */
    public static SongSnippet copy(SongSnippet s) {
        return new SongSnippet(s.getUuid(), s.getId(), s.getSongId(), s.getSnippetNo(), s.getTitle(),
                s.getStartTime(), s.getEndTime(), s.getIncludeInRankings());
    }

    /** Same row for DiffUtil: matched on database id. */
    public static boolean sameSnippet(SongSnippet a, SongSnippet b) {
        return a.getId() == b.getId();
    }

    /** Same visible contents for DiffUtil. */
    public static boolean sameContents(SongSnippet a, SongSnippet b) {
        return a.getSnippetNo() == b.getSnippetNo()
                && a.getStartTime() == b.getStartTime()
                && a.getEndTime() == b.getEndTime()
                && a.getIncludeInRankings() == b.getIncludeInRankings()
                && Objects.equals(a.getTitle(), b.getTitle())
                && Objects.equals(a.getUuid(), b.getUuid());
    }
}
