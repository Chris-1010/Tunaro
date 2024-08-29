package com.ca.tunaro;

import java.util.ArrayList;

import se.michaelthelin.spotify.model_objects.specification.Image;

public class PlaylistModel {
    String id;
    String playlistName;
    int songCount;
    Image[] image;
    ArrayList<SongModel> songs;

    public PlaylistModel(String id, String playlistName, int songCount, Image[] image, ArrayList<SongModel> songs) {
        this.id = id;
        this.playlistName = playlistName;
        this.songCount = songCount;
        this.image = image;
        this.songs = songs;
    }

    public String getPlaylistName() {
        return playlistName;
    }

    public int getSongCount() {
        return songCount;
    }

    public String getImage() {
        return image[0].getUrl();
    }

    public String getId() {
        return id;
    }

    public ArrayList<SongModel> getSongs() { return songs; }
}