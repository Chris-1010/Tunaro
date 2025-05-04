package com.ca.tunaro;

/**
 * This file passes over an instance of SongModel to start a new SongView Activity
 */
public class SelectedSongHolder {
    private MainActivity mainActivity;
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
        this.mainActivity = activity;
    }

    public SongModel getSelectedSong() {
        return selectedSong;
    }

    public MainActivity getMainActivity() {
        return mainActivity;
    }

    public void clearSelectedSong() {
        selectedSong = null;
        mainActivity = null;
    }
}
