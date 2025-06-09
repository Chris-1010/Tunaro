package com.ca.tunaro;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.SeekBar;
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
    private View snippetCreationOverlay;
    private long snippetStartTime = -1;
    private long snippetEndTime = -1;
    private TextView snippetTimeDisplay;
    private EditText startTimeInput, endTimeInput;
    private Button previewStartButton, previewEndButton, saveSnippetButton;
    private View snippetRangeOverlay, snippetStartMarker, snippetEndMarker;
    private SeekBar snippetSeekBar;
    private PlaybackManager playbackManager;

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
        showSnippetCreationOverlay();
    }

    private void showSnippetCreationOverlay() {
        // Initialize playback manager
        playbackManager = PlaybackManager.getInstance();

        // Find or create the overlay
        if (snippetCreationOverlay == null) {
            View parentView = getActivity().findViewById(android.R.id.content);
            if (parentView instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) parentView;
                snippetCreationOverlay = LayoutInflater.from(requireContext())
                        .inflate(R.layout.snippet_creation_overlay, parent, false);
                parent.addView(snippetCreationOverlay);
                setupSnippetCreationOverlay();
            }
        }

        // Reset state
        snippetStartTime = -1;
        snippetEndTime = -1;
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
            public void onStartTrackingTouch(SeekBar seekBar) {}

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
                Toast.makeText(requireContext(), "End time must be after start time", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(requireContext(), "Please set both start and end times", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get the next snippet number
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
                Toast.makeText(requireContext(), "Snippet saved", Toast.LENGTH_SHORT).show();
                hideSnippetCreationOverlay();
            } else {
                Toast.makeText(requireContext(), "Error saving snippet", Toast.LENGTH_SHORT).show();
            }
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> hideSnippetCreationOverlay());

        // Play/pause button
        ImageButton playPauseButton = snippetCreationOverlay.findViewById(R.id.snippet_play_pause);
        playPauseButton.setOnClickListener(v -> playbackManager.togglePlayPause());
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
                Toast.makeText(requireContext(), "Invalid start time format. Use M:SS.mmm", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(requireContext(), "Invalid end time format or must be after start time", Toast.LENGTH_SHORT).show();
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
        if (song == null) return;

        ImageView albumCover = snippetCreationOverlay.findViewById(R.id.snippet_album_cover);
        TextView songName = snippetCreationOverlay.findViewById(R.id.snippet_song_name);
        TextView artistName = snippetCreationOverlay.findViewById(R.id.snippet_artist_name);
        ImageButton playPauseButton = snippetCreationOverlay.findViewById(R.id.snippet_play_pause);

        // Load song info
        Glide.with(requireContext()).load(song.getAlbumCoverUrl()).into(albumCover);
        songName.setText(song.getName());
        artistName.setText(song.getArtist());

        // Setup seekbar with song duration
        snippetSeekBar.setMax(song.getDuration());
        snippetSeekBar.setProgress((int) playbackManager.getCurrentPositionMs());

        // Update play/pause button
        playPauseButton.setImageResource(playbackManager.isPlaying() ?
                R.drawable.pause_circle_filled : R.drawable.play_circle_filled);

        // Start listening for playback updates
        startPlaybackUpdates();
    }

    private void startPlaybackUpdates() {
        Handler handler = new Handler();
        Runnable updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (snippetCreationOverlay != null && snippetCreationOverlay.getVisibility() == View.VISIBLE) {
                    updatePlaybackUI();
                    handler.postDelayed(this, 100);
                }
            }
        };
        handler.post(updateRunnable);
    }

    private void updatePlaybackUI() {
        if (playbackManager.isConnected()) {
            snippetSeekBar.setProgress((int) playbackManager.getCurrentPositionMs());

            ImageButton playPauseButton = snippetCreationOverlay.findViewById(R.id.snippet_play_pause);
            playPauseButton.setImageResource(playbackManager.isPlaying() ?
                    R.drawable.pause_circle_filled : R.drawable.play_circle_filled);
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
        }
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (snippetHandler != null && pauseRunnable != null) {
            snippetHandler.removeCallbacks(pauseRunnable);
        }
    }
}