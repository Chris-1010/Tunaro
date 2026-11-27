package com.ca.tunaro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ca.tunaro.models.SongSnippet;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SnippetEditorInputTest {
    @Test
    public void parsesFullAndShortForms() {
        assertEquals(83_456, SnippetEditorInput.parseTime("1:23.456"));
        assertEquals(83_500, SnippetEditorInput.parseTime("1:23.5"));
        assertEquals(83_450, SnippetEditorInput.parseTime("1:23.45"));
        assertEquals(83_456, SnippetEditorInput.parseTime("1:23.4567"));
        assertEquals(83_000, SnippetEditorInput.parseTime(" 1:23 "));
        assertEquals(605_000, SnippetEditorInput.parseTime("10:05.000"));
        assertEquals(83_456, SnippetEditorInput.parseTime("1.23.456"));
    }

    @Test
    public void rejectsMalformed() {
        assertEquals(-1, SnippetEditorInput.parseTime(null));
        assertEquals(-1, SnippetEditorInput.parseTime(""));
        assertEquals(-1, SnippetEditorInput.parseTime("123"));
        assertEquals(-1, SnippetEditorInput.parseTime(":12.000"));
        assertEquals(-1, SnippetEditorInput.parseTime("1:"));
        assertEquals(-1, SnippetEditorInput.parseTime("1:2.3.4"));
        assertEquals(-1, SnippetEditorInput.parseTime("1:23."));
        assertEquals(-1, SnippetEditorInput.parseTime("1:75.000"));
        assertEquals(-1, SnippetEditorInput.parseTime("a:bc.def"));
    }

    @Test
    public void formatRoundTrips() {
        assertEquals("1:23.456", SnippetEditorInput.formatTime(83_456));
        assertEquals("0:00.000", SnippetEditorInput.formatTime(0));
        assertEquals(83_456, SnippetEditorInput.parseTime(SnippetEditorInput.formatTime(83_456)));
    }

    @Test
    public void validatesAgainstDurationAndStart() {
        assertEquals(SnippetEditorInput.VALID, SnippetEditorInput.validateStart(1000, 5000));
        assertEquals(SnippetEditorInput.BAD_FORMAT, SnippetEditorInput.validateStart(-1, 5000));
        assertEquals(SnippetEditorInput.PAST_END, SnippetEditorInput.validateStart(5000, 5000));

        assertEquals(SnippetEditorInput.VALID, SnippetEditorInput.validateEnd(2000, 1000, 5000));
        assertEquals(SnippetEditorInput.VALID, SnippetEditorInput.validateEnd(2000, -1, 5000));
        assertEquals(SnippetEditorInput.NOT_AFTER_START, SnippetEditorInput.validateEnd(1000, 1000, 5000));
        assertEquals(SnippetEditorInput.PAST_END, SnippetEditorInput.validateEnd(6000, 1000, 5000));
        assertEquals(SnippetEditorInput.BAD_FORMAT, SnippetEditorInput.validateEnd(-1, 1000, 5000));

        assertNull(SnippetEditorInput.errorText(SnippetEditorInput.VALID));
        assertEquals("Use M:SS.mmm", SnippetEditorInput.errorText(SnippetEditorInput.BAD_FORMAT));
    }

    @Test
    public void nextSnippetNoSkipsPastHighest() {
        SongSnippet a = new SongSnippet("u1", 1, "s", 1, "", 0, 1, false);
        SongSnippet c = new SongSnippet("u3", 3, "s", 5, "", 0, 1, false);
        assertEquals(1, SnippetEditorInput.nextSnippetNo(Collections.emptyList(), 0));
        assertEquals(6, SnippetEditorInput.nextSnippetNo(Arrays.asList(a, c), 2));
        assertEquals(2, SnippetEditorInput.nextSnippetNo(Collections.singletonList(a), 1));
    }

    @Test
    public void diffComparesIdThenVisibleFields() {
        SongSnippet a = new SongSnippet("u", 1, "s", 1, "Chorus", 1000, 2000, true);
        SongSnippet copy = SnippetEditorInput.copy(a);
        assertNotSame(a, copy);
        assertTrue(SnippetEditorInput.sameSnippet(a, copy));
        assertTrue(SnippetEditorInput.sameContents(a, copy));

        copy.setEndTime(2500);
        assertFalse(SnippetEditorInput.sameContents(a, copy));
        assertEquals(2000, a.getEndTime());

        SongSnippet other = new SongSnippet("v", 2, "s", 2, "Chorus", 1000, 2000, true);
        assertFalse(SnippetEditorInput.sameSnippet(a, other));
    }
}
