package com.ca.tunaro.adapters;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.ca.tunaro.models.SongModel;

import java.util.List;
import java.util.Objects;

/**
 * DiffUtil callback for song rows, keyed by Spotify URI. Content equality covers every field a row
 * renders from the model, so an unchanged song keeps its bound view (and its loaded cover).
 */
public final class SongListDiff extends DiffUtil.Callback {

    /** Above this combined size a full rebind is cheaper than a Myers diff on the main thread. */
    static final int MAX_DIFF_ITEMS = 4000;

    private final List<SongModel> oldList;
    private final List<SongModel> newList;

    public SongListDiff(@NonNull List<SongModel> oldList, @NonNull List<SongModel> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    /** Whether diffing two lists of these sizes is worth it versus a full rebind. */
    public static boolean shouldDiff(int oldSize, int newSize) {
        return oldSize + newSize <= MAX_DIFF_ITEMS;
    }

    /** Whether move detection is affordable (it adds a quadratic pass over removed/inserted items). */
    public static boolean shouldDetectMoves(int oldSize, int newSize) {
        return oldSize + newSize <= MAX_DIFF_ITEMS / 2;
    }

    public static boolean sameItem(SongModel a, SongModel b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getUri(), b.getUri());
    }

    public static boolean sameContent(SongModel a, SongModel b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getId(), b.getId())
                && Objects.equals(a.getName(), b.getName())
                && Objects.equals(a.getArtist(), b.getArtist())
                && Objects.equals(a.getAlbumCoverUrl(), b.getAlbumCoverUrl())
                && Objects.equals(a.getDateAddedToPlaylist(), b.getDateAddedToPlaylist())
                && Objects.equals(a.getReleaseDate(), b.getReleaseDate())
                && a.getDuration() == b.getDuration()
                && a.getPopularity() == b.getPopularity();
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldPos, int newPos) {
        return sameItem(oldList.get(oldPos), newList.get(newPos));
    }

    @Override
    public boolean areContentsTheSame(int oldPos, int newPos) {
        return sameContent(oldList.get(oldPos), newList.get(newPos));
    }
}
