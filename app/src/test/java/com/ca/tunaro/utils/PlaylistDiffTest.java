package com.ca.tunaro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlaylistDiffTest {

    @Test
    public void returnsStoredUrisMissingFromCurrent() {
        List<String> removed = PlaylistDiff.removedUris(
                Arrays.asList("a", "b", "c", "d"),
                Arrays.asList("d", "b", "e"));
        assertEquals(Arrays.asList("a", "c"), removed);
    }

    @Test
    public void nothingRemovedWhenAllStillPresent() {
        assertTrue(PlaylistDiff.removedUris(
                Arrays.asList("a", "b"), Arrays.asList("b", "a", "a")).isEmpty());
    }

    @Test
    public void everythingRemovedWhenCurrentIsEmpty() {
        assertEquals(Arrays.asList("a", "b"),
                PlaylistDiff.removedUris(Arrays.asList("a", "b"), Collections.<String>emptyList()));
    }

    @Test
    public void emptyStoredYieldsEmpty() {
        assertTrue(PlaylistDiff.removedUris(
                Collections.<String>emptyList(), Arrays.asList("a")).isEmpty());
    }
}
