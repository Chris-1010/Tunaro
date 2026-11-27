package com.ca.tunaro.utils;

/** Picks which empty-state message the playlists screen shows for its current filter. */
public final class PlaylistsEmptyState {

    public enum Kind {
        /** The filtered list has rows: no empty state. */
        NONE,
        /** The account has no playlists at all. */
        NO_PLAYLISTS,
        /** Every playlist is archived, so the main view is empty. */
        ALL_ARCHIVED,
        /** The archived view has nothing in it. */
        NO_ARCHIVED
    }

    private PlaylistsEmptyState() {
    }

    public static Kind resolve(boolean showingArchived, int totalPlaylists, int visiblePlaylists) {
        if (visiblePlaylists > 0) return Kind.NONE;
        if (showingArchived) return Kind.NO_ARCHIVED;
        return totalPlaylists > 0 ? Kind.ALL_ARCHIVED : Kind.NO_PLAYLISTS;
    }
}
