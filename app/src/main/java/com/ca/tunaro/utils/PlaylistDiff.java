package com.ca.tunaro.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure set arithmetic used when reconciling stored playlist membership with Spotify. */
public final class PlaylistDiff {

    private PlaylistDiff() {}

    /**
     * Returns the stored URIs that are no longer present in the current track list,
     * preserving stored order. Runs in O(stored + current) via a hash lookup.
     */
    public static List<String> removedUris(Collection<String> stored, Collection<String> current) {
        Set<String> currentSet = new HashSet<>(current);
        List<String> removed = new ArrayList<>();
        for (String uri : stored) {
            if (!currentSet.contains(uri)) removed.add(uri);
        }
        return removed;
    }
}
