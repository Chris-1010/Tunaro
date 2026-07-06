package com.ca.tunaro.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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
import com.ca.tunaro.activites.MainActivity;
import com.ca.tunaro.adapters.Album_RecyclerViewAdapter;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.models.AlbumModel;
import com.ca.tunaro.models.SongModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;

/**
 * Issue #80 Albums tab. Renders + sorts the discography that {@link ArtistView} fetches and owns,
 * subscribing as a {@link ArtistView.DiscographyListener}. Album click opens AlbumView.
 */
public class ArtistAlbumsFragment extends Fragment
        implements Album_RecyclerViewAdapter.OnAlbumClickListener, ArtistView.DiscographyListener {
    private static final String TAG = "ArtistAlbumsFragment";
    private static final String ARG_ARTIST_ID = "artist_id";

    private String artistId;
    private View rootView;
    private RecyclerView recyclerView;
    private Album_RecyclerViewAdapter adapter;
    private Spinner sortSpinner;
    private ImageView sortDirectionIcon;
    private ImageView sortIcon;
    private ImageView searchIcon;
    private android.widget.EditText searchBar;
    private boolean searchModeActive = false;
    private String searchQuery = "";
    private boolean isAscending = false;
    private int sortOption = Album_RecyclerViewAdapter.SORT_RELEASE_DATE;

    // Full discography (unfiltered); `albums` is the rendered, possibly search-filtered view.
    private final ArrayList<AlbumModel> allAlbums = new ArrayList<>();
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
        setupSearch();

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
        sortIcon = rootView.findViewById(R.id.sort_icon);

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

    //#endregion

    //#region Search

    private void setupSearch() {
        searchBar = rootView.findViewById(R.id.search_bar);
        searchIcon = rootView.findViewById(R.id.search_icon);

        searchBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                applySort();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        searchIcon.setOnClickListener(v -> setSearchMode(!searchModeActive));
    }

    private void setSearchMode(boolean active) {
        searchModeActive = active;
        int sortVis = active ? View.GONE : View.VISIBLE;
        sortIcon.setVisibility(sortVis);
        sortSpinner.setVisibility(sortVis);
        sortDirectionIcon.setVisibility(sortVis);
        searchBar.setVisibility(active ? View.VISIBLE : View.GONE);
        searchIcon.setImageResource(active ? R.drawable.ic_close : R.drawable.ic_search);

        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (active) {
            if (host() != null) host().collapseHeader();
            searchBar.requestFocus();
            if (imm != null) imm.showSoftInput(searchBar, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        } else {
            if (imm != null) imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
            searchBar.setText(""); // Clearing re-applies the current sort via the text watcher.
        }
    }

    //#endregion

    //#region Sorting

    private void applySort() {
        // Rebuild the rendered list from the full discography with any active search filter.
        albums.clear();
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            albums.addAll(allAlbums);
        } else {
            String q = searchQuery.toLowerCase().trim();
            for (AlbumModel a : allAlbums) {
                String name = a.getName() == null ? "" : a.getName().toLowerCase();
                if (name.contains(q)) albums.add(a);
            }
        }
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

        // When a search is active, reflect no-match state immediately (not just on final render).
        boolean searching = searchQuery != null && !searchQuery.trim().isEmpty();
        if (searching) showEmpty(albums.isEmpty());
        else if (!allAlbums.isEmpty()) showEmpty(false);
    }

    //#endregion

    //#region Discography (fetched + owned by ArtistView)

    @Override
    public void onDiscographyProgress(List<AlbumModel> partial) {
        render(partial, false);
    }

    @Override
    public void onDiscographyReady(List<AlbumModel> ready) {
        render(ready, true);
    }

    // Renders whatever set of albums is available so far. On the final call the shimmer is always
    // cleared (even for an empty discography, which then shows the empty label); on interim calls
    // it is cleared only once the first albums arrive.
    private void render(List<AlbumModel> list, boolean isFinal) {
        if (!isAdded()) return;
        if (isFinal || (list != null && !list.isEmpty())) showShimmer(false);
        allAlbums.clear();
        if (list != null) allAlbums.addAll(list);
        applySort(); // Rebuilds `albums` (with any search filter), re-sorts, and owns the empty-state.
        // applySort already reflects a "No matches" search result on every call. Only the
        // non-search "nothing loaded" case is deferred to the final render so an empty interim
        // batch doesn't briefly flash "No releases found".
        boolean searching = searchQuery != null && !searchQuery.trim().isEmpty();
        if (!searching) showEmpty(albums.isEmpty() && isFinal);
    }

    //#endregion

    // Tapping/holding the album cover plays it, discarding the current queue. The album's tracks
    // are fetched on demand (AlbumSimplified carries no track list), then handed to PlaybackManager
    // as a fresh queue.
    @Override
    public void onAlbumPlay(AlbumModel album) {
        if (album == null || album.getAlbumId() == null) return;
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) return;

        android.widget.Toast.makeText(requireContext(),
                "Playing " + album.getName(), android.widget.Toast.LENGTH_SHORT).show();

        mainActivity.executeWithTokenRefresh(() -> mainActivity.getSpotifyApi()
                        .getAlbumsTracks(album.getAlbumId()).limit(50).build())
                .thenAccept(paging -> {
                    List<SongModel> tracks = new ArrayList<>();
                    TrackSimplified[] items = paging != null ? paging.getItems() : null;
                    if (items != null) {
                        for (TrackSimplified t : items) {
                            SongModel m = fromSimplifiedTrack(t, album);
                            if (m != null) tracks.add(m);
                        }
                    }
                    if (!isAdded() || tracks.isEmpty()) return;
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        // Discards any current queue and starts playing this album from the top.
                        PlaybackManager.getInstance().playQueue(tracks, 0);
                    });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to fetch album tracks for playback", throwable);
                    return null;
                });
    }

    // Tapping the rest of the row opens the album's detail view.
    @Override
    public void onAlbumOpen(AlbumModel album) {
        if (album == null) return;
        Intent intent = new Intent(requireContext(), AlbumView.class);
        intent.putExtra("album_id", album.getAlbumId());
        intent.putExtra("album_name", album.getName());
        startActivity(intent);
    }

    private SongModel fromSimplifiedTrack(TrackSimplified t, AlbumModel album) {
        if (t == null || t.getUri() == null) return null;
        String primaryArtist = t.getArtists() != null && t.getArtists().length > 0
                ? t.getArtists()[0].getName() : null;
        return new SongModel(t.getUri(), t.getName(), primaryArtist, t.getDurationMs(),
                t.getUri(), album.getCoverImageUrl(), 0, album.getName(), album.getReleaseDate());
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
        TextView label = rootView.findViewById(R.id.empty_label);
        // Distinguish "nothing loaded" from "search matched nothing".
        boolean searching = searchQuery != null && !searchQuery.trim().isEmpty();
        label.setText(searching ? "No matches" : "No releases found");
        label.setVisibility(show ? View.VISIBLE : View.GONE);
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
