package com.ca.tunaro.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ColorRequestCacheTest {

    @Test
    public void firstRequestStartsLoadAndLaterOnesJoinIt() {
        ColorRequestCache<String> cache = new ColorRequestCache<>(4);
        assertTrue(cache.enqueue("u", "a"));
        assertFalse(cache.enqueue("u", "b"));
        assertTrue(cache.enqueue("other", "c"));

        List<String> waiters = cache.complete("u", new int[]{1, 2});
        assertEquals(Arrays.asList("a", "b"), waiters);
        assertArrayEquals(new int[]{1, 2}, cache.get("u"));
    }

    @Test
    public void failureIsNotCachedAndAllowsRetry() {
        ColorRequestCache<String> cache = new ColorRequestCache<>(4);
        cache.enqueue("u", "a");
        assertEquals(Arrays.asList("a"), cache.complete("u", null));
        assertNull(cache.get("u"));
        assertTrue(cache.enqueue("u", "b"));
    }

    @Test
    public void completingTwiceReturnsNoWaitersTheSecondTime() {
        ColorRequestCache<String> cache = new ColorRequestCache<>(4);
        cache.enqueue("u", "a");
        cache.complete("u", new int[]{1, 2});
        assertTrue(cache.complete("u", null).isEmpty());
        assertArrayEquals(new int[]{1, 2}, cache.get("u"));
    }

    @Test
    public void evictsLeastRecentlyUsed() {
        ColorRequestCache<String> cache = new ColorRequestCache<>(2);
        cache.complete("a", new int[]{1, 1});
        cache.complete("b", new int[]{2, 2});
        cache.get("a"); // touch a so b becomes eldest
        cache.complete("c", new int[]{3, 3});
        assertEquals(2, cache.size());
        assertNull(cache.get("b"));
        assertArrayEquals(new int[]{1, 1}, cache.get("a"));
        assertArrayEquals(new int[]{3, 3}, cache.get("c"));
    }
}
