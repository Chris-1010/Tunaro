package com.ca.tunaro;

/**
 This file is to be able to pass over an instance of PlaylistModel when clicking on a playlist in the playFragment to start a new PlaylistView activity
 */
public class SelectedPlaylistHolder {
    private MainActivity mainActivity;
    private static SelectedPlaylistHolder instance;
    private PlaylistModel selectedPlaylist;

    private SelectedPlaylistHolder() {}

    public static synchronized SelectedPlaylistHolder getInstance() {
        if (instance == null) {
            instance = new SelectedPlaylistHolder();
        }
        return instance;
    }

    public void setSelectedPlaylist(PlaylistModel playlist, MainActivity activity) {
        this.selectedPlaylist = playlist;
        this.mainActivity = activity;
    }

    public PlaylistModel getSelectedPlaylist() {
        return selectedPlaylist;
    }

    public MainActivity getMainActivity() {
        return mainActivity;
    }

    public void clearSelectedPlaylist() {
        selectedPlaylist = null;
        mainActivity = null;
    }
}
