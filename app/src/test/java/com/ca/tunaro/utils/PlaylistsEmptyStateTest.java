package com.ca.tunaro.utils;

import static org.junit.Assert.assertEquals;

import com.ca.tunaro.utils.PlaylistsEmptyState.Kind;

import org.junit.Test;

public class PlaylistsEmptyStateTest {

    @Test
    public void visibleRowsMeanNoEmptyState() {
        assertEquals(Kind.NONE, PlaylistsEmptyState.resolve(false, 5, 3));
        assertEquals(Kind.NONE, PlaylistsEmptyState.resolve(true, 5, 2));
    }

    @Test
    public void noPlaylistsAtAll() {
        assertEquals(Kind.NO_PLAYLISTS, PlaylistsEmptyState.resolve(false, 0, 0));
    }

    @Test
    public void everythingArchived() {
        assertEquals(Kind.ALL_ARCHIVED, PlaylistsEmptyState.resolve(false, 4, 0));
    }

    @Test
    public void emptyArchiveView() {
        assertEquals(Kind.NO_ARCHIVED, PlaylistsEmptyState.resolve(true, 4, 0));
        assertEquals(Kind.NO_ARCHIVED, PlaylistsEmptyState.resolve(true, 0, 0));
    }
}
