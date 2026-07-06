package com.ca.tunaro.activites;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.ca.tunaro.BaseActivity;
import com.ca.tunaro.R;
import com.ca.tunaro.adapters.ArtistTabAdapter;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.fragments.ArtistAddedSongsSheet;
import com.ca.tunaro.models.AlbumModel;
import com.ca.tunaro.utils.ColorExtractor;
import com.ca.tunaro.utils.SnippetTheme;
import com.google.android.material.tabs.TabLayout;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import se.michaelthelin.spotify.model_objects.specification.Image;

/**
 * Issue #80 — artist detail screen. Header shows cached followers/popularity/genres
 * (stale-while-revalidate via getArtist) plus discography-derived counts; Songs and
 * Albums tabs sit below. The discography fetch is owned here and shared with both tabs.
 */
public class ArtistView extends BaseActivity {
    private static final String TAG = "ArtistView";

    private String artistId;
    private String artistName;

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ArtistTabAdapter tabAdapter;

    private int tabSelectedTextColor = Color.BLACK;
    private int tabUnselectedTextColor = Color.WHITE;

    // Shared discography state. The fetch is owned here (not in a tab fragment) so it can be
    // triggered from either tab and the result reused by both plus the header summary.
    private static final int DISCOGRAPHY_PAGE_LIMIT = 50;
    private static final int DISCOGRAPHY_ENRICH_BATCH = 20;
    private List<AlbumModel> discography = new ArrayList<>();
    private boolean discographyLoaded = false;
    private boolean discographyLoading = false;
    private final List<DiscographyListener> discographyListeners = new ArrayList<>();

    public interface DiscographyListener {
        void onDiscographyReady(List<AlbumModel> albums);

        // Interim callback as albums stream in; final state still arrives via onDiscographyReady.
        default void onDiscographyProgress(List<AlbumModel> albums) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForRecovery()) return;

        setContentView(R.layout.activity_artist_view);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Pad the app bar (which holds the pinned back button/tabs) below the status bar.
            View appBar = findViewById(R.id.app_bar);
            if (appBar != null) {
                appBar.setPadding(appBar.getPaddingLeft(), systemBars.top,
                        appBar.getPaddingRight(), appBar.getPaddingBottom());
            }
            View playbackBar = findViewById(R.id.playback_bar);
            if (playbackBar != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) playbackBar.getLayoutParams();
                params.bottomMargin = systemBars.bottom;
                playbackBar.setLayoutParams(params);
            }
            return WindowInsetsCompat.CONSUMED;
        });

        artistId = getIntent().getStringExtra("artist_id");
        artistName = getIntent().getStringExtra("artist_name");

        if (artistId == null || artistId.isEmpty()) {
            finish();
            return;
        }

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.collapsed_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        setupTabs();
        setupCollapsingHeader();
        setupAddedSongsRow();
        loadHeader();
    }

    //#region Header

    // Fades the small collapsed-state artist image + name in as the app bar collapses, so they stay
    // visible once the big expanded header has scrolled away.
    private void setupCollapsingHeader() {
        com.google.android.material.appbar.AppBarLayout appBar = findViewById(R.id.app_bar);
        TextView collapsedName = findViewById(R.id.collapsed_artist_name);
        View collapsedImageCard = findViewById(R.id.collapsed_artist_image_card);
        if (appBar == null || collapsedName == null) return;
        collapsedName.setText(artistName);
        appBar.addOnOffsetChangedListener((bar, verticalOffset) -> {
            int range = bar.getTotalScrollRange();
            // 0 when fully expanded, 1 when fully collapsed.
            float fraction = range == 0 ? 0f : Math.abs(verticalOffset) / (float) range;
            // Only ramp up over the last portion of the collapse for a snappier fade.
            float alpha = Math.min(1f, Math.max(0f, (fraction - 0.6f) / 0.4f));
            collapsedName.setAlpha(alpha);
            if (collapsedImageCard != null) collapsedImageCard.setAlpha(alpha);
        });
    }

    private void loadHeader() {
        TextView nameView = findViewById(R.id.artist_name);
        nameView.setSelected(true);
        if (artistName != null) nameView.setText(artistName);

        DatabaseHelper db = new DatabaseHelper(this);
        DatabaseHelper.ArtistStats cached = db.getArtistStats(artistId);
        db.close();

        boolean firstEverVisit = cached == null || cached.fetchedAt == null;
        if (firstEverVisit) {
            showHeaderShimmer(true);
        } else {
            renderHeaderStats(cached.name, cached.imageUrl, cached.followers, cached.popularity, cached.genres);
        }

        // Always revalidate in the background (stale-while-revalidate).
        fetchArtistFromApi(firstEverVisit);
    }

    private void fetchArtistFromApi(boolean firstEverVisit) {
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            if (firstEverVisit) runOnUiThread(() -> showHeaderShimmer(false));
            return;
        }

        String id = bareId(artistId);
        Log.d(TAG, "API: getArtist id=" + id);
        mainActivity.executeWithTokenRefresh(() -> mainActivity.getSpotifyApi().getArtist(id).build())
                .thenAccept(artist -> {
                    if (artist == null) {
                        if (firstEverVisit) runOnUiThread(() -> showHeaderShimmer(false));
                        return;
                    }
                    Image[] images = artist.getImages();
                    String imageUrl = images != null && images.length > 0 ? images[0].getUrl() : null;
                    Integer followers = artist.getFollowers() != null ? artist.getFollowers().getTotal() : null;
                    Integer popularity = artist.getPopularity();
                    List<String> genres = artist.getGenres() != null
                            ? java.util.Arrays.asList(artist.getGenres()) : new ArrayList<>();
                    String name = artist.getName() != null ? artist.getName() : artistName;

                    DatabaseHelper writeDb = new DatabaseHelper(getApplicationContext());
                    writeDb.upsertArtistStats(artistId, name, imageUrl, followers, popularity, genres);
                    writeDb.close();

                    runOnUiThread(() -> {
                        showHeaderShimmer(false);
                        renderHeaderStats(name, imageUrl, followers, popularity, genres);
                    });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to fetch artist", throwable);
                    if (firstEverVisit) runOnUiThread(() -> showHeaderShimmer(false));
                    return null;
                });
    }

    private void renderHeaderStats(String name, String imageUrl, Integer followers,
                                   Integer popularity, List<String> genres) {
        if (name != null) {
            artistName = name;
            ((TextView) findViewById(R.id.artist_name)).setText(name);
            ((TextView) findViewById(R.id.collapsed_artist_name)).setText(name);
        }

        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.song_placeholder)
                .error(R.drawable.song_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into((android.widget.ImageView) findViewById(R.id.artist_image));

        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.song_placeholder)
                .error(R.drawable.song_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into((android.widget.ImageView) findViewById(R.id.collapsed_artist_image));

        TextView meta = findViewById(R.id.artist_meta);
        List<String> metaParts = new ArrayList<>();
        if (followers != null) {
            metaParts.add(NumberFormat.getInstance(Locale.getDefault()).format(followers) + " followers");
        }
        if (popularity != null) {
            metaParts.add(popularity + "% popularity");
        }
        meta.setText(String.join(" · ", metaParts));

        TextView genresView = findViewById(R.id.artist_genres);
        if (genres != null && !genres.isEmpty()) {
            genresView.setText(String.join(" · ", genres));
            genresView.setVisibility(View.VISIBLE);
        } else {
            genresView.setVisibility(View.GONE);
        }

        applyBackgroundFromImage(imageUrl);
    }

    private void applyBackgroundFromImage(String imageUrl) {
        if (imageUrl == null) {
            applyTabTheme(SnippetTheme.fallback());
            return;
        }
        ColorExtractor.extractColors(this, imageUrl, new ColorExtractor.ColorExtractionCallback() {
            @Override
            public void onColorExtracted(int dominantColor, int vibrantColor) {
                applyGradientBackground(ColorExtractor.pickBackgroundColor(dominantColor, vibrantColor));
                applyTabTheme(SnippetTheme.from(vibrantColor, dominantColor));
            }

            @Override
            public void onError() {
                applyTabTheme(SnippetTheme.fallback());
            }
        });
    }

    private void applyGradientBackground(int dominantColor) {
        View mainLayout = findViewById(R.id.main);
        if (mainLayout != null) {
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TR_BL,
                    new int[]{dominantColor, Color.BLACK});
            mainLayout.setBackground(gradient);
        }
        applyCollapsedScrim(dominantColor);
    }

    // Collapsed toolbar scrim: black on the left, fading to a tint of the extracted colour on the
    // right. The tint begins just past the small artist image (~20% from the left) and strengthens
    // toward the right edge, leaving the back arrow + image on clean black.
    private void applyCollapsedScrim(int dominantColor) {
        com.google.android.material.appbar.CollapsingToolbarLayout collapsing =
                findViewById(R.id.collapsing_toolbar);
        if (collapsing == null) return;
        // Blend a third of the way toward black so the tint stays readable but pronounced.
        int tint = ColorUtils.blendARGB(dominantColor, Color.BLACK, 0.35f);
        GradientDrawable scrim = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.BLACK, Color.BLACK, tint});
        // Hold black across the first ~20%, then ramp to the tint at the right edge.
        scrim.setGradientCenter(0.2f, 0.5f);
        collapsing.setContentScrim(scrim);
    }

    private void showHeaderShimmer(boolean show) {
        com.facebook.shimmer.ShimmerFrameLayout shimmer = findViewById(R.id.artist_header_shimmer);
        View content = findViewById(R.id.artist_header_content);
        if (show) {
            shimmer.setVisibility(View.VISIBLE);
            shimmer.startShimmer();
            content.setVisibility(View.INVISIBLE);
        } else {
            shimmer.stopShimmer();
            shimmer.setVisibility(View.GONE);
            content.setVisibility(View.VISIBLE);
        }
    }

    /** Updates the header's discography summary line once the album list is known. */
    public void updateDiscographySummary(List<AlbumModel> albums) {
        TextView summary = findViewById(R.id.artist_discography_summary);
        if (albums == null || albums.isEmpty()) {
            summary.setVisibility(View.GONE);
            return;
        }

        int albumCount = 0, singleCount = 0, earliestYear = Integer.MAX_VALUE;
        AlbumModel latest = null;
        for (AlbumModel a : albums) {
            if ("single".equalsIgnoreCase(a.getAlbumType())) singleCount++;
            else albumCount++;
            int year = a.getReleaseYear();
            if (year > 0 && year < earliestYear) earliestYear = year;
            if (latest == null || a.getReleaseYear() > latest.getReleaseYear()) latest = a;
        }

        List<String> parts = new ArrayList<>();
        if (albumCount > 0) parts.add(albumCount + (albumCount == 1 ? " album" : " albums"));
        if (singleCount > 0) parts.add(singleCount + (singleCount == 1 ? " single" : " singles"));
        if (earliestYear != Integer.MAX_VALUE) parts.add("Active since " + earliestYear);
        if (latest != null && latest.getReleaseYear() > 0) {
            parts.add("Latest: " + latest.getName() + " (" + latest.getReleaseYear() + ")");
        }
        summary.setText(String.join(" · ", parts));
        summary.setVisibility(View.VISIBLE);
    }

    //#endregion

    //#region Added songs row

    private void setupAddedSongsRow() {
        LinearLayout row = findViewById(R.id.added_songs_row);
        TextView label = findViewById(R.id.added_songs_label);

        DatabaseHelper db = new DatabaseHelper(this);
        int count = db.getArtistSongsInFavOrArchivedPlaylists(artistId).size();
        db.close();

        if (count == 0) {
            row.setVisibility(View.GONE);
            return;
        }
        label.setText(count + (count == 1 ? " added song" : " added songs"));
        row.setVisibility(View.VISIBLE);
        row.setOnClickListener(v -> {
            ArtistAddedSongsSheet sheet = ArtistAddedSongsSheet.newInstance(artistId, artistName);
            sheet.show(getSupportFragmentManager(), "artist_added_songs");
        });
    }

    //#endregion

    //#region Discography (owned here so either tab can trigger and consume it)

    public String getArtistId() { return artistId; }
    public String getArtistName() { return artistName; }

    public boolean isDiscographyLoaded() { return discographyLoaded; }
    public List<AlbumModel> getDiscography() { return discography; }

    /**
     * Registers a listener for the discography. If already loaded it fires immediately; otherwise
     * the fetch is kicked off (idempotently) so a listener added from either tab — including the
     * Songs tab's "Load full discography" when the Albums tab was never opened — is guaranteed to
     * eventually receive the result.
     */
    public void addDiscographyListener(DiscographyListener listener) {
        if (discographyLoaded) {
            listener.onDiscographyReady(discography);
            return;
        }
        if (!discographyListeners.contains(listener)) discographyListeners.add(listener);
        ensureDiscographyLoading();
    }

    public void removeDiscographyListener(DiscographyListener listener) {
        discographyListeners.remove(listener);
    }

    // Interim update as albums page in / get enriched: listeners re-render the growing list but
    // the discography is not yet marked complete, so consumers that need the full set (the Songs
    // tab's "Load full discography") keep waiting for the final publish.
    private void publishProgress(List<AlbumModel> albums) {
        runOnUiThread(() -> {
            discography = albums;
            updateDiscographySummary(albums);
            for (DiscographyListener listener : new ArrayList<>(discographyListeners)) {
                listener.onDiscographyProgress(albums);
            }
        });
    }

    private void publishDiscography(List<AlbumModel> albums) {
        runOnUiThread(() -> {
            discography = albums;
            discographyLoaded = true;
            discographyLoading = false;
            updateDiscographySummary(albums);
            for (DiscographyListener listener : new ArrayList<>(discographyListeners)) {
                listener.onDiscographyReady(albums);
            }
        });
    }

    private void ensureDiscographyLoading() {
        if (discographyLoaded || discographyLoading) return;
        discographyLoading = true;

        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            publishDiscography(new ArrayList<>());
            return;
        }
        fetchAlbumPage(mainActivity, 0, new java.util.LinkedHashMap<>());
    }

    // Pages getArtistsAlbums (album,single), dedup'd by normalized name keeping the earliest release.
    private void fetchAlbumPage(MainActivity mainActivity, int offset,
                                java.util.Map<String, AlbumModel> byName) {
        String id = bareId(artistId);
        Log.d(TAG, "API: getArtistsAlbums id=" + id + " offset=" + offset);
        mainActivity.executeWithTokenRefresh(() -> mainActivity.getSpotifyApi()
                        .getArtistsAlbums(id)
                        .album_type("album,single")
                        .limit(DISCOGRAPHY_PAGE_LIMIT)
                        .offset(offset)
                        .build())
                .thenAccept(paging -> {
                    se.michaelthelin.spotify.model_objects.specification.AlbumSimplified[] items =
                            paging != null ? paging.getItems() : null;
                    if (items != null) {
                        for (se.michaelthelin.spotify.model_objects.specification.AlbumSimplified a : items) {
                            String key = a.getName() == null ? "" : a.getName().trim().toLowerCase(Locale.getDefault());
                            AlbumModel candidate = toAlbumModel(a);
                            AlbumModel existing = byName.get(key);
                            if (existing == null || isEarlier(candidate, existing)) byName.put(key, candidate);
                        }
                    }
                    // Surface albums as each page lands so the list fills progressively.
                    publishProgress(new ArrayList<>(byName.values()));
                    boolean morePages = items != null && items.length == DISCOGRAPHY_PAGE_LIMIT
                            && paging.getNext() != null;
                    if (morePages) {
                        fetchAlbumPage(mainActivity, offset + DISCOGRAPHY_PAGE_LIMIT, byName);
                    } else {
                        enrichAlbums(mainActivity, new ArrayList<>(byName.values()), 0);
                    }
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to fetch artist albums", throwable);
                    enrichAlbums(mainActivity, new ArrayList<>(byName.values()), 0);
                    return null;
                });
    }

    // Enriches in batches of 20 via getSeveralAlbums for track count + popularity.
    private void enrichAlbums(MainActivity mainActivity, List<AlbumModel> collected, int start) {
        if (collected.isEmpty() || start >= collected.size()) {
            publishDiscography(collected);
            return;
        }
        int end = Math.min(start + DISCOGRAPHY_ENRICH_BATCH, collected.size());
        java.util.Map<String, AlbumModel> byId = new java.util.LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        for (AlbumModel a : collected.subList(start, end)) {
            if (a.getAlbumId() != null) {
                byId.put(a.getAlbumId(), a);
                ids.add(a.getAlbumId());
            }
        }
        if (ids.isEmpty()) {
            enrichAlbums(mainActivity, collected, end);
            return;
        }
        String[] idArray = ids.toArray(new String[0]);
        Log.d(TAG, "API: getSeveralAlbums count=" + idArray.length);
        mainActivity.executeWithTokenRefresh(() -> mainActivity.getSpotifyApi().getSeveralAlbums(idArray).build())
                .thenAccept(full -> {
                    if (full != null) {
                        for (se.michaelthelin.spotify.model_objects.specification.Album album : full) {
                            if (album == null) continue;
                            AlbumModel model = byId.get(album.getId());
                            if (model == null) continue;
                            if (album.getTracks() != null && album.getTracks().getTotal() != null) {
                                model.setTrackCount(album.getTracks().getTotal());
                            }
                            if (album.getPopularity() != null) model.setPopularity(album.getPopularity());
                        }
                    }
                    // Re-publish so the freshly enriched track counts / popularity show up.
                    publishProgress(collected);
                    enrichAlbums(mainActivity, collected, end);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to enrich album batch", throwable);
                    enrichAlbums(mainActivity, collected, end);
                    return null;
                });
    }

    private static AlbumModel toAlbumModel(se.michaelthelin.spotify.model_objects.specification.AlbumSimplified a) {
        Image[] images = a.getImages();
        String imageUrl = images != null && images.length > 0 ? images[0].getUrl() : null;
        String type = a.getAlbumType() != null ? a.getAlbumType().getType() : "album";
        return new AlbumModel(a.getId(), a.getName(), type, a.getReleaseDate(), imageUrl);
    }

    // A known year beats an unknown one; between known years the smaller wins. A yearless candidate
    // never displaces an entry that already has a year.
    private static boolean isEarlier(AlbumModel candidate, AlbumModel existing) {
        int c = candidate.getReleaseYear();
        int e = existing.getReleaseYear();
        if (c == 0) return false;
        if (e == 0) return true;
        return c < e;
    }

    //#endregion

    //#region Tabs

    private void setupTabs() {
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);

        tabAdapter = new ArtistTabAdapter(this, artistId);
        viewPager.setAdapter(tabAdapter);

        String[] tabTitles = {"Songs", "Albums"};
        for (String title : tabTitles) {
            TabLayout.Tab tab = tabLayout.newTab();
            @SuppressLint("InflateParams")
            View customView = LayoutInflater.from(this).inflate(R.layout.custom_tab, null, false);
            TextView titleView = customView.findViewById(R.id.tab_title);
            titleView.setText(title);
            titleView.setTextColor(Color.WHITE);
            customView.findViewById(R.id.tab_badge).setVisibility(View.GONE);
            tab.setCustomView(customView);
            tabLayout.addTab(tab);
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
                View customView = tab.getCustomView();
                if (customView != null) {
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

    //#endregion

    private static String bareId(String idOrUri) {
        if (idOrUri == null) return null;
        return idOrUri.contains(":") ? idOrUri.substring(idOrUri.lastIndexOf(":") + 1) : idOrUri;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
