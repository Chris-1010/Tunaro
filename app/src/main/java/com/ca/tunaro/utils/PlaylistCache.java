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
import java.util.List;

public class PlaylistCache {
    public static class PlaylistMetadata {
        private final String id;
        private final String name;
        private final int songCount;
        private final String imageUrl;
        private final List<String> songIds;

        public PlaylistMetadata(String id, String name, int songCount, String imageUrl, List<String> songIds) {
            this.id = id;
            this.name = name;
            this.songCount = songCount;
            this.imageUrl = imageUrl;
            this.songIds = songIds;
        }

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public int getSongCount() { return songCount; }
        public String getImageUrl() { return imageUrl; }
        public List<String> getSongIds() {
            return songIds != null ? songIds : new ArrayList<>();
        }
    }


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
            List<PlaylistMetadata> metadataList = new ArrayList<>();

            for (PlaylistModel playlist : playlists) {
                // Only extract song IDs if songs are actually loaded
                List<String> songIds = new ArrayList<>();
                if (playlist.getSongs() != null && !playlist.getSongs().isEmpty()) {
                    for (SongModel song : playlist.getSongs()) {
                        songIds.add(song.getId());
                    }
                    Log.d(TAG, "Caching " + songIds.size() + " song IDs for playlist: " + playlist.getPlaylistName());
                } else {
                    Log.d(TAG, "Playlist " + playlist.getPlaylistName() + " has no songs loaded yet - caching empty list");
                }

                PlaylistMetadata metadata = new PlaylistMetadata(
                        playlist.getId(),
                        playlist.getPlaylistName(),
                        playlist.getSongCount(),
                        playlist.getImage(),
                        songIds
                );
                metadataList.add(metadata);
            }

            String playlistsJson = gson.toJson(metadataList);
            Log.d(TAG, "Caching " + metadataList.size() + " playlist metadata entries");
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

            Type metadataListType = new TypeToken<List<PlaylistMetadata>>(){}.getType();
            List<PlaylistMetadata> metadataList = gson.fromJson(playlistsJson, metadataListType);

            if (metadataList == null || metadataList.isEmpty()) {
                Log.d(TAG, "Cached playlists parsed to null or empty");
                return null;
            }

            // Convert metadata back to PlaylistModel objects (without songs for now)
            ArrayList<PlaylistModel> playlists = new ArrayList<>();
            for (PlaylistMetadata metadata : metadataList) {
                // Create empty PlaylistModel - songs will be populated later from SongCache
                PlaylistModel playlist = new PlaylistModel(
                        metadata.getId(),
                        metadata.getName(),
                        metadata.getSongCount(),
                        null, // PlaylistModel will handle the URL directly
                        new ArrayList<>() // Empty songs list
                );

                playlist.setImageUrl(metadata.getImageUrl());
                playlists.add(playlist);
            }

            Log.d(TAG, "Successfully retrieved " + playlists.size() + " playlists from cache");
            return playlists;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving cached playlists", e);
            return null;
        }
    }

    public List<String> getCachedPlaylistSongIds(String playlistId) {
        try {
            String playlistsJson = prefs.getString(PLAYLISTS_KEY, null);
            if (playlistsJson == null) {
                return null;
            }

            Type metadataListType = new TypeToken<List<PlaylistMetadata>>(){}.getType();
            List<PlaylistMetadata> metadataList = gson.fromJson(playlistsJson, metadataListType);

            if (metadataList != null) {
                for (PlaylistMetadata metadata : metadataList) {
                    if (metadata.getId().equals(playlistId)) {
                        List<String> songIds = metadata.getSongIds();
                        if (songIds != null) {
                            Log.d(TAG, "Found " + songIds.size() + " song IDs for playlist " + playlistId);
                            return songIds;
                        } else {
                            Log.d(TAG, "Song IDs list is null for playlist " + playlistId);
                            return null;
                        }
                    }
                }
            }

            Log.d(TAG, "No cached song IDs found for playlist " + playlistId);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving cached song IDs for playlist " + playlistId, e);
            return null;
        }
    }

    public void updatePlaylistSongs(String playlistId, List<String> songIds) {
        try {
            String playlistsJson = prefs.getString(PLAYLISTS_KEY, null);
            if (playlistsJson == null) return;

            Type metadataListType = new TypeToken<List<PlaylistMetadata>>(){}.getType();
            List<PlaylistMetadata> metadataList = gson.fromJson(playlistsJson, metadataListType);

            if (metadataList != null) {
                for (PlaylistMetadata metadata : metadataList) {
                    if (metadata.getId().equals(playlistId)) {
                        // Update the song IDs for this playlist
                        PlaylistMetadata updatedMetadata = new PlaylistMetadata(
                                metadata.getId(),
                                metadata.getName(),
                                metadata.getSongCount(),
                                metadata.getImageUrl(),
                                songIds
                        );

                        // Replace in list
                        int index = metadataList.indexOf(metadata);
                        metadataList.set(index, updatedMetadata);

                        // Save back to preferences
                        String updatedJson = gson.toJson(metadataList);
                        prefs.edit().putString(PLAYLISTS_KEY, updatedJson).apply();

                        Log.d(TAG, "Updated playlist " + playlistId + " with " + songIds.size() + " song IDs");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating playlist songs", e);
        }
    }

    public void clearCache() {
        Log.d(TAG, "Clearing cache");
        prefs.edit().clear().apply();
    }
}