package com.ca.tunaro;

import com.spotify.android.appremote.api.SpotifyAppRemote;

import se.michaelthelin.spotify.SpotifyApi;

/**
 This file is to be able to pass over an instance of PlaylistModel when clicking on a playlist in the playFragment to start a new PlaylistView activity
 */
public class SelectedPlaylistHolder {
    private static SelectedPlaylistHolder instance;
    private PlaylistModel selectedPlaylist;
    private SpotifyApi spotifyApi;
    private MainActivity mainActivity;
    private SpotifyAppRemote mSpotifyAppRemote;

    private SelectedPlaylistHolder() {}

    public static synchronized SelectedPlaylistHolder getInstance() {
        if (instance == null) {
            instance = new SelectedPlaylistHolder();
        }
        return instance;
    }

    public void setSelectedPlaylist(PlaylistModel playlist, SpotifyApi api, SpotifyAppRemote appRemote, MainActivity activity) {
        this.selectedPlaylist = playlist;
        this.spotifyApi = api;
        this.mSpotifyAppRemote = appRemote;
        this.mainActivity = activity;
    }

    public PlaylistModel getSelectedPlaylist() {
        return selectedPlaylist;
    }

    public SpotifyApi getSpotifyApi() {
        return spotifyApi;
    }

    public SpotifyAppRemote getSpotifyAppRemote() {
        return mSpotifyAppRemote;
    }

    public MainActivity getMainActivity() {
        return mainActivity;
    }

    public void clearSelectedPlaylist() {
        selectedPlaylist = null;
        spotifyApi = null;
        mSpotifyAppRemote = null;
        mainActivity = null;
    }
}
