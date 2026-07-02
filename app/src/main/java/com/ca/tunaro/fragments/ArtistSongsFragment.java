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
import com.ca.tunaro.adapters.Song_RecyclerViewAdapter;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.interfaces.Song_RecyclerViewInterface;
import com.ca.tunaro.models.AlbumModel;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private Spinner sortSpinner;
    private ImageView sortDirectionIcon;
    private MaterialButton loadFullButton;

    private boolean isAscending = false;
    private int sortOption = 0; // 0 Release Date, 1 Duration, 2 Popularity, 3 Title

    // Top tracks (with popularity), kept distinct from the full set so a re-load doesn't dup them.
    private final ArrayList<SongModel> songs = new ArrayList<>();
    private final Set<String> presentUris = new java.util.HashSet<>();
    private Set<String> localUris = new java.util.HashSet<>();
    private boolean fullDiscographyLoaded = false;

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
        adapter = new Song_RecyclerViewAdapter(requireContext(), this, songs);
        recyclerView.setAdapter(adapter);

        DatabaseHelper db = new DatabaseHelper(requireContext());
        localUris = db.getArtistLocalSongUris(artistId);
        db.close();
        adapter.setAddedUris(localUris);

        setupSorting();
        setupLoadFullButton();

        showShimmer(true);
        fetchTopTracks();

        return rootView;
    }

    //#region Sorting

    private void setupSorting() {
        sortSpinner = rootView.findViewById(R.id.sort_spinner);
        sortDirectionIcon = rootView.findViewById(R.id.sort_direction_icon);

        String[] options = {"Release Date", "Duration", "Popularity", "Title"};
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
        Comparator<SongModel> comparator;
        switch (sortOption) {
            case 1: // Duration
                comparator = Comparator.comparingInt(SongModel::getDuration);
                break;
            case 2: // Popularity
                comparator = Comparator.comparingInt(SongModel::getPopularity);
                break;
            case 3: // Title
                comparator = Comparator.comparing(s -> s.getName() == null ? "" : s.getName().toLowerCase(Locale.getDefault()));
                break;
            case 0: // Release Date
            default:
                comparator = Comparator.comparing(s -> s.getReleaseDate() == null ? "" : s.getReleaseDate());
                break;
        }
        if (!isAscending) comparator = comparator.reversed();
        songs.sort(comparator);
        adapter.notifyDataSetChanged();
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
        if (position < 0 || position >= songs.size()) return;
        SelectedSongHolder.getInstance().setSelectedSong(songs.get(position));
        startActivity(new Intent(requireContext(), SongView.class));
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
        rootView.findViewById(R.id.empty_label).setVisibility(show ? View.VISIBLE : View.GONE);
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
