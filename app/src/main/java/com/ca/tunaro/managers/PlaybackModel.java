package com.ca.tunaro.managers;

import com.ca.tunaro.models.Playable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure navigation model for Previous/Next over Play History.
 *
 * Owns the Play History, the Navigation Cursor, the Primary/Secondary queues and
 * the Anchor. Holds no Android or Spotify dependencies (it works against the
 * {@link Playable} contract) so the cursor logic can be tested directly. See
 * docs/adr/0001-cursor-based-prev-next-navigation.md.
 */
public class PlaybackModel<T extends Playable> {

    /** Why a track started playing, used by the centralized history recorder. */
    public enum PlayReason {
        /** A new selection (or a Spotify recommendation) that joins the timeline. */
        FRESH,
        /** Replaying an existing history entry via cursor navigation. */
        REPLAY
    }

    /** Outcome of {@link #addToQueue}. */
    public enum AddResult {
        /** Nothing was playing; the caller should start this song now. */
        PLAY_NOW,
        /** Appended to the Primary Queue behind the current song. */
        ENQUEUED,
        /** Already current or already queued; ignored. */
        REJECTED
    }

    /** The end-of-queue boundary policy (ADR-0002). */
    public enum BoundaryMode {
        /** Delegate forward to Spotify autoplay / skipToNext at the boundary. */
        RECOMMENDATIONS,
        /** Pause at the boundary. */
        STOP
    }

    /** What the translator (PlaybackManager) should do in response to navigation. */
    public enum Action {
        /** Play {@link NavDecision#song} with {@link NavDecision#reason}. */
        PLAY,
        /** Restart the current song from the beginning (dead-end Previous). */
        SEEK_TO_START,
        /** Delegate forward to Spotify (Recommendations boundary). */
        SKIP_TO_NEXT,
        /** Pause at the end of the queue (Stop boundary). */
        PAUSE
    }

    /** The outcome of a {@link #next} or {@link #previous} call. */
    public static final class NavDecision<S> {
        public final Action action;
        public final S song;
        public final PlayReason reason;

        private NavDecision(Action action, S song, PlayReason reason) {
            this.action = action;
            this.song = song;
            this.reason = reason;
        }

        public static <S> NavDecision<S> play(S song, PlayReason reason) {
            return new NavDecision<>(Action.PLAY, song, reason);
        }

        public static <S> NavDecision<S> of(Action action) {
            return new NavDecision<>(action, null, null);
        }
    }

    private static final int MAX_HISTORY = 200;

    private final List<T> primaryQueue = new ArrayList<>();
    private List<T> secondaryQueue = new ArrayList<>();
    private int secondaryIndex = -1;
    private final List<T> history = new ArrayList<>();
    private int cursor = -1;

    /**
     * Add an explicit (swipe-to-queue) song. With nothing playing, signals that
     * the caller should start it immediately; otherwise appends it to the Primary
     * Queue. A song that is already current or already queued is rejected.
     */
    public AddResult addToQueue(T song) {
        if (song == null || isCurrent(song) || isInQueue(song)) {
            return AddResult.REJECTED;
        }
        if (getCurrentSong() == null) {
            return AddResult.PLAY_NOW;
        }
        primaryQueue.add(song);
        return AddResult.ENQUEUED;
    }

    private boolean isCurrent(T song) {
        T current = getCurrentSong();
        return current != null && current.getUri().equals(song.getUri());
    }

    /**
     * True when the song is upcoming: in the Primary Queue, or ahead of the Anchor
     * in the Secondary Queue. The currently-playing song is never "in queue".
     */
    public boolean isInQueue(T song) {
        if (song == null || isCurrent(song)) {
            return false;
        }
        for (T s : primaryQueue) {
            if (s.getUri().equals(song.getUri())) {
                return true;
            }
        }
        for (int i = secondaryIndex + 1; i >= 0 && i < secondaryQueue.size(); i++) {
            if (secondaryQueue.get(i).getUri().equals(song.getUri())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Seed the Secondary Queue (playlist flow), skipping unplayable songs from
     * startIndex. Returns the song the Anchor lands on (to be played), or null if
     * nothing from startIndex onward is playable.
     */
    public T seedSecondary(List<T> songs, int startIndex) {
        secondaryQueue = new ArrayList<>(songs);
        while (startIndex < secondaryQueue.size() && !secondaryQueue.get(startIndex).isPlayable()) {
            startIndex++;
        }
        if (startIndex >= secondaryQueue.size()) {
            secondaryQueue.clear();
            secondaryIndex = -1;
            return null;
        }
        secondaryIndex = startIndex;
        return secondaryQueue.get(secondaryIndex);
    }

    /**
     * Move the Anchor to the given song if it is in the Secondary Queue, returning
     * it so the caller can play it; returns null when the song is not in the queue.
     */
    public T skipToSecondary(T song) {
        for (int i = 0; i < secondaryQueue.size(); i++) {
            if (secondaryQueue.get(i).getUri().equals(song.getUri())) {
                secondaryIndex = i;
                return secondaryQueue.get(i);
            }
        }
        return null;
    }

    /**
     * Remove a song from whichever queue holds it; the current song cannot be
     * removed. The Anchor is fixed up to stay on the same Secondary song.
     */
    public boolean removeFromQueue(T song) {
        if (song == null || isCurrent(song)) {
            return false;
        }
        for (int i = 0; i < primaryQueue.size(); i++) {
            if (primaryQueue.get(i).getUri().equals(song.getUri())) {
                primaryQueue.remove(i);
                return true;
            }
        }
        for (int i = 0; i < secondaryQueue.size(); i++) {
            if (secondaryQueue.get(i).getUri().equals(song.getUri())) {
                secondaryQueue.remove(i);
                if (i < secondaryIndex) {
                    secondaryIndex--;
                }
                if (secondaryQueue.isEmpty()) {
                    secondaryIndex = -1;
                }
                return true;
            }
        }
        return false;
    }

    /** Clear both queues and reset the Anchor. */
    public void clearQueue() {
        primaryQueue.clear();
        secondaryQueue.clear();
        secondaryIndex = -1;
    }

    /** Whether advancing at the Live Edge would land on a real next queued song. */
    public boolean hasNextSong() {
        return hasPlayableInQueues();
    }

    /** Whether either queue currently holds any songs (playable or not). */
    public boolean hasActiveQueue() {
        return !primaryQueue.isEmpty() || !secondaryQueue.isEmpty();
    }

    /** The Secondary Queue, used to draw the upcoming-playlist connecting line. */
    public List<T> getSecondaryQueue() {
        return secondaryQueue;
    }

    /** The Anchor: the Secondary Queue index the playlist flow is positioned at. */
    public int getAnchor() {
        return secondaryIndex;
    }

    /** Whether Previous would replay an earlier entry (vs. restart the current). */
    public boolean hasPrevious() {
        return cursor > 0;
    }

    /**
     * Whether Next would advance to another track (used to rubber-band a dead-end
     * swipe). Behind the Live Edge a replay is always available; at the Live Edge
     * it depends on the queues, and on Recommendations mode delegating to Spotify.
     */
    public boolean hasNext(BoundaryMode mode) {
        if (cursor < history.size() - 1) {
            return true;
        }
        if (hasPlayableInQueues()) {
            return true;
        }
        return mode == BoundaryMode.RECOMMENDATIONS;
    }

    /**
     * The song {@link #previous} would replay, without moving the cursor. Null at
     * the start of Play History (where Previous restarts the current song instead).
     * Used to render the carousel's incoming panel during a drag.
     */
    public T peekPrevious() {
        return cursor > 0 ? history.get(cursor - 1) : null;
    }

    /**
     * The song {@link #next} would land on, without mutating the cursor or queues.
     * Mirrors {@code next()}'s search order (forward replay, then Primary, then
     * Secondary) but skips unplayables non-destructively. Null at the end of the
     * queue, where the incoming track is unknown (Recommendations) or absent
     * (Stop) — {@link #hasNext} decides whether the swipe may commit.
     */
    public T peekNext() {
        if (cursor < history.size() - 1) {
            return history.get(cursor + 1);
        }
        for (T s : primaryQueue) {
            if (s.isPlayable()) {
                return s;
            }
        }
        for (int i = secondaryIndex + 1; i >= 0 && i < secondaryQueue.size(); i++) {
            if (secondaryQueue.get(i).isPlayable()) {
                return secondaryQueue.get(i);
            }
        }
        return null;
    }

    // Whether advancing at the Live Edge would land on a real next queued song.
    private boolean hasPlayableInQueues() {
        for (T s : primaryQueue) {
            if (s.isPlayable()) {
                return true;
            }
        }
        for (int i = secondaryIndex + 1; i >= 0 && i < secondaryQueue.size(); i++) {
            if (secondaryQueue.get(i).isPlayable()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Move the Navigation Cursor back one entry and replay it. Non-destructive:
     * the entry stays in history so a following Next returns to it.
     */
    public NavDecision<T> previous() {
        if (cursor <= 0) {
            return NavDecision.of(Action.SEEK_TO_START);
        }
        cursor--;
        return NavDecision.play(history.get(cursor), PlayReason.REPLAY);
    }

    /**
     * Advance one step. Behind the Live Edge this replays the next history entry
     * (non-destructive); at the Live Edge it consumes the queues (Primary before
     * Secondary); at the end of the queue it returns the boundary action.
     */
    public NavDecision<T> next(BoundaryMode mode) {
        if (cursor < history.size() - 1) {
            cursor++;
            return NavDecision.play(history.get(cursor), PlayReason.REPLAY);
        }
        // Live Edge: consume the queues. Primary (explicit adds) takes priority.
        while (!primaryQueue.isEmpty() && !primaryQueue.get(0).isPlayable()) {
            primaryQueue.remove(0);
        }
        if (!primaryQueue.isEmpty()) {
            return NavDecision.play(primaryQueue.remove(0), PlayReason.FRESH);
        }
        // Then the Secondary (playlist) flow: advance the Anchor past unplayables.
        int nextIndex = secondaryIndex + 1;
        while (nextIndex < secondaryQueue.size() && !secondaryQueue.get(nextIndex).isPlayable()) {
            nextIndex++;
        }
        if (nextIndex < secondaryQueue.size()) {
            secondaryIndex = nextIndex;
            return NavDecision.play(secondaryQueue.get(secondaryIndex), PlayReason.FRESH);
        }
        // End of queue: delegate forward to Spotify, or pause (ADR-0002).
        return NavDecision.of(mode == BoundaryMode.STOP ? Action.PAUSE : Action.SKIP_TO_NEXT);
    }

    /**
     * Record that a track change has landed. FRESH plays append at the Live Edge;
     * REPLAY plays only move the cursor (handled elsewhere) and are not appended.
     */
    public void onTrackConfirmed(T song, PlayReason reason) {
        if (reason == PlayReason.REPLAY) {
            // The cursor was already moved by next()/previous(); nothing to record.
            return;
        }
        // FRESH: a new selection. Drop forward history (browser-style) so the
        // timeline stays linear, then append at the new Live Edge.
        while (history.size() > cursor + 1) {
            history.remove(history.size() - 1);
        }
        // Collapse a consecutive duplicate so repeated confirms don't bloat history.
        if (!history.isEmpty() && history.get(cursor).getUri().equals(song.getUri())) {
            return;
        }
        history.add(song);
        cursor = history.size() - 1;
        // Cap the timeline; dropping the oldest entry shifts the cursor with it.
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
            cursor--;
        }
    }

    public T getCurrentSong() {
        return cursor >= 0 ? history.get(cursor) : null;
    }

    public List<T> getHistory() {
        return history;
    }
}
