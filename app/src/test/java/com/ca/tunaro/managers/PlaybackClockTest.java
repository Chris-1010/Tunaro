package com.ca.tunaro.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackClockTest {

    @Test
    public void interpolatesWhilePlaying() {
        PlaybackClock clock = new PlaybackClock();
        clock.sync(10_000, 200_000, true, 1_000);
        assertEquals(10_000, clock.positionAt(1_000));
        assertEquals(10_750, clock.positionAt(1_750));
        assertEquals(189_250, clock.remainingAt(1_750));
    }

    @Test
    public void holdsPositionWhilePaused() {
        PlaybackClock clock = new PlaybackClock();
        clock.sync(10_000, 200_000, false, 1_000);
        assertEquals(10_000, clock.positionAt(60_000));
        assertFalse(clock.isPlaying());
    }

    @Test
    public void clampsToDurationAndIgnoresClockGoingBackwards() {
        PlaybackClock clock = new PlaybackClock();
        clock.sync(199_000, 200_000, true, 0);
        assertEquals(200_000, clock.positionAt(5_000));
        assertEquals(199_000, clock.positionAt(-100));
    }

    @Test
    public void unknownDurationIsNotClamped() {
        PlaybackClock clock = new PlaybackClock();
        clock.sync(5_000, 0, true, 0);
        assertEquals(6_000, clock.positionAt(1_000));
    }

    @Test
    public void resyncReplacesAnchor() {
        PlaybackClock clock = new PlaybackClock();
        clock.sync(10_000, 200_000, true, 0);
        clock.sync(50_000, 200_000, true, 1_000);
        assertEquals(50_500, clock.positionAt(1_500));
        assertTrue(clock.isPlaying());
    }

    @Test
    public void endCheckDelayArmsJustBeforeLeadWindow() {
        assertEquals(8_500, PlaybackClock.endCheckDelay(10_000, true, 200_000));
        assertEquals(0, PlaybackClock.endCheckDelay(1_000, true, 200_000));
        assertEquals(-1, PlaybackClock.endCheckDelay(10_000, false, 200_000));
        assertEquals(-1, PlaybackClock.endCheckDelay(10_000, true, 0));
    }

    @Test
    public void decideAdvancesInsideLeadWindow() {
        assertEquals(PlaybackClock.END_ADVANCE,
                PlaybackClock.decideEndOfSong(1_500, true, 200_000, true, 10_000));
        assertEquals(PlaybackClock.END_ADVANCE,
                PlaybackClock.decideEndOfSong(200, true, 200_000, true, 10_000));
    }

    @Test
    public void decideRechecksWhenTooEarly() {
        assertEquals(500, PlaybackClock.decideEndOfSong(2_000, true, 200_000, true, 10_000));
        // Drift just outside the window never busy-loops.
        assertEquals(PlaybackClock.MIN_RECHECK_MS,
                PlaybackClock.decideEndOfSong(1_501, true, 200_000, true, 10_000));
        // Too early is a re-check even when ineligible, so later state changes
        // (a song queued, snippet detached) are still picked up near the end.
        assertEquals(500, PlaybackClock.decideEndOfSong(2_000, true, 200_000, false, 10_000));
    }

    @Test
    public void decideDoesNothingWhenIneligiblePausedOrUnknown() {
        assertEquals(PlaybackClock.END_NONE,
                PlaybackClock.decideEndOfSong(1_000, true, 200_000, false, 10_000));
        assertEquals(PlaybackClock.END_NONE,
                PlaybackClock.decideEndOfSong(1_000, false, 200_000, true, 10_000));
        assertEquals(PlaybackClock.END_NONE,
                PlaybackClock.decideEndOfSong(1_000, true, 0, true, 10_000));
    }

    @Test
    public void decideWaitsOutGracePeriodAfterAdvance() {
        assertEquals(2_000, PlaybackClock.decideEndOfSong(1_000, true, 200_000, true, 1_000));
        assertEquals(PlaybackClock.MIN_RECHECK_MS,
                PlaybackClock.decideEndOfSong(1_000, true, 200_000, true, 2_990));
        assertEquals(PlaybackClock.END_ADVANCE,
                PlaybackClock.decideEndOfSong(1_000, true, 200_000, true, 3_000));
    }
}
