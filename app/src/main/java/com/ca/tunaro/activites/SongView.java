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
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.ColorExtractor;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

public class SongView extends BaseActivity {
    private static final String TAG = "SongView";

    private SongModel selectedSong;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private SongTabAdapter tabAdapter;

    private boolean playlistPanelVisible = false;
    private static final int PANEL_WIDTH_DP = 56;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_song_view);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            View headerContent = findViewById(R.id.header_content);
            if (headerContent != null) {
                headerContent.setPadding(
                        headerContent.getPaddingLeft(),
                        systemBars.top + 16,
                        headerContent.getPaddingRight(),
                        headerContent.getPaddingBottom()
                );
            }

            View playbackBar = findViewById(R.id.playback_bar);
            if (playbackBar != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) playbackBar.getLayoutParams();
                params.bottomMargin = systemBars.bottom;
                playbackBar.setLayoutParams(params);
            }

            return WindowInsetsCompat.CONSUMED;
        });

        selectedSong = SelectedSongHolder.getInstance().getSelectedSong();
        if (selectedSong == null) {
            finish();
            return;
        }

        // Upgrade to full model so Details tab has createdAt and variants
        SongModel fullSong = new DatabaseHelper(this).getFullSong(selectedSong.getId());
        if (fullSong != null) selectedSong = fullSong;

        setupBackButton();
        setupBasicSongInfo();
        setupAlbumCover();
        setupPlaylistPanel();
        setupDynamicBackground();
        setupTabs();
    }

    private void setupBackButton() {
        LinearLayout backButton = findViewById(R.id.back_button);
        TextView backButtonText = findViewById(R.id.back_button_text);

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

    private void setupBasicSongInfo() {
        TextView nameView = findViewById(R.id.SongView_SongName);
        TextView artistView = findViewById(R.id.SongView_ArtistName);
        ImageView albumCoverImageView = findViewById(R.id.SongView_AlbumCover);

        nameView.setText(selectedSong.getName());
        nameView.setSelected(true);
        artistView.setText(selectedSong.getArtist());

        Glide.with(this)
                .load(selectedSong.getAlbumCoverUrl())
                .placeholder(R.drawable.song_placeholder)
                .error(R.drawable.song_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(albumCoverImageView);
    }

    private void setupAlbumCover() {
        ImageView albumCoverImageView = findViewById(R.id.SongView_AlbumCover);

        // Tap: toggle playlist panel with a slight scale-down feedback
        albumCoverImageView.setOnClickListener(v -> {
            v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                        togglePlaylistPanel();
                    }).start();
        });

        // Long-press: play song
        albumCoverImageView.setOnLongClickListener(v -> {
            playSong();
            return true;
        });
    }

    private void setupPlaylistPanel() {
        LinearLayout panel = findViewById(R.id.playlist_panel);
        LinearLayout iconsContainer = findViewById(R.id.playlist_panel_icons);
        TextView emptyLabel = findViewById(R.id.playlist_panel_empty);
        View scrollView = findViewById(R.id.playlist_panel_scroll);

        DatabaseHelper dbHelper = new DatabaseHelper(this);
        List<DatabaseHelper.PlaylistLink> playlists = dbHelper.getPlaylistsForSong(selectedSong.getId());

        if (playlists.isEmpty()) {
            emptyLabel.setVisibility(View.VISIBLE);
            scrollView.setVisibility(View.GONE);
        } else {
            emptyLabel.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);

            int iconSize = dpToPx(40);
            int iconMargin = dpToPx(4);

            for (DatabaseHelper.PlaylistLink link : playlists) {
                ImageView icon = new ImageView(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(iconSize, iconSize);
                params.setMargins(iconMargin, iconMargin, iconMargin, iconMargin);
                icon.setLayoutParams(params);
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                icon.setClipToOutline(true);
                icon.setBackgroundResource(R.drawable.rounded_md);

                float alpha = link.isActive() ? 1.0f : 0.4f;
                icon.setAlpha(alpha);

                Glide.with(this)
                        .load(link.imageUrl)
                        .placeholder(R.drawable.playlist_placeholder)
                        .error(R.drawable.playlist_placeholder)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(icon);

                // Hold → show tooltip with playlist name (and removal date if removed)
                icon.setOnLongClickListener(v -> {
                    String tooltip = link.name;
                    if (!link.isActive() && link.removedAt != null) {
                        tooltip += "\nRemoved " + formatRemovedAt(link.removedAt);
                    }
                    showToast(tooltip);
                    return true;
                });

                iconsContainer.addView(icon);
            }
        }
    }

    private void togglePlaylistPanel() {
        LinearLayout panel = findViewById(R.id.playlist_panel);
        int panelWidthPx = dpToPx(PANEL_WIDTH_DP);

        if (playlistPanelVisible) {
            panel.animate()
                    .translationX(-panelWidthPx)
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction(() -> panel.setVisibility(View.INVISIBLE))
                    .start();
            playlistPanelVisible = false;
        } else {
            panel.setVisibility(View.VISIBLE);
            panel.setAlpha(0f);
            panel.setTranslationX(-panelWidthPx);
            panel.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(220)
                    .start();
            playlistPanelVisible = true;
        }
    }

    private void setupDynamicBackground() {
        ColorExtractor.extractColors(this, selectedSong.getAlbumCoverUrl(), new ColorExtractor.ColorExtractionCallback() {
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

        ViewCompat.setNestedScrollingEnabled(viewPager, true);

        tabLayout.setBackgroundColor(Color.TRANSPARENT);
        tabLayout.setTabRippleColor(null);
        tabLayout.setPadding(16, 8, 16, 8);

        tabAdapter = new SongTabAdapter(this, selectedSong);
        viewPager.setAdapter(tabAdapter);

        String[] tabTitles = {"Details", "Notes", "Snippets"};
        for (int i = 0; i < tabTitles.length; i++) {
            TabLayout.Tab tab = tabLayout.newTab();
            @SuppressLint("InflateParams") View customView = LayoutInflater.from(this).inflate(R.layout.custom_tab, null, false);

            TextView tabTitleView = customView.findViewById(R.id.tab_title);
            TextView tabBadgeView = customView.findViewById(R.id.tab_badge);

            tabTitleView.setText(tabTitles[i]);
            tabTitleView.setTextColor(i == 0 ? Color.BLACK : Color.WHITE);

            if (i > 0) {
                DatabaseHelper dbHelper = new DatabaseHelper(this);
                int count = i == 1
                        ? dbHelper.getSongNotes(selectedSong.getId()).size()
                        : dbHelper.getSongSnippets(selectedSong.getId()).size();

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

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
                View customView = tab.getCustomView();
                if (customView != null) {
                    customView.findViewById(R.id.tab_title).performClick();
                    ((TextView) customView.findViewById(R.id.tab_title)).setTextColor(Color.BLACK);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                View customView = tab.getCustomView();
                if (customView != null) {
                    ((TextView) customView.findViewById(R.id.tab_title)).setTextColor(Color.WHITE);
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

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private String formatRemovedAt(String utcTimestamp) {
        try {
            java.text.SimpleDateFormat inFmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
            inFmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = inFmt.parse(utcTimestamp);
            java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US);
            return outFmt.format(date);
        } catch (Exception e) {
            return utcTimestamp;
        }
    }

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
