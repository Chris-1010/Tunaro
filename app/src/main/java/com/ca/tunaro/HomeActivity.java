package com.ca.tunaro;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;

public class HomeActivity extends BaseActivity {
    // UI elements
    private CardView libraryButton, thisSeasonButton;
    private CardView similarButton, playlistsButton, rankingsButton;
    private MaterialButton playEarliestButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize UI elements
        libraryButton = findViewById(R.id.library_button);
        thisSeasonButton = findViewById(R.id.this_season_button);
        similarButton = findViewById(R.id.similar_button);
        playlistsButton = findViewById(R.id.playlists_button);
        rankingsButton = findViewById(R.id.rankings_button);
        playEarliestButton = findViewById(R.id.play_earliest_button);

        setupClickListeners();
    }

    private void setupClickListeners() {
        libraryButton.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, LibraryActivity.class);
            startActivity(intent);
        });

        // This Season button
        thisSeasonButton.setOnClickListener(view -> Toast.makeText(this, "This Season feature coming soon", Toast.LENGTH_SHORT).show());

        // Similar button
        similarButton.setOnClickListener(view -> Toast.makeText(this, "Similar Songs feature coming soon", Toast.LENGTH_SHORT).show());

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
        playEarliestButton.setOnClickListener(view -> Toast.makeText(this, "Play from Earliest feature coming soon", Toast.LENGTH_SHORT).show());
    }
}