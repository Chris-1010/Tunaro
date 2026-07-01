package com.ca.tunaro.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ca.tunaro.managers.PlaybackModel.Action;
import com.ca.tunaro.managers.PlaybackModel.AddResult;
import com.ca.tunaro.managers.PlaybackModel.BoundaryMode;
import com.ca.tunaro.managers.PlaybackModel.NavDecision;
import com.ca.tunaro.managers.PlaybackModel.PlayReason;
import com.ca.tunaro.models.Playable;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PlaybackModelTest {

    // Minimal Playable fake: identity + playability are all the model needs.
    private static final class Song implements Playable {
        final String uri;
        final boolean playable;

        Song(String uri, boolean playable) {
            this.uri = uri;
            this.playable = playable;
        }

        @Override
        public String getUri() {
            return uri;
        }

        @Override
        public boolean isPlayable() {
            return playable;
        }
    }

    private static Song song(String id) {
        return new Song("spotify:track:" + id, true);
    }

    @Test
    public void freshConfirmedTrackBecomesCurrentAndIsRecorded() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");

        model.onTrackConfirmed(a, PlayReason.FRESH);

        assertSame(a, model.getCurrentSong());
        assertEquals(Collections.singletonList(a), model.getHistory());
    }

    @Test
    public void previousReplaysThePrecedingEntry() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");
        Song b = song("b");
        model.onTrackConfirmed(a, PlayReason.FRESH);
        model.onTrackConfirmed(b, PlayReason.FRESH);

        NavDecision<Song> decision = model.previous();

        assertEquals(Action.PLAY, decision.action);
        assertSame(a, decision.song);
        assertEquals(PlayReason.REPLAY, decision.reason);
    }

    @Test
    public void nextReplaysForwardWhenBehindLiveEdgeWithoutMutatingHistory() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");
        Song b = song("b");
        model.onTrackConfirmed(a, PlayReason.FRESH);
        model.onTrackConfirmed(b, PlayReason.FRESH);
        model.previous();

        NavDecision<Song> decision = model.next(BoundaryMode.RECOMMENDATIONS);

        assertEquals(Action.PLAY, decision.action);
        assertSame(b, decision.song);
        assertEquals(PlayReason.REPLAY, decision.reason);
        assertEquals(2, model.getHistory().size());
    }

    @Test
    public void previousAtStartOfHistoryRestartsCurrentSong() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");
        model.onTrackConfirmed(a, PlayReason.FRESH);

        NavDecision<Song> decision = model.previous();

        assertEquals(Action.SEEK_TO_START, decision.action);
        assertSame(a, model.getCurrentSong());
    }

    @Test
    public void nextAtLiveEdgeConsumesPrimaryQueueAsFresh() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");
        Song b = song("b");
        model.onTrackConfirmed(a, PlayReason.FRESH);
        model.addToQueue(b);

        NavDecision<Song> decision = model.next(BoundaryMode.RECOMMENDATIONS);

        assertEquals(Action.PLAY, decision.action);
        assertSame(b, decision.song);
        assertEquals(PlayReason.FRESH, decision.reason);
    }

    @Test
    public void nextAtLiveEdgeAdvancesSecondaryQueueAndAnchorWhenPrimaryEmpty() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");
        Song b = song("b");
        Song c = song("c");
        model.seedSecondary(Arrays.asList(a, b, c), 0);
        model.onTrackConfirmed(a, PlayReason.FRESH);

        NavDecision<Song> decision = model.next(BoundaryMode.RECOMMENDATIONS);

        assertEquals(Action.PLAY, decision.action);
        assertSame(b, decision.song);
        assertEquals(PlayReason.FRESH, decision.reason);
        assertEquals(1, model.getAnchor());
    }

    @Test
    public void primaryQueueTakesPriorityOverSecondaryAtLiveEdge() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");
        Song b = song("b");
        Song x = song("x");
        model.seedSecondary(Arrays.asList(a, b), 0);
        model.onTrackConfirmed(a, PlayReason.FRESH);
        model.addToQueue(x);

        NavDecision<Song> decision = model.next(BoundaryMode.RECOMMENDATIONS);

        assertSame(x, decision.song);
        assertEquals(PlayReason.FRESH, decision.reason);
        assertEquals(0, model.getAnchor());
    }

    @Test
    public void nextAtBoundaryInRecommendationsModeDelegatesToSpotify() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        model.onTrackConfirmed(song("a"), PlayReason.FRESH);

        NavDecision<Song> decision = model.next(BoundaryMode.RECOMMENDATIONS);

        assertEquals(Action.SKIP_TO_NEXT, decision.action);
    }

    @Test
    public void nextAtBoundaryInStopModePauses() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        model.onTrackConfirmed(song("a"), PlayReason.FRESH);

        NavDecision<Song> decision = model.next(BoundaryMode.STOP);

        assertEquals(Action.PAUSE, decision.action);
    }

    @Test
    public void freshConfirmBehindLiveEdgeTruncatesForwardHistory() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");
        Song b = song("b");
        Song c = song("c");
        Song d = song("d");
        model.onTrackConfirmed(a, PlayReason.FRESH);
        model.onTrackConfirmed(b, PlayReason.FRESH);
        model.onTrackConfirmed(c, PlayReason.FRESH);
        model.previous();
        model.previous();

        model.onTrackConfirmed(d, PlayReason.FRESH);

        assertEquals(Arrays.asList(a, d), model.getHistory());
        assertSame(d, model.getCurrentSong());
    }

    @Test
    public void replayConfirmDoesNotAppendToHistory() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");
        Song b = song("b");
        model.onTrackConfirmed(a, PlayReason.FRESH);
        model.onTrackConfirmed(b, PlayReason.FRESH);
        model.previous();

        model.onTrackConfirmed(a, PlayReason.REPLAY);

        assertEquals(Arrays.asList(a, b), model.getHistory());
        assertSame(a, model.getCurrentSong());
    }

    @Test
    public void freshConfirmCollapsesConsecutiveDuplicate() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song first = new Song("spotify:track:a", true);
        Song sameUriAgain = new Song("spotify:track:a", true);
        model.onTrackConfirmed(first, PlayReason.FRESH);

        model.onTrackConfirmed(sameUriAgain, PlayReason.FRESH);

        assertEquals(Collections.singletonList(first), model.getHistory());
    }

    @Test
    public void historyIsCappedAtMaxHistory() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        for (int i = 0; i < 250; i++) {
            model.onTrackConfirmed(song("s" + i), PlayReason.FRESH);
        }

        assertEquals(200, model.getHistory().size());
        assertSame(model.getHistory().get(199), model.getCurrentSong());
    }

    @Test
    public void hasPreviousIsFalseAtStartAndTrueOnceBehindLiveEdgeIsPossible() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        model.onTrackConfirmed(song("a"), PlayReason.FRESH);
        assertFalse(model.hasPrevious());

        model.onTrackConfirmed(song("b"), PlayReason.FRESH);
        assertTrue(model.hasPrevious());
    }

    @Test
    public void hasNextReflectsReplayQueueAndBoundaryMode() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        model.onTrackConfirmed(song("a"), PlayReason.FRESH);
        model.onTrackConfirmed(song("b"), PlayReason.FRESH);
        model.previous();
        // Behind the Live Edge: a replay is always available.
        assertTrue(model.hasNext(BoundaryMode.STOP));

        model.next(BoundaryMode.STOP); // back to Live Edge, empty queues
        assertFalse(model.hasNext(BoundaryMode.STOP));
        assertTrue(model.hasNext(BoundaryMode.RECOMMENDATIONS));

        model.addToQueue(song("c"));
        assertTrue(model.hasNext(BoundaryMode.STOP));
    }

    @Test
    public void addToQueuePlaysImmediatelyWhenNothingIsPlaying() {
        PlaybackModel<Song> model = new PlaybackModel<>();

        assertEquals(AddResult.PLAY_NOW, model.addToQueue(song("a")));
    }

    @Test
    public void addToQueueEnqueuesBehindTheCurrentSong() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        model.onTrackConfirmed(song("a"), PlayReason.FRESH);

        assertEquals(AddResult.ENQUEUED, model.addToQueue(song("b")));
    }

    @Test
    public void addToQueueRejectsDuplicateAndCurrentSong() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        model.onTrackConfirmed(new Song("spotify:track:a", true), PlayReason.FRESH);
        model.addToQueue(new Song("spotify:track:b", true));

        assertEquals(AddResult.REJECTED, model.addToQueue(new Song("spotify:track:b", true)));
        assertEquals(AddResult.REJECTED, model.addToQueue(new Song("spotify:track:a", true)));
    }

    @Test
    public void removingASecondarySongBeforeTheAnchorShiftsTheAnchorBack() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");
        Song b = song("b");
        Song c = song("c");
        model.seedSecondary(Arrays.asList(a, b, c), 2);
        model.onTrackConfirmed(c, PlayReason.FRESH);

        assertTrue(model.removeFromQueue(a));

        assertEquals(1, model.getAnchor());
    }

    @Test
    public void currentSongCannotBeRemovedFromTheQueue() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");
        model.seedSecondary(Arrays.asList(a, song("b")), 0);
        model.onTrackConfirmed(a, PlayReason.FRESH);

        assertFalse(model.removeFromQueue(a));
    }

    @Test
    public void seedSecondarySkipsUnplayableStartAndReturnsFirstPlayable() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song unplayable = new Song("spotify:track:bad", false);
        Song good = song("good");

        Song start = model.seedSecondary(Arrays.asList(unplayable, good), 0);

        assertSame(good, start);
        assertEquals(1, model.getAnchor());
    }

    @Test
    public void skipToSecondaryAnchorsToTheFoundSong() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        Song a = song("a");
        Song b = song("b");
        model.seedSecondary(Arrays.asList(a, b, song("c")), 0);
        model.onTrackConfirmed(a, PlayReason.FRESH);

        assertSame(b, model.skipToSecondary(b));
        assertEquals(1, model.getAnchor());
    }

    @Test
    public void skipToSecondaryReturnsNullWhenSongNotInQueue() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        model.seedSecondary(Arrays.asList(song("a"), song("b")), 0);

        assertNull(model.skipToSecondary(song("z")));
    }

    @Test
    public void clearQueueEmptiesBothQueuesAndResetsAnchor() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        model.seedSecondary(Arrays.asList(song("a"), song("b")), 0);
        model.onTrackConfirmed(song("a"), PlayReason.FRESH);
        model.addToQueue(song("x"));

        model.clearQueue();

        assertFalse(model.hasNextSong());
        assertEquals(-1, model.getAnchor());
    }

    @Test
    public void hasActiveQueueReflectsEitherQueueHavingSongs() {
        PlaybackModel<Song> model = new PlaybackModel<>();
        assertFalse(model.hasActiveQueue());

        model.seedSecondary(Arrays.asList(song("a"), song("b")), 0);
        assertTrue(model.hasActiveQueue());

        model.clearQueue();
        assertFalse(model.hasActiveQueue());
    }
}
