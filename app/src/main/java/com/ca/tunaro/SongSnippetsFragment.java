package com.ca.tunaro;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.slider.RangeSlider;
import com.spotify.android.appremote.api.SpotifyAppRemote;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SongSnippetsFragment extends Fragment {
    private MainActivity mainActivity;
    private SpotifyAppRemote spotifyAppRemote;
    private SongModel song;
    private DatabaseHelper dbHelper;
    private RecyclerView snippetsRecyclerView;
    private SongSnippetsAdapter snippetAdapter;
    private List<SongSnippet> snippets = new ArrayList<>();

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

        // Initialize MainActivity reference
        if (getActivity() instanceof MainActivity) {
            mainActivity = (MainActivity) getActivity();
        }
        else if (SelectedSongHolder.getInstance().getMainActivity() != null) {
            mainActivity = SelectedSongHolder.getInstance().getMainActivity();
        }
        else if (SelectedPlaylistHolder.getInstance().getMainActivity() != null) {
            mainActivity = SelectedPlaylistHolder.getInstance().getMainActivity();
        }

        if (mainActivity == null) {
            Toast.makeText(requireContext(), "Warning: Cannot access Spotify playback",
                    Toast.LENGTH_SHORT).show();
        }
        else spotifyAppRemote = mainActivity.getSpotifyAppRemote();


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
        if (spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
            // First play the song
            spotifyAppRemote.getPlayerApi().play(song.getUri())
                    .setResultCallback(empty -> {
                        // Add a delay before seeking to ensure the track has started loading
                        new android.os.Handler().postDelayed(() -> {
                            // Pause first to make seeking more reliable
                            spotifyAppRemote.getPlayerApi().pause()
                                    .setResultCallback(pauseResult -> {
                                        // Another small delay to ensure pause completes
                                        new android.os.Handler().postDelayed(() -> {
                                            // Then seek to the snippet start position
                                            spotifyAppRemote.getPlayerApi().seekTo(snippet.getStartTime())
                                                    .setResultCallback(seekResult -> {
                                                        // Resume playback after seeking
                                                        spotifyAppRemote.getPlayerApi().resume();

                                                        // Start a timer to pause at the end time
                                                        long duration = snippet.getEndTime() - snippet.getStartTime();
                                                        startSnippetEndTimer(duration);
                                                    });
                                        }, 100); // Delay after pause
                                    });
                        }, 100); // Delay after play
                    });
        } else {
            Toast.makeText(requireContext(), "Spotify not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSnippetEndTimer(long duration) {
        // Create a handler and runnable to pause playback after duration
        new android.os.Handler().postDelayed(() -> {
            SpotifyAppRemote spotifyAppRemote = mainActivity.getSpotifyAppRemote();

            if (spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
                spotifyAppRemote.getPlayerApi().pause();
            }
        }, duration);
    }

    private void detachSnippet() {
        // This method would simply cancel any timer that would pause the playback
        Toast.makeText(requireContext(), "Playback will continue after snippet end", Toast.LENGTH_SHORT).show();
        // TODO need to implement a way to cancel the timer here
    }

    private void showAddSnippetDialog() {
        // Use a custom layout instead of programmatically creating views
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_snippet, null);

        // Get references to all the views
        ImageView previewAlbumCover = dialogView.findViewById(R.id.preview_album_cover);
        TextView previewSongTitle = dialogView.findViewById(R.id.preview_song_title);
        TextView previewArtist = dialogView.findViewById(R.id.preview_artist);

        EditText titleInput = dialogView.findViewById(R.id.snippet_title_input);
        RangeSlider timeRangeSlider = dialogView.findViewById(R.id.time_range_slider);
        TextView startTimeText = dialogView.findViewById(R.id.start_time_text);
        TextView endTimeText = dialogView.findViewById(R.id.end_time_text);
        CheckBox includeInRankingsCheckbox = dialogView.findViewById(R.id.include_in_rankings);

        // Set up the preview section
        Glide.with(requireContext())
                .load(song.getAlbumCoverUrl())
                .into(previewAlbumCover);
        previewSongTitle.setText(song.getName());
        previewArtist.setText(song.getArtist());

        // Set up the time range slider
        int songDurationMs = song.getDuration();
        timeRangeSlider.setValueFrom(0);
        timeRangeSlider.setValueTo(songDurationMs);
        timeRangeSlider.setValues(0f, songDurationMs / 4f); // Default to first quarter

        // Update time text views when slider values change
        timeRangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            long startMs = values.get(0).longValue();
            long endMs = values.get(1).longValue();

            startTimeText.setText(formatTime(startMs));
            endTimeText.setText(formatTime(endMs));
        });

        // Trigger initial text update
        timeRangeSlider.getValues();

        // Test playback button
        Button testPlaybackButton = dialogView.findViewById(R.id.test_playback_button);
        testPlaybackButton.setOnClickListener(v -> {
            List<Float> values = timeRangeSlider.getValues();
            long startMs = values.get(0).longValue();
            long endMs = values.get(1).longValue();

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
                    // Get values
                    String title = titleInput.getText().toString();
                    List<Float> values = timeRangeSlider.getValues();
                    long startMs = values.get(0).longValue();
                    long endMs = values.get(1).longValue();
                    boolean includeInRankings = includeInRankingsCheckbox.isChecked();

                    // Validate
                    if (startMs >= endMs) {
                        Toast.makeText(requireContext(), "End time must be after start time", Toast.LENGTH_SHORT).show();
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

    private void showEditSnippetDialog(SongSnippet snippet) {
        Toast.makeText(requireContext(), "Edit snippet not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void loadSnippets() {
        snippets = dbHelper.getSongSnippets(song.getId());
        snippetAdapter.updateSnippets(snippets);
    }

    // Format milliseconds into MM:SS format
    private String formatTime(long timeMs) {
        int totalSeconds = (int) (timeMs / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
}