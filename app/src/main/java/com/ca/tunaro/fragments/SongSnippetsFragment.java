package com.ca.tunaro.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.R;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.models.SongSnippet;
import com.ca.tunaro.adapters.SongSnippetsAdapter;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.utils.ColorExtractor;
import com.ca.tunaro.utils.SnippetTheme;
import com.spotify.android.appremote.api.SpotifyAppRemote;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SongSnippetsFragment extends Fragment implements PlaybackManager.PlaybackListener {
    private static final String TAG = "SongSnippetsFragment";

    private View snippetCreationOverlay;
    private long snippetStartTime = -1;
    private long snippetEndTime = -1;
    private EditText startTimeInput, endTimeInput;
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


        dbHelper = new DatabaseHelper(requireContext());
        if (getArguments() != null) {
            variantUris = getArguments().getStringArrayList("variantUris");
        }

        // Initialize the add snippet button and recycler view
        addSnippetButton = view.findViewById(R.id.addSnippetButton);
        snippetsRecyclerView = view.findViewById(R.id.snippetsRecyclerView);

        // Setup button click handler
        addSnippetButton.setOnClickListener(v -> showSnippetCreationOverlay());
        setupSnippetsList();

        // Load snippets — merge across variants so all versions' snippets are visible
        snippets = variantUris != null && variantUris.size() > 1
                ? dbHelper.getSongSnippetsForUris(variantUris)
                : dbHelper.getSongSnippets(song.getId());
        snippetAdapter.updateSnippets(snippets);

        applySnippetTheme();

        return view;
    }

    /**
     * Derive a colour theme for the snippet rows from the album cover, matching
     * the dynamic background used by {@code SongView}.
     */
    private void applySnippetTheme() {
        String coverUrl = song != null ? song.getAlbumCoverUrl() : null;
        if (coverUrl == null && song != null) {
            SongModel lean = dbHelper.getLeanSong(song.getId());
            if (lean != null) coverUrl = lean.getAlbumCoverUrl();
        }
        if (coverUrl == null) {
            applyTheme(SnippetTheme.fallback());
            return;
        }
        ColorExtractor.extractColors(requireContext(), coverUrl, new ColorExtractor.ColorExtractionCallback() {
            @Override
            public void onColorExtracted(int dominantColor, int vibrantColor) {
                if (!isAdded()) return;
                applyTheme(SnippetTheme.from(vibrantColor, dominantColor));
            }

            @Override
            public void onError() {
                if (!isAdded()) return;
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
                currentTheme.rowBackground, Color.parseColor("#1A1A1A"));
        View card = snippetCreationOverlay.findViewById(R.id.snippet_controls_card);
        if (card instanceof androidx.cardview.widget.CardView) {
            ((androidx.cardview.widget.CardView) card).setCardBackgroundColor(cardColor);
        }

        // Title field: white-ish box/hint normally, accent when focused.
        View titleLayout = snippetCreationOverlay.findViewById(R.id.snippet_title_layout);
        if (titleLayout instanceof com.google.android.material.textfield.TextInputLayout) {
            com.google.android.material.textfield.TextInputLayout til =
                    (com.google.android.material.textfield.TextInputLayout) titleLayout;
            int unfocused = Color.parseColor("#B3FFFFFF");
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
        snippetAdapter = new SongSnippetsAdapter(requireContext(), snippets, new SongSnippetsAdapter.OnSnippetActionListener() {
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
            public void onDetachSnippet(SongSnippet snippet) {
                // Detach logic
                detachSnippet();
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
    }

    private void playSnippet(SongSnippet snippet) {
        PlaybackManager.getInstance().playSnippet(snippet);
    }

    private void detachSnippet() {
        PlaybackManager.getInstance().detachSnippet();
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

        // Reset state if creating a new snippet
        if (editingSnippet == null) {
            snippetStartTime = -1;
            snippetEndTime = -1;
        }
        updateTimeDisplays();
        updateSnippetMarkers();

        // Show the overlay
        snippetCreationOverlay.setVisibility(View.VISIBLE);

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
            updateTimeDisplays();
            updateSnippetMarkers();
            updateButtonStates();
        });

        // Set End button
        setEndButton.setOnClickListener(v -> {
            long currentPos = playbackManager.getCurrentPositionMs();
            if (snippetStartTime != -1 && currentPos > snippetStartTime) {
                snippetEndTime = currentPos;
                updateTimeDisplays();
                updateSnippetMarkers();
                updateButtonStates();
            } else {
                showToast("End time must be after start time");
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

        // Save button
        saveSnippetButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString();
            boolean includeRankings = includeInRankings.isChecked();

            if (snippetStartTime == -1 || snippetEndTime == -1) {
                showToast("Please set both start and end times");
                return;
            }

            if (editingSnippet != null) {
                // Update existing snippet
                editingSnippet.setTitle(title);
                editingSnippet.setStartTime(snippetStartTime);
                editingSnippet.setEndTime(snippetEndTime);
                editingSnippet.setIncludeInRankings(includeRankings);

                dbHelper.editSnippet(editingSnippet);

                // Update the list
                for (int i = 0; i < snippets.size(); i++) {
                    if (snippets.get(i).getId() == editingSnippet.getId()) {
                        snippets.set(i, editingSnippet);
                        break;
                    }
                }

                snippetAdapter.updateSnippets(snippets);
                showToast("Snippet updated");
                editingSnippet = null; // Clear the editing state
            } else {
                // Create new snippet (existing code)
                long snippetNo = snippets.size() + 1;
                for (SongSnippet existingSnippet : snippets) {
                    if (existingSnippet.getSnippetNo() >= snippetNo) {
                        snippetNo = existingSnippet.getSnippetNo() + 1;
                    }
                }

                SongSnippet newSnippet = new SongSnippet(null, song.getId(), snippetNo, title,
                        snippetStartTime, snippetEndTime, includeRankings);

                long id = dbHelper.addSnippet(newSnippet);
                if (id != -1) {
                    newSnippet.setId(id);
                    snippets.add(newSnippet);
                    snippetAdapter.updateSnippets(snippets);
                    updateSnippetsBadge();
                    showToast("Snippet saved");
                } else {
                    showToast("Error saving snippet");
                }
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
                            dbHelper.deleteSnippet(editingSnippet.getId());
                            snippets.removeIf(snippet -> snippet.getId() == editingSnippet.getId());
                            snippetAdapter.updateSnippets(snippets);
                            updateSnippetsBadge();
                            showToast("Snippet deleted");
                            hideSnippetCreationOverlay();
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
                    showToast("Playing " + song.getName() + " for snippet creation");
                } else {
                    playbackManager.connectSpotify(requireContext(), () -> {
                        playbackManager.playSong(song);
                        showToast("Playing " + song.getName() + " for snippet creation");
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
                showToast("Playing " + song.getName() + " for snippet creation");
            } else {
                playbackManager.connectSpotify(requireContext(), () -> {
                    playbackManager.playSong(song);
                    showToast("Playing " + song.getName() + " for snippet creation");
                });
            }
        });
    }

    private void setupTimeInputListeners() {
        startTimeInput.setOnEditorActionListener((v, actionId, event) -> {
            String timeText = startTimeInput.getText().toString();
            long timeMs = parseTimeString(timeText);
            if (timeMs != -1 && timeMs < song.getDuration()) {
                snippetStartTime = timeMs;
                updateSnippetMarkers();
                updateButtonStates();
                return true;
            } else {
                showToast("Invalid start time format. Use M:SS.mmm");
                updateTimeDisplays();
                return false;
            }
        });

        endTimeInput.setOnEditorActionListener((v, actionId, event) -> {
            String timeText = endTimeInput.getText().toString();
            long timeMs = parseTimeString(timeText);
            if (timeMs != -1 && timeMs < song.getDuration() && (snippetStartTime == -1 || timeMs > snippetStartTime)) {
                snippetEndTime = timeMs;
                updateSnippetMarkers();
                updateButtonStates();
                return true;
            } else {
                showToast("Invalid end time format or must be after start time");
                updateTimeDisplays();
                return false;
            }
        });

        // Also update on focus lost
        startTimeInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String timeText = startTimeInput.getText().toString();
                long timeMs = parseTimeString(timeText);
                if (timeMs != -1 && timeMs < song.getDuration()) {
                    snippetStartTime = timeMs;
                    updateSnippetMarkers();
                    updateButtonStates();
                } else {
                    updateTimeDisplays(); // Reset to valid value
                }
            }
        });

        endTimeInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String timeText = endTimeInput.getText().toString();
                long timeMs = parseTimeString(timeText);
                if (timeMs != -1 && timeMs < song.getDuration() && (snippetStartTime == -1 || timeMs > snippetStartTime)) {
                    snippetEndTime = timeMs;
                    updateSnippetMarkers();
                    updateButtonStates();
                } else {
                    updateTimeDisplays(); // Reset to valid value
                }
            }
        });
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
        playbackUpdateHandler = new Handler();
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
        String startText = snippetStartTime != -1 ? formatTimeWithMilliseconds(snippetStartTime) : "";
        String endText = snippetEndTime != -1 ? formatTimeWithMilliseconds(snippetEndTime) : "";

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
        if (snippetCreationOverlay != null) {
            snippetCreationOverlay.setVisibility(View.GONE);
            stopPlaybackUpdates();

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

    private long parseTimeString(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return -1;
        }

        try {
            // Expected format: M:SS.mmm or MM:SS.mmm
            String[] parts = timeStr.split(":");
            if (parts.length != 2) return -1;

            int minutes = Integer.parseInt(parts[0]);

            String[] secondsParts = parts[1].split("\\.");
            if (secondsParts.length != 2) return -1;

            int seconds = Integer.parseInt(secondsParts[0]);
            int milliseconds = Integer.parseInt(secondsParts[1]);

            // Ensure milliseconds is 3 digits (pad or truncate)
            if (secondsParts[1].length() == 1) {
                milliseconds *= 100;
            } else if (secondsParts[1].length() == 2) {
                milliseconds *= 10;
            } else if (secondsParts[1].length() > 3) {
                milliseconds = Integer.parseInt(secondsParts[1].substring(0, 3));
            }

            return (minutes * 60 * 1000L) + (seconds * 1000L) + milliseconds;

        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String formatTimeWithMilliseconds(long timeMs) {
        int totalMs = (int) timeMs;
        int minutes = totalMs / (60 * 1000);
        int seconds = (totalMs % (60 * 1000)) / 1000;
        int milliseconds = totalMs % 1000;

        return String.format(Locale.getDefault(), "%d:%02d.%03d", minutes, seconds, milliseconds);
    }

    private void updateSnippetCreationWarning() {
        TextView warningText = snippetCreationOverlay.findViewById(R.id.snippet_warning_text);
        ImageButton warningPlayButton = snippetCreationOverlay.findViewById(R.id.snippet_warning_play_button);

        if (warningText != null && warningPlayButton != null) {
            if (!isCorrectSongPlaying()) {
                warningText.setVisibility(View.VISIBLE);
                warningText.setText("Play this song to create a snippet");
                warningText.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
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

    private void updateSnippetsBadge() {
        if (getActivity() instanceof com.ca.tunaro.activites.SongView) {
            int count = (variantUris != null && variantUris.size() > 1
                    ? dbHelper.getSongSnippetsForUris(variantUris)
                    : dbHelper.getSongSnippets(song.getId())).size();
            ((com.ca.tunaro.activites.SongView) getActivity()).updateTabBadge(2, count);
        }
    }

    private void showToast(String message) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            Log.v(TAG, "showed Toast: " + message);
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