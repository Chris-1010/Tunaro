package com.ca.tunaro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ca.tunaro.utils.PlaylistScanPolicy.Decision;

import org.junit.Test;

public class PlaylistScanPolicyTest {

    @Test
    public void skipsWhenCountsAndSnapshotMatch() {
        assertEquals(Decision.SKIP, PlaylistScanPolicy.decide(10, 10, "s1", "s1", 10, -1));
    }

    @Test
    public void scansWhenSnapshotChangedWithSameCount() {
        assertEquals(Decision.SCAN, PlaylistScanPolicy.decide(10, 10, "s2", "s1", 10, -1));
    }

    @Test
    public void scansWhenCountChanged() {
        assertEquals(Decision.SCAN, PlaylistScanPolicy.decide(11, 10, "s1", "s1", 10, -1));
    }

    @Test
    public void scansWhenNeverScanned() {
        assertEquals(Decision.SCAN, PlaylistScanPolicy.decide(0, 0, "s1", null, 0, -1));
    }

    @Test
    public void scansWhenCacheTruncated() {
        assertEquals(Decision.SCAN, PlaylistScanPolicy.decide(10, 10, "s1", "s1", 7, -1));
    }

    @Test
    public void emptyCacheDoesNotForceScan() {
        assertEquals(Decision.SKIP, PlaylistScanPolicy.decide(10, 10, "s1", "s1", 0, -1));
    }

    @Test
    public void adoptsSnapshotForRowsScannedBeforeSnapshotsExisted() {
        assertEquals(Decision.ADOPT_SNAPSHOT, PlaylistScanPolicy.decide(10, 10, "s1", null, 10, -1));
    }

    @Test
    public void skipsWhenSnapshotUnknown() {
        assertEquals(Decision.SKIP, PlaylistScanPolicy.decide(10, 10, null, null, 10, -1));
    }

    @Test
    public void stampedCacheSkipsWhenSpotifyTotalIncludesUncachedItems() {
        // 10 on Spotify, of which 2 are episodes or unavailable: 8 cached, stamped with 10.
        assertEquals(Decision.SKIP, PlaylistScanPolicy.decide(10, 10, "s1", "s1", 8, 10));
    }

    @Test
    public void stampedCacheScansWhenTotalMovedOn() {
        assertEquals(Decision.SCAN, PlaylistScanPolicy.decide(10, 10, "s1", "s1", 8, 9));
    }

    @Test
    public void cacheCompletenessUsesStampWhenPresent() {
        assertTrue(PlaylistScanPolicy.isCacheComplete(8, 10, 10));
        assertFalse(PlaylistScanPolicy.isCacheComplete(10, 9, 10));
    }

    @Test
    public void cacheCompletenessFallsBackToLengthWithoutStamp() {
        assertTrue(PlaylistScanPolicy.isCacheComplete(10, -1, 10));
        assertFalse(PlaylistScanPolicy.isCacheComplete(8, -1, 10));
    }

    @Test
    public void cacheCompleteWhenRemoteTotalUnknown() {
        assertTrue(PlaylistScanPolicy.isCacheComplete(3, -1, 0));
    }
}
