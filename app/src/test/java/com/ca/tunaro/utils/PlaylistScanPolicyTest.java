package com.ca.tunaro.utils;

import static org.junit.Assert.assertEquals;

import com.ca.tunaro.utils.PlaylistScanPolicy.Decision;

import org.junit.Test;

public class PlaylistScanPolicyTest {

    @Test
    public void skipsWhenCountsAndSnapshotMatch() {
        assertEquals(Decision.SKIP, PlaylistScanPolicy.decide(10, 10, "s1", "s1", 10));
    }

    @Test
    public void scansWhenSnapshotChangedWithSameCount() {
        assertEquals(Decision.SCAN, PlaylistScanPolicy.decide(10, 10, "s2", "s1", 10));
    }

    @Test
    public void scansWhenCountChanged() {
        assertEquals(Decision.SCAN, PlaylistScanPolicy.decide(11, 10, "s1", "s1", 10));
    }

    @Test
    public void scansWhenNeverScanned() {
        assertEquals(Decision.SCAN, PlaylistScanPolicy.decide(0, 0, "s1", null, 0));
    }

    @Test
    public void scansWhenCacheTruncated() {
        assertEquals(Decision.SCAN, PlaylistScanPolicy.decide(10, 10, "s1", "s1", 7));
    }

    @Test
    public void emptyCacheDoesNotForceScan() {
        assertEquals(Decision.SKIP, PlaylistScanPolicy.decide(10, 10, "s1", "s1", 0));
    }

    @Test
    public void adoptsSnapshotForRowsScannedBeforeSnapshotsExisted() {
        assertEquals(Decision.ADOPT_SNAPSHOT, PlaylistScanPolicy.decide(10, 10, "s1", null, 10));
    }

    @Test
    public void skipsWhenSnapshotUnknown() {
        assertEquals(Decision.SKIP, PlaylistScanPolicy.decide(10, 10, null, null, 10));
    }
}
