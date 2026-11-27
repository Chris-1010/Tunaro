package com.ca.tunaro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ca.tunaro.models.SongNote;

import org.junit.Test;

public class NoteEditorInputTest {
    @Test
    public void validateAcceptsFilledFields() {
        assertEquals(NoteEditorInput.VALID, NoteEditorInput.validate("Mood", "Great bridge"));
    }

    @Test
    public void validateFlagsBlankFieldsIndependently() {
        assertEquals(NoteEditorInput.MISSING_TYPE, NoteEditorInput.validate("  ", "text"));
        assertEquals(NoteEditorInput.MISSING_CONTENT, NoteEditorInput.validate("Mood", "\n "));
        assertEquals(NoteEditorInput.MISSING_TYPE | NoteEditorInput.MISSING_CONTENT,
                NoteEditorInput.validate(null, ""));
    }

    @Test
    public void trimmedHandlesNull() {
        assertEquals("", NoteEditorInput.trimmed(null));
        assertEquals("a b", NoteEditorInput.trimmed("  a b "));
    }

    @Test
    public void diffComparesIdThenVisibleText() {
        SongNote a = new SongNote("u", 1, "s", "Mood", "x", "01-01-2026 10:00");
        SongNote sameText = new SongNote("u", 1, "s", "Mood", "x", "01-01-2026 10:00");
        SongNote edited = new SongNote("u", 1, "s", "Mood", "y", "01-01-2026 10:05");
        SongNote other = new SongNote("v", 2, "s", "Mood", "x", "01-01-2026 10:00");

        assertTrue(NoteEditorInput.sameNote(a, sameText));
        assertTrue(NoteEditorInput.sameContents(a, sameText));
        assertFalse(NoteEditorInput.sameContents(a, edited));
        assertFalse(NoteEditorInput.sameNote(a, other));
    }
}
