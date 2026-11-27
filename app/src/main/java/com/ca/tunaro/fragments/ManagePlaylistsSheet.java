package com.ca.tunaro.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;

import com.ca.tunaro.R;
import com.ca.tunaro.utils.AppExecutors;
import com.ca.tunaro.utils.UiFeedback;
import com.ca.tunaro.activites.MainActivity;
import com.ca.tunaro.adapters.ManagablePlaylistAdapter;
import com.ca.tunaro.database.DatabaseHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Bottom sheet for issue #86 — add/remove the current song to/from playlists in a grid.
 * Tapping playlist thumbnails toggles membership; Save writes the diff to Spotify and the
 * local DB. Swiping the sheet down (or tapping outside) discards pending changes.
 */
public class ManagePlaylistsSheet extends BottomSheetDialogFragment {

    public interface OnChangesSavedListener {
        void onChangesSaved();
    }

    private static final String ARG_SONG_URI = "song_uri";
    private static final String ARG_VARIANT_URIS = "variant_uris";

    private String songUri;
    private List<String> variantUris;
    private boolean includeArchived = false;
    // Bumped on each load so a slower, superseded read never overwrites a newer one.
    private int loadGeneration = 0;

    private RecyclerView recycler;
    private TextView emptyLabel;
    private MaterialButton saveButton;
    private ManagablePlaylistAdapter adapter;

    private OnChangesSavedListener savedListener;

    public static ManagePlaylistsSheet newInstance(String songUri, List<String> variantUris) {
        ManagePlaylistsSheet sheet = new ManagePlaylistsSheet();
        Bundle args = new Bundle();
        args.putString(ARG_SONG_URI, songUri);
        args.putStringArrayList(ARG_VARIANT_URIS, new ArrayList<>(variantUris));
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnChangesSavedListener(OnChangesSavedListener listener) {
        this.savedListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_manage_playlists, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        songUri = args != null ? args.getString(ARG_SONG_URI) : null;
        variantUris = args != null ? args.getStringArrayList(ARG_VARIANT_URIS) : new ArrayList<>();
        if (variantUris == null || variantUris.isEmpty()) {
            variantUris = new ArrayList<>();
            if (songUri != null) variantUris.add(songUri);
        }

        recycler = view.findViewById(R.id.manage_recycler);
        emptyLabel = view.findViewById(R.id.manage_empty);
        CheckBox showArchived = view.findViewById(R.id.manage_show_archived);

        recycler.setLayoutManager(new GridLayoutManager(getContext(), 3));

        attachFloatingSaveButton();

        showArchived.setOnCheckedChangeListener((buttonView, isChecked) -> {
            includeArchived = isChecked;
            loadPlaylists();
        });

        loadPlaylists();
    }

    /**
     * The Save button is pinned to the screen, not the sheet, so it stays in place as the
     * sheet drags from its initial height up to full screen. We achieve that by adding it
     * to the dialog window's decor view (which spans the whole window) rather than to the
     * sheet content, anchored a fixed distance from the bottom of the screen.
     */
    private void attachFloatingSaveButton() {
        Dialog dialog = getDialog();
        if (dialog == null || dialog.getWindow() == null) return;
        ViewGroup decor = (ViewGroup) dialog.getWindow().getDecorView();

        saveButton = (MaterialButton) LayoutInflater.from(getContext())
                .inflate(R.layout.view_manage_save_button, decor, false);
        saveButton.setOnClickListener(v -> saveChanges());

        int buttonWidth = Math.round(getResources().getDisplayMetrics().widthPixels * 0.6f);
        int bottomMarginPx = Math.round(28 * getResources().getDisplayMetrics().density);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                buttonWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = bottomMarginPx;
        decor.addView(saveButton, lp);
    }

    @Override
    public void onDestroyView() {
        // Remove the window-decor button so it isn't leaked across dialog recreation.
        if (saveButton != null && saveButton.getParent() instanceof ViewGroup) {
            ((ViewGroup) saveButton.getParent()).removeView(saveButton);
        }
        saveButton = null;
        super.onDestroyView();
    }

    // Read the playlists off the main thread, then bind on the UI thread if the sheet is
    // still showing and no newer load (e.g. toggling archived) has started.
    private void loadPlaylists() {
        final int generation = ++loadGeneration;
        final boolean archived = includeArchived;
        final List<String> uris = new ArrayList<>(variantUris);
        final Context ctx = requireContext().getApplicationContext();
        if (saveButton != null) saveButton.setVisibility(View.GONE);
        AppExecutors.io().execute(() -> {
            List<DatabaseHelper.ManagablePlaylist> playlists =
                    DatabaseHelper.getInstance(ctx).getManagablePlaylists(uris, archived);
            View root = getView();
            if (root == null) return;
            root.post(() -> {
                if (getView() == null || getContext() == null || generation != loadGeneration) return;
                bindPlaylists(playlists);
            });
        });
    }

    private void bindPlaylists(List<DatabaseHelper.ManagablePlaylist> playlists) {
        if (playlists.isEmpty()) {
            recycler.setVisibility(View.GONE);
            emptyLabel.setVisibility(View.VISIBLE);
        } else {
            recycler.setVisibility(View.VISIBLE);
            emptyLabel.setVisibility(View.GONE);
        }

        adapter = new ManagablePlaylistAdapter(getContext(), playlists, hasPendingChanges -> {
            if (saveButton != null) saveButton.setVisibility(hasPendingChanges ? View.VISIBLE : View.GONE);
        });
        recycler.setAdapter(adapter);
        // Reloading (e.g. toggling archived) resets selection to membership, so hide Save.
        if (saveButton != null) saveButton.setVisibility(View.GONE);
    }

    private void saveChanges() {
        if (adapter == null) return;
        List<DatabaseHelper.ManagablePlaylist> additions = adapter.getAdditions();
        List<DatabaseHelper.ManagablePlaylist> removals = adapter.getRemovals();
        if (additions.isEmpty() && removals.isEmpty()) {
            dismiss();
            return;
        }

        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            Toast.makeText(getContext(), "Not connected to Spotify yet", Toast.LENGTH_SHORT).show();
            return;
        }

        saveButton.setEnabled(false);
        // Resolved once here: later chain steps may run after the sheet has detached.
        final DatabaseHelper db = DatabaseHelper.getInstance(requireContext().getApplicationContext());

        AtomicInteger added = new AtomicInteger(0);
        AtomicInteger removed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        // The shared SpotifyApi uses a single-connection HTTP manager, so it can only service
        // one request at a time — firing all operations in parallel makes all but one fail.
        // Chain them so exactly one request is in flight at any moment.
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (DatabaseHelper.ManagablePlaylist playlist : additions) {
            chain = chain.thenCompose(ignored ->
                    addToPlaylist(mainActivity, db, playlist.playlistId)
                            .handle((ok, err) -> {
                                if (err == null) added.incrementAndGet(); else failed.incrementAndGet();
                                return null;
                            }));
        }

        for (DatabaseHelper.ManagablePlaylist playlist : removals) {
            chain = chain.thenCompose(ignored ->
                    removeFromPlaylist(mainActivity, db, playlist.playlistId)
                            .handle((ok, err) -> {
                                if (err == null) removed.incrementAndGet(); else failed.incrementAndGet();
                                return null;
                            }));
        }

        chain.whenComplete((ignored, err) -> finishSave(added.get(), removed.get(), failed.get()));
    }

    /** Adds the displayed song URI to the playlist on Spotify, then mirrors it locally. */
    private CompletableFuture<Void> addToPlaylist(MainActivity mainActivity, DatabaseHelper db,
                                                  String playlistId) {
        return mainActivity.executeWithTokenRefresh(
                () -> mainActivity.getSpotifyApi().addItemsToPlaylist(playlistId, new String[]{songUri}).build())
                .thenAccept(snapshot -> {
                    db.upsertSongPlaylistLink(songUri, playlistId, utcTimestamp());
                });
    }

    /**
     * Removes every variant URI actually present in the playlist (one API call), then
     * marks each removed variant in the local DB. This handles the case where more than
     * one variant of the same recording is in the playlist.
     */
    private CompletableFuture<Void> removeFromPlaylist(MainActivity mainActivity, DatabaseHelper db,
                                                       String playlistId) {
        // Look up the linked variants off the main thread before calling Spotify.
        return CompletableFuture.supplyAsync(
                () -> db.getActiveVariantsInPlaylist(variantUris, playlistId), AppExecutors.io())
                .thenCompose(variantsInPlaylist -> {
                    // Fall back to the displayed URI when there is no record of which variants are linked.
                    if (variantsInPlaylist.isEmpty()) variantsInPlaylist.add(songUri);

                    JsonArray tracks = new JsonArray();
                    for (String uri : variantsInPlaylist) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("uri", uri);
                        tracks.add(obj);
                    }

                    final List<String> removedUris = variantsInPlaylist;
                    return mainActivity.executeWithTokenRefresh(
                            () -> mainActivity.getSpotifyApi().removeItemsFromPlaylist(playlistId, tracks).build())
                            .thenAccept(snapshot -> {
                                for (String uri : removedUris) db.markSongRemovedFromPlaylist(uri, playlistId);
                            });
                });
    }

    private void finishSave(int added, int removed, int failed) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            // Show on the host Activity so the message outlives the sheet.
            UiFeedback.show(requireActivity(), saveSummary(added, removed, failed));

            if (savedListener != null) savedListener.onChangesSaved();
            dismiss();
        });
    }

    /** Friendly summary of a save, e.g. "Added to 2 playlists, removed from 1". */
    static String saveSummary(int added, int removed, int failed) {
        StringBuilder msg = new StringBuilder();
        if (added > 0) msg.append("Added to ").append(playlists(added));
        if (removed > 0) {
            msg.append(msg.length() > 0 ? ", removed from " : "Removed from ").append(playlists(removed));
        }
        if (failed > 0) {
            if (msg.length() > 0) msg.append(". ");
            msg.append(failed == 1 ? "1 change failed" : failed + " changes failed");
        }
        if (msg.length() == 0) msg.append("No changes");
        return msg.toString();
    }

    private static String playlists(int n) {
        return n == 1 ? "1 playlist" : n + " playlists";
    }

    private static String utcTimestamp() {
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return fmt.format(new java.util.Date());
    }
}
