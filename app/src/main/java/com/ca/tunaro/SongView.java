package com.ca.tunaro;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import com.spotify.android.appremote.api.SpotifyAppRemote;

import java.util.Objects;

public class SongView extends AppCompatActivity {
    // Fields
    private SongModel selectedSong;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private SongTabAdapter tabAdapter;
    private AppBarLayout appBarLayout;

    // Creation
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_song_view);

        // Retrieve the selected song
        selectedSong = SelectedSongHolder.getInstance().getSelectedSong();
        if (selectedSong == null) {
            finish();
            return;
        }

        // Initialize AppBarLayout
        appBarLayout = findViewById(R.id.appbar);

        // Set up basic song info
        setupBasicSongInfo();

        // Set up tabs
        setupTabs();

        // Add play button functionality
        ImageView playButton = findViewById(R.id.play_button);
        playButton.setOnClickListener(v -> playSong(getIntent().getStringExtra("source")));

        // Force the collapsible details part to be collapsed initially
        appBarLayout.setExpanded(false, false);
    }

    // Setup methods
    private void setupBasicSongInfo() {
        String name = selectedSong.getName();
        String artist = selectedSong.getArtist();
        String albumCover = selectedSong.getAlbumCoverUrl();
        String albumName = selectedSong.getAlbumName();
        String duration = selectedSong.getDurationString();

        TextView nameView = findViewById(R.id.SongView_SongName);
        TextView artistView = findViewById(R.id.SongView_ArtistName);
        ImageView albumCoverImageView = findViewById(R.id.SongView_AlbumCover);
        TextView albumView = findViewById(R.id.SongView_AlbumName);
        TextView durationView = findViewById(R.id.SongView_SongDuration);

        nameView.setText(name);
        artistView.setText(artist);
        Glide.with(this)
                .load(albumCover)
                .into(albumCoverImageView);
        albumView.setText(albumName);
        durationView.setText(duration);
    }

    private void setupTabs() {
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);

        // First get a DatabaseHelper instance
        DatabaseHelper dbHelper = new DatabaseHelper(this);

        // Configure the TabLayout appearance
        tabLayout.setBackgroundColor(Color.TRANSPARENT);
        tabLayout.setTabRippleColor(null); // Remove ripple effect (feedback on screen upon pressing a tab)

        // Add padding around the TabLayout
        tabLayout.setPadding(16, 8, 16, 8);

        tabAdapter = new SongTabAdapter(this, selectedSong);
        viewPager.setAdapter(tabAdapter);

        // Create custom tab views
        String[] tabTitles = {"Notes", "Snippets"};
        for (int i = 0; i < tabTitles.length; i++) {
            TabLayout.Tab tab = tabLayout.newTab();
            View customView = LayoutInflater.from(this).inflate(R.layout.custom_tab, null);

            TextView tabTitleView = customView.findViewById(R.id.tab_title);
            TextView tabBadgeView = customView.findViewById(R.id.tab_badge);

            tabTitleView.setText(tabTitles[i]);

            // Set text color based on position (will update in selection listener)
            tabTitleView.setTextColor(i == 0 ? Color.BLACK : Color.WHITE);

            // Set the badge count
            int count = 0;
            if (i == 0) {
                // Get actual notes count
                count = dbHelper.getSongNotes(selectedSong.getId()).size();
            } else if (i == 1) {
                // For snippets tab (placeholder for now)
                count = 0; // Replace with actual count when you implement snippets
            }

            if (count > 0) {
                tabBadgeView.setVisibility(View.VISIBLE);
                tabBadgeView.setText(String.valueOf(count));
            } else {
                tabBadgeView.setVisibility(View.GONE);
            }

            tab.setCustomView(customView);
            tabLayout.addTab(tab);
        }

        // Connect TabLayout with ViewPager2
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
                // Update text color when tab is selected
                View customView = tab.getCustomView();
                if (customView != null) {
                    TextView tabTitleView = customView.findViewById(R.id.tab_title);
                    tabTitleView.setTextColor(Color.BLACK);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Update text color when tab is unselected
                View customView = tab.getCustomView();
                if (customView != null) {
                    TextView tabTitleView = customView.findViewById(R.id.tab_title);
                    tabTitleView.setTextColor(Color.WHITE);
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tabLayout.selectTab(tabLayout.getTabAt(position));
            }
        });

        // Select Notes tab by default. TODO Allow preference in settings
        viewPager.setCurrentItem(0);
    }

    private void playSong(String source) {
        MainActivity mainActivity;
        if (Objects.equals(source, "playlist")) mainActivity = SelectedPlaylistHolder.getInstance().getMainActivity();
        else mainActivity = SelectedSongHolder.getInstance().getMainActivity();
        SpotifyAppRemote mSpotifyAppRemote = mainActivity.getSpotifyAppRemote();

        // Try to reconnect Spotify if MainActivity is available
        if (!mSpotifyAppRemote.isConnected()) {
            Toast.makeText(this, "Attempting to reconnect to Spotify...", Toast.LENGTH_SHORT).show();
            // Call a method in MainActivity to reconnect
            mainActivity.connectSpotifyAppRemote();
        }

        if (selectedSong != null) {
            try {
                // Play the song
                mSpotifyAppRemote.getPlayerApi().play(selectedSong.getUri())
                        .setResultCallback(empty -> {
                            // Create a "Date Listened" note
//                            String currentDate = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm")
//                                    .format(new java.util.Date());
//                            SongNote note = new SongNote(
//                                    selectedSong.getId(),
//                                    SongNote.NoteType.DATE_LISTENED.getDisplayName(),
//                                    currentDate
//                            );
//
//                            // Save to database
//                            dbHelper.addNote(note);
//
//                            // Refresh notes display
//                            loadExistingNotes();
//
                            Toast.makeText(this, "Playing " + selectedSong.getName(),
                                    Toast.LENGTH_SHORT).show();
                        })
                        .setErrorCallback(throwable -> {
                            Toast.makeText(this, "Error playing song: " + throwable.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            Log.e("SongView", "PlaybackError: " + throwable.getMessage());
                        });
            } catch (Exception e) {
                Log.e("SongView", "PlaybackException: " + e.getMessage());
            }
        } else {
            Toast.makeText(this, "Unable to play song. Please check Spotify connection.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Destroy
    @Override
    protected void onDestroy() {
        super.onDestroy();
        SelectedSongHolder.getInstance().clearSelectedSong();
    }
}