package com.ca.tunaro.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ca.tunaro.R;
import com.ca.tunaro.activites.AlbumView;
import com.ca.tunaro.activites.ArtistView;
import com.ca.tunaro.adapters.Album_RecyclerViewAdapter;
import com.ca.tunaro.models.AlbumModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Issue #80 Albums tab. Renders + sorts the discography that {@link ArtistView} fetches and owns,
 * subscribing as a {@link ArtistView.DiscographyListener}. Album click opens AlbumView.
 */
public class ArtistAlbumsFragment extends Fragment
        implements Album_RecyclerViewAdapter.OnAlbumClickListener, ArtistView.DiscographyListener {
    private static final String ARG_ARTIST_ID = "artist_id";

    private String artistId;
    private View rootView;
    private RecyclerView recyclerView;
    private Album_RecyclerViewAdapter adapter;
    private Spinner sortSpinner;
    private ImageView sortDirectionIcon;
    private boolean isAscending = false;
    private int sortOption = Album_RecyclerViewAdapter.SORT_RELEASE_DATE;

    private final ArrayList<AlbumModel> albums = new ArrayList<>();

    public static ArtistAlbumsFragment newInstance(String artistId) {
        ArtistAlbumsFragment fragment = new ArtistAlbumsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ARTIST_ID, artistId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) artistId = getArguments().getString(ARG_ARTIST_ID);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_artist_albums, container, false);

        recyclerView = rootView.findViewById(R.id.albums_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new Album_RecyclerViewAdapter(requireContext(), albums, this);
        recyclerView.setAdapter(adapter);

        setupSorting();

        ArtistView host = host();
        if (host != null) {
            if (!host.isDiscographyLoaded()) showShimmer(true);
            // Triggers the fetch if not already running; fires onDiscographyReady when done.
            host.addDiscographyListener(this);
        }

        return rootView;
    }

    //#region Sorting

    private void setupSorting() {
        sortSpinner = rootView.findViewById(R.id.sort_spinner);
        sortDirectionIcon = rootView.findViewById(R.id.sort_direction_icon);

        String[] options = {"Release Date", "Type", "Name", "Track Count", "Popularity"};
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<String>(requireContext(), R.layout.spinner_dropdown_item, options) {
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = new TextView(getContext());
                tv.setText(getItem(position));
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(15);
                tv.setGravity(android.view.Gravity.CENTER_VERTICAL);
                tv.setSingleLine(true);
                return tv;
            }
        };
        sortAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);

        sortDirectionIcon.setOnClickListener(v -> {
            isAscending = !isAscending;
            updateSortDirectionIcon();
            applySort();
        });

        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sortOption = position;
                adapter.setSortOption(position);
                applySort();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateSortDirectionIcon() {
        sortDirectionIcon.setImageResource(isAscending ? R.drawable.ic_arrow_upward : R.drawable.ic_arrow_downward);
    }

    private void applySort() {
        Comparator<AlbumModel> comparator;
        switch (sortOption) {
            case Album_RecyclerViewAdapter.SORT_TYPE:
                comparator = Comparator.comparing(a -> a.getAlbumType() == null ? "" : a.getAlbumType());
                break;
            case Album_RecyclerViewAdapter.SORT_NAME:
                comparator = Comparator.comparing(a -> a.getName() == null ? "" : a.getName().toLowerCase(Locale.getDefault()));
                break;
            case Album_RecyclerViewAdapter.SORT_TRACK_COUNT:
                comparator = Comparator.comparingInt(AlbumModel::getTrackCount);
                break;
            case Album_RecyclerViewAdapter.SORT_POPULARITY:
                comparator = Comparator.comparingInt(AlbumModel::getPopularity);
                break;
            case Album_RecyclerViewAdapter.SORT_RELEASE_DATE:
            default:
                comparator = Comparator.comparing(a -> a.getReleaseDate() == null ? "" : a.getReleaseDate());
                break;
        }
        if (!isAscending) comparator = comparator.reversed();
        albums.sort(comparator);
        adapter.notifyDataSetChanged();
    }

    //#endregion

    //#region Discography (fetched + owned by ArtistView)

    @Override
    public void onDiscographyReady(List<AlbumModel> ready) {
        if (!isAdded()) return;
        showShimmer(false);
        albums.clear();
        if (ready != null) albums.addAll(ready);
        applySort();
        showEmpty(albums.isEmpty());
    }

    //#endregion

    @Override
    public void onAlbumClick(AlbumModel album) {
        Intent intent = new Intent(requireContext(), AlbumView.class);
        intent.putExtra("album_id", album.getAlbumId());
        intent.putExtra("album_name", album.getName());
        startActivity(intent);
    }

    //#region Helpers

    private void showShimmer(boolean show) {
        com.facebook.shimmer.ShimmerFrameLayout shimmer = rootView.findViewById(R.id.albums_shimmer);
        if (show) {
            shimmer.setVisibility(View.VISIBLE);
            shimmer.startShimmer();
            recyclerView.setVisibility(View.GONE);
        } else {
            shimmer.stopShimmer();
            shimmer.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showEmpty(boolean show) {
        rootView.findViewById(R.id.empty_label).setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private ArtistView host() {
        return getActivity() instanceof ArtistView ? (ArtistView) getActivity() : null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ArtistView host = host();
        if (host != null) host.removeDiscographyListener(this);
    }

    //#endregion
}
