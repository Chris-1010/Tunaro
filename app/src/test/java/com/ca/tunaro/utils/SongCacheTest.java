package com.ca.tunaro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ca.tunaro.models.SongModel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SongCacheTest {
    private static final long NOW = 1_000_000_000_000L;
    private static final long EXPIRY = SongCache.CACHE_EXPIRY_MS;

    private static SongModel song(String id) {
        return new SongModel(id, "name-" + id, new String[]{"artist"}, 1000, id, "cover");
    }

    @Test
    public void isExpired_boundary() {
        assertFalse(SongCache.isExpired(NOW, NOW));
        assertFalse(SongCache.isExpired(NOW - EXPIRY, NOW));
        assertTrue(SongCache.isExpired(NOW - EXPIRY - 1, NOW));
    }

    @Test
    public void selectFresh_skipsExpiredMissingAndUntimestamped() {
        Map<String, SongModel> songs = new HashMap<>();
        Map<String, Long> ts = new HashMap<>();
        songs.put("fresh", song("fresh"));
        ts.put("fresh", NOW - 10);
        songs.put("old", song("old"));
        ts.put("old", NOW - EXPIRY - 1);
        songs.put("noTs", song("noTs"));
        ts.put("noSong", NOW);

        Map<String, SongModel> result = SongCache.selectFresh(songs, ts,
                Arrays.asList("fresh", "old", "noTs", "noSong", "absent", null), NOW);

        assertEquals(Collections.singleton("fresh"), result.keySet());
        assertSame(songs.get("fresh"), result.get("fresh"));
    }

    @Test
    public void pruneExpired_dropsExpiredAndOrphans() {
        Map<String, SongModel> songs = new HashMap<>();
        Map<String, Long> ts = new HashMap<>();
        songs.put("fresh", song("fresh"));
        ts.put("fresh", NOW);
        songs.put("old", song("old"));
        ts.put("old", NOW - EXPIRY - 1);
        songs.put("orphan", song("orphan"));

        int removed = SongCache.pruneExpired(songs, ts, NOW);

        assertEquals(2, removed);
        assertEquals(Collections.singleton("fresh"), songs.keySet());
        assertEquals(Collections.singleton("fresh"), ts.keySet());
    }

    @Test
    public void orderedHits_preservesOrderAndCollectsMisses() {
        Map<String, SongModel> found = new HashMap<>();
        found.put("a", song("a"));
        found.put("c", song("c"));
        List<String> missing = new ArrayList<>();

        ArrayList<SongModel> hits = SongCache.orderedHits(Arrays.asList("c", "b", "a", "d"), found, missing);

        assertEquals(Arrays.asList("c", "a"), Arrays.asList(hits.get(0).getId(), hits.get(1).getId()));
        assertEquals(Arrays.asList("b", "d"), missing);
    }

    @Test
    public void orderedHits_mergeAfterFallbackFillsGapsInPlace() {
        List<String> ids = Arrays.asList("a", "b", "c");
        Map<String, SongModel> found = new HashMap<>();
        found.put("a", song("a"));
        List<String> missing = new ArrayList<>();
        SongCache.orderedHits(ids, found, missing);
        assertEquals(Arrays.asList("b", "c"), missing);

        found.put("b", song("b")); // e.g. recovered from the DB
        missing.clear();
        ArrayList<SongModel> hits = SongCache.orderedHits(ids, found, missing);

        assertEquals(2, hits.size());
        assertEquals("a", hits.get(0).getId());
        assertEquals("b", hits.get(1).getId());
        assertEquals(Collections.singletonList("c"), missing);
    }

    @Test
    public void copyConstructor_isolatesPlaylistContextDate() {
        SongModel original = song("x");
        SongModel copy = new SongModel(original);
        copy.setDateAddedToPlaylist(new Date(5));

        assertNull(original.getDateAddedToPlaylist());
        assertEquals("x", copy.getId());
        assertNotSame(original.getArtistArray(), copy.getArtistArray());
    }
}
