package com.ca.tunaro.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.models.SongModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class PlaylistCache {
    private static final String TAG = "PlaylistCache";
    private static final String PREF_NAME = "PlaylistCache";
    private static final String PLAYLISTS_KEY = "cached_playlists";
    private static final String SONGS_KEY_PREFIX = "songs_for_playlist_";

    private final SharedPreferences prefs;
    private final Gson gson;

    public PlaylistCache(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new GsonBuilder()
                .serializeNulls()
                .create();
    }

    public void cachePlaylists(ArrayList<PlaylistModel> playlists) {
        try {
            String playlistsJson = gson.toJson(playlists);
            Log.d(TAG, "Caching playlists: " + playlistsJson);
            prefs.edit().putString(PLAYLISTS_KEY, playlistsJson).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error caching playlists", e);
        }
    }

    public ArrayList<PlaylistModel> getCachedPlaylists() {
        try {
            String playlistsJson = prefs.getString(PLAYLISTS_KEY, null);
            Log.d(TAG, "Retrieved cached playlists: " + playlistsJson);

            if (playlistsJson == null) {
                Log.d(TAG, "No cached playlists found");
                return null;
            }

            Type playlistType = new TypeToken<ArrayList<PlaylistModel>>(){}.getType();
            ArrayList<PlaylistModel> playlists = gson.fromJson(playlistsJson, playlistType);

            if (playlists == null || playlists.isEmpty()) {
                Log.d(TAG, "Cached playlists parsed to null or empty");
                return null;
            }

            Log.d(TAG, "Successfully retrieved " + playlists.size() + " playlists from cache");
            return playlists;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving cached playlists", e);
            return null;
        }
    }

    public void cacheSongsForPlaylist(String playlistId, ArrayList<SongModel> songs) {
        try {
            String songsJson = gson.toJson(songs);
            Log.d(TAG, "Caching songs for playlist " + playlistId + ": " + songsJson);
            prefs.edit().putString(SONGS_KEY_PREFIX + playlistId, songsJson).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error caching songs for playlist " + playlistId, e);
        }
    }

    public ArrayList<SongModel> getCachedSongsForPlaylist(String playlistId) {
        try {
            String songsJson = prefs.getString(SONGS_KEY_PREFIX + playlistId, null);
            Log.d(TAG, "Retrieved cached songs for playlist " + playlistId + ": " + songsJson);

            if (songsJson == null) {
                return null;
            }

            Type songType = new TypeToken<ArrayList<SongModel>>(){}.getType();
            return gson.fromJson(songsJson, songType);
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving cached songs for playlist " + playlistId, e);
            return null;
        }
    }

    public void clearCache() {
        Log.d(TAG, "Clearing cache");
        prefs.edit().clear().apply();
    }
}