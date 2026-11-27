package com.ca.tunaro.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import com.ca.tunaro.R;
import com.ca.tunaro.activites.AlbumView;
import com.ca.tunaro.activites.SongView;
import com.ca.tunaro.activites.SongWebInfoActivity;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.AppExecutors;
import com.ca.tunaro.utils.DarkListDialog;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SongDetailsFragment extends Fragment {
    private static final String TAG = "SongDetailsFragment";
    private static final String ARG_SONG = "song";
    private static final String ARG_LOADING = "is_loading";
    private static final String ARG_VARIANT_URIS = "variant_uris";
    private static final long EXPAND_ANIM_MS = 200L;

    private SongModel song;
    private List<String> variantUris;
    private View rootView;
    private boolean isHistoryExpanded = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static SongDetailsFragment newInstance(SongModel song, boolean isLoading, List<String> variantUris) {
        SongDetailsFragment fragment = new SongDetailsFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_SONG, song);
        args.putBoolean(ARG_LOADING, isLoading);
        args.putStringArrayList(ARG_VARIANT_URIS, new java.util.ArrayList<>(variantUris));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            song = (SongModel) getArguments().getSerializable(ARG_SONG);
            variantUris = getArguments().getStringArrayList(ARG_VARIANT_URIS);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_song_details, container, false);

        boolean isLoading = getArguments() != null && getArguments().getBoolean(ARG_LOADING, false);

        com.facebook.shimmer.ShimmerFrameLayout shimmer = rootView.findViewById(R.id.details_shimmer);
        View detailsContent = rootView.findViewById(R.id.details_content);

        if (isLoading) {
            shimmer.setVisibility(View.VISIBLE);
            shimmer.startShimmer();
            detailsContent.setVisibility(View.GONE);
            rootView.findViewById(R.id.listening_history_section).setVisibility(View.GONE);
            rootView.findViewById(R.id.more_details_button).setVisibility(View.GONE);
            rootView.findViewById(R.id.first_seen_value).setVisibility(View.GONE);
            rootView.findViewById(R.id.song_id_value).setVisibility(View.GONE);
        } else if (song != null) {
            shimmer.setVisibility(View.GONE);
            detailsContent.setVisibility(View.VISIBLE);
            setupSongDetails();
            setupMoreDetailsButton();
            loadDetailsAsync();
        }

        return rootView;
    }

    private void setupSongDetails() {
        // Album info
        TextView albumTypeView = rootView.findViewById(R.id.SongView_AlbumType);
        TextView albumNameView = rootView.findViewById(R.id.SongView_AlbumName);
        LinearLayout albumRow = rootView.findViewById(R.id.album_row);

        String albumType = song.getAlbumType();
        if (albumType != null) {
            albumTypeView.setText(capitalise(albumType));
            // Colour by album type (shared with the album page's type chip).
            int typeColor = com.ca.tunaro.utils.AlbumFormat.typeColor(albumType);
            if (typeColor != com.ca.tunaro.utils.AlbumFormat.COLOR_UNKNOWN) {
                albumTypeView.setTextColor(typeColor);
            }
        }

        albumNameView.setText(song.getAlbumName());

        albumRow.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AlbumView.class);
            intent.putExtra("album_id", song.getAlbumId());
            intent.putExtra("album_name", song.getAlbumName());
            startActivity(intent);
        });

        // Release date
        TextView releaseDateView = rootView.findViewById(R.id.SongView_ReleaseDate);
        releaseDateView.setText(formatReleaseDate(song.getReleaseDate()));

        // Duration
        TextView durationView = rootView.findViewById(R.id.SongView_SongDuration);
        durationView.setText(song.getDurationString());

        // Popularity — show the highest value across all variants
        LinearLayout popularityRow = rootView.findViewById(R.id.popularity_row);
        TextView popularityView = rootView.findViewById(R.id.SongView_SongPopularity);
        // Single-version songs bind immediately; multi-version maxima arrive with loadDetailsAsync().
        if (!hasVariants()) bindPopularity(song.getPopularity());

        // Variants row
        LinearLayout variantsRow = rootView.findViewById(R.id.variants_row);
        if (variantUris != null && variantUris.size() > 1) {
            variantsRow.setVisibility(View.VISIBLE);
            TextView variantsValue = rootView.findViewById(R.id.variants_value);
            List<String> otherUris = new ArrayList<>();
            for (String uri : variantUris) {
                if (!uri.equals(song.getId())) otherUris.add(uri);
            }
            variantsValue.setText(getResources().getQuantityString(
                    R.plurals.other_versions_count, otherUris.size(), otherUris.size()));
            variantsRow.setOnClickListener(v -> openVariantPicker(otherUris));
        }

        // First seen + song ID footer
        TextView firstSeenView = rootView.findViewById(R.id.first_seen_value);
        String createdAt = formatAbsoluteDate(song.getCreatedAt());
        if (createdAt != null && !createdAt.equals("Unknown")) {
            firstSeenView.setText(getString(R.string.first_seen_value, createdAt));
        } else {
            firstSeenView.setVisibility(View.GONE);
        }
        TextView songIdView = rootView.findViewById(R.id.song_id_value);
        songIdView.setText(song.getId());
    }

    private boolean hasVariants() {
        return variantUris != null && variantUris.size() > 1;
    }

    private void bindPopularity(int popularity) {
        if (popularity <= 0) return;
        rootView.findViewById(R.id.popularity_row).setVisibility(View.VISIBLE);
        TextView popularityView = rootView.findViewById(R.id.SongView_SongPopularity);
        popularityView.setText(getString(R.string.popularity_value, popularity));
    }

    /** Reads variant popularity and listen history on the I/O pool, then binds on return. */
    private void loadDetailsAsync() {
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(requireContext());
        final boolean variants = hasVariants();
        final List<String> uris = variants ? new ArrayList<>(variantUris) : null;
        final String songId = song.getId();
        final View boundRoot = rootView;
        AppExecutors.io().execute(() -> {
            int popularity = variants ? dbHelper.getMaxPopularityForUris(uris) : -1;
            List<String> history = variants
                    ? dbHelper.getListenHistoryForUris(uris)
                    : dbHelper.getListenHistory(songId);
            Map<String, Integer> grouped = groupListensByTimePeriod(history);
            int total = history.size();
            mainHandler.post(() -> {
                if (!isAdded() || getView() != boundRoot) return;
                if (variants) bindPopularity(popularity);
                bindListeningHistory(total, grouped);
            });
        });
    }

    private void bindListeningHistory(int totalListens, Map<String, Integer> groupedListens) {
        LinearLayout historySection = rootView.findViewById(R.id.listening_history_section);
        LinearLayout historyHeader = rootView.findViewById(R.id.listening_history_header);
        LinearLayout historyContent = rootView.findViewById(R.id.listening_history_content);
        TextView listenCountView = rootView.findViewById(R.id.listen_count);
        ImageView expandCollapseIcon = rootView.findViewById(R.id.expand_collapse_icon);

        if (totalListens == 0) {
            historySection.setVisibility(View.GONE);
            return;
        }

        historySection.setVisibility(View.VISIBLE);
        listenCountView.setText(getResources().getQuantityString(
                R.plurals.listen_count, totalListens, totalListens));

        int rowPadding = getResources().getDimensionPixelSize(R.dimen.space_sm);
        historyContent.removeAllViews();
        for (Map.Entry<String, Integer> entry : groupedListens.entrySet()) {
            TextView textView = new TextView(requireContext());
            int count = entry.getValue();
            String displayText = count > 1
                    ? getResources().getQuantityString(R.plurals.listen_group, count, entry.getKey(), count)
                    : entry.getKey();
            textView.setText(displayText);
            TextViewCompat.setTextAppearance(textView, R.style.TextAppearance_Tunaro_BodySmall);
            textView.setPadding(0, rowPadding, 0, rowPadding);
            historyContent.addView(textView);
        }

        // Restore the expanded state when the view is recreated.
        historyContent.setVisibility(isHistoryExpanded ? View.VISIBLE : View.GONE);
        expandCollapseIcon.setRotation(isHistoryExpanded ? 180f : 0f);

        historyHeader.setOnClickListener(v -> {
            isHistoryExpanded = !isHistoryExpanded;
            ViewGroup sceneRoot = (ViewGroup) rootView;
            TransitionManager.beginDelayedTransition(sceneRoot,
                    new AutoTransition().setDuration(EXPAND_ANIM_MS));
            historyContent.setVisibility(isHistoryExpanded ? View.VISIBLE : View.GONE);
            expandCollapseIcon.animate()
                    .rotation(isHistoryExpanded ? 180f : 0f)
                    .setDuration(EXPAND_ANIM_MS)
                    .start();
        });
    }

    private void setupMoreDetailsButton() {
        MaterialButton moreDetailsButton = rootView.findViewById(R.id.more_details_button);
        moreDetailsButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SongWebInfoActivity.class);
            startActivity(intent);
        });
    }

    private void openVariantPicker(List<String> otherUris) {
        DatabaseHelper db = DatabaseHelper.getInstance(requireContext());
        AppExecutors.io().execute(() -> {
            List<SongModel> variants = new ArrayList<>();
            for (String uri : otherUris) {
                SongModel s = db.getLeanSong(uri);
                if (s != null) variants.add(s);
            }
            mainHandler.post(() -> {
                if (isAdded()) showVariantPicker(variants);
            });
        });
    }

    private void showVariantPicker(List<SongModel> variants) {
        if (variants.isEmpty()) return;

        if (variants.size() == 1) {
            navigateToVariant(variants.get(0));
            return;
        }

        String[] labels = new String[variants.size()];
        for (int i = 0; i < variants.size(); i++) {
            SongModel v = variants.get(i);
            String album = v.getAlbumName() != null ? v.getAlbumName() : "Unknown album";
            labels[i] = album + " — " + v.getArtist();
        }

        DarkListDialog.show(requireContext(), "Other versions", Arrays.asList(labels),
                position -> navigateToVariant(variants.get(position)));
    }

    private void navigateToVariant(SongModel variant) {
        SelectedSongHolder.getInstance().setSelectedSong(variant);
        startActivity(new Intent(requireContext(), SongView.class));
    }

    private static Map<String, Integer> groupListensByTimePeriod(List<String> timestamps) {
        Map<String, Integer> grouped = new LinkedHashMap<>();

        // Support both formats - with and without milliseconds
        java.text.SimpleDateFormat formatWithMillis = new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        java.text.SimpleDateFormat formatWithoutMillis = new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);

        formatWithMillis.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        formatWithoutMillis.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

        for (String timestamp : timestamps) {
            try {
                java.util.Date listenDate;

                // Try with milliseconds first, then without
                try {
                    listenDate = formatWithMillis.parse(timestamp);
                } catch (java.text.ParseException e) {
                    listenDate = formatWithoutMillis.parse(timestamp);
                }

                String timeDescription = DatabaseHelper.getRelativeTimeDescription(listenDate);
                grouped.put(timeDescription, grouped.getOrDefault(timeDescription, 0) + 1);
            } catch (Exception e) {
                // Fallback for malformed timestamps
                grouped.put("Unknown time", grouped.getOrDefault("Unknown time", 0) + 1);
            }
        }

        return grouped;
    }

    private String formatReleaseDate(String releaseDate) {
        if (releaseDate == null || releaseDate.isEmpty()) {
            return "Unknown";
        }

        try {
            String[] parts = releaseDate.split("-");

            if (parts.length >= 1) {
                String year = parts[0];

                if (parts.length >= 2) {
                    int month = Integer.parseInt(parts[1]);
                    String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                    String monthName = monthNames[month - 1];

                    if (parts.length == 3) {
                        String day = parts[2];
                        return day + " " + monthName + " " + year;
                    } else {
                        return monthName + " " + year;
                    }
                }

                return year;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting release date: " + releaseDate, e);
        }

        return releaseDate;
    }

    private String formatAbsoluteDate(String utcTimestamp) {
        if (utcTimestamp == null || utcTimestamp.isEmpty()) return "Unknown";
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

    private String capitalise(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1).toLowerCase(Locale.ROOT);
    }
}
