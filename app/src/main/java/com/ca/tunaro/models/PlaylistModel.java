package com.ca.tunaro.models;

import android.util.Log;

import java.util.ArrayList;

import se.michaelthelin.spotify.model_objects.specification.Image;

public class PlaylistModel {
    String id;
    String playlistName;
    int songCount;
    private String imageUrl;
    Image[] images;
    ArrayList<SongModel> songs;
    boolean isFavourite;

    public PlaylistModel(String id, String playlistName, int songCount, Image[] images, ArrayList<SongModel> songs) {
        this.id = id;
        this.playlistName = playlistName;
        this.songCount = songCount;
        this.images = images;
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
        if (imageUrl != null) {
            return imageUrl;
        } else if (images != null && images.length > 0 && images[0].getUrl() != null) {
            return images[0].getUrl();
        }

        Log.w("PlaylistModel", "getImage: No image available for playlist " + playlistName);
        return "";
    }

    public String getId() {
        return id;
    }

    public ArrayList<SongModel> getSongs() {
        return songs;
    }

    public boolean isFavourite() {
        return isFavourite;
    }

    //#endregion

    //#region Setters

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setSongs(ArrayList<SongModel> songs) {
        this.songs = songs;
    }

    public void setFavourite(boolean favourite) {
        isFavourite = favourite;
    }

    //#endregion
}