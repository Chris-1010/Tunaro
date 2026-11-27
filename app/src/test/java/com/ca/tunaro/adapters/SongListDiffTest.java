package com.ca.tunaro.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;

import com.ca.tunaro.models.SongModel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SongListDiffTest {

    private static SongModel song(String id, String name) {
        return new SongModel(id, name, "Artist", 1000, "spotify:track:" + id,
                "https://img/" + id, 50, "Album", "2020-01-01");
    }

    /** Counts dispatched operations by kind. */
    private static final class Counter implements ListUpdateCallback {
        int inserted, removed, moved, changed;
        @Override public void onInserted(int position, int count) { inserted += count; }
        @Override public void onRemoved(int position, int count) { removed += count; }
        @Override public void onMoved(int fromPosition, int toPosition) { moved++; }
        @Override public void onChanged(int position, int count, @Nullable Object payload) { changed += count; }
    }

    private static Counter diff(List<SongModel> oldList, List<SongModel> newList, boolean moves) {
        Counter counter = new Counter();
        DiffUtil.calculateDiff(new SongListDiff(oldList, newList), moves).dispatchUpdatesTo(counter);
        return counter;
    }

    @Test
    public void appendingSongsOnlyInserts() {
        List<SongModel> old = Arrays.asList(song("a", "A"), song("b", "B"));
        List<SongModel> next = new ArrayList<>(old);
        next.add(song("c", "C"));
        Counter c = diff(old, next, true);
        assertEquals(1, c.inserted);
        assertEquals(0, c.removed);
        assertEquals(0, c.changed);
    }

    @Test
    public void reorderingWithMovesDoesNotRebind() {
        SongModel a = song("a", "A"), b = song("b", "B"), c = song("c", "C");
        Counter counter = diff(Arrays.asList(a, b, c), Arrays.asList(c, b, a), true);
        assertTrue(counter.moved > 0);
        assertEquals(0, counter.changed);
        assertEquals(0, counter.inserted);
    }

    @Test
    public void equalCopiesAreSameContent() {
        assertTrue(SongListDiff.sameItem(song("a", "A"), song("a", "A")));
        assertTrue(SongListDiff.sameContent(song("a", "A"), song("a", "A")));
    }

    @Test
    public void renamedSongIsSameItemButChangedContent() {
        SongModel before = song("a", "A");
        SongModel after = song("a", "A (Remastered)");
        assertTrue(SongListDiff.sameItem(before, after));
        assertFalse(SongListDiff.sameContent(before, after));
        Counter c = diff(Arrays.asList(before), Arrays.asList(after), true);
        assertEquals(1, c.changed);
    }

    @Test
    public void largeListsFallBackToFullRebind() {
        assertTrue(SongListDiff.shouldDiff(1000, 1000));
        assertFalse(SongListDiff.shouldDiff(3000, 3000));
        assertFalse(SongListDiff.shouldDetectMoves(1500, 1500));
    }
}
