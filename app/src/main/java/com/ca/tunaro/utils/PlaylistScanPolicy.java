package com.ca.tunaro.utils;

/**
 * Decides whether a playlist's stored songs must be refetched from Spotify. Pure logic, kept
 * separate from {@link PlaylistSetup} so it can be unit tested.
 */
public final class PlaylistScanPolicy {

    public enum Decision {
        /** Stored songs are current. */
        SKIP,
        /** Stored songs match by count and no snapshot was ever recorded: adopt the current one. */
        ADOPT_SNAPSHOT,
        /** Stored songs are stale or incomplete: rescan. */
        SCAN
    }

    private PlaylistScanPolicy() {
    }

    /**
     * @param remoteCount       latest Spotify track total
     * @param scannedCount      track total recorded at the last scan
     * @param snapshotId        latest Spotify snapshot_id, or null when unknown
     * @param scannedSnapshotId snapshot_id recorded at the last scan, or null when none
     * @param cachedCount       number of song IDs in the playlist cache (0 when nothing cached)
     */
    public static Decision decide(int remoteCount, int scannedCount, String snapshotId,
                                  String scannedSnapshotId, int cachedCount) {
        if (remoteCount <= 0 || scannedCount != remoteCount) return Decision.SCAN;
        // A truncated cache entry needs a rescan even when the DB link count is correct; the two
        // are written separately and can disagree.
        if (cachedCount > 0 && cachedCount != remoteCount) return Decision.SCAN;
        if (snapshotId == null) return Decision.SKIP;
        // Rows scanned before snapshot ids were stored: counts agree, so trust them once instead
        // of rescanning every playlist after the upgrade.
        if (scannedSnapshotId == null) return Decision.ADOPT_SNAPSHOT;
        return snapshotId.equals(scannedSnapshotId) ? Decision.SKIP : Decision.SCAN;
    }
}
