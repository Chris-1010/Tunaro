package com.ca.tunaro.utils;

import androidx.annotation.Nullable;

import com.ca.tunaro.models.SongNote;

import java.util.Objects;

/** Pure helpers for the note editor dialog and note list diffing. */
public final class NoteEditorInput {
    /** Validation result: both fields are filled in. */
    public static final int VALID = 0;
    /** Bit set when the note type is blank. */
    public static final int MISSING_TYPE = 1;
    /** Bit set when the note content is blank. */
    public static final int MISSING_CONTENT = 1 << 1;

    private NoteEditorInput() {
    }

    /** Returns {@link #VALID} or a bitmask of the blank fields (after trimming). */
    public static int validate(@Nullable CharSequence type, @Nullable CharSequence content) {
        int result = VALID;
        if (trimmed(type).isEmpty()) result |= MISSING_TYPE;
        if (trimmed(content).isEmpty()) result |= MISSING_CONTENT;
        return result;
    }

    public static String trimmed(@Nullable CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }

    /** True when both rows represent the same stored note. */
    public static boolean sameNote(SongNote a, SongNote b) {
        return a.getId() == b.getId();
    }

    /** True when a row's visible text is unchanged. */
    public static boolean sameContents(SongNote a, SongNote b) {
        return Objects.equals(a.getNoteType(), b.getNoteType())
                && Objects.equals(a.getContent(), b.getContent())
                && Objects.equals(a.getTimestamp(), b.getTimestamp());
    }
}
