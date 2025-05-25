package com.ca.tunaro;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.spotify.android.appremote.api.SpotifyAppRemote;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SongSnippetsFragment extends Fragment {
    private SpotifyAppRemote spotifyAppRemote;
    private SongModel song;
    private DatabaseHelper dbHelper;
    private RecyclerView snippetsRecyclerView;
    private SongSnippetsAdapter snippetAdapter;
    private List<SongSnippet> snippets = new ArrayList<>();

    private int activeTimers = 0;
    private final android.os.Handler snippetHandler = new android.os.Handler();
    private Runnable pauseRunnable;

    public static SongSnippetsFragment newInstance(SongModel song) {
        SongSnippetsFragment fragment = new SongSnippetsFragment();
        Bundle args = new Bundle();
        args.putString("songId", song.getId());
        fragment.setArguments(args);
        fragment.song = song;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_song_snippets, container, false);

        spotifyAppRemote = PlaybackManager.getInstance().getSpotifyAppRemote();


        dbHelper = new DatabaseHelper(requireContext());

        // Initialize the add snippet button and recycler view
        Button addSnippetButton = view.findViewById(R.id.addSnippetButton);
        snippetsRecyclerView = view.findViewById(R.id.snippetsRecyclerView);

        // Setup button click handler
        addSnippetButton.setOnClickListener(v -> showAddSnippetDialog());
        setupSnippetsList();
        loadSnippets();

        return view;
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

        // Setup recycler view
        snippetsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        snippetsRecyclerView.setAdapter(snippetAdapter);
    }

    private void playSnippet(SongSnippet snippet) {
        PlaybackManager playbackManager = PlaybackManager.getInstance();

        if (spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
            // Cancel any existing timer
            if (pauseRunnable != null) {
                snippetHandler.removeCallbacks(pauseRunnable);
                activeTimers = 0; // Reset count when starting a new playback
            }
            if (!snippet.getSongId().equals(playbackManager.getCurrentSong().getId())) {
                spotifyAppRemote.getPlayerApi().play(song.getUri())
                        .setResultCallback(empty -> {
                            // Add a delay to ensure the song has loaded
                            new android.os.Handler().postDelayed(() -> {
                                // Pause first to make seeking more reliable
                                spotifyAppRemote.getPlayerApi().pause()
                                        .setResultCallback(pauseResult -> {
                                            // Another small delay to ensure pause completes
                                            new android.os.Handler().postDelayed(() -> {
                                                // Seek to the position
                                                spotifyAppRemote.getPlayerApi().seekTo(snippet.getStartTime())
                                                        .setResultCallback(seekResult -> {
                                                            // Resume playback after seeking
                                                            spotifyAppRemote.getPlayerApi().resume();
                                                            // Start a timer to pause after the duration
                                                            startSnippetEndTimer(snippet.getEndTime() - snippet.getStartTime());
                                                        });
                                            }, 100); // Delay after pause
                                        });
                            }, 100); // Delay after play
                        })
                        .setErrorCallback(throwable -> {
                            Toast.makeText(requireContext(),
                                    "Error playing song: " + throwable.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
            } else {
                // The currently requested snippet is from the same song currently being played
                if (playbackManager.isPlaying()) {
                    spotifyAppRemote.getPlayerApi().seekTo(snippet.getStartTime())
                            .setResultCallback(seekResult -> {
                                startSnippetEndTimer(snippet.getEndTime() - snippet.getStartTime());
                            });
                } else {
                    spotifyAppRemote.getPlayerApi().seekTo(snippet.getStartTime())
                            .setResultCallback(seekResult -> {
                                spotifyAppRemote.getPlayerApi().resume()
                                        .setResultCallback(playResult -> {
                                            startSnippetEndTimer(snippet.getEndTime() - snippet.getStartTime());
                                        });
                            });
                }
            }
        } else {
            Toast.makeText(requireContext(), "Connecting to Spotify...", Toast.LENGTH_SHORT).show();
            playbackManager.connectSpotify(requireContext(), () -> {
                // Get the updated remote after connection
                spotifyAppRemote = playbackManager.getSpotifyAppRemote();

                // Once connected, try to play the snippet again
                playSnippet(snippet);
            });
        }
    }

    private void startSnippetEndTimer(long duration) {
        activeTimers++;

        // Create a handler and runnable to pause playback after duration
        pauseRunnable = () -> {
            // Decrement the counter
            activeTimers--;

            // Only pause if this is the last timer
            if (activeTimers <= 0) {
                activeTimers = 0; // Ensure non-negative
                if (spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
                    spotifyAppRemote.getPlayerApi().pause();
                }
            }
        };

        // Schedule the runnable
        snippetHandler.postDelayed(pauseRunnable, duration);
    }

    private void detachSnippet() {
        if (pauseRunnable != null) {
            snippetHandler.removeCallbacks(pauseRunnable);
            activeTimers = 0; // Reset count when starting a new playback
        }

        Toast.makeText(requireContext(), "Playback will continue after snippet end", Toast.LENGTH_SHORT).show();
    }

    private void showAddSnippetDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_snippet, null);

        // Get references to all the views
        ImageView previewAlbumCover = dialogView.findViewById(R.id.preview_album_cover);
        TextView previewSongTitle = dialogView.findViewById(R.id.preview_song_title);
        TextView previewArtist = dialogView.findViewById(R.id.preview_artist);

        EditText titleInput = dialogView.findViewById(R.id.snippet_title_input);
        CheckBox includeInRankingsCheckbox = dialogView.findViewById(R.id.include_in_rankings);

        // Set up the preview section
        Glide.with(requireContext())
                .load(song.getAlbumCoverUrl())
                .into(previewAlbumCover);
        previewSongTitle.setText(song.getName());
        previewArtist.setText(song.getArtist());

        CheckBox previewStartCheckbox = dialogView.findViewById(R.id.preview_start_checkbox);
        CheckBox previewEndCheckbox = dialogView.findViewById(R.id.preview_end_checkbox);

        // Get song duration and break down into components
        int totalDurationMs = song.getDuration();
        int totalSeconds = totalDurationMs / 1000;
        int maxMinutes = totalSeconds / 60;
        int maxSecondsInLastMinute = totalSeconds % 60;

        // Initialize start time pickers
        NumberPicker startMinutesPicker = dialogView.findViewById(R.id.start_minutes_picker);
        NumberPicker startSecondsPicker = dialogView.findViewById(R.id.start_seconds_picker);
        NumberPicker startMillisecondsPicker = dialogView.findViewById(R.id.start_milliseconds_picker);

        // Initialize end time pickers
        NumberPicker endMinutesPicker = dialogView.findViewById(R.id.end_minutes_picker);
        NumberPicker endSecondsPicker = dialogView.findViewById(R.id.end_seconds_picker);
        NumberPicker endMillisecondsPicker = dialogView.findViewById(R.id.end_milliseconds_picker);

        // Configure minutes pickers
        startMinutesPicker.setMinValue(0);
        startMinutesPicker.setMaxValue(maxMinutes);

        endMinutesPicker.setMinValue(0);
        endMinutesPicker.setMaxValue(maxMinutes);

        // Configure seconds pickers (initially full range)
        startSecondsPicker.setMinValue(0);
        startSecondsPicker.setMaxValue(59);

        endSecondsPicker.setMinValue(0);
        endSecondsPicker.setMaxValue(59);

        // Configure milliseconds pickers (using steps of 100ms for better usability)
        startMillisecondsPicker.setMinValue(0);
        startMillisecondsPicker.setMaxValue(9);
        startMillisecondsPicker.setDisplayedValues(new String[]{"000", "100", "200", "300", "400", "500", "600", "700", "800", "900"});

        endMillisecondsPicker.setMinValue(0);
        endMillisecondsPicker.setMaxValue(9);
        endMillisecondsPicker.setDisplayedValues(new String[]{"000", "100", "200", "300", "400", "500", "600", "700", "800", "900"});

        // Add listeners to handle constraints between minutes and seconds
        startMinutesPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            // Adjust seconds maximum if at the maximum minute
            if (newVal == maxMinutes) {
                startSecondsPicker.setMaxValue(maxSecondsInLastMinute);
            } else {
                startSecondsPicker.setMaxValue(59);
            }
        });

        endMinutesPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            // Adjust seconds maximum if at the maximum minute
            if (newVal == maxMinutes) {
                endSecondsPicker.setMaxValue(maxSecondsInLastMinute);
            } else {
                endSecondsPicker.setMaxValue(59);
            }
        });

        // Value listener that updates the end time if start time becomes greater
        NumberPicker.OnValueChangeListener startTimeListener = (picker, oldVal, newVal) -> {
            // Calculate current start and end times in milliseconds
            long startMs = calculateTimeInMs(
                    startMinutesPicker.getValue(),
                    startSecondsPicker.getValue(),
                    startMillisecondsPicker.getValue() * 100
            );

            long endMs = calculateTimeInMs(
                    endMinutesPicker.getValue(),
                    endSecondsPicker.getValue(),
                    endMillisecondsPicker.getValue() * 100
            );

            // If start time becomes greater than or equal to end time,
            // adjust end time to be at least 500ms after start time
            if (startMs >= endMs) {
                startMs += 500; // Add 500ms buffer

                // Make sure not to exceed song duration
                if (startMs < totalDurationMs) {
                    // Convert back to minutes, seconds, milliseconds
                    int newEndMinutes = (int) (startMs / 60000);
                    int newEndSeconds = (int) ((startMs % 60000) / 1000);
                    int newEndMillis = (int) ((startMs % 1000) / 100);

                    // Update end time pickers without triggering their listeners
                    endMinutesPicker.setValue(newEndMinutes);
                    endSecondsPicker.setValue(newEndSeconds);
                    endMillisecondsPicker.setValue(newEndMillis);
                } else {
                    // If exceeding song duration, roll back the start time change
                    if (picker == startMinutesPicker) {
                        startMinutesPicker.setValue(oldVal);
                    } else if (picker == startSecondsPicker) {
                        startSecondsPicker.setValue(oldVal);
                    } else if (picker == startMillisecondsPicker) {
                        startMillisecondsPicker.setValue(oldVal);
                    }

                    Toast.makeText(requireContext(),
                            "Cannot set start time this high - would exceed song duration",
                            Toast.LENGTH_SHORT).show();
                }
            }

            // Check if at the maximum minute and adjust seconds accordingly
            if (startMinutesPicker.getValue() == maxMinutes) {
                startSecondsPicker.setMaxValue(maxSecondsInLastMinute);
            } else {
                startSecondsPicker.setMaxValue(59);
            }
        };

        // Value listener that updates the start time if end time becomes smaller
        NumberPicker.OnValueChangeListener endTimeListener = (picker, oldVal, newVal) -> {
            // Calculate current start and end times in milliseconds
            long startMs = calculateTimeInMs(
                    startMinutesPicker.getValue(),
                    startSecondsPicker.getValue(),
                    startMillisecondsPicker.getValue() * 100
            );

            long endMs = calculateTimeInMs(
                    endMinutesPicker.getValue(),
                    endSecondsPicker.getValue(),
                    endMillisecondsPicker.getValue() * 100
            );

            // If end time becomes less than or equal to start time,
            // prevent the change by resetting to old value
            if (endMs <= startMs) {
                if (picker == endMinutesPicker) {
                    endMinutesPicker.setValue(oldVal);
                } else if (picker == endSecondsPicker) {
                    endSecondsPicker.setValue(oldVal);
                } else if (picker == endMillisecondsPicker) {
                    endMillisecondsPicker.setValue(oldVal);
                }

//                Toast.makeText(requireContext(),
//                        "End time must be after start time",
//                        Toast.LENGTH_SHORT).show();
            }

            // Check if at the maximum minute and adjust seconds accordingly
            if (endMinutesPicker.getValue() == maxMinutes) {
                endSecondsPicker.setMaxValue(maxSecondsInLastMinute);
            } else {
                endSecondsPicker.setMaxValue(59);
            }
        };

        // Listeners for preview functionality
        NumberPicker.OnValueChangeListener startTimePreviewListener = (picker, oldVal, newVal) -> {
            if (previewStartCheckbox.isChecked() && spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
                // Calculate current start time in milliseconds
                long startMs = calculateTimeInMs(
                        startMinutesPicker.getValue(),
                        startSecondsPicker.getValue(),
                        startMillisecondsPicker.getValue() * 100
                );

                // Preview start position (play 3 seconds starting from the selected position)
                long previewEndMs = Math.min(startMs + 3000, totalDurationMs);
                SongSnippet previewSnippet = new SongSnippet(
                        song.getId(),
                        1, // Temporary number
                        "Preview End",
                        startMs,
                        previewEndMs,
                        false);

                playSnippet(previewSnippet);
            }
        };

        NumberPicker.OnValueChangeListener endTimePreviewListener = (picker, oldVal, newVal) -> {
            if (previewEndCheckbox.isChecked() && spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
                // Calculate current end time in milliseconds
                long endMs = calculateTimeInMs(
                        endMinutesPicker.getValue(),
                        endSecondsPicker.getValue(),
                        endMillisecondsPicker.getValue() * 100
                );

                // Preview end position (play 3 seconds leading up to the end position)
                long previewStartMs = Math.max(0, endMs - 3000);
                SongSnippet previewSnippet = new SongSnippet(
                        song.getId(),
                        1, // Temporary number
                        "Preview End",
                        previewStartMs,
                        endMs,
                        false);

                playSnippet(previewSnippet);
            }
        };

        // Add a way to apply the preview listeners when checkboxes are checked
        previewStartCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // When checkbox is checked, add the preview listener to all start time pickers
                startMinutesPicker.setOnValueChangedListener(
                        (picker, oldVal, newVal) -> {
                            startTimeListener.onValueChange(picker, oldVal, newVal);
                            startTimePreviewListener.onValueChange(picker, oldVal, newVal);
                        });
                startSecondsPicker.setOnValueChangedListener(
                        (picker, oldVal, newVal) -> {
                            startTimeListener.onValueChange(picker, oldVal, newVal);
                            startTimePreviewListener.onValueChange(picker, oldVal, newVal);
                        });
                startMillisecondsPicker.setOnValueChangedListener(
                        (picker, oldVal, newVal) -> {
                            startTimeListener.onValueChange(picker, oldVal, newVal);
                            startTimePreviewListener.onValueChange(picker, oldVal, newVal);
                        });

                // Trigger preview with current values
                startTimePreviewListener.onValueChange(startMinutesPicker, 0, startMinutesPicker.getValue());
            } else {
                // When unchecked, revert to original listeners
                startMinutesPicker.setOnValueChangedListener(startTimeListener);
                startSecondsPicker.setOnValueChangedListener(startTimeListener);
                startMillisecondsPicker.setOnValueChangedListener(startTimeListener);
            }
        });

        previewEndCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // When checkbox is checked, add the preview listener to all end time pickers
                endMinutesPicker.setOnValueChangedListener(
                        (picker, oldVal, newVal) -> {
                            endTimeListener.onValueChange(picker, oldVal, newVal);
                            endTimePreviewListener.onValueChange(picker, oldVal, newVal);
                        });
                endSecondsPicker.setOnValueChangedListener(
                        (picker, oldVal, newVal) -> {
                            endTimeListener.onValueChange(picker, oldVal, newVal);
                            endTimePreviewListener.onValueChange(picker, oldVal, newVal);
                        });
                endMillisecondsPicker.setOnValueChangedListener(
                        (picker, oldVal, newVal) -> {
                            endTimeListener.onValueChange(picker, oldVal, newVal);
                            endTimePreviewListener.onValueChange(picker, oldVal, newVal);
                        });

                // Trigger preview with current values
                endTimePreviewListener.onValueChange(endMinutesPicker, 0, endMinutesPicker.getValue());
            } else {
                // When unchecked, revert to original listeners
                endMinutesPicker.setOnValueChangedListener(endTimeListener);
                endSecondsPicker.setOnValueChangedListener(endTimeListener);
                endMillisecondsPicker.setOnValueChangedListener(endTimeListener);
            }
        });

        // Apply listeners
        startMinutesPicker.setOnValueChangedListener(startTimeListener);
        startSecondsPicker.setOnValueChangedListener(startTimeListener);
        startMillisecondsPicker.setOnValueChangedListener(startTimeListener);
        endMinutesPicker.setOnValueChangedListener(endTimeListener);
        endSecondsPicker.setOnValueChangedListener(endTimeListener);
        endMillisecondsPicker.setOnValueChangedListener(endTimeListener);

        // Set default values (start at 0, end at 25% of the song)
        startMinutesPicker.setValue(0);
        startSecondsPicker.setValue(0);
        startMillisecondsPicker.setValue(0);

        int quarterDurationSecs = totalSeconds / 4;
        endMinutesPicker.setValue(quarterDurationSecs / 60);
        endSecondsPicker.setValue(quarterDurationSecs % 60);
        endMillisecondsPicker.setValue(0);

        // Test playback button
        Button testPlaybackButton = dialogView.findViewById(R.id.test_playback_button);
        testPlaybackButton.setOnClickListener(v -> {
            // Calculate time values in milliseconds
            long startMs = calculateTimeInMs(
                    startMinutesPicker.getValue(),
                    startSecondsPicker.getValue(),
                    startMillisecondsPicker.getValue() * 100
            );

            long endMs = calculateTimeInMs(
                    endMinutesPicker.getValue(),
                    endSecondsPicker.getValue(),
                    endMillisecondsPicker.getValue() * 100
            );

            // Create temporary snippet for testing
            SongSnippet testSnippet = new SongSnippet(
                    song.getId(),
                    1, // Temporary number
                    titleInput.getText().toString(),
                    startMs,
                    endMs,
                    includeInRankingsCheckbox.isChecked()
            );

            playSnippet(testSnippet);
        });

        // Show the dialog
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Add Snippet")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    // Get title and rankings checkbox
                    String title = titleInput.getText().toString();
                    boolean includeInRankings = includeInRankingsCheckbox.isChecked();

                    // Calculate time values in milliseconds
                    long startMs = calculateTimeInMs(
                            startMinutesPicker.getValue(),
                            startSecondsPicker.getValue(),
                            startMillisecondsPicker.getValue() * 100
                    );

                    long endMs = calculateTimeInMs(
                            endMinutesPicker.getValue(),
                            endSecondsPicker.getValue(),
                            endMillisecondsPicker.getValue() * 100
                    );

                    // Validate
                    if (startMs >= endMs) {
//                        Toast.makeText(requireContext(), "End time must be after start time", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Get the next snippet number
                    long snippetNo = snippets.size() + 1;
                    for (SongSnippet existingSnippet : snippets) {
                        if (existingSnippet.getSnippetNo() >= snippetNo) {
                            snippetNo = existingSnippet.getSnippetNo() + 1;
                        }
                    }

                    // Create and save the snippet
                    SongSnippet newSnippet = new SongSnippet(
                            song.getId(),
                            snippetNo,
                            title,
                            startMs,
                            endMs,
                            includeInRankings
                    );

                    long id = dbHelper.addSnippet(newSnippet);
                    if (id != -1) {
                        newSnippet.setId(id);
                        snippets.add(newSnippet);
                        snippetAdapter.updateSnippets(snippets);
                        Toast.makeText(requireContext(), "Snippet saved", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Error saving snippet", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Calculate milliseconds from components
    private long calculateTimeInMs(int minutes, int seconds, int milliseconds) {
        return ((long) minutes * 60 * 1000) + (seconds * 1000L) + milliseconds;
    }

    private void showEditSnippetDialog(SongSnippet snippet) {
        Toast.makeText(requireContext(), "Edit snippet not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void loadSnippets() {
        snippets = dbHelper.getSongSnippets(song.getId());
        snippetAdapter.updateSnippets(snippets);
    }

    // Helper method to preview a position with a specified duration
    private void previewPosition(long positionMs, long durationMs) {
        if (spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
            // Cancel any existing timer
            if (pauseRunnable != null) {
                snippetHandler.removeCallbacks(pauseRunnable);
                activeTimers = 0; // Reset count when starting a new playback
            }
            spotifyAppRemote.getPlayerApi().play(song.getUri())
                    .setResultCallback(empty -> {
                        // Add a delay to ensure the song has loaded
                        new android.os.Handler().postDelayed(() -> {
                            // Seek to the position
                            spotifyAppRemote.getPlayerApi().seekTo(positionMs)
                                    .setResultCallback(seekResult -> {
                                        // Start a timer to pause after the duration
                                        startSnippetEndTimer(durationMs);
                                    });
                        }, 100); // Delay after play
                    });
        } else {
            Toast.makeText(requireContext(), "Spotify not connected", Toast.LENGTH_SHORT).show();
        }
    }

    // Format milliseconds into MM:SS format
    private String formatTime(long timeMs) {
        int totalSeconds = (int) (timeMs / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (snippetHandler != null && pauseRunnable != null) {
            snippetHandler.removeCallbacks(pauseRunnable);
        }
    }
}