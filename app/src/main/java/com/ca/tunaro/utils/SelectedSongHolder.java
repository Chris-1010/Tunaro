package com.ca.tunaro.utils;

import com.ca.tunaro.activites.MainActivity;
import com.ca.tunaro.models.SongModel;

/**
 * This file passes over an instance of SongModel to start a new SongView Activity
 */
public class SelectedSongHolder {
    private static SelectedSongHolder instance;
    private SongModel selectedSong;

    private SelectedSongHolder() {
    }

    public static synchronized SelectedSongHolder getInstance() {
        if (instance == null) {
            instance = new SelectedSongHolder();
        }
        return instance;
    }

    public void setSelectedSong(SongModel song, MainActivity activity) {
        this.selectedSong = song;
    }

    public SongModel getSelectedSong() {
        return selectedSong;
    }

    public void clearSelectedSong() {
        selectedSong = null;
    }
}