package com.ca.tunaro.fragments;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ManagePlaylistsSheetSummaryTest {

    @Test
    public void noChanges() {
        assertEquals("No changes", ManagePlaylistsSheet.saveSummary(0, 0, 0));
    }

    @Test
    public void singularAndPlural() {
        assertEquals("Added to 1 playlist", ManagePlaylistsSheet.saveSummary(1, 0, 0));
        assertEquals("Removed from 3 playlists", ManagePlaylistsSheet.saveSummary(0, 3, 0));
    }

    @Test
    public void combined() {
        assertEquals("Added to 2 playlists, removed from 1 playlist. 1 change failed",
                ManagePlaylistsSheet.saveSummary(2, 1, 1));
    }

    @Test
    public void onlyFailures() {
        assertEquals("2 changes failed", ManagePlaylistsSheet.saveSummary(0, 0, 2));
    }
}
