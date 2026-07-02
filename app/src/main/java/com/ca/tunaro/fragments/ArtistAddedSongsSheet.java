package com.ca.tunaro.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ca.tunaro.R;
import com.ca.tunaro.activites.SongView;
import com.ca.tunaro.adapters.Song_RecyclerViewAdapter;
import com.ca.tunaro.database.DatabaseHelper;
import com.ca.tunaro.interfaces.Song_RecyclerViewInterface;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.utils.SelectedSongHolder;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Issue #80 — bottom sheet listing the artist's songs that are in a favourite or archived
 * playlist. Pure local query; rows reuse {@link Song_RecyclerViewAdapter} and open SongView.
 */
public class ArtistAddedSongsSheet extends BottomSheetDialogFragment implements Song_RecyclerViewInterface {
    private static final String ARG_ARTIST_ID = "artist_id";
    private static final String ARG_ARTIST_NAME = "artist_name";

    private String artistId;
    private String artistName;
    private final ArrayList<SongModel> songs = new ArrayList<>();

    public static ArtistAddedSongsSheet newInstance(String artistId, String artistName) {
        ArtistAddedSongsSheet sheet = new ArtistAddedSongsSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ARTIST_ID, artistId);
        args.putString(ARG_ARTIST_NAME, artistName);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_artist_added_songs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        artistId = args != null ? args.getString(ARG_ARTIST_ID) : null;
        artistName = args != null ? args.getString(ARG_ARTIST_NAME) : null;

        DatabaseHelper db = new DatabaseHelper(requireContext());
        List<SongModel> loaded = db.getArtistSongsInFavOrArchivedPlaylists(artistId);
        db.close();
        songs.addAll(loaded);

        TextView title = view.findViewById(R.id.sheet_title);
        String who = artistName != null ? artistName + "'s" : "Artist's";
        title.setText(who + " added songs (" + songs.size() + ")");

        RecyclerView recycler = view.findViewById(R.id.added_songs_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(new Song_RecyclerViewAdapter(requireContext(), this, songs));
    }

    @Override
    public void onItemClick(int position, View itemView) {
        if (position < 0 || position >= songs.size()) return;
        SelectedSongHolder.getInstance().setSelectedSong(songs.get(position));
        startActivity(new Intent(requireContext(), SongView.class));
        dismiss();
    }
}
