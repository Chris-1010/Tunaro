package com.ca.tunaro.activites;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.google.android.material.button.MaterialButton;

public class HomeActivity extends BaseActivity {
    private static final String TAG = "HomeActivity";

    // UI elements
    private ImageView settingsIcon;
    private CardView libraryButton, thisSeasonButton;
    private CardView similarButton, playlistsButton, rankingsButton;
    private MaterialButton playEarliestButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize UI elements
        settingsIcon = findViewById(R.id.settings_icon);
        libraryButton = findViewById(R.id.library_button);
        thisSeasonButton = findViewById(R.id.this_season_button);
        similarButton = findViewById(R.id.similar_button);
        playlistsButton = findViewById(R.id.playlists_button);
        rankingsButton = findViewById(R.id.rankings_button);
        playEarliestButton = findViewById(R.id.play_earliest_button);

        setupClickListeners();
        setupProfileImage();
    }

    private void setupProfileImage() {
        ImageView profileImage = findViewById(R.id.user_profile_image);
        SharedPreferences prefs = getSharedPreferences("SpotifyPrefs", MODE_PRIVATE);
        String imageUrl = prefs.getString("spotify_profile_image_url", null);
        if (imageUrl != null && profileImage != null) {
            Glide.with(this)
                    .load(imageUrl)
                    .circleCrop()
                    .into(profileImage);
        }
    }

    private void setupClickListeners() {
        settingsIcon.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, SettingsActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_up_in, R.anim.no_animation);
        });

        libraryButton.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, LibraryActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.zoom_in, R.anim.zoom_out);
        });

        thisSeasonButton.setOnClickListener(view -> showToast("This Season feature coming soon"));

        similarButton.setOnClickListener(view -> showToast("Similar Songs feature coming soon"));

        playlistsButton.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, PlaylistsActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.zoom_in, R.anim.zoom_out);
        });

        rankingsButton.setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, RankingsActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.zoom_in, R.anim.zoom_out);
        });

        findViewById(R.id.developer_button).setOnClickListener(view -> {
            Intent intent = new Intent(HomeActivity.this, DeveloperActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.zoom_in, R.anim.zoom_out);
        });

        playEarliestButton.setOnClickListener(view -> showToast("Play from Earliest feature coming soon"));
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}