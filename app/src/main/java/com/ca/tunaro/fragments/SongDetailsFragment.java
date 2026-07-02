package com.ca.tunaro.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ca.tunaro.R;
import com.ca.tunaro.activites.AlbumView;
import com.ca.tunaro.activites.SongView;
import com.ca.tunaro.activites.SongWebInfoActivity;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.DarkListDialog;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SongDetailsFragment extends Fragment {
    private static final String TAG = "SongDetailsFragment";
    private static final String ARG_SONG = "song";
    private static final String ARG_LOADING = "is_loading";
    private static final String ARG_VARIANT_URIS = "variant_uris";

    private SongModel song;
    private List<String> variantUris;
    private View rootView;
    private boolean isHistoryExpanded = false;

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
            setupListeningHistory();
            setupMoreDetailsButton();
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
            // Color based on album type
            switch (albumType.toLowerCase()) {
                case "single":
                    albumTypeView.setTextColor(0xFF4CAF50); // Green
                    break;
                case "album":
                    albumTypeView.setTextColor(0xFF2196F3); // Blue
                    break;
                case "compilation":
                    albumTypeView.setTextColor(0xFFFF9800); // Orange
                    break;
            }
        }

        albumNameView.setText(song.getAlbumName());

        albumRow.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AlbumView.class);
            intent.putExtra("album_id", song.getAlbumId());
            intent.putExtra("album_name", song.getAlbumName());
            startActivity(intent);
        });

        setupArtists();

        // Release date
        TextView releaseDateView = rootView.findViewById(R.id.SongView_ReleaseDate);
        releaseDateView.setText(formatReleaseDate(song.getReleaseDate()));

        // Duration
        TextView durationView = rootView.findViewById(R.id.SongView_SongDuration);
        durationView.setText(song.getDurationString());

        // Popularity — show the highest value across all variants
        LinearLayout popularityRow = rootView.findViewById(R.id.popularity_row);
        TextView popularityView = rootView.findViewById(R.id.SongView_SongPopularity);
        DatabaseHelper dbHelper = new DatabaseHelper(requireContext());
        int popularity = variantUris != null && variantUris.size() > 1
                ? dbHelper.getMaxPopularityForUris(variantUris)
                : song.getPopularity();
        dbHelper.close();
        if (popularity > 0) {
            popularityRow.setVisibility(View.VISIBLE);
            popularityView.setText(getString(R.string.popularity_value, popularity));
        }

        // Variants row
        LinearLayout variantsRow = rootView.findViewById(R.id.variants_row);
        if (variantUris != null && variantUris.size() > 1) {
            variantsRow.setVisibility(View.VISIBLE);
            TextView variantsValue = rootView.findViewById(R.id.variants_value);
            List<String> otherUris = new ArrayList<>();
            for (String uri : variantUris) {
                if (!uri.equals(song.getId())) otherUris.add(uri);
            }
            variantsValue.setText(otherUris.size() + (otherUris.size() == 1 ? " other version" : " other versions"));
            variantsRow.setOnClickListener(v -> openVariantPicker(otherUris));
        }

        // First seen + song ID footer
        TextView firstSeenView = rootView.findViewById(R.id.first_seen_value);
        String createdAt = formatAbsoluteDate(song.getCreatedAt());
        if (createdAt != null && !createdAt.equals("Unknown")) {
            firstSeenView.setText("First seen " + createdAt);
        } else {
            firstSeenView.setVisibility(View.GONE);
        }
        TextView songIdView = rootView.findViewById(R.id.song_id_value);
        songIdView.setText(song.getId());
    }

    // Builds a clickable chip per artist (including features), each opening ArtistView. Sourced
    // from the local song_artists join so feature artists are covered, not just the primary.
    private void setupArtists() {
        LinearLayout artistsRow = rootView.findViewById(R.id.artists_row);
        LinearLayout container = rootView.findViewById(R.id.artist_chips_container);
        container.removeAllViews();

        DatabaseHelper db = new DatabaseHelper(requireContext());
        List<com.ca.tunaro.models.Artist> artists = db.getSongArtists(song.getId());
        db.close();

        if (artists.isEmpty()) {
            artistsRow.setVisibility(View.GONE);
            return;
        }
        artistsRow.setVisibility(View.VISIBLE);

        int marginEnd = Math.round(6 * getResources().getDisplayMetrics().density);
        int padH = Math.round(10 * getResources().getDisplayMetrics().density);
        int padV = Math.round(5 * getResources().getDisplayMetrics().density);

        for (com.ca.tunaro.models.Artist artist : artists) {
            TextView chip = new TextView(requireContext());
            chip.setText(artist.getName());
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(14f);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
            chip.setPadding(padH, padV, padH, padV);
            chip.setBackgroundResource(R.drawable.rounded_md);
            chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00116A));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(marginEnd);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), com.ca.tunaro.activites.ArtistView.class);
                intent.putExtra("artist_id", artist.getArtistId());
                intent.putExtra("artist_name", artist.getName());
                startActivity(intent);
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });

            container.addView(chip);
        }
    }

    private void setupListeningHistory() {
        LinearLayout historySection = rootView.findViewById(R.id.listening_history_section);
        LinearLayout historyHeader = rootView.findViewById(R.id.listening_history_header);
        LinearLayout historyContent = rootView.findViewById(R.id.listening_history_content);
        TextView listenCountView = rootView.findViewById(R.id.listen_count);
        ImageView expandCollapseIcon = rootView.findViewById(R.id.expand_collapse_icon);

        DatabaseHelper dbHelper = new DatabaseHelper(requireContext());
        List<String> listenHistory = variantUris != null && variantUris.size() > 1
                ? dbHelper.getListenHistoryForUris(variantUris)
                : dbHelper.getListenHistory(song.getId());
        dbHelper.close();

        if (listenHistory.isEmpty()) {
            historySection.setVisibility(View.GONE);
            return;
        }

        // Show section and set listen count
        historySection.setVisibility(View.VISIBLE);
        int totalListens = listenHistory.size();
        listenCountView.setText(totalListens + (totalListens == 1 ? " listen" : " listens"));

        // Group listens by relative time periods
        Map<String, Integer> groupedListens = groupListensByTimePeriod(listenHistory);

        // Populate history content
        for (Map.Entry<String, Integer> entry : groupedListens.entrySet()) {
            TextView textView = new TextView(requireContext());

            String timeDescription = entry.getKey();
            int count = entry.getValue();

            String displayText = timeDescription;
            if (count > 1) {
                displayText += " - " + count + " listens";
            }

            textView.setText(displayText);
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(14f);
            textView.setPadding(0, 8, 0, 8);

            historyContent.addView(textView);
        }

        // Set up expand/collapse
        historyHeader.setOnClickListener(v -> {
            isHistoryExpanded = !isHistoryExpanded;
            historyContent.setVisibility(isHistoryExpanded ? View.VISIBLE : View.GONE);
            expandCollapseIcon.setRotation(isHistoryExpanded ? 180 : 0);
        });
    }

    private void setupMoreDetailsButton() {
        MaterialButton moreDetailsButton = rootView.findViewById(R.id.more_details_button);
        moreDetailsButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SongWebInfoActivity.class);
            startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }

    private void openVariantPicker(List<String> otherUris) {
        DatabaseHelper db = new DatabaseHelper(requireContext());
        List<SongModel> variants = new ArrayList<>();
        for (String uri : otherUris) {
            SongModel s = db.getLeanSong(uri);
            if (s != null) variants.add(s);
        }
        db.close();

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
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
        java.text.SimpleDateFormat formatWithoutMillis = new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault());

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
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }
}
