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
import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.ColorExtractor;
import com.ca.tunaro.utils.SnippetTheme;
import com.ca.tunaro.utils.SelectedPlaylistHolder;

import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Track;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class SongView extends BaseActivity {
    private static final String TAG = "SongView";

    private SongModel selectedSong;
    private List<String> allVariantUris = new ArrayList<>();
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private SongTabAdapter tabAdapter;

    // Themed tab text colours, derived from the album art.
    private int tabSelectedTextColor = Color.BLACK;
    private int tabUnselectedTextColor = Color.WHITE;

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

        // Upgrade to full model so Details tab has complete metadata.
        // Rows can be partial stubs: PlaybackManager writes name/duration only on
        // track change, and older writers stored album_id without the album row or
        // artist links. Treat any of those as missing so we refetch from the API.
        DatabaseHelper db = new DatabaseHelper(this);
        SongModel fullSong = db.getFullSong(selectedSong.getId());
        boolean loadingFromApi = fullSong == null
                || fullSong.getAlbumId() == null
                || fullSong.getAlbumName() == null
                || fullSong.getArtist().isEmpty();
        if (!loadingFromApi) {
            selectedSong = fullSong;
            allVariantUris.add(selectedSong.getId());
            allVariantUris.addAll(db.getIsrcLinkedUris(selectedSong.getId()));
        } else {
            allVariantUris.add(selectedSong.getId());
        }
        db.close();

        setupBackButton();
        setupBasicSongInfo();
        if (loadingFromApi) {
            startHeaderShimmer();
            showNewSongPill();
            fetchAndPopulateFromApi(selectedSong.getId());
        }
        setupAlbumCover();
        setupPlaylistPanel();
        setupDynamicBackground();
        setupTabs(loadingFromApi);
    }

    private void startHeaderShimmer() {
        TextView nameView = findViewById(R.id.SongView_SongName);
        if (nameView == null) return;
        android.view.ViewGroup parent = (android.view.ViewGroup) nameView.getParent();
        if (parent instanceof com.facebook.shimmer.ShimmerFrameLayout) return; // already wrapped

        int nameIndex = parent.indexOfChild(nameView);
        TextView artistView = findViewById(R.id.SongView_ArtistName);

        com.facebook.shimmer.ShimmerFrameLayout shimmer = new com.facebook.shimmer.ShimmerFrameLayout(this);
        shimmer.setId(R.id.song_info_shimmer);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        shimmer.setLayoutParams(lp);

        android.widget.LinearLayout inner = new android.widget.LinearLayout(this);
        inner.setOrientation(android.widget.LinearLayout.VERTICAL);
        inner.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams innerLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        inner.setLayoutParams(innerLp);

        parent.removeView(nameView);
        if (artistView != null) parent.removeView(artistView);
        inner.addView(nameView);
        if (artistView != null) inner.addView(artistView);
        shimmer.addView(inner);
        parent.addView(shimmer, nameIndex);

        shimmer.startShimmer();
    }

    private void stopHeaderShimmer() {
        View shimmerView = findViewById(R.id.song_info_shimmer);
        if (!(shimmerView instanceof com.facebook.shimmer.ShimmerFrameLayout)) return;
        com.facebook.shimmer.ShimmerFrameLayout shimmer = (com.facebook.shimmer.ShimmerFrameLayout) shimmerView;
        shimmer.stopShimmer();

        android.widget.LinearLayout inner = (android.widget.LinearLayout) shimmer.getChildAt(0);
        android.view.ViewGroup parent = (android.view.ViewGroup) shimmer.getParent();
        int shimmerIndex = parent.indexOfChild(shimmer);

        TextView nameView = inner.findViewById(R.id.SongView_SongName);
        TextView artistView = inner.findViewById(R.id.SongView_ArtistName);
        inner.removeAllViews();
        shimmer.removeAllViews();
        parent.removeView(shimmer);

        if (nameView != null) parent.addView(nameView, shimmerIndex);
        if (artistView != null) parent.addView(artistView, shimmerIndex + 1);
    }

    private void fetchAndPopulateFromApi(String spotifyUri) {
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            stopHeaderShimmer();
            return;
        }

        String trackId = spotifyUri.substring(spotifyUri.lastIndexOf(":") + 1);
        Log.d(TAG, "API: getTrack trackId=" + trackId + " (song not yet in DB)");

        mainActivity.getSpotifyApi().getTrack(trackId)
                .build()
                .executeAsync()
                .thenAccept(track -> {
                    if (track == null) {
                        runOnUiThread(this::stopHeaderShimmer);
                        return;
                    }

                    se.michaelthelin.spotify.model_objects.specification.AlbumSimplified trackAlbum = track.getAlbum();
                    Image[] images = trackAlbum != null ? trackAlbum.getImages() : null;
                    String imageUrl = images != null && images.length > 0 ? images[0].getUrl() : null;

                    SongModel.Album album = trackAlbum != null ? new SongModel.Album(
                            trackAlbum.getId(),
                            trackAlbum.getName(),
                            trackAlbum.getAlbumType() != null ? trackAlbum.getAlbumType().getType() : null,
                            trackAlbum.getReleaseDate(),
                            imageUrl
                    ) : null;

                    String isrc = null;
                    if (track.getExternalIds() != null && track.getExternalIds().getExternalIds() != null) {
                        isrc = track.getExternalIds().getExternalIds().getOrDefault("isrc", null);
                    }

                    Boolean playable = track.getIsPlayable();
                    ArtistSimplified[] artists = track.getArtists();
                    SongModel fullSong = new SongModel(
                            spotifyUri,
                            track.getName(),
                            artists,
                            track.getDurationMs(),
                            spotifyUri,
                            track.getPopularity(),
                            album,
                            isrc,
                            null,
                            playable == null || playable
                    );

                    DatabaseHelper dbHelper = new DatabaseHelper(getApplicationContext());
                    dbHelper.upsertFullTrack(track, fullSong);
                    dbHelper.close();

                    runOnUiThread(() -> {
                        selectedSong = fullSong;
                        stopHeaderShimmer();
                        TextView nameView = findViewById(R.id.SongView_SongName);
                        TextView artistView = findViewById(R.id.SongView_ArtistName);
                        if (nameView != null) nameView.setAlpha(0f);
                        if (artistView != null) artistView.setAlpha(0f);
                        setupBasicSongInfo();
                        if (nameView != null) nameView.animate().alpha(1f).setDuration(300).start();
                        if (artistView != null) artistView.animate().alpha(1f).setDuration(300).start();
                        setupDynamicBackground();
                        // Recreate the adapter so the Details tab picks up the full metadata
                        allVariantUris.clear();
                        allVariantUris.add(fullSong.getId());
                        DatabaseHelper dbHelper2 = new DatabaseHelper(getApplicationContext());
                        allVariantUris.addAll(dbHelper2.getIsrcLinkedUris(fullSong.getId()));
                        dbHelper2.close();
                        int currentTab = viewPager.getCurrentItem();
                        tabAdapter = new SongTabAdapter(SongView.this, fullSong, false, allVariantUris);
                        viewPager.setAdapter(tabAdapter);
                        viewPager.setCurrentItem(currentTab, false);
                    });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to fetch track metadata", throwable);
                    runOnUiThread(this::stopHeaderShimmer);
                    return null;
                });
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
        List<DatabaseHelper.PlaylistLink> playlists = allVariantUris.size() > 1
                ? dbHelper.getPlaylistsForUris(allVariantUris)
                : dbHelper.getPlaylistsForSong(selectedSong.getId());

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

                icon.setOnClickListener(v -> {
                    PlaylistModel playlist =
                            new DatabaseHelper(this).getPlaylistById(link.playlistId);
                    if (playlist == null) return;
                    SelectedPlaylistHolder.getInstance()
                            .setSelectedPlaylist(playlist, MainActivity.getInstance());
                    startActivity(new android.content.Intent(this, PlaylistView.class));
                });

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
        View albumCoverImg = findViewById(R.id.SongView_AlbumCover);
        View albumCard = (View) albumCoverImg.getParent(); // CardView wrapping the ImageView
        int panelWidthPx = dpToPx(PANEL_WIDTH_DP);

        Runnable doToggle = () -> {
            // shownX: panel's right edge flush with cover's left edge within the FrameLayout
            int shownX = albumCard.getLeft() - panelWidthPx;

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
                        .translationX(shownX)
                        .alpha(1f)
                        .setDuration(220)
                        .start();
                playlistPanelVisible = true;
            }
        };

        if (albumCard.getWidth() == 0) {
            albumCard.post(doToggle);
        } else {
            doToggle.run();
        }
    }

    private void showNewSongPill() {
        View pillShimmer = findViewById(R.id.new_song_pill_shimmer);
        if (pillShimmer == null) return;
        pillShimmer.setVisibility(View.VISIBLE);
        pillShimmer.setScaleX(0f);
        pillShimmer.setScaleY(0f);
        pillShimmer.animate().scaleX(1f).scaleY(1f)
                .setDuration(350)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .start();
    }

    private void styleNewSongPill(int startColor, int endColor) {
        TextView pill = findViewById(R.id.new_song_pill);
        View pillShimmer = findViewById(R.id.new_song_pill_shimmer);
        if (pill == null || pillShimmer == null || pillShimmer.getVisibility() != View.VISIBLE) return;

        // Inverted album colours so the pill pops against the cover it sits on
        int invertedStart = invertColor(startColor);
        int invertedEnd = invertColor(endColor);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{invertedStart, invertedEnd});
        bg.setCornerRadius(dpToPx(50));
        bg.setStroke(dpToPx(1), Color.argb(80, 255, 255, 255));
        pill.setBackground(bg);

        int midColor = androidx.core.graphics.ColorUtils.blendARGB(invertedStart, invertedEnd, 0.5f);
        pill.setTextColor(SnippetTheme.contrastColor(midColor));
    }

    private static int invertColor(int color) {
        return Color.rgb(255 - Color.red(color), 255 - Color.green(color), 255 - Color.blue(color));
    }

    private void setupDynamicBackground() {
        ColorExtractor.extractColors(this, selectedSong.getAlbumCoverUrl(), new ColorExtractor.ColorExtractionCallback() {
            @Override
            public void onColorExtracted(int dominantColor, int vibrantColor) {
                styleNewSongPill(vibrantColor, dominantColor);
                applyGradientBackground(ColorExtractor.pickBackgroundColor(dominantColor, vibrantColor));
                applyTabTheme(SnippetTheme.from(vibrantColor, dominantColor));
            }

            @Override
            public void onError() {
                applyGradientBackground(Color.parseColor("#424242"));
                applyTabTheme(SnippetTheme.fallback());
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

    /**
     * Tint the Details/Notes/Snippets tab pills to match the album theme:
     * the selected tab uses the vibrant accent, the rest a translucent dark
     * fill, with text colours chosen for contrast against each.
     */
    private void applyTabTheme(SnippetTheme theme) {
        if (tabLayout == null) return;

        int selectedFill = theme.playButton;
        int unselectedFill = theme.rowBackground;

        tabSelectedTextColor = SnippetTheme.contrastColor(selectedFill);
        tabUnselectedTextColor = theme.primaryText;

        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab == null || tab.getCustomView() == null) continue;
            View custom = tab.getCustomView();

            android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_selected}, pill(selectedFill));
            bg.addState(new int[]{}, pill(unselectedFill));
            custom.setBackground(bg);

            TextView title = custom.findViewById(R.id.tab_title);
            title.setTextColor(tab.isSelected() ? tabSelectedTextColor : tabUnselectedTextColor);
        }
    }

    private GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dpToPx(8));
        return d;
    }

    private void setupTabs(boolean isLoading) {
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);

        ViewCompat.setNestedScrollingEnabled(viewPager, true);

        tabLayout.setBackgroundColor(Color.TRANSPARENT);
        tabLayout.setTabRippleColor(null);
        tabLayout.setPadding(16, 8, 16, 8);

        tabAdapter = new SongTabAdapter(this, selectedSong, isLoading, allVariantUris);
        viewPager.setAdapter(tabAdapter);

        String[] tabTitles = {"Details", "Notes", "Snippets"};
        for (int i = 0; i < tabTitles.length; i++) {
            TabLayout.Tab tab = tabLayout.newTab();
            @SuppressLint("InflateParams") View customView = LayoutInflater.from(this).inflate(R.layout.custom_tab, null, false);

            TextView tabTitleView = customView.findViewById(R.id.tab_title);
            TextView tabBadgeView = customView.findViewById(R.id.tab_badge);

            tabTitleView.setText(tabTitles[i]);
            tabTitleView.setTextColor(i == 0 ? Color.BLACK : Color.WHITE);

            // "Details" has no count badge, so trim its horizontal padding a little
            // to keep its pill width visually in line with the badged tabs.
            if (i == 0) {
                int padH = (int) (8 * getResources().getDisplayMetrics().density);
                customView.setPadding(padH, customView.getPaddingTop(),
                        padH, customView.getPaddingBottom());
            }

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

            // tabMode="auto": each tab cell sizes to its own content (text + badge)
            // so "Snippets" shows at full size and never clips. The cells are laid
            // out left-to-right and centred as a group (tabGravity="center").
            View tabView = (View) customView.getParent();
            if (tabView != null && tabView.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tabView.getLayoutParams();
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.weight = 0f;
                tabView.setLayoutParams(lp);
            }
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
                View customView = tab.getCustomView();
                if (customView != null) {
                    customView.findViewById(R.id.tab_title).performClick();
                    ((TextView) customView.findViewById(R.id.tab_title)).setTextColor(tabSelectedTextColor);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                View customView = tab.getCustomView();
                if (customView != null) {
                    ((TextView) customView.findViewById(R.id.tab_title)).setTextColor(tabUnselectedTextColor);
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
