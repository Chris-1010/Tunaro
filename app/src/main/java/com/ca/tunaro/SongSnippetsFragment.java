package com.ca.tunaro;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

public class SongSnippetsFragment extends Fragment {
    private SongModel song;

    public static SongSnippetsFragment newInstance(SongModel song) {
        SongSnippetsFragment fragment = new SongSnippetsFragment();
        Bundle args = new Bundle();
        args.putString("songId", song.getId());
        fragment.setArguments(args);
        fragment.song = song;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_song_snippets, container, false);
    }
}