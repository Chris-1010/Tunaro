package com.ca.tunaro.activites;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.ca.tunaro.adapters.SongTabAdapter;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.ColorExtractor;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.google.android.material.tabs.TabLayout;

public class SongView extends BaseActivity {
    private static final String TAG = "SongView";

    // Fields
    private SongModel selectedSong;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private SongTabAdapter tabAdapter;

    // Creation
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_song_view);

        // Apply window insets properly to avoid content behind system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Apply top inset to header content
            View headerContent = findViewById(R.id.header_content);
            if (headerContent != null) {
                headerContent.setPadding(
                        headerContent.getPaddingLeft(),
                        systemBars.top + 16,
                        headerContent.getPaddingRight(),
                        headerContent.getPaddingBottom()
                );
            }

            // Apply bottom inset to playback bar
            View playbackBar = findViewById(R.id.playback_bar);

            // Apply bottom margin to playback bar equal to navigation bar height
            if (playbackBar != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) playbackBar.getLayoutParams();
                params.bottomMargin = systemBars.bottom;
                playbackBar.setLayoutParams(params);
            }

            return WindowInsetsCompat.CONSUMED;
        });

        // Retrieve the selected song
        selectedSong = SelectedSongHolder.getInstance().getSelectedSong();
        if (selectedSong == null) {
            finish();
            return;
        }

        // Set up UI components
        setupBackButton();
        setupBasicSongInfo();
        setupAlbumCoverLongPress();
        setupDynamicBackground();
        setupTabs();
    }

    private void setupBackButton() {
        LinearLayout backButton = findViewById(R.id.back_button);
        TextView backButtonText = findViewById(R.id.back_button_text);

        // Get playlist/source name from intent extras if available
        String source = getIntent().getStringExtra("source");
        String playlistName = getIntent().getStringExtra("playlist_name");

        if (playlistName != null && !playlistName.isEmpty()) {
            backButtonText.setText("Back to " + playlistName);
        } else if ("library".equals(source)) {
            backButtonText.setText("Back to Library");
        } else {
            backButtonText.setText("Back");
        }

        backButton.setOnClickListener(v -> finish());
    }

    // Setup methods
    private void setupBasicSongInfo() {
        String name = selectedSong.getName();
        String artist = selectedSong.getArtist();
        String albumCover = selectedSong.getAlbumCoverUrl();

        TextView nameView = findViewById(R.id.SongView_SongName);
        TextView artistView = findViewById(R.id.SongView_ArtistName);
        ImageView albumCoverImageView = findViewById(R.id.SongView_AlbumCover);

        nameView.setText(name);
        nameView.setSelected(true); // Enable marquee
        artistView.setText(artist);

        Glide.with(this)
                .load(albumCover)
                .placeholder(R.drawable.song_placeholder)
                .error(R.drawable.song_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(albumCoverImageView);
    }

    private void setupAlbumCoverLongPress() {
        ImageView albumCoverImageView = findViewById(R.id.SongView_AlbumCover);
        albumCoverImageView.setOnLongClickListener(v -> {
            playSong();
            return true;
        });
    }

    private void setupDynamicBackground() {
        String albumCover = selectedSong.getAlbumCoverUrl();

        ColorExtractor.extractColors(this, albumCover, new ColorExtractor.ColorExtractionCallback() {
            @Override
            public void onColorExtracted(int dominantColor, int vibrantColor) {
                if (ColorExtractor.hasSufficientContrast(dominantColor, Color.BLACK, 0)) {
                    applyGradientBackground(dominantColor);
                    return;
                }
                applyGradientBackground(vibrantColor);
            }

            @Override
            public void onError() {
                applyGradientBackground(Color.parseColor("#424242"));
            }
        });
    }

    private void applyGradientBackground(int dominantColor) {
        View mainLayout = findViewById(R.id.main);

        if (mainLayout != null) {
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TR_BL,
                    new int[]{dominantColor, Color.BLACK}
            );
            mainLayout.setBackground(gradient);
        }
    }

    private void setupTabs() {
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);

        // Enable nested scrolling so fragments can trigger collapse
        ViewCompat.setNestedScrollingEnabled(viewPager, true);

        // Configure the TabLayout appearance
        tabLayout.setBackgroundColor(Color.TRANSPARENT);
        tabLayout.setTabRippleColor(null);
        tabLayout.setPadding(16, 8, 16, 8);

        // Create adapter with 3 tabs: Details, Notes, Snippets
        tabAdapter = new SongTabAdapter(this, selectedSong);
        viewPager.setAdapter(tabAdapter);

        // Create custom tab views
        String[] tabTitles = {"Details", "Notes", "Snippets"};
        for (int i = 0; i < tabTitles.length; i++) {
            TabLayout.Tab tab = tabLayout.newTab();
            @SuppressLint("InflateParams") View customView = LayoutInflater.from(this).inflate(R.layout.custom_tab, null, false);

            TextView tabTitleView = customView.findViewById(R.id.tab_title);
            TextView tabBadgeView = customView.findViewById(R.id.tab_badge);

            tabTitleView.setText(tabTitles[i]);
            tabTitleView.setTextColor(i == 0 ? Color.BLACK : Color.WHITE);

            // Set badge count for Notes and Snippets tabs
            if (i > 0) {
                com.ca.tunaro.database.DatabaseHelper dbHelper = new com.ca.tunaro.database.DatabaseHelper(this);
                int count = 0;

                if (i == 1) { // Notes
                    count = dbHelper.getSongNotes(selectedSong.getId()).size();
                } else if (i == 2) { // Snippets
                    count = dbHelper.getSongSnippets(selectedSong.getId()).size();
                }

                if (count > 0) {
                    tabBadgeView.setVisibility(View.VISIBLE);
                    tabBadgeView.setText(String.valueOf(count));
                } else {
                    tabBadgeView.setVisibility(View.GONE);
                }
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
                View customView = tab.getCustomView();
                if (customView != null) {
                    TextView tabTitleView = customView.findViewById(R.id.tab_title);
                    tabTitleView.setTextColor(Color.BLACK);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
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

        // Select Details tab by default
        viewPager.setCurrentItem(0);
    }

    public void updateTabBadge(int tabIndex, int count) {
        TabLayout.Tab tab = tabLayout.getTabAt(tabIndex);
        if (tab == null || tab.getCustomView() == null) return;

        TextView badgeView = tab.getCustomView().findViewById(R.id.tab_badge);
        if (count > 0) {
            badgeView.setVisibility(View.VISIBLE);
            badgeView.setText(String.valueOf(count));
        } else {
            badgeView.setVisibility(View.GONE);
        }
    }

    private void playSong() {
        if (!playbackManager.isConnected()) {
            showToast("Connecting to Spotify...");
            playbackManager.connectSpotify(this, () -> {
                if (selectedSong != null) {
                    playbackManager.playSong(selectedSong);
                    showToast("Playing " + selectedSong.getName());
                }
            });
        } else if (selectedSong != null) {
            playbackManager.playSong(selectedSong);
            showToast("Playing " + selectedSong.getName());
        }
    }

    // Destroy
    @Override
    protected void onDestroy() {
        super.onDestroy();
        SelectedSongHolder.getInstance().clearSelectedSong();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.v(TAG, "showed Toast: " + message);
    }
}
