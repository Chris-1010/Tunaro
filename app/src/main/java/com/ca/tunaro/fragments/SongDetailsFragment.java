package com.ca.tunaro.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
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
import com.ca.tunaro.activites.SongWebInfoActivity;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.models.SongModel;
import com.google.android.material.button.MaterialButton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SongDetailsFragment extends Fragment {
    private static final String TAG = "SongDetailsFragment";
    private static final String ARG_SONG = "song";

    private SongModel song;
    private View rootView;
    private boolean isHistoryExpanded = false;

    public static SongDetailsFragment newInstance(SongModel song) {
        SongDetailsFragment fragment = new SongDetailsFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_SONG, song);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            song = (SongModel) getArguments().getSerializable(ARG_SONG);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_song_details, container, false);

        if (song != null) {
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

        // Click to open album on Spotify
        albumRow.setOnClickListener(v -> {
            String albumId = song.getAlbumId();
            if (albumId != null && !albumId.isEmpty()) {
                String url = "https://open.spotify.com/album/" + albumId;
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            }
        });

        // Release date
        TextView releaseDateView = rootView.findViewById(R.id.SongView_ReleaseDate);
        releaseDateView.setText(formatReleaseDate(song.getReleaseDate()));

        // Duration
        TextView durationView = rootView.findViewById(R.id.SongView_SongDuration);
        durationView.setText(song.getDurationString());

        // Popularity
        LinearLayout popularityRow = rootView.findViewById(R.id.popularity_row);
        TextView popularityView = rootView.findViewById(R.id.SongView_SongPopularity);
        int popularity = song.getPopularity();
        if (popularity > 0) {
            popularityRow.setVisibility(View.VISIBLE);
            popularityView.setText(getString(R.string.popularity_value, popularity));
        }
    }

    private void setupListeningHistory() {
        LinearLayout historySection = rootView.findViewById(R.id.listening_history_section);
        LinearLayout historyHeader = rootView.findViewById(R.id.listening_history_header);
        LinearLayout historyContent = rootView.findViewById(R.id.listening_history_content);
        TextView listenCountView = rootView.findViewById(R.id.listen_count);
        ImageView expandCollapseIcon = rootView.findViewById(R.id.expand_collapse_icon);

        DatabaseHelper dbHelper = new DatabaseHelper(requireContext());
        List<String> listenHistory = dbHelper.getListenHistory(song.getId());

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
        });
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

    private String capitalise(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }
}
