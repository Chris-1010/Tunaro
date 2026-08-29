package com.ca.tunaro.fragments;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.R;
import com.ca.tunaro.adapters.ManagablePlaylistAdapter;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.PlaylistModel;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Playlist picker for starting a ranking game. Reuses the "add to playlist" grid
 * (tap thumbnails to toggle, green border + tick when picked) but as a plain
 * multi-select: no membership diff, no Spotify writes. Confirm floats over the
 * window decor so it stays visible without dragging the sheet to full height, with
 * a live strip of the chosen covers — and the resulting song count — at the bottom.
 */
public class RankingPlaylistSheet extends BottomSheetDialogFragment {

    public interface OnPlaylistsConfirmedListener {
        void onPlaylistsConfirmed(List<String> playlistIds);
    }

    private RecyclerView recycler;
    private TextView emptyLabel;
    private MaterialButton confirmButton;
    private View selectedRow;
    private LinearLayout selectedIcons;
    private TextView songCountText;
    private ShimmerFrameLayout countShimmer;
    private ManagablePlaylistAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Guards against a slower song-count query overwriting a newer selection.
    private int countRequestToken = 0;

    private OnPlaylistsConfirmedListener confirmedListener;

    public void setOnPlaylistsConfirmedListener(OnPlaylistsConfirmedListener listener) {
        this.confirmedListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_ranking_playlists, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recycler = view.findViewById(R.id.ranking_playlist_recycler);
        emptyLabel = view.findViewById(R.id.ranking_playlist_empty);
        selectedRow = view.findViewById(R.id.ranking_selected_row);
        selectedIcons = view.findViewById(R.id.ranking_selected_icons);
        songCountText = view.findViewById(R.id.ranking_song_count);
        countShimmer = view.findViewById(R.id.ranking_count_shimmer);

        recycler.setLayoutManager(new GridLayoutManager(getContext(), 3));

        attachFloatingConfirmButton();

        loadPlaylists();
    }

    /**
     * Pin Confirm to the window (like the manage-playlists sheet's Save button) so it
     * stays in place as the sheet drags from its initial height up to full screen.
     * The button lives on the dialog window's decor view, not the sheet content.
     */
    private void attachFloatingConfirmButton() {
        Dialog dialog = getDialog();
        if (dialog == null || dialog.getWindow() == null) return;
        ViewGroup decor = (ViewGroup) dialog.getWindow().getDecorView();

        confirmButton = (MaterialButton) LayoutInflater.from(getContext())
                .inflate(R.layout.view_ranking_confirm_button, decor, false);
        confirmButton.setOnClickListener(v -> confirm());

        int buttonWidth = Math.round(getResources().getDisplayMetrics().widthPixels * 0.6f);
        int bottomMarginPx = Math.round(28 * getResources().getDisplayMetrics().density);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                buttonWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = bottomMarginPx;
        decor.addView(confirmButton, lp);
    }

    @Override
    public void onDestroyView() {
        // Remove the window-decor button so it isn't leaked across dialog recreation.
        if (confirmButton != null && confirmButton.getParent() instanceof ViewGroup) {
            ((ViewGroup) confirmButton.getParent()).removeView(confirmButton);
        }
        confirmButton = null;
        executor.shutdownNow();
        super.onDestroyView();
    }

    private void loadPlaylists() {
        DatabaseHelper db = new DatabaseHelper(requireContext());
        List<PlaylistModel> playlists = db.getNonArchivedPlaylists();
        db.close();

        // Wrap each playlist as an unselected grid cell (no song context, so
        // containsSong is always false and every pick is an "addition").
        List<DatabaseHelper.ManagablePlaylist> cells = new ArrayList<>();
        for (PlaylistModel playlist : playlists) {
            cells.add(new DatabaseHelper.ManagablePlaylist(
                    playlist.getId(), playlist.getPlaylistName(), playlist.getImage(),
                    playlist.isFavourite(), false));
        }

        boolean empty = cells.isEmpty();
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyLabel.setVisibility(empty ? View.VISIBLE : View.GONE);

        adapter = new ManagablePlaylistAdapter(getContext(), cells, hasPendingChanges -> updateSelectionUi());
        recycler.setAdapter(adapter);
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        List<DatabaseHelper.ManagablePlaylist> selected =
                adapter != null ? adapter.getAdditions() : new ArrayList<>();
        boolean any = !selected.isEmpty();

        if (confirmButton != null) confirmButton.setVisibility(any ? View.VISIBLE : View.GONE);
        selectedRow.setVisibility(any ? View.VISIBLE : View.GONE);

        selectedIcons.removeAllViews();
        if (!any) {
            countShimmer.stopShimmer();
            countShimmer.setVisibility(View.GONE);
            songCountText.setText("");
            return;
        }

        int size = Math.round(28 * getResources().getDisplayMetrics().density);
        int gap = Math.round(4 * getResources().getDisplayMetrics().density);
        List<String> selectedIds = new ArrayList<>();
        for (DatabaseHelper.ManagablePlaylist playlist : selected) {
            selectedIds.add(playlist.playlistId);

            ImageView icon = new ImageView(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, gap, 0);
            icon.setLayoutParams(lp);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this)
                    .load(playlist.imageUrl)
                    .placeholder(R.drawable.playlist_placeholder)
                    .error(R.drawable.playlist_placeholder)
                    .into(icon);
            selectedIcons.addView(icon);
        }

        updateSongCount(selectedIds);
    }

    // Count the distinct songs across the chosen playlists (the tournament's entrant
    // pool) off the main thread, ignoring any result that a newer selection outraced.
    private void updateSongCount(List<String> selectedIds) {
        songCountText.setVisibility(View.GONE);
        countShimmer.setVisibility(View.VISIBLE);
        countShimmer.startShimmer();
        final int token = ++countRequestToken;
        final Context ctx = requireContext().getApplicationContext();
        executor.execute(() -> {
            DatabaseHelper db = new DatabaseHelper(ctx);
            Set<String> union = new HashSet<>();
            for (String id : selectedIds) {
                union.addAll(db.getActiveSongUrisForPlaylist(id));
            }
            db.close();
            int count = union.size();
            View root = getView();
            if (root == null) return;
            root.post(() -> {
                if (token != countRequestToken) return;
                countShimmer.stopShimmer();
                countShimmer.setVisibility(View.GONE);
                songCountText.setText(count == 1 ? "1 song" : count + " songs");
                songCountText.setVisibility(View.VISIBLE);
            });
        });
    }

    private void confirm() {
        if (adapter == null) return;
        List<String> ids = new ArrayList<>();
        for (DatabaseHelper.ManagablePlaylist playlist : adapter.getAdditions()) {
            ids.add(playlist.playlistId);
        }
        if (ids.isEmpty()) return;
        if (confirmedListener != null) confirmedListener.onPlaylistsConfirmed(ids);
        dismiss();
    }
}
