package com.ca.tunaro;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.types.Track;

public class HomeActivity extends AppCompatActivity {
    private MainActivity mainActivity;
    private SpotifyAppRemote spotifyAppRemote;
    private boolean isPlaying = false;

    // UI elements
    private CardView libraryButton, thisSeasonButton;
    private CardView similarButton, playlistsButton, rankingsButton;
    private MaterialButton playEarliestButton;
    private FloatingActionButton playPauseButton;
    private ImageView currentSongImage;
    private TextView currentSongName, currentArtistName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Get main activity reference for Spotify
        mainActivity = MainActivity.getInstance();
        if (mainActivity != null) {
            spotifyAppRemote = mainActivity.getSpotifyAppRemote();
        }

        // Initialize UI elements
        initializeUI();
        setupClickListeners();
        setupCurrentPlayback();
    }

    private void initializeUI() {
        libraryButton = findViewById(R.id.library_button);
        thisSeasonButton = findViewById(R.id.this_season_button);
        similarButton = findViewById(R.id.similar_button);
        playlistsButton = findViewById(R.id.playlists_button);
        rankingsButton = findViewById(R.id.rankings_button);
        playEarliestButton = findViewById(R.id.play_earliest_button);
        playPauseButton = findViewById(R.id.play_pause_button);
        currentSongImage = findViewById(R.id.current_song_image);
        currentSongName = findViewById(R.id.current_song_name);
        currentArtistName = findViewById(R.id.current_artist_name);
    }

    private void setupClickListeners() {
        libraryButton.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, LibraryActivity.class);
            startActivity(intent);
        });

        // This Season button
        thisSeasonButton.setOnClickListener(view -> {
            Toast.makeText(this, "This Season feature coming soon", Toast.LENGTH_SHORT).show();
        });

        // Similar button
        similarButton.setOnClickListener(view -> {
            Toast.makeText(this, "Similar Songs feature coming soon", Toast.LENGTH_SHORT).show();
        });

        // Playlists button - Opens PlaylistsActivity
        playlistsButton.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, PlaylistsActivity.class);
            startActivity(intent);
        });

        // Rankings button
        rankingsButton.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, RankingsActivity.class);
            startActivity(intent);
        });

        // Play Earliest button
        playEarliestButton.setOnClickListener(view -> {
            Toast.makeText(this, "Play from Earliest feature coming soon", Toast.LENGTH_SHORT).show();
        });

        // Play/Pause button
        playPauseButton.setOnClickListener(view -> {
            if (spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
                if (isPlaying) {
                    spotifyAppRemote.getPlayerApi().pause();
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                } else {
                    spotifyAppRemote.getPlayerApi().resume();
                    playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                }
                isPlaying = !isPlaying;
            } else {
                Toast.makeText(this, "Spotify not connected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupCurrentPlayback() {
        // Subscribe to player state to update current song
        if (spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
            spotifyAppRemote.getPlayerApi()
                    .subscribeToPlayerState()
                    .setEventCallback(playerState -> {
                        final Track track = playerState.track;
                        if (track != null) {
                            // Update UI with current track
                            runOnUiThread(() -> {
                                currentSongName.setText(track.name);
                                currentArtistName.setText(track.artist.name);

                                // Load album image
                                Glide.with(this)
                                        .load(track.imageUri.raw)
                                        .placeholder(R.drawable.ic_note)
                                        .into(currentSongImage);

                                // Update play/pause button
                                isPlaying = !playerState.isPaused;
                                playPauseButton.setImageResource(
                                        isPlaying ? android.R.drawable.ic_media_pause
                                                : android.R.drawable.ic_media_play);
                            });
                        }
                    });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh current playback state when activity resumes
        if (spotifyAppRemote != null && spotifyAppRemote.isConnected()) {
            setupCurrentPlayback();
        }
    }
}