package com.ca.tunaro;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

public class SongView extends BaseActivity {
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Get your playback bar
            View playbackBar = findViewById(R.id.playback_bar);

            // Apply bottom margin to playback bar equal to navigation bar height
            if (playbackBar != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) playbackBar.getLayoutParams();
                params.bottomMargin = systemBars.bottom;
                playbackBar.setLayoutParams(params);
            }

            return insets;
        });

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

        setupTabs();
        setupListeningHistory();

        // Add play button functionality
        ImageView playButton = findViewById(R.id.play_button);
        playButton.setOnClickListener(v -> playSong());

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
        String popularity = String.valueOf(selectedSong.getPopularity());

        TextView nameView = findViewById(R.id.SongView_SongName);
        TextView artistView = findViewById(R.id.SongView_ArtistName);
        ImageView albumCoverImageView = findViewById(R.id.SongView_AlbumCover);
        TextView albumView = findViewById(R.id.SongView_AlbumName);
        TextView durationView = findViewById(R.id.SongView_SongDuration);
        TextView popularityView = findViewById(R.id.SongView_SongPopularity);

        nameView.setText(name);
        artistView.setText(artist);
        Glide.with(this)
                .load(albumCover)
                .into(albumCoverImageView);
        albumView.setText(albumName);
        durationView.setText(duration);
        if (!popularity.equals("0")) popularityView.setText("Popularity: " + popularity + "%");
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
            @SuppressLint("InflateParams") View customView = LayoutInflater.from(this).inflate(R.layout.custom_tab, null, false);

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
                count = dbHelper.getSongSnippets(selectedSong.getId()).size();
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
            public void onTabReselected(TabLayout.Tab tab) {
            }
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

    private void setupListeningHistory() {
        LinearLayout historySection = findViewById(R.id.listening_history_section);
        RecyclerView historyRecycler = findViewById(R.id.listening_history_recycler);

        if (selectedSong == null) return;

        DatabaseHelper dbHelper = new DatabaseHelper(this);
        List<String> listenHistory = dbHelper.getListenHistory(selectedSong.getId());

        if (listenHistory.isEmpty()) {
            historySection.setVisibility(View.GONE);
            return;
        }

        // Show the history section
        historySection.setVisibility(View.VISIBLE);

        // Create simple adapter for timestamps
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listenHistory) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
                }

                TextView textView = convertView.findViewById(android.R.id.text1);
                String timestamp = getItem(position);

                // Format the timestamp nicely
                try {
                    java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
                    java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", java.util.Locale.getDefault());
                    java.util.Date date = inputFormat.parse(timestamp);
                    textView.setText(outputFormat.format(date));
                } catch (Exception e) {
                    textView.setText(timestamp); // Fallback to raw timestamp
                }

                textView.setTextColor(getResources().getColor(android.R.color.white));
                textView.setTextSize(14f);
                textView.setPadding(0, 8, 0, 8);

                return convertView;
            }
        };

        // Create a LinearLayout to hold the history items instead of ListView
        LinearLayout historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);

// Manually add each item as a TextView to the LinearLayout
        for (String timestamp : listenHistory) {
            TextView textView = new TextView(this);

            // Format the timestamp nicely
            try {
                java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
                java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", java.util.Locale.getDefault());
                java.util.Date date = inputFormat.parse(timestamp);
                textView.setText(outputFormat.format(date));
            } catch (Exception e) {
                textView.setText(timestamp); // Fallback to raw timestamp
            }

            textView.setTextColor(getResources().getColor(android.R.color.white));
            textView.setTextSize(14f);
            textView.setPadding(0, 8, 0, 8);

            historyContainer.addView(textView);
        }

        // Replace RecyclerView with the LinearLayout container
        ViewGroup parent = (ViewGroup) historyRecycler.getParent();
        int index = parent.indexOfChild(historyRecycler);
        parent.removeView(historyRecycler);
        parent.addView(historyContainer, index);
    }

    private void playSong() {
        if (!playbackManager.isConnected()) {
            Toast.makeText(this, "Connecting to Spotify...", Toast.LENGTH_SHORT).show();
            // Use PlaybackManager to reconnect
            playbackManager.connectSpotify(this, () -> {
                // After connection, play the song
                if (selectedSong != null) {
                    playbackManager.playSong(selectedSong);
                    Toast.makeText(this, "Playing " + selectedSong.getName(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else if (selectedSong != null) {
            // Already connected, just play
            playbackManager.playSong(selectedSong);
            Toast.makeText(this, "Playing " + selectedSong.getName(),
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