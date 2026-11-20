package com.ca.tunaro.managers;

/**
 * Pure timing logic for playback position, kept free of Android types so it can
 * be unit tested on the JVM.
 *
 * <p>Spotify App Remote is only asked for the real position occasionally; in
 * between, the position is interpolated locally from the last synced anchor
 * ({@code anchorPosition + elapsed} while playing). The static end-of-song
 * helpers decide when to re-check the real position near the end of a track and
 * whether to auto-advance.
 */
public final class PlaybackClock {

    /** Auto-advance window: intercept once this little of the song is left. */
    public static final long END_LEAD_MS = 1500;
    /** Delay between catching the end of a song and advancing. */
    public static final long ADVANCE_DELAY_MS = 300;
    /** After an advance, ignore end-of-song checks for this long (stale positions). */
    public static final long ADVANCE_GRACE_MS = 3000;
    /** Floor for re-check delays so drift never degenerates into a busy loop. */
    public static final long MIN_RECHECK_MS = 50;

    /** {@link #decideEndOfSong} result: nothing to do until the next state change. */
    public static final long END_NONE = -1;
    /** {@link #decideEndOfSong} result: advance now (after {@link #ADVANCE_DELAY_MS}). */
    public static final long END_ADVANCE = 0;

    private long anchorPositionMs = 0;
    private long anchorTimeMs = 0;
    private long durationMs = 0;
    private boolean playing = false;

    /** Re-anchor the clock to a position reported (or requested) at {@code nowMs}. */
    public void sync(long positionMs, long durationMs, boolean playing, long nowMs) {
        this.anchorPositionMs = Math.max(0, positionMs);
        this.anchorTimeMs = nowMs;
        this.durationMs = durationMs;
        this.playing = playing;
    }

    /** The interpolated position at {@code nowMs}, clamped to the track duration. */
    public long positionAt(long nowMs) {
        long pos = anchorPositionMs;
        if (playing) {
            pos += Math.max(0, nowMs - anchorTimeMs);
        }
        if (durationMs > 0 && pos > durationMs) {
            pos = durationMs;
        }
        return pos;
    }

    public long remainingAt(long nowMs) {
        return durationMs - positionAt(nowMs);
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isPlaying() {
        return playing;
    }

    /**
     * When to take a fresh position reading for the end-of-song check, given the
     * interpolated time remaining. Returns -1 when no check is needed (paused or
     * duration unknown), otherwise a delay in ms (0 = check now).
     */
    public static long endCheckDelay(long remainingMs, boolean playing, long durationMs) {
        if (!playing || durationMs <= 0) return -1;
        return Math.max(0, remainingMs - END_LEAD_MS);
    }

    /**
     * Decide the end-of-song action from a fresh position reading.
     *
     * @param remainingMs      time left in the track per the fresh reading
     * @param playing          whether the track is actually playing
     * @param durationMs       track duration (0 or less = unknown)
     * @param eligible         whether an intercept is wanted at all (a next song or
     *                         Stop mode, not in snippet mode, no advance pending)
     * @param sinceAdvanceMs   time since the last navigation/advance
     * @return {@link #END_NONE}, {@link #END_ADVANCE}, or a positive delay after
     *         which to re-check with a fresh reading
     */
    public static long decideEndOfSong(long remainingMs, boolean playing, long durationMs,
                                       boolean eligible, long sinceAdvanceMs) {
        if (!playing || durationMs <= 0) return END_NONE;
        if (remainingMs > END_LEAD_MS) {
            return Math.max(MIN_RECHECK_MS, remainingMs - END_LEAD_MS);
        }
        if (!eligible) return END_NONE;
        if (sinceAdvanceMs < ADVANCE_GRACE_MS) {
            return Math.max(MIN_RECHECK_MS, ADVANCE_GRACE_MS - sinceAdvanceMs);
        }
        return END_ADVANCE;
    }
}
