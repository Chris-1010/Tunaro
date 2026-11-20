package com.ca.tunaro.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LRU cache of extracted colours keyed by image URL, with de-duplication of
 * in-flight requests: concurrent requests for the same URL share one load and
 * every waiter is handed back on completion.
 *
 * Pure Java (no Android types) so it can be unit tested on the JVM.
 *
 * @param <W> waiter type, typically a callback
 */
final class ColorRequestCache<W> {

    private final Map<String, int[]> colors;
    private final Map<String, List<W>> pending = new HashMap<>();

    ColorRequestCache(final int maxEntries) {
        this.colors = new LinkedHashMap<String, int[]>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /** Cached colours for {@code key}, or null when not cached. */
    synchronized int[] get(String key) {
        return colors.get(key);
    }

    /**
     * Register {@code waiter} for {@code key}.
     *
     * @return true when this is the first waiter, i.e. the caller must start the load
     */
    synchronized boolean enqueue(String key, W waiter) {
        List<W> waiters = pending.get(key);
        if (waiters != null) {
            waiters.add(waiter);
            return false;
        }
        waiters = new ArrayList<>();
        waiters.add(waiter);
        pending.put(key, waiters);
        return true;
    }

    /**
     * Finish the load for {@code key}. Non-null {@code result} is cached; failures
     * are not, so a later request retries.
     *
     * @return every waiter registered for {@code key}, to be notified by the caller
     */
    synchronized List<W> complete(String key, int[] result) {
        if (result != null) {
            colors.put(key, result);
        }
        List<W> waiters = pending.remove(key);
        return waiters != null ? waiters : Collections.<W>emptyList();
    }

    synchronized int size() {
        return colors.size();
    }
}
