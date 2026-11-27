package com.ca.tunaro.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.utils.AppExecutors;
import com.ca.tunaro.utils.SnippetEditorInput;
import com.ca.tunaro.utils.StateMessage;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.R;
import com.ca.tunaro.utils.UiFeedback;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.models.SongSnippet;
import com.ca.tunaro.adapters.SongSnippetsAdapter;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.utils.ColorExtractor;
import com.ca.tunaro.utils.SnippetTheme;
import com.google.android.material.textfield.TextInputLayout;
import com.spotify.android.appremote.api.SpotifyAppRemote;

import java.util.ArrayList;
import java.util.List;

public class SongSnippetsFragment extends Fragment implements PlaybackManager.PlaybackListener {
    private View snippetCreationOverlay;
    private long snippetStartTime = -1;
    private long snippetEndTime = -1;
    private EditText startTimeInput, endTimeInput;
    private TextInputLayout startTimeLayout, endTimeLayout;
    private Button previewStartButton, previewEndButton, saveSnippetButton;
    private View snippetRangeOverlay, snippetStartMarker, snippetEndMarker;
    private SeekBar snippetSeekBar;
    private PlaybackManager playbackManager;

    private SpotifyAppRemote spotifyAppRemote;
    private SongModel song;
    private DatabaseHelper dbHelper;
    private RecyclerView snippetsRecyclerView;
    private Button addSnippetButton;
    private SongSnippetsAdapter snippetAdapter;
    private List<SongSnippet> snippets = new ArrayList<>();

    // edit/delete operations
    private SongSnippet editingSnippet;
    private ImageButton deleteSnippetButton;

    private Handler playbackUpdateHandler;
    private Runnable playbackUpdateRunnable;
    private boolean isUpdatingPlayback = false;

    private List<String> variantUris;
    private SnippetTheme currentTheme;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Context appContext;
    private int loadGeneration;
    private boolean emptyStateBound;

    /** Overlay show/hide motion, matching the design's 150-250ms fade/slide. */
    private static final long OVERLAY_ANIM_MS = 200;

    // Enabled only while the editor overlay is open, so Back closes it instead of leaving SongView.
    private final OnBackPressedCallback closeOverlayOnBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            hideSnippetCreationOverlay();
        }
    };

    public static SongSnippetsFragment newInstance(SongModel song, List<String> variantUris) {
        SongSnippetsFragment fragment = new SongSnippetsFragment();
        Bundle args = new Bundle();
        args.putString("songId", song.getId());
        args.putStringArrayList("variantUris", new java.util.ArrayList<>(variantUris));
        fragment.setArguments(args);
        fragment.song = song;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_song_snippets, container, false);

        spotifyAppRemote = PlaybackManager.getInstance().getSpotifyAppRemote();


        appContext = requireContext().getApplicationContext();
        dbHelper = DatabaseHelper.getInstance(appContext);
        if (getArguments() != null) {
            variantUris = getArguments().getStringArrayList("variantUris");
        }

        // Initialize the add snippet button and recycler view
        addSnippetButton = view.findViewById(R.id.addSnippetButton);
        snippetsRecyclerView = view.findViewById(R.id.snippetsRecyclerView);

        // Setup button click handler
        addSnippetButton.setOnClickListener(v -> showSnippetCreationOverlay());
        setupSnippetsList();

        loadSnippets();
        applySnippetTheme();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), closeOverlayOnBack);
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    /** Post {@code task} to the main thread, dropping it if the view has gone. */
    private void postToUi(Runnable task) {
        mainHandler.post(() -> {
            if (isAdded() && getView() != null) task.run();
        });
    }

    /**
     * Read the snippets off the main thread (merged across variants so every version's snippets
     * show), then refresh the list and the tab badge.
     */
    private void loadSnippets() {
        if (song == null) return;
        final int generation = ++loadGeneration;
        final List<String> uris = variantUris != null ? new ArrayList<>(variantUris) : null;
        final String songId = song.getId();
        AppExecutors.io().execute(() -> {
            List<SongSnippet> loaded = uris != null && uris.size() > 1
                    ? dbHelper.getSongSnippetsForUris(uris)
                    : dbHelper.getSongSnippets(songId);
            postToUi(() -> {
                if (generation != loadGeneration) return;
                snippets = new ArrayList<>(loaded);
                snippetAdapter.updateSnippets(loaded);
                if (!emptyStateBound) {
                    // Bound after the first read so "No snippets yet" never flashes while loading.
                    emptyStateBound = true;
                    StateMessage.bindEmptyState(snippetAdapter,
                            ((View) snippetsRecyclerView.getParent()).findViewById(R.id.snippets_empty),
                            R.drawable.ic_state_snippet, R.string.snippets_empty, R.string.snippets_empty_hint);
                }
                updateSnippetsBadge(loaded.size());
            });
        });
    }

    /**
     * Derive a colour theme for the snippet rows from the album cover, matching
     * the dynamic background used by {@code SongView}.
     */
    private void applySnippetTheme() {
        String coverUrl = song != null ? song.getAlbumCoverUrl() : null;
        if (coverUrl != null || song == null) {
            themeFromCover(coverUrl);
            return;
        }
        final String songId = song.getId();
        AppExecutors.io().execute(() -> {
            SongModel lean = dbHelper.getLeanSong(songId);
            String leanCover = lean != null ? lean.getAlbumCoverUrl() : null;
            postToUi(() -> themeFromCover(leanCover));
        });
    }

    private void themeFromCover(@Nullable String coverUrl) {
        if (coverUrl == null) {
            applyTheme(SnippetTheme.fallback());
            return;
        }
        ColorExtractor.extractColors(requireContext(), coverUrl, new ColorExtractor.ColorExtractionCallback() {
            @Override
            public void onColorExtracted(int dominantColor, int vibrantColor) {
                if (!isAdded() || getView() == null) return;
                applyTheme(SnippetTheme.from(vibrantColor, dominantColor));
            }

            @Override
            public void onError() {
                if (!isAdded() || getView() == null) return;
                applyTheme(SnippetTheme.fallback());
            }
        });
    }

    private void applyTheme(SnippetTheme theme) {
        currentTheme = theme;
        snippetAdapter.setTheme(theme);
        applyOverlayTheme();

        // Match the "Add Snippet" button to the vibrant accent.
        if (addSnippetButton != null) {
            addSnippetButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(theme.playButton));
            addSnippetButton.setTextColor(SnippetTheme.contrastColor(theme.playButton));
        }
    }

    /**
     * Tint the snippet-creation modal (card + primary buttons) to the album
     * theme. Safe to call before the overlay exists or before a theme arrives.
     */
    private void applyOverlayTheme() {
        if (snippetCreationOverlay == null || currentTheme == null) return;

        // Opaque, readable card fill: the album tint composited over a dark base.
        int cardColor = androidx.core.graphics.ColorUtils.compositeColors(
                currentTheme.rowBackground, ContextCompat.getColor(requireContext(), R.color.surface_raised));
        View card = snippetCreationOverlay.findViewById(R.id.snippet_controls_card);
        if (card instanceof androidx.cardview.widget.CardView) {
            ((androidx.cardview.widget.CardView) card).setCardBackgroundColor(cardColor);
        }

        // Title field: white-ish box/hint normally, accent when focused.
        View titleLayout = snippetCreationOverlay.findViewById(R.id.snippet_title_layout);
        if (titleLayout instanceof com.google.android.material.textfield.TextInputLayout) {
            com.google.android.material.textfield.TextInputLayout til =
                    (com.google.android.material.textfield.TextInputLayout) titleLayout;
            int unfocused = ContextCompat.getColor(requireContext(), R.color.text_secondary);
            int focused = currentTheme.seekbarProgress;
            android.content.res.ColorStateList boxColors = new android.content.res.ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_focused},
                            new int[]{}
                    },
                    new int[]{focused, unfocused});
            til.setBoxStrokeColorStateList(boxColors);
            til.setHintTextColor(boxColors);
            til.setDefaultHintTextColor(boxColors);
        }

        android.content.res.ColorStateList accent =
                android.content.res.ColorStateList.valueOf(currentTheme.playButton);

        // Filled primary buttons: accent background, contrast label.
        int onAccent = SnippetTheme.contrastColor(currentTheme.playButton);
        int[] primaryButtons = {R.id.set_start_button, R.id.set_end_button, R.id.save_snippet_button};
        for (int id : primaryButtons) {
            View b = snippetCreationOverlay.findViewById(id);
            if (b instanceof Button) {
                b.setBackgroundTintList(accent);
                ((Button) b).setTextColor(onAccent);
            }
        }

        // Outlined buttons (Preview Start/End, Cancel): accent text + stroke,
        // replacing the default purple.
        int[] outlinedButtons = {R.id.preview_start_button, R.id.preview_end_button, R.id.cancel_snippet_button};
        for (int id : outlinedButtons) {
            View b = snippetCreationOverlay.findViewById(id);
            if (b instanceof com.google.android.material.button.MaterialButton) {
                com.google.android.material.button.MaterialButton mb =
                        (com.google.android.material.button.MaterialButton) b;
                mb.setTextColor(currentTheme.seekbarProgress);
                mb.setStrokeColor(android.content.res.ColorStateList.valueOf(currentTheme.seekbarProgress));
                mb.setRippleColor(accent);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        PlaybackManager.getInstance().addListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        PlaybackManager.getInstance().removeListener(this);
    }

    // ----- PlaybackManager.PlaybackListener -----

    private String currentPlayingSongId;
    private boolean currentlyPlaying;

    @Override
    public void onPlaybackStateChanged(boolean isPlaying, SongModel currentSong) {
        currentPlayingSongId = currentSong != null ? currentSong.getId() : null;
        currentlyPlaying = isPlaying;
    }

    @Override
    public void onConnectionStateChanged(boolean isConnected) {
        // No-op: snippet rows only react to position.
    }

    @Override
    public void onPlaybackPositionChanged(long positionMs, long durationMs) {
        if (snippetAdapter != null) {
            snippetAdapter.updatePlaybackPosition(positionMs, currentPlayingSongId, currentlyPlaying);
        }
    }

    private void setupSnippetsList() {
        // Setup recycler view with the adapter
        snippetAdapter = new SongSnippetsAdapter(requireContext(), new ArrayList<>(), new SongSnippetsAdapter.OnSnippetActionListener() {
            @Override
            public void onPlaySnippet(SongSnippet snippet) {
                // Playback logic
                playSnippet(snippet);
            }

            @Override
            public void onPauseSnippet(SongSnippet snippet) {
                PlaybackManager.getInstance().pauseSnippet();
            }

            @Override
            public void onEditSnippet(SongSnippet snippet) {
                // Edit logic
                showEditSnippetDialog(snippet);
            }
        });

        snippetAdapter.setVariantUris(variantUris != null ? variantUris
                : java.util.Collections.singletonList(song.getId()));

        // Setup recycler view
        snippetsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        snippetsRecyclerView.setAdapter(snippetAdapter);
        emptyStateBound = false;
    }

    private void playSnippet(SongSnippet snippet) {
        PlaybackManager.getInstance().playSnippet(snippet);
    }

    private void showSnippetCreationOverlay() {
        // Initialize playback manager
        playbackManager = PlaybackManager.getInstance();

        // Find or create the overlay
        if (snippetCreationOverlay == null) {
            View parentView = requireActivity().findViewById(android.R.id.content);
            if (parentView instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) parentView;
                snippetCreationOverlay = LayoutInflater.from(requireContext())
                        .inflate(R.layout.snippet_creation_overlay, parent, false);
                parent.addView(snippetCreationOverlay);
                setupSnippetCreationOverlay();
                applyOverlayTheme();
            }
        }

        if (snippetCreationOverlay == null) return;

        // Reset state if creating a new snippet
        if (editingSnippet == null) {
            snippetStartTime = -1;
            snippetEndTime = -1;
            // Start blank rather than showing the last edited snippet's title.
            EditText titleInput = snippetCreationOverlay.findViewById(R.id.snippet_title_input);
            CheckBox includeInRankings = snippetCreationOverlay.findViewById(R.id.include_in_rankings);
            titleInput.setText("");
            includeInRankings.setChecked(true);
        }
        clearTimeErrors();
        updateTimeDisplays();
        updateSnippetMarkers();
        updateButtonStates();

        animateOverlayIn();
        closeOverlayOnBack.setEnabled(true);

        // Setup playback bar with current song
        setupSnippetPlaybackBar();
    }

    private void setupSnippetCreationOverlay() {
        EditText titleInput = snippetCreationOverlay.findViewById(R.id.snippet_title_input);
        CheckBox includeInRankings = snippetCreationOverlay.findViewById(R.id.include_in_rankings);

        previewStartButton = snippetCreationOverlay.findViewById(R.id.preview_start_button);
        previewEndButton = snippetCreationOverlay.findViewById(R.id.preview_end_button);
        Button setStartButton = snippetCreationOverlay.findViewById(R.id.set_start_button);
        Button setEndButton = snippetCreationOverlay.findViewById(R.id.set_end_button);
        saveSnippetButton = snippetCreationOverlay.findViewById(R.id.save_snippet_button);
        Button cancelButton = snippetCreationOverlay.findViewById(R.id.cancel_snippet_button);
        deleteSnippetButton = snippetCreationOverlay.findViewById(R.id.delete_snippet_button);

        snippetSeekBar = snippetCreationOverlay.findViewById(R.id.snippet_seekbar);
        snippetRangeOverlay = snippetCreationOverlay.findViewById(R.id.snippet_range_overlay);
        snippetStartMarker = snippetCreationOverlay.findViewById(R.id.snippet_start_marker);
        snippetEndMarker = snippetCreationOverlay.findViewById(R.id.snippet_end_marker);

        startTimeInput = snippetCreationOverlay.findViewById(R.id.start_time_input);
        endTimeInput = snippetCreationOverlay.findViewById(R.id.end_time_input);
        startTimeLayout = snippetCreationOverlay.findViewById(R.id.start_time_layout);
        endTimeLayout = snippetCreationOverlay.findViewById(R.id.end_time_layout);
        setupTimeInputListeners();

        // Setup seekbar
        snippetSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateSnippetMarkers();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (playbackManager.isConnected()) {
                    playbackManager.seekTo(seekBar.getProgress());
                }
            }
        });

        // Set Start button
        setStartButton.setOnClickListener(v -> {
            snippetStartTime = playbackManager.getCurrentPositionMs();
            clearTimeErrors();
            updateTimeDisplays();
            updateSnippetMarkers();
            updateButtonStates();
        });

        // Set End button
        setEndButton.setOnClickListener(v -> {
            long currentPos = playbackManager.getCurrentPositionMs();
            if (snippetStartTime != -1 && currentPos > snippetStartTime) {
                snippetEndTime = currentPos;
                endTimeLayout.setError(null);
                updateTimeDisplays();
                updateSnippetMarkers();
                updateButtonStates();
            } else {
                endTimeLayout.setError(SnippetEditorInput.errorText(SnippetEditorInput.NOT_AFTER_START));
            }
        });

        // Preview buttons
        previewStartButton.setOnClickListener(v -> {
            if (snippetStartTime != -1) {
                long previewEnd = Math.min(snippetStartTime + 3000, song.getDuration());
                SongSnippet previewSnippet = new SongSnippet(null, song.getId(), 1, "Preview",
                        snippetStartTime, previewEnd, false);
                playSnippet(previewSnippet);
            }
        });

        previewEndButton.setOnClickListener(v -> {
            if (snippetEndTime != -1) {
                long previewStart = Math.max(snippetEndTime - 3000, 0);
                SongSnippet previewSnippet = new SongSnippet(null, song.getId(), 1, "Preview",
                        previewStart, snippetEndTime, false);
                playSnippet(previewSnippet);
            }
        });

        // Save button: commit any typed times first, then validate in place so the
        // overlay (and what was typed) stays open on a bad value.
        saveSnippetButton.setOnClickListener(v -> {
            boolean startOk = commitStartInput();
            boolean endOk = commitEndInput();
            if (!startOk || !endOk) return;
            if (snippetStartTime == -1 || snippetEndTime == -1) {
                if (snippetStartTime == -1) startTimeLayout.setError("Set a start time");
                if (snippetEndTime == -1) endTimeLayout.setError("Set an end time");
                return;
            }

            String title = titleInput.getText() != null ? titleInput.getText().toString() : "";
            boolean includeRankings = includeInRankings.isChecked();

            if (editingSnippet != null) {
                // Edit a copy so the row bound in the adapter stays a faithful snapshot for diffing.
                SongSnippet updated = SnippetEditorInput.copy(editingSnippet);
                updated.setTitle(title);
                updated.setStartTime(snippetStartTime);
                updated.setEndTime(snippetEndTime);
                updated.setIncludeInRankings(includeRankings);
                AppExecutors.io().execute(() -> {
                    dbHelper.editSnippet(updated);
                    UiFeedback.show(appContext, "Snippet updated");
                    postToUi(this::loadSnippets);
                });
            } else {
                long snippetNo = SnippetEditorInput.nextSnippetNo(snippets, snippets.size());
                SongSnippet newSnippet = new SongSnippet(null, song.getId(), snippetNo, title,
                        snippetStartTime, snippetEndTime, includeRankings);
                AppExecutors.io().execute(() -> {
                    long id = dbHelper.addSnippet(newSnippet);
                    if (id == -1) {
                        UiFeedback.show(appContext, "Couldn't save the snippet");
                        return;
                    }
                    UiFeedback.show(appContext, "Snippet saved");
                    postToUi(this::loadSnippets);
                });
            }

            hideSnippetCreationOverlay();
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> hideSnippetCreationOverlay());

        // Delete button
        deleteSnippetButton.setOnClickListener(v -> {
            if (editingSnippet != null) {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Delete Snippet")
                        .setMessage("Are you sure you want to delete this snippet?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            if (editingSnippet == null) return;
                            final long snippetId = editingSnippet.getId();
                            // Drop any load already in flight so it cannot restore the deleted row.
                            ++loadGeneration;
                            snippets.removeIf(snippet -> snippet.getId() == snippetId);
                            snippetAdapter.updateSnippets(snippets);
                            updateSnippetsBadge(snippets.size());
                            hideSnippetCreationOverlay();
                            AppExecutors.io().execute(() -> {
                                dbHelper.deleteSnippet(snippetId);
                                UiFeedback.show(appContext, "Snippet deleted");
                                postToUi(this::loadSnippets);
                            });
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        // Play/pause button
        ImageButton playPauseButton = snippetCreationOverlay.findViewById(R.id.snippet_play_pause);
        playPauseButton.setOnClickListener(v -> {
            if (!isCorrectSongPlaying()) {
                // Wrong song is playing, play the correct song
                if (playbackManager.isConnected()) {
                    playbackManager.playSong(song);
                } else {
                    playbackManager.connectSpotify(requireContext(), () -> {
                        playbackManager.playSong(song);
                    });
                }
            } else {
                // Correct song is playing, toggle play/pause
                playbackManager.togglePlayPause();
            }
        });

        // Warning play button
        ImageButton warningPlayButton = snippetCreationOverlay.findViewById(R.id.snippet_warning_play_button);
        warningPlayButton.setOnClickListener(v -> {
            if (playbackManager.isConnected()) {
                playbackManager.playSong(song);
            } else {
                playbackManager.connectSpotify(requireContext(), () -> {
                    playbackManager.playSong(song);
                });
            }
        });
    }

    private void setupTimeInputListeners() {
        // IME "next" on start moves to end; "done" on end commits and closes the keyboard.
        startTimeInput.setOnEditorActionListener((v, actionId, event) -> {
            if (commitStartInput()) {
                endTimeInput.requestFocus();
            }
            return true;
        });

        endTimeInput.setOnEditorActionListener((v, actionId, event) -> {
            if (commitEndInput()) {
                endTimeInput.clearFocus();
                hideKeyboard();
            }
            return true;
        });

        // Also commit on focus loss; a bad value keeps its text and shows the error in the field.
        startTimeInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitStartInput();
        });
        endTimeInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitEndInput();
        });
    }

    /**
     * Validate the typed start time and apply it when valid. An empty field with no time set is
     * left alone. Returns false (with a field error) when the text is invalid.
     */
    private boolean commitStartInput() {
        if (startTimeInput == null || song == null) return true;
        String text = startTimeInput.getText() != null ? startTimeInput.getText().toString() : "";
        if (text.trim().isEmpty() && snippetStartTime == -1) {
            startTimeLayout.setError(null);
            return true;
        }
        long timeMs = SnippetEditorInput.parseTime(text);
        int result = SnippetEditorInput.validateStart(timeMs, song.getDuration());
        startTimeLayout.setError(SnippetEditorInput.errorText(result));
        if (result != SnippetEditorInput.VALID) return false;
        snippetStartTime = timeMs;
        updateSnippetMarkers();
        updateButtonStates();
        return true;
    }

    /** As {@link #commitStartInput()}, for the end time (which must also follow the start). */
    private boolean commitEndInput() {
        if (endTimeInput == null || song == null) return true;
        String text = endTimeInput.getText() != null ? endTimeInput.getText().toString() : "";
        if (text.trim().isEmpty() && snippetEndTime == -1) {
            endTimeLayout.setError(null);
            return true;
        }
        long timeMs = SnippetEditorInput.parseTime(text);
        int result = SnippetEditorInput.validateEnd(timeMs, snippetStartTime, song.getDuration());
        endTimeLayout.setError(SnippetEditorInput.errorText(result));
        if (result != SnippetEditorInput.VALID) return false;
        snippetEndTime = timeMs;
        updateSnippetMarkers();
        updateButtonStates();
        return true;
    }

    private void clearTimeErrors() {
        if (startTimeLayout != null) startTimeLayout.setError(null);
        if (endTimeLayout != null) endTimeLayout.setError(null);
    }

    private void hideKeyboard() {
        if (snippetCreationOverlay == null) return;
        InputMethodManager imm = ContextCompat.getSystemService(requireContext(), InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(snippetCreationOverlay.getWindowToken(), 0);
    }

    /** Fade the scrim in and slide the editor card up from slightly below. */
    private void animateOverlayIn() {
        View card = snippetCreationOverlay.findViewById(R.id.snippet_controls_card);
        float offset = getResources().getDimension(R.dimen.space_xl);
        snippetCreationOverlay.animate().cancel();
        card.animate().cancel();
        snippetCreationOverlay.setAlpha(0f);
        snippetCreationOverlay.setVisibility(View.VISIBLE);
        card.setTranslationY(offset);
        snippetCreationOverlay.animate().alpha(1f).setDuration(OVERLAY_ANIM_MS).withEndAction(null).start();
        card.animate().translationY(0f).setDuration(OVERLAY_ANIM_MS)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    /** Reverse of {@link #animateOverlayIn()}; the overlay goes GONE once the fade finishes. */
    private void animateOverlayOut() {
        final View overlay = snippetCreationOverlay;
        View card = overlay.findViewById(R.id.snippet_controls_card);
        float offset = getResources().getDimension(R.dimen.space_xl);
        overlay.animate().cancel();
        card.animate().cancel();
        card.animate().translationY(offset).setDuration(OVERLAY_ANIM_MS)
                .setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
        overlay.animate().alpha(0f).setDuration(OVERLAY_ANIM_MS).withEndAction(() -> {
            overlay.setVisibility(View.GONE);
            overlay.setAlpha(1f);
        }).start();
    }

    private void setupSnippetPlaybackBar() {
        ImageView albumCover = snippetCreationOverlay.findViewById(R.id.snippet_album_cover);
        TextView songName = snippetCreationOverlay.findViewById(R.id.snippet_song_name);
        TextView artistName = snippetCreationOverlay.findViewById(R.id.snippet_artist_name);
        ImageButton playPauseButton = snippetCreationOverlay.findViewById(R.id.snippet_play_pause);

        // Get the currently playing song, not the target snippet song
        SongModel currentlyPlaying = playbackManager.getCurrentSong();

        if (currentlyPlaying != null) {
            // Load currently playing song info
            Glide.with(requireContext()).load(currentlyPlaying.getAlbumCoverUrl()).into(albumCover);
            songName.setText(currentlyPlaying.getName());
            artistName.setText(currentlyPlaying.getArtist());

            // Setup seekbar with current song duration
            snippetSeekBar.setMax(currentlyPlaying.getDuration());
        } else {
            // No song playing - show snippet target song but indicate it's not playing
            Glide.with(requireContext()).load(song.getAlbumCoverUrl()).into(albumCover);
            songName.setText(song.getName());
            artistName.setText(song.getArtist());
            snippetSeekBar.setMax(song.getDuration());
        }

        snippetSeekBar.setProgress((int) playbackManager.getCurrentPositionMs());

        // Update play/pause button
        playPauseButton.setImageResource(playbackManager.isPlaying() ?
                R.drawable.pause_circle_filled : R.drawable.play_circle_filled);

        // Start listening for playback updates
        startPlaybackUpdates();
    }

    private void startPlaybackUpdates() {
        if (isUpdatingPlayback) {
            return;
        }

        isUpdatingPlayback = true;
        playbackUpdateHandler = new Handler(Looper.getMainLooper());
        playbackUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (snippetCreationOverlay != null && snippetCreationOverlay.getVisibility() == View.VISIBLE && isUpdatingPlayback) {
                    updatePlaybackUI();
                    playbackUpdateHandler.postDelayed(this, 100);
                } else {
                    isUpdatingPlayback = false;
                }
            }
        };
        playbackUpdateHandler.post(playbackUpdateRunnable);
    }

    private void stopPlaybackUpdates() {
        if (playbackUpdateHandler != null && playbackUpdateRunnable != null) {
            playbackUpdateHandler.removeCallbacks(playbackUpdateRunnable);
        }
        isUpdatingPlayback = false;
    }

    private void updatePlaybackUI() {
        if (playbackManager.isConnected()) {
            snippetSeekBar.setProgress((int) playbackManager.getCurrentPositionMs());

            ImageButton playPauseButton = snippetCreationOverlay.findViewById(R.id.snippet_play_pause);

            if (!isCorrectSongPlaying()) {
                // Wrong song playing - show play icon to indicate user should play this song
                playPauseButton.setImageResource(R.drawable.play_circle_filled);
            } else {
                // Correct song playing - show actual play/pause state
                playPauseButton.setImageResource(playbackManager.isPlaying() ?
                        R.drawable.pause_circle_filled : R.drawable.play_circle_filled);
            }

            // Update warning message
            updateSnippetCreationWarning();

            // Update song info in case it changed
            setupSnippetPlaybackBar();
        }
    }

    private void updateTimeDisplays() {
        String startText = snippetStartTime != -1 ? SnippetEditorInput.formatTime(snippetStartTime) : "";
        String endText = snippetEndTime != -1 ? SnippetEditorInput.formatTime(snippetEndTime) : "";

        startTimeInput.setText(startText);
        endTimeInput.setText(endText);
    }

    private void updateSnippetMarkers() {
        if (snippetSeekBar == null) return;

        int seekBarWidth = snippetSeekBar.getWidth() - snippetSeekBar.getPaddingLeft() - snippetSeekBar.getPaddingRight();
        int maxValue = snippetSeekBar.getMax();

        if (seekBarWidth <= 0 || maxValue <= 0) return;

        // Update start marker
        if (snippetStartTime != -1) {
            snippetStartMarker.setVisibility(View.VISIBLE);
            float startPercent = (float) snippetStartTime / maxValue;
            int startX = (int) (seekBarWidth * startPercent);
            snippetStartMarker.setTranslationX(startX);
        } else {
            snippetStartMarker.setVisibility(View.GONE);
        }

        // Update end marker
        if (snippetEndTime != -1) {
            snippetEndMarker.setVisibility(View.VISIBLE);
            float endPercent = (float) snippetEndTime / maxValue;
            int endX = (int) (seekBarWidth * endPercent);
            snippetEndMarker.setTranslationX(endX);
        } else {
            snippetEndMarker.setVisibility(View.GONE);
        }

        // Update range overlay
        if (snippetStartTime != -1 && snippetEndTime != -1) {
            snippetRangeOverlay.setVisibility(View.VISIBLE);
            float startPercent = (float) snippetStartTime / maxValue;
            float endPercent = (float) snippetEndTime / maxValue;

            int startX = (int) (seekBarWidth * startPercent);
            int endX = (int) (seekBarWidth * endPercent);
            int width = endX - startX;

            snippetRangeOverlay.setTranslationX(startX);
            ViewGroup.LayoutParams params = snippetRangeOverlay.getLayoutParams();
            params.width = width;
            snippetRangeOverlay.setLayoutParams(params);
        } else {
            snippetRangeOverlay.setVisibility(View.GONE);
        }
    }

    private void updateButtonStates() {
        previewStartButton.setEnabled(snippetStartTime != -1);
        previewEndButton.setEnabled(snippetEndTime != -1);
        saveSnippetButton.setEnabled(snippetStartTime != -1 && snippetEndTime != -1);
    }

    private void hideSnippetCreationOverlay() {
        closeOverlayOnBack.setEnabled(false);
        if (snippetCreationOverlay != null) {
            hideKeyboard();
            View focused = snippetCreationOverlay.findFocus();
            if (focused != null) focused.clearFocus();
            animateOverlayOut();
            stopPlaybackUpdates();
            clearTimeErrors();

            // Reset editing state
            editingSnippet = null;

            // Hide delete button
            if (deleteSnippetButton != null) {
                deleteSnippetButton.setVisibility(View.GONE);
            }

            // Reset button text
            Button saveButton = snippetCreationOverlay.findViewById(R.id.save_snippet_button);
            if (saveButton != null) {
                saveButton.setText("Save Snippet");
            }
        }
    }

    private void showEditSnippetDialog(SongSnippet snippet) {
        // Store the snippet being edited
        editingSnippet = snippet;

        // Set the current snippet times
        snippetStartTime = snippet.getStartTime();
        snippetEndTime = snippet.getEndTime();

        showSnippetCreationOverlay();

        // Pre-fill the form with existing snippet data
        if (snippetCreationOverlay != null) {
            EditText titleInput = snippetCreationOverlay.findViewById(R.id.snippet_title_input);
            CheckBox includeInRankings = snippetCreationOverlay.findViewById(R.id.include_in_rankings);

            titleInput.setText(snippet.getTitle());
            includeInRankings.setChecked(snippet.getIncludeInRankings());

            deleteSnippetButton.setVisibility(View.VISIBLE);

            // Update the time displays
            updateTimeDisplays();
            updateSnippetMarkers();
            updateButtonStates();

            // Change the save button text to indicate editing
            Button saveButton = snippetCreationOverlay.findViewById(R.id.save_snippet_button);
            saveButton.setText("Update Snippet");
        }
    }

    private void updateSnippetCreationWarning() {
        TextView warningText = snippetCreationOverlay.findViewById(R.id.snippet_warning_text);
        ImageButton warningPlayButton = snippetCreationOverlay.findViewById(R.id.snippet_warning_play_button);

        if (warningText != null && warningPlayButton != null) {
            if (!isCorrectSongPlaying()) {
                warningText.setVisibility(View.VISIBLE);
                warningText.setText("Play this song to create a snippet");
                warningText.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning));
                warningPlayButton.setVisibility(View.VISIBLE);
            } else {
                warningText.setVisibility(View.GONE);
                warningPlayButton.setVisibility(View.GONE);
            }
        }
    }

    private boolean isCorrectSongPlaying() {
        SongModel currentlyPlaying = playbackManager.getCurrentSong();
        return currentlyPlaying != null && currentlyPlaying.getId().equals(song.getId());
    }

    private void updateSnippetsBadge(int count) {
        if (getActivity() instanceof com.ca.tunaro.activites.SongView) {
            ((com.ca.tunaro.activites.SongView) getActivity()).updateTabBadge(2, count);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (snippetCreationOverlay != null && snippetCreationOverlay.getParent() != null) {
            ((ViewGroup) snippetCreationOverlay.getParent()).removeView(snippetCreationOverlay);
            snippetCreationOverlay = null;
        }

        stopPlaybackUpdates();
    }
}