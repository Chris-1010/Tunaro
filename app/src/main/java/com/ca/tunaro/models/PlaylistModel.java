package com.ca.tunaro.models;

import java.util.ArrayList;

import se.michaelthelin.spotify.model_objects.specification.Image;

public class PlaylistModel {
    String id;
    String playlistName;
    int songCount;
    Image[] image;
    ArrayList<SongModel> songs;
    boolean isFavourite;

    public PlaylistModel(String id, String playlistName, int songCount, Image[] image, ArrayList<SongModel> songs) {
        this.id = id;
        this.playlistName = playlistName;
        this.songCount = songCount;
        this.image = image;
        this.songs = songs;
        this.isFavourite = false;
    }

    //#region Getters

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

    public boolean isFavourite() {
        return isFavourite;
    }

    //#endregion

    //#region Setters

    public void setSongs(ArrayList<SongModel> songs) {
        this.songs = songs;
    }

    public void setFavourite(boolean favourite) {
        isFavourite = favourite;
    }

    //#endregion
}