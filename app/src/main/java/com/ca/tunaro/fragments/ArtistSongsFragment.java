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
import com.ca.tunaro.activites.ArtistView;
import com.ca.tunaro.activites.MainActivity;
import com.ca.tunaro.activites.SongView;
import com.ca.tunaro.adapters.QueueLineDecoration;
import com.ca.tunaro.adapters.Song_RecyclerViewAdapter;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.interfaces.Song_RecyclerViewInterface;
import com.ca.tunaro.managers.PlaybackManager;
import com.ca.tunaro.models.AlbumModel;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;

/**
 * Issue #80 Songs tab. Loads the artist's top tracks immediately, then optionally pages every
 * album's tracks (via the shared discography) on demand. Songs already in the local DB are
 * flagged with a green tick. Reuses {@link Song_RecyclerViewAdapter}.
 */
public class ArtistSongsFragment extends Fragment
        implements Song_RecyclerViewInterface, ArtistView.DiscographyListener {
    private static final String TAG = "ArtistSongsFragment";
    private static final String ARG_ARTIST_ID = "artist_id";

    private String artistId;
    private View rootView;
    private RecyclerView recyclerView;
    private Song_RecyclerViewAdapter adapter;
    private QueueLineDecoration queueLineDecoration;
    // URI of the row currently highlighted as now-playing; drives targeted decoration refreshes.
    private String highlightedUri;
    private Spinner sortSpinner;
    private ImageView sortDirectionIcon;
    private ImageView sortIcon;
    private ImageView searchIcon;
    private android.widget.EditText searchBar;
    private boolean searchModeActive = false;
    private String searchQuery = "";
    private MaterialButton loadFullButton;

    private boolean isAscending = false;
    // Spinner positions for this tab. "Title" is intentionally omitted (search covers
    // title lookup); Popularity is the default. Each position maps to a contextual-info
    // code understood by Song_RecyclerViewAdapter.updateSortContext (see CONTEXT_CODES).
    private static final String[] SORT_OPTIONS = {"Popularity", "Release Date", "Duration"};
    // Adapter contextual codes, parallel to SORT_OPTIONS: Popularity=5, Release Date=7, Duration=3.
    private static final int[] CONTEXT_CODES = {5, 7, 3};
    private int sortOption = 0; // index into SORT_OPTIONS; 0 = Popularity (default)

    // Full backing set (top tracks + any paged discography), deduped by URI.
    private final ArrayList<SongModel> songs = new ArrayList<>();
    // What the adapter actually renders: `songs` sorted, optionally filtered to added-only.
    private final ArrayList<SongModel> displayed = new ArrayList<>();
    private final Set<String> presentUris = new java.util.HashSet<>();
    private Set<String> localUris = new java.util.HashSet<>();
    // Full song rows for the artist's locally-added songs. Merged into the filtered view so
    // added songs appear even when they were never loaded from the API top tracks / discography.
    private final ArrayList<SongModel> localSongs = new ArrayList<>();
    private boolean fullDiscographyLoaded = false;
    private boolean addedOnlyFilter = false;

    public static ArtistSongsFragment newInstance(String artistId) {
        ArtistSongsFragment fragment = new ArtistSongsFragment();
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
        rootView = inflater.inflate(R.layout.fragment_artist_songs, container, false);

        recyclerView = rootView.findViewById(R.id.songs_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new Song_RecyclerViewAdapter(requireContext(), this, displayed);
        adapter.setShowArtist(false); // The page already belongs to this artist.
        recyclerView.setAdapter(adapter);
        queueLineDecoration = new QueueLineDecoration(adapter);
        recyclerView.addItemDecoration(queueLineDecoration);

        // Swipe an album cover right to add/remove the song from the queue: refresh the line.
        adapter.setOnQueueChangeListener((position, added) -> recyclerView.invalidateItemDecorations());

        DatabaseHelper db = new DatabaseHelper(requireContext());
        localUris = db.getArtistLocalSongUris(artistId);
        localSongs.addAll(db.getArtistLocalSongs(artistId));
        db.close();
        adapter.setAddedUris(localUris);

        // Reflect any toggle already flipped on the host before this tab was created.
        if (host() != null) addedOnlyFilter = host().isAddedFilterActive();

        setupSorting();
        setupSearch();
        setupLoadFullButton();

        showShimmer(true);
        fetchTopTracks();

        return rootView;
    }

    //#region Sorting

    private void setupSorting() {
        sortSpinner = rootView.findViewById(R.id.sort_spinner);
        sortDirectionIcon = rootView.findViewById(R.id.sort_direction_icon);
        sortIcon = rootView.findViewById(R.id.sort_icon);

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<String>(requireContext(), R.layout.spinner_dropdown_item, SORT_OPTIONS) {
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
                refreshDisplayed(); // Also refreshes the empty-state.
                adapter.updateSortContext(CONTEXT_CODES[sortOption]);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // The search icon (right of the sort-direction icon) toggles search mode: the sort widgets
        // are swapped inline for the search field, which is auto-focused with the keyboard shown.
        searchIcon.setOnClickListener(v -> setSearchMode(!searchModeActive));
    }

    // Swaps between the sort widgets and the inline search field. Entering focuses the field, shows
    // the keyboard, and collapses the artist header so more rows are visible; leaving clears the
    // query, restores the sort, and hides the keyboard.
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

    private Comparator<SongModel> currentComparator() {
        Comparator<SongModel> comparator;
        switch (sortOption) {
            case 1: // Release Date
                comparator = Comparator.comparing(s -> s.getReleaseDate() == null ? "" : s.getReleaseDate());
                break;
            case 2: // Duration
                comparator = Comparator.comparingInt(SongModel::getDuration);
                break;
            case 0: // Popularity
            default:
                comparator = Comparator.comparingInt(SongModel::getPopularity);
                break;
        }
        return isAscending ? comparator : comparator.reversed();
    }

    private void applySort() {
        refreshDisplayed();
        // Drive the row's contextual-info line off the adapter's own sort codes.
        adapter.updateSortContext(CONTEXT_CODES[sortOption]);
    }

    // Toggles the added-only filter (driven by the host's added-songs button).
    public void setAddedOnlyFilter(boolean addedOnly) {
        if (addedOnlyFilter == addedOnly) return;
        addedOnlyFilter = addedOnly;
        refreshDisplayed(); // Also refreshes the empty-state.
        adapter.updateSortContext(CONTEXT_CODES[sortOption]);
    }

    // Rebuilds the rendered list from the backing set, applying the added-only filter and sort.
    // When filtering, locally-added songs missing from the loaded set are merged in so every
    // added song shows, not just those among the top tracks / discography.
    private void refreshDisplayed() {
        displayed.clear();
        if (addedOnlyFilter) {
            Set<String> seen = new java.util.HashSet<>();
            for (SongModel s : songs) {
                if (localUris.contains(s.getId())) { displayed.add(s); seen.add(s.getId()); }
            }
            for (SongModel s : localSongs) {
                if (!seen.contains(s.getId())) { displayed.add(s); seen.add(s.getId()); }
            }
        } else {
            displayed.addAll(songs);
        }
        // Client-side search over the current set, matching name / artist / album.
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            String q = searchQuery.toLowerCase().trim();
            for (java.util.Iterator<SongModel> it = displayed.iterator(); it.hasNext(); ) {
                SongModel s = it.next();
                String name = s.getName() == null ? "" : s.getName().toLowerCase();
                String artist = s.getArtist() == null ? "" : s.getArtist().toLowerCase();
                String album = s.getAlbumName() == null ? "" : s.getAlbumName().toLowerCase();
                if (!name.contains(q) && !artist.contains(q) && !album.contains(q)) it.remove();
            }
        }
        displayed.sort(currentComparator());
        adapter.updateSongs(displayed);
        // Keep the empty-state in sync on every rebuild, including streaming addSongs batches that
        // land after a search has been entered (the text watcher only fires on keystrokes).
        showEmpty(displayed.isEmpty());
        // The rendered order just changed, so the queue can no longer be assumed to run through
        // consecutive rows; hide the connecting line until a queue is created from this order.
        if (queueLineDecoration != null) {
            queueLineDecoration.setQueueMatchesDisplay(false);
            recyclerView.invalidateItemDecorations();
        }
    }

    //#endregion

    //#region Top tracks

    private void fetchTopTracks() {
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null || mainActivity.getSpotifyApi() == null) {
            showShimmer(false);
            showEmpty(true);
            return;
        }
        String id = bareId(artistId);
        Log.d(TAG, "API: getArtistsTopTracks id=" + id);
        mainActivity.executeWithTokenRefresh(() -> mainActivity.getSpotifyApi()
                        .getArtistsTopTracks(id, com.neovisionaries.i18n.CountryCode.GB).build())
                .thenAccept(tracks -> {
                    List<SongModel> models = new ArrayList<>();
                    if (tracks != null) {
                        for (Track t : tracks) {
                            SongModel m = fromTrack(t);
                            if (m != null) models.add(m);
                        }
                    }
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        addSongs(models);
                        showShimmer(false);
                        showEmpty(songs.isEmpty());
                    });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to fetch top tracks", throwable);
                    if (!isAdded()) return null;
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        showShimmer(false);
                        showEmpty(songs.isEmpty());
                    });
                    return null;
                });
    }

    //#endregion

    //#region Full discography

    private void setupLoadFullButton() {
        loadFullButton = rootView.findViewById(R.id.load_full_discography_button);
        loadFullButton.setOnClickListener(v -> {
            loadFullButton.setEnabled(false);
            loadFullButton.setText("Loading full discography…");
            ArtistView host = host();
            if (host != null) {
                // Pull (or trigger) the shared discography; onDiscographyReady continues from there.
                host.addDiscographyListener(this);
            }
        });
    }

    @Override
    public void onDiscographyReady(List<AlbumModel> albums) {
        if (fullDiscographyLoaded) return;
        fullDiscographyLoaded = true;
        if (albums == null || albums.isEmpty()) {
            if (loadFullButton != null) loadFullButton.setVisibility(View.GONE);
            return;
        }
        loadAlbumTracks(new ArrayList<>(albums), 0);
    }

    // Pages each album's tracks sequentially. Simplified tracks lack popularity/cover, so the
    // album's own cover and release date are stitched in.
    private void loadAlbumTracks(List<AlbumModel> albumList, int index) {
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity == null || mainActivity.getSpotifyApi() == null || index >= albumList.size()) {
            finishFullLoad();
            return;
        }
        AlbumModel album = albumList.get(index);
        if (album.getAlbumId() == null) {
            loadAlbumTracks(albumList, index + 1);
            return;
        }
        Log.d(TAG, "API: getAlbumsTracks albumId=" + album.getAlbumId());
        mainActivity.executeWithTokenRefresh(() -> mainActivity.getSpotifyApi()
                        .getAlbumsTracks(album.getAlbumId()).limit(50).build())
                .thenAccept(paging -> {
                    List<SongModel> models = new ArrayList<>();
                    TrackSimplified[] items = paging != null ? paging.getItems() : null;
                    if (items != null) {
                        for (TrackSimplified t : items) {
                            SongModel m = fromSimplifiedTrack(t, album);
                            if (m != null) models.add(m);
                        }
                    }
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        addSongs(models);
                    });
                    loadAlbumTracks(albumList, index + 1);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Failed to fetch album tracks", throwable);
                    loadAlbumTracks(albumList, index + 1);
                    return null;
                });
    }

    private void finishFullLoad() {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            if (loadFullButton != null) loadFullButton.setVisibility(View.GONE);
            applySort();
            showEmpty(songs.isEmpty());
        });
    }

    //#endregion

    // Adds songs not already present (dedup by URI), refreshing the list.
    private void addSongs(List<SongModel> models) {
        boolean changed = false;
        for (SongModel m : models) {
            if (m.getId() == null || presentUris.contains(m.getId())) continue;
            presentUris.add(m.getId());
            songs.add(m);
            changed = true;
        }
        if (changed) applySort();
    }

    @Override
    public void onItemClick(int position, View itemView) {
        if (position < 0 || position >= displayed.size()) return;
        SelectedSongHolder.getInstance().setSelectedSong(displayed.get(position));
        startActivity(new Intent(requireContext(), SongView.class));
    }

    // Tap on album cover — start a queue from this position (discarding the current queue),
    // mirroring PlaylistView.
    @Override
    public void onAlbumCoverClick(int position) {
        if (position < 0 || position >= displayed.size()) return;
        ArrayList<SongModel> currentList = new ArrayList<>(displayed);
        PlaybackManager pm = PlaybackManager.getInstance();
        if (!pm.isConnected()) {
            pm.connectSpotify(requireContext(), () -> {
                pm.playQueue(currentList, position);
                onQueueCreated();
            });
        } else {
            pm.playQueue(currentList, position);
            onQueueCreated();
        }
    }

    // A queue was just created from this screen in the current display order: the connecting line
    // can now be trusted to run through consecutive queued rows. Mirrors PlaylistView.onQueueCreated.
    private void onQueueCreated() {
        if (queueLineDecoration != null) queueLineDecoration.setQueueMatchesDisplay(true);
        if (adapter != null) adapter.notifyDataSetChanged();
        recyclerView.invalidateItemDecorations();
        SongModel current = PlaybackManager.getInstance().getCurrentSong();
        highlightedUri = current != null ? current.getUri() : null;
    }

    // Long press on album cover — play the song individually (no queue), mirroring PlaylistView.
    @Override
    public void onAlbumCoverLongClick(int position) {
        if (position < 0 || position >= displayed.size()) return;
        SongModel clickedSong = displayed.get(position);
        PlaybackManager pm = PlaybackManager.getInstance();
        if (!pm.isConnected()) {
            pm.connectSpotify(requireContext(), () -> pm.playSong(clickedSong));
        } else {
            pm.playSong(clickedSong);
        }
    }

    // Rebinds every row so the now-playing highlight and queued lime edges re-evaluate, and
    // refreshes the connecting line. Only acts when the current song actually changed (a pure
    // play<->pause toggle needs no rebind). Mirrors PlaylistView.onPlaybackStateChanged.
    public void refreshPlaybackState() {
        if (adapter == null) return;
        SongModel current = PlaybackManager.getInstance().getCurrentSong();
        String newUri = current != null ? current.getUri() : null;
        if (!java.util.Objects.equals(newUri, highlightedUri)) {
            adapter.notifyDataSetChanged();
            recyclerView.invalidateItemDecorations();
            highlightedUri = newUri;
        }
    }

    //#region Helpers

    private SongModel fromTrack(Track t) {
        if (t == null) return null;
        se.michaelthelin.spotify.model_objects.specification.AlbumSimplified album = t.getAlbum();
        String cover = null, releaseDate = null, albumName = null;
        if (album != null) {
            Image[] images = album.getImages();
            cover = images != null && images.length > 0 ? images[0].getUrl() : null;
            releaseDate = album.getReleaseDate();
            albumName = album.getName();
        }
        String primaryArtist = t.getArtists() != null && t.getArtists().length > 0
                ? t.getArtists()[0].getName() : null;
        int popularity = t.getPopularity() != null ? t.getPopularity() : 0;
        return new SongModel(t.getUri(), t.getName(), primaryArtist, t.getDurationMs(),
                t.getUri(), cover, popularity, albumName, releaseDate);
    }

    private SongModel fromSimplifiedTrack(TrackSimplified t, AlbumModel album) {
        if (t == null || t.getUri() == null) return null;
        String primaryArtist = t.getArtists() != null && t.getArtists().length > 0
                ? t.getArtists()[0].getName() : null;
        return new SongModel(t.getUri(), t.getName(), primaryArtist, t.getDurationMs(),
                t.getUri(), album.getCoverImageUrl(), 0, album.getName(), album.getReleaseDate());
    }

    private void showShimmer(boolean show) {
        com.facebook.shimmer.ShimmerFrameLayout shimmer = rootView.findViewById(R.id.songs_shimmer);
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
        label.setText(searching ? "No matches" : "No songs found");
        label.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private ArtistView host() {
        return getActivity() instanceof ArtistView ? (ArtistView) getActivity() : null;
    }

    private static String bareId(String idOrUri) {
        if (idOrUri == null) return null;
        return idOrUri.contains(":") ? idOrUri.substring(idOrUri.lastIndexOf(":") + 1) : idOrUri;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ArtistView host = host();
        if (host != null) host.removeDiscographyListener(this);
    }

    //#endregion
}
