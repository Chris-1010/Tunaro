package com.ca.tunaro.database;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

import com.ca.tunaro.models.Artist;
import com.ca.tunaro.models.ListenHistoryEntry;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.models.SongNote;
import com.ca.tunaro.models.SongSnippet;
import com.ca.tunaro.models.SongVariant;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DatabaseHelper extends SQLiteOpenHelper {

    //#region Initialisations

    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "TunaroDB";
    private static final int DATABASE_VERSION = 9;

    // Table names
    private static final String TABLE_ARTISTS = "artists";
    private static final String TABLE_ALBUMS = "albums";
    private static final String TABLE_SONGS = "songs";
    private static final String TABLE_SONG_ARTISTS = "song_artists";
    private static final String TABLE_SONG_VARIANTS = "song_variants";
    private static final String TABLE_SONG_VARIANT_ARTISTS = "song_variant_artists";
    private static final String TABLE_PLAYLISTS = "playlists";
    private static final String TABLE_SONG_PLAYLISTS = "song_playlists";
    private static final String TABLE_SONG_NOTES = "song_notes";
    private static final String TABLE_SONG_SNIPPETS = "song_snippets";
    private static final String TABLE_LISTEN_HISTORY = "listen_history";

    // Shared columns
    private static final String COLUMN_UUID = "uuid";
    private static final String COLUMN_ID = "id";

    // Artists columns
    private static final String COLUMN_ARTIST_ID = "artist_id";
    private static final String COLUMN_ARTIST_NAME = "name";

    // Albums columns
    private static final String COLUMN_ALBUM_ID = "album_id";
    private static final String COLUMN_ALBUM_NAME = "name";
    private static final String COLUMN_ALBUM_TYPE = "album_type";
    private static final String COLUMN_RELEASE_DATE = "release_date";
    private static final String COLUMN_COVER_IMAGE_URL = "cover_image_url";

    // Songs columns
    private static final String COLUMN_SONG_ID = "song_id";
    private static final String COLUMN_SONG_NAME = "name";
    private static final String COLUMN_DURATION_MS = "duration_ms";
    private static final String COLUMN_SPOTIFY_URI = "spotify_uri";
    private static final String COLUMN_ISRC = "isrc";
    private static final String COLUMN_POPULARITY = "popularity";
    private static final String COLUMN_IS_PLAYABLE = "is_playable";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_LAST_REFRESHED_AT = "last_refreshed_at";
    private static final String COLUMN_USER_CANONICAL_VARIANT_ID = "user_canonical_variant_id";

    // Song artists columns
    private static final String COLUMN_POSITION = "position";

    // Song variants columns
    private static final String COLUMN_VARIANT_ID = "variant_id";
    private static final String COLUMN_FIRST_SEEN_AT = "first_seen_at";

    // Playlists columns
    private static final String COLUMN_PLAYLIST_ID = "playlist_id";
    private static final String COLUMN_PLAYLIST_NAME = "name";
    private static final String COLUMN_DESCRIPTION = "description";
    private static final String COLUMN_IMAGE_URL = "image_url";
    private static final String COLUMN_TRACK_COUNT = "track_count";
    private static final String COLUMN_OWNER = "owner";
    private static final String COLUMN_IS_FAVOURITE = "is_favourite";
    private static final String COLUMN_IS_ARCHIVED = "is_archived";

    // Song playlists columns
    private static final String COLUMN_ADDED_AT = "added_at";
    private static final String COLUMN_REMOVED_AT = "removed_at";

    // Song notes columns
    private static final String COLUMN_NOTE_TYPE = "note_type";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    // Song snippets columns
    private static final String COLUMN_SNIPPET_NO = "snippet_no";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_START_TIME = "start_time";
    private static final String COLUMN_END_TIME = "end_time";
    private static final String COLUMN_INCLUDE_IN_RANKINGS = "include_in_rankings";

    // Listen history columns
    private static final String COLUMN_LISTEN_TIMESTAMP = "listen_timestamp";
    private static final String CURSOR_PREF_KEY = "last_sync_cursor";

    //#region Create table SQL

    private static final String CREATE_TABLE_ARTISTS =
            "CREATE TABLE " + TABLE_ARTISTS + "("
                    + COLUMN_ARTIST_ID + " TEXT PRIMARY KEY,"
                    + COLUMN_ARTIST_NAME + " TEXT NOT NULL"
                    + ")";

    private static final String CREATE_TABLE_ALBUMS =
            "CREATE TABLE " + TABLE_ALBUMS + "("
                    + COLUMN_ALBUM_ID + " TEXT PRIMARY KEY,"
                    + COLUMN_ALBUM_NAME + " TEXT NOT NULL,"
                    + COLUMN_ALBUM_TYPE + " TEXT,"
                    + COLUMN_RELEASE_DATE + " TEXT,"
                    + COLUMN_COVER_IMAGE_URL + " TEXT"
                    + ")";

    private static final String CREATE_TABLE_SONGS =
            "CREATE TABLE " + TABLE_SONGS + "("
                    + COLUMN_SONG_ID + " TEXT PRIMARY KEY,"
                    + COLUMN_SONG_NAME + " TEXT NOT NULL,"
                    + COLUMN_DURATION_MS + " INTEGER NOT NULL,"
                    + COLUMN_SPOTIFY_URI + " TEXT NOT NULL,"
                    + COLUMN_ISRC + " TEXT,"
                    + COLUMN_POPULARITY + " INTEGER DEFAULT 0,"
                    + COLUMN_IS_PLAYABLE + " INTEGER DEFAULT 1,"
                    + COLUMN_ALBUM_ID + " TEXT,"
                    + COLUMN_USER_CANONICAL_VARIANT_ID + " INTEGER,"
                    + COLUMN_CREATED_AT + " TEXT NOT NULL,"
                    + COLUMN_LAST_REFRESHED_AT + " TEXT"
                    + ")";

    private static final String CREATE_TABLE_SONG_ARTISTS =
            "CREATE TABLE " + TABLE_SONG_ARTISTS + "("
                    + COLUMN_SONG_ID + " TEXT NOT NULL,"
                    + COLUMN_ARTIST_ID + " TEXT NOT NULL,"
                    + COLUMN_POSITION + " INTEGER NOT NULL,"
                    + "PRIMARY KEY (" + COLUMN_SONG_ID + ", " + COLUMN_ARTIST_ID + ")"
                    + ")";

    private static final String CREATE_TABLE_SONG_VARIANTS =
            "CREATE TABLE " + TABLE_SONG_VARIANTS + "("
                    + COLUMN_VARIANT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SONG_ID + " TEXT NOT NULL,"
                    + COLUMN_SPOTIFY_URI + " TEXT NOT NULL,"
                    + COLUMN_ALBUM_ID + " TEXT,"
                    + COLUMN_POPULARITY + " INTEGER DEFAULT 0,"
                    + COLUMN_FIRST_SEEN_AT + " TEXT NOT NULL"
                    + ")";

    private static final String CREATE_TABLE_SONG_VARIANT_ARTISTS =
            "CREATE TABLE " + TABLE_SONG_VARIANT_ARTISTS + "("
                    + COLUMN_VARIANT_ID + " INTEGER NOT NULL,"
                    + COLUMN_ARTIST_ID + " TEXT NOT NULL,"
                    + COLUMN_POSITION + " INTEGER NOT NULL,"
                    + "PRIMARY KEY (" + COLUMN_VARIANT_ID + ", " + COLUMN_ARTIST_ID + ")"
                    + ")";

    private static final String CREATE_TABLE_PLAYLISTS =
            "CREATE TABLE " + TABLE_PLAYLISTS + "("
                    + COLUMN_PLAYLIST_ID + " TEXT PRIMARY KEY,"
                    + COLUMN_PLAYLIST_NAME + " TEXT NOT NULL,"
                    + COLUMN_DESCRIPTION + " TEXT,"
                    + COLUMN_IMAGE_URL + " TEXT,"
                    + COLUMN_TRACK_COUNT + " INTEGER DEFAULT 0,"
                    + COLUMN_OWNER + " TEXT,"
                    + COLUMN_IS_FAVOURITE + " INTEGER DEFAULT 0,"
                    + COLUMN_IS_ARCHIVED + " INTEGER DEFAULT 0"
                    + ")";

    private static final String CREATE_TABLE_SONG_PLAYLISTS =
            "CREATE TABLE " + TABLE_SONG_PLAYLISTS + "("
                    + COLUMN_SONG_ID + " TEXT NOT NULL,"
                    + COLUMN_PLAYLIST_ID + " TEXT NOT NULL,"
                    + COLUMN_ADDED_AT + " TEXT,"
                    + COLUMN_REMOVED_AT + " TEXT,"
                    + "PRIMARY KEY (" + COLUMN_SONG_ID + ", " + COLUMN_PLAYLIST_ID + ")"
                    + ")";

    private static final String CREATE_TABLE_SONG_NOTES =
            "CREATE TABLE " + TABLE_SONG_NOTES + "("
                    + COLUMN_UUID + " TEXT UNIQUE NOT NULL,"
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SONG_ID + " TEXT NOT NULL,"
                    + COLUMN_NOTE_TYPE + " TEXT NOT NULL,"
                    + COLUMN_CONTENT + " TEXT NOT NULL,"
                    + COLUMN_TIMESTAMP + " TEXT DEFAULT (strftime('%d-%m-%Y %H:%M', 'now', 'localtime'))"
                    + ")";

    private static final String CREATE_TABLE_SONG_SNIPPETS =
            "CREATE TABLE " + TABLE_SONG_SNIPPETS + "("
                    + COLUMN_UUID + " TEXT UNIQUE NOT NULL,"
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SONG_ID + " TEXT NOT NULL,"
                    + COLUMN_SNIPPET_NO + " INTEGER NOT NULL,"
                    + COLUMN_TITLE + " TEXT,"
                    + COLUMN_START_TIME + " INTEGER NOT NULL,"
                    + COLUMN_END_TIME + " INTEGER NOT NULL,"
                    + COLUMN_INCLUDE_IN_RANKINGS + " INTEGER DEFAULT 1"
                    + ")";

    private static final String CREATE_TABLE_LISTEN_HISTORY =
            "CREATE TABLE " + TABLE_LISTEN_HISTORY + "("
                    + COLUMN_UUID + " TEXT UNIQUE NOT NULL,"
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SONG_ID + " TEXT NOT NULL,"
                    + COLUMN_LISTEN_TIMESTAMP + " TEXT NOT NULL"
                    + ")";

    //#endregion

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_ARTISTS);
        db.execSQL(CREATE_TABLE_ALBUMS);
        db.execSQL(CREATE_TABLE_SONGS);
        db.execSQL(CREATE_TABLE_SONG_ARTISTS);
        db.execSQL(CREATE_TABLE_SONG_VARIANTS);
        db.execSQL(CREATE_TABLE_SONG_VARIANT_ARTISTS);
        db.execSQL(CREATE_TABLE_PLAYLISTS);
        db.execSQL(CREATE_TABLE_SONG_PLAYLISTS);
        db.execSQL(CREATE_TABLE_SONG_NOTES);
        db.execSQL(CREATE_TABLE_SONG_SNIPPETS);
        db.execSQL(CREATE_TABLE_LISTEN_HISTORY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Fresh schema — drop everything and recreate
        db.execSQL("DROP TABLE IF EXISTS favourite_playlists");
        db.execSQL("DROP TABLE IF EXISTS archived_playlists");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ARTISTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ALBUMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_ARTISTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_VARIANT_ARTISTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_VARIANTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_PLAYLISTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLISTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_NOTES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_SNIPPETS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LISTEN_HISTORY);
        onCreate(db);
    }

    //#endregion

    //#region ======== ARTISTS ========

    public void upsertArtist(String artistId, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ARTIST_ID, artistId);
        values.put(COLUMN_ARTIST_NAME, name);
        db.insertWithOnConflict(TABLE_ARTISTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    //#endregion

    //#region ======== ALBUMS ========

    public void upsertAlbum(String albumId, String name, String albumType, String releaseDate, String coverImageUrl) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ALBUM_ID, albumId);
        values.put(COLUMN_ALBUM_NAME, name);
        values.put(COLUMN_ALBUM_TYPE, albumType);
        values.put(COLUMN_RELEASE_DATE, releaseDate);
        values.put(COLUMN_COVER_IMAGE_URL, coverImageUrl);
        db.insertWithOnConflict(TABLE_ALBUMS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    //#endregion

    //#region ======== SONGS ========

    public void upsertSong(SongModel song) {
        SQLiteDatabase db = this.getWritableDatabase();
        String now = utcNow();

        ContentValues values = new ContentValues();
        values.put(COLUMN_SONG_ID, song.getId());
        values.put(COLUMN_SONG_NAME, song.getName());
        values.put(COLUMN_DURATION_MS, song.getDuration());
        values.put(COLUMN_SPOTIFY_URI, song.getUri());
        values.put(COLUMN_ISRC, song.getIsrc());
        values.put(COLUMN_ALBUM_ID, song.getAlbumId());
        values.put(COLUMN_IS_PLAYABLE, song.isPlayable() ? 1 : 0);

        // On conflict: update mutable fields only, preserve created_at and user_canonical_variant_id
        long result = db.insertWithOnConflict(TABLE_SONGS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (result == -1) {
            // Row already exists — update mutable fields
            ContentValues update = new ContentValues();
            update.put(COLUMN_SPOTIFY_URI, song.getUri());
            update.put(COLUMN_ISRC, song.getIsrc());
            update.put(COLUMN_ALBUM_ID, song.getAlbumId());
            update.put(COLUMN_IS_PLAYABLE, song.isPlayable() ? 1 : 0);
            db.update(TABLE_SONGS, update, COLUMN_SONG_ID + " = ?", new String[]{song.getId()});
        } else {
            // New row — set created_at
            ContentValues createdAt = new ContentValues();
            createdAt.put(COLUMN_CREATED_AT, now);
            db.update(TABLE_SONGS, createdAt, COLUMN_SONG_ID + " = ?", new String[]{song.getId()});
        }

        db.close();

        // Upsert song–artist links
        upsertSongArtists(song);
    }

    private void upsertSongArtists(SongModel song) {
        String[] artistNames = song.getArtistArray();
        if (artistNames == null) return;
        // song_artists uses artist_id from artists table; for remote-track songs we don't have IDs
        // so we skip — this is populated properly via PlaylistSetup/LibraryActivity paths
    }

    public void upsertSongArtistLink(String songId, String artistId, int position) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SONG_ID, songId);
        values.put(COLUMN_ARTIST_ID, artistId);
        values.put(COLUMN_POSITION, position);
        db.insertWithOnConflict(TABLE_SONG_ARTISTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public void updateSongPopularity(String songId, int popularity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_POPULARITY, popularity);
        values.put(COLUMN_LAST_REFRESHED_AT, utcNow());
        db.update(TABLE_SONGS, values, COLUMN_SONG_ID + " = ?", new String[]{songId});
        db.close();
    }

    public void updateSongIsPlayable(String songId, boolean isPlayable) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_PLAYABLE, isPlayable ? 1 : 0);
        values.put(COLUMN_LAST_REFRESHED_AT, utcNow());
        db.update(TABLE_SONGS, values, COLUMN_SONG_ID + " = ?", new String[]{songId});
        db.close();
    }

    public void refreshSong(String songId, int popularity, boolean isPlayable) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_POPULARITY, popularity);
        values.put(COLUMN_IS_PLAYABLE, isPlayable ? 1 : 0);
        values.put(COLUMN_LAST_REFRESHED_AT, utcNow());
        db.update(TABLE_SONGS, values, COLUMN_SONG_ID + " = ?", new String[]{songId});
        db.close();
    }

    public void promoteVariantToCanonical(String songId, long variantId) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Fetch the variant
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SPOTIFY_URI + ", " + COLUMN_ALBUM_ID +
                        " FROM " + TABLE_SONG_VARIANTS +
                        " WHERE " + COLUMN_VARIANT_ID + " = ?",
                new String[]{String.valueOf(variantId)});

        if (cursor.moveToFirst()) {
            String variantUri = cursor.getString(0);
            String variantAlbumId = cursor.getString(1);

            ContentValues values = new ContentValues();
            values.put(COLUMN_USER_CANONICAL_VARIANT_ID, variantId);
            values.put(COLUMN_SPOTIFY_URI, variantUri);
            if (variantAlbumId != null) values.put(COLUMN_ALBUM_ID, variantAlbumId);
            db.update(TABLE_SONGS, values, COLUMN_SONG_ID + " = ?", new String[]{songId});
        }

        cursor.close();
        db.close();
    }

    // Returns songId → spotifyUri for songs not refreshed in the last 7 days
    public Map<String, String> getSongsNeedingRefresh() {
        Map<String, String> result = new HashMap<>();
        String sevenDaysAgo = utcDaysAgo(7);

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SONG_ID + ", " + COLUMN_SPOTIFY_URI + " FROM " + TABLE_SONGS +
                        " WHERE " + COLUMN_SPOTIFY_URI + " IS NOT NULL AND (" +
                        COLUMN_LAST_REFRESHED_AT + " IS NULL OR " +
                        COLUMN_LAST_REFRESHED_AT + " < ?)",
                new String[]{sevenDaysAgo});

        if (cursor.moveToFirst()) {
            do {
                result.put(cursor.getString(0), cursor.getString(1));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return result;
    }

    // Maps Spotify track IDs → composite song_ids in a single query
    public Map<String, String> getSongIdsBySpotifyTrackIds(List<String> spotifyTrackIds) {
        Map<String, String> result = new HashMap<>();
        if (spotifyTrackIds == null || spotifyTrackIds.isEmpty()) return result;

        List<String> uris = new ArrayList<>(spotifyTrackIds.size());
        for (String id : spotifyTrackIds) uris.add(SongModel.SPOTIFY_TRACK_URI_PREFIX + id);

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SONG_ID + ", " + COLUMN_SPOTIFY_URI + " FROM " + TABLE_SONGS +
                        " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + buildPlaceholders(uris.size()) + ")",
                uris.toArray(new String[0]));
        while (cursor.moveToNext()) {
            String songId = cursor.getString(0);
            String uri = cursor.getString(1);
            String trackId = uri.substring(SongModel.SPOTIFY_TRACK_URI_PREFIX.length());
            result.put(trackId, songId);
        }
        cursor.close();
        db.close();
        return result;
    }

    // Lean SongModel — for list views (PlaylistView, LibraryActivity)
    public SongModel getLeanSong(String songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT s." + COLUMN_SONG_ID + ", s." + COLUMN_SONG_NAME +
                        ", s." + COLUMN_DURATION_MS + ", s." + COLUMN_SPOTIFY_URI +
                        ", s." + COLUMN_POPULARITY + ", a." + COLUMN_COVER_IMAGE_URL +
                        ", a." + COLUMN_ALBUM_NAME + ", a." + COLUMN_RELEASE_DATE +
                        ", ar." + COLUMN_ARTIST_NAME +
                        " FROM " + TABLE_SONGS + " s" +
                        " LEFT JOIN " + TABLE_ALBUMS + " a ON s." + COLUMN_ALBUM_ID + " = a." + COLUMN_ALBUM_ID +
                        " LEFT JOIN " + TABLE_SONG_ARTISTS + " sa ON s." + COLUMN_SONG_ID + " = sa." + COLUMN_SONG_ID + " AND sa." + COLUMN_POSITION + " = 0" +
                        " LEFT JOIN " + TABLE_ARTISTS + " ar ON sa." + COLUMN_ARTIST_ID + " = ar." + COLUMN_ARTIST_ID +
                        " WHERE s." + COLUMN_SONG_ID + " = ?",
                new String[]{songId});

        SongModel song = null;
        if (cursor.moveToFirst()) {
            song = leanSongFromCursor(cursor);
        }
        cursor.close();
        db.close();
        return song;
    }

    // Full SongModel — for SongView
    public SongModel getFullSong(String songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT s.*, a." + COLUMN_ALBUM_NAME + ", a." + COLUMN_ALBUM_TYPE +
                        ", a." + COLUMN_RELEASE_DATE + ", a." + COLUMN_COVER_IMAGE_URL +
                        " FROM " + TABLE_SONGS + " s" +
                        " LEFT JOIN " + TABLE_ALBUMS + " a ON s." + COLUMN_ALBUM_ID + " = a." + COLUMN_ALBUM_ID +
                        " WHERE s." + COLUMN_SONG_ID + " = ?",
                new String[]{songId});

        SongModel song = null;
        if (cursor.moveToFirst()) {
            String id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_ID));
            String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_NAME));
            int duration = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DURATION_MS));
            String uri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SPOTIFY_URI));
            String isrc = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ISRC));
            int popularity = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_POPULARITY));
            boolean isPlayable = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_PLAYABLE)) == 1;
            String albumId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ALBUM_ID));
            String createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
            String albumName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ALBUM_NAME));
            String albumType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ALBUM_TYPE));
            String releaseDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RELEASE_DATE));
            String coverImageUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COVER_IMAGE_URL));

            // Fetch all artists for this song
            List<Artist> artistList = getSongArtists(songId);
            String primaryArtist = !artistList.isEmpty() ? artistList.get(0).getName() : null;
            String[] artistNames = new String[artistList.size()];
            for (int i = 0; i < artistList.size(); i++) artistNames[i] = artistList.get(i).getName();

            song = new SongModel(id, name, primaryArtist, artistNames, duration, uri, coverImageUrl,
                    popularity, albumId, albumName, albumType, releaseDate, isrc, isPlayable, createdAt);
        }
        cursor.close();
        db.close();

        if (song != null) {
            song.setVariants(getSongVariants(songId));
        }
        return song;
    }

    public boolean songExists(String songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT 1 FROM " + TABLE_SONGS + " WHERE " + COLUMN_SONG_ID + " = ?",
                new String[]{songId});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        db.close();
        return exists;
    }

    private SongModel leanSongFromCursor(Cursor cursor) {
        String id = cursor.getString(0);
        String name = cursor.getString(1);
        int duration = cursor.getInt(2);
        String uri = cursor.getString(3);
        int popularity = cursor.getInt(4);
        String coverImageUrl = cursor.getString(5);
        String albumName = cursor.getString(6);
        String releaseDate = cursor.getString(7);
        String primaryArtist = cursor.getString(8);
        return new SongModel(id, name, primaryArtist, duration, uri, coverImageUrl, popularity, albumName, releaseDate);
    }

    //#endregion

    //#region ======== SONG ARTISTS ========

    public List<Artist> getSongArtists(String songId) {
        List<Artist> artists = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT ar." + COLUMN_ARTIST_ID + ", ar." + COLUMN_ARTIST_NAME +
                        " FROM " + TABLE_SONG_ARTISTS + " sa" +
                        " JOIN " + TABLE_ARTISTS + " ar ON sa." + COLUMN_ARTIST_ID + " = ar." + COLUMN_ARTIST_ID +
                        " WHERE sa." + COLUMN_SONG_ID + " = ?" +
                        " ORDER BY sa." + COLUMN_POSITION + " ASC",
                new String[]{songId});

        if (cursor.moveToFirst()) {
            do {
                artists.add(new Artist(cursor.getString(0), cursor.getString(1)));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return artists;
    }

    //#endregion

    //#region ======== SONG VARIANTS ========

    // Returns variant_id if a variant with this URI already exists for the song, else -1
    public long getVariantIdByUri(String songId, String spotifyUri) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_VARIANT_ID + " FROM " + TABLE_SONG_VARIANTS +
                        " WHERE " + COLUMN_SONG_ID + " = ? AND " + COLUMN_SPOTIFY_URI + " = ?",
                new String[]{songId, spotifyUri});
        long id = cursor.moveToFirst() ? cursor.getLong(0) : -1;
        cursor.close();
        db.close();
        return id;
    }

    public long insertVariant(String songId, String spotifyUri, String albumId, int popularity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SONG_ID, songId);
        values.put(COLUMN_SPOTIFY_URI, spotifyUri);
        values.put(COLUMN_ALBUM_ID, albumId);
        values.put(COLUMN_POPULARITY, popularity);
        values.put(COLUMN_FIRST_SEEN_AT, utcNow());
        long id = db.insert(TABLE_SONG_VARIANTS, null, values);
        db.close();
        return id;
    }

    public void updateVariantPopularity(long variantId, int popularity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_POPULARITY, popularity);
        db.update(TABLE_SONG_VARIANTS, values, COLUMN_VARIANT_ID + " = ?", new String[]{String.valueOf(variantId)});
        db.close();
    }

    public void upsertVariantArtistLink(long variantId, String artistId, int position) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_VARIANT_ID, variantId);
        values.put(COLUMN_ARTIST_ID, artistId);
        values.put(COLUMN_POSITION, position);
        db.insertWithOnConflict(TABLE_SONG_VARIANT_ARTISTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public List<SongVariant> getSongVariants(String songId) {
        List<SongVariant> variants = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT sv." + COLUMN_VARIANT_ID + ", sv." + COLUMN_SONG_ID +
                        ", sv." + COLUMN_SPOTIFY_URI + ", sv." + COLUMN_ALBUM_ID +
                        ", a." + COLUMN_ALBUM_NAME + ", a." + COLUMN_COVER_IMAGE_URL +
                        ", sv." + COLUMN_POPULARITY + ", sv." + COLUMN_FIRST_SEEN_AT +
                        " FROM " + TABLE_SONG_VARIANTS + " sv" +
                        " LEFT JOIN " + TABLE_ALBUMS + " a ON sv." + COLUMN_ALBUM_ID + " = a." + COLUMN_ALBUM_ID +
                        " WHERE sv." + COLUMN_SONG_ID + " = ?" +
                        " ORDER BY sv." + COLUMN_VARIANT_ID + " ASC",
                new String[]{songId});

        if (cursor.moveToFirst()) {
            do {
                SongVariant v = new SongVariant(
                        cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5),
                        cursor.getInt(6), cursor.getString(7));
                variants.add(v);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();

        // Populate artists for each variant
        for (SongVariant v : variants) {
            v.setArtists(getVariantArtists(v.getVariantId()));
        }
        return variants;
    }

    private List<Artist> getVariantArtists(long variantId) {
        List<Artist> artists = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT ar." + COLUMN_ARTIST_ID + ", ar." + COLUMN_ARTIST_NAME +
                        " FROM " + TABLE_SONG_VARIANT_ARTISTS + " sva" +
                        " JOIN " + TABLE_ARTISTS + " ar ON sva." + COLUMN_ARTIST_ID + " = ar." + COLUMN_ARTIST_ID +
                        " WHERE sva." + COLUMN_VARIANT_ID + " = ?" +
                        " ORDER BY sva." + COLUMN_POSITION + " ASC",
                new String[]{String.valueOf(variantId)});

        if (cursor.moveToFirst()) {
            do {
                artists.add(new Artist(cursor.getString(0), cursor.getString(1)));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return artists;
    }

    // Recomputes songs.popularity as max across all variants (and the canonical row itself)
    public void recomputeMaxPopularity(String songId, int canonicalPopularity) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT MAX(" + COLUMN_POPULARITY + ") FROM " + TABLE_SONG_VARIANTS +
                        " WHERE " + COLUMN_SONG_ID + " = ?",
                new String[]{songId});

        int maxVariantPop = cursor.moveToFirst() ? cursor.getInt(0) : 0;
        cursor.close();
        db.close();

        int maxPop = Math.max(canonicalPopularity, maxVariantPop);
        updateSongPopularity(songId, maxPop);
    }

    public List<String> getVariantSpotifyUris(String songId) {
        List<String> uris = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SPOTIFY_URI + " FROM " + TABLE_SONG_VARIANTS +
                        " WHERE " + COLUMN_SONG_ID + " = ?",
                new String[]{songId});
        if (cursor.moveToFirst()) {
            do { uris.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return uris;
    }

    //#endregion

    //#region ======== PLAYLISTS ========

    public void upsertPlaylist(String playlistId, String name, String description,
                               String imageUrl, int trackCount, String owner) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PLAYLIST_ID, playlistId);
        values.put(COLUMN_PLAYLIST_NAME, name);
        values.put(COLUMN_DESCRIPTION, description);
        values.put(COLUMN_IMAGE_URL, imageUrl);
        values.put(COLUMN_TRACK_COUNT, trackCount);
        values.put(COLUMN_OWNER, owner);
        // Preserve is_favourite and is_archived on conflict
        long result = db.insertWithOnConflict(TABLE_PLAYLISTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (result == -1) {
            ContentValues update = new ContentValues();
            update.put(COLUMN_PLAYLIST_NAME, name);
            update.put(COLUMN_DESCRIPTION, description);
            update.put(COLUMN_IMAGE_URL, imageUrl);
            update.put(COLUMN_TRACK_COUNT, trackCount);
            update.put(COLUMN_OWNER, owner);
            db.update(TABLE_PLAYLISTS, update, COLUMN_PLAYLIST_ID + " = ?", new String[]{playlistId});
        }
        db.close();
    }

    public void favouritePlaylist(String playlistId) {
        setPlaylistFlag(playlistId, COLUMN_IS_FAVOURITE, 1);
    }

    public void unfavouritePlaylist(String playlistId) {
        setPlaylistFlag(playlistId, COLUMN_IS_FAVOURITE, 0);
    }

    public void archivePlaylist(String playlistId) {
        setPlaylistFlag(playlistId, COLUMN_IS_ARCHIVED, 1);
    }

    public void unarchivePlaylist(String playlistId) {
        setPlaylistFlag(playlistId, COLUMN_IS_ARCHIVED, 0);
    }

    private void setPlaylistFlag(String playlistId, String column, int value) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(column, value);
        db.update(TABLE_PLAYLISTS, values, COLUMN_PLAYLIST_ID + " = ?", new String[]{playlistId});
        db.close();
    }

    public boolean isPlaylistFavourited(String playlistId) {
        return getPlaylistFlag(playlistId, COLUMN_IS_FAVOURITE);
    }

    public boolean isPlaylistArchived(String playlistId) {
        return getPlaylistFlag(playlistId, COLUMN_IS_ARCHIVED);
    }

    private boolean getPlaylistFlag(String playlistId, String column) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + column + " FROM " + TABLE_PLAYLISTS +
                        " WHERE " + COLUMN_PLAYLIST_ID + " = ?",
                new String[]{playlistId});
        boolean flag = cursor.moveToFirst() && cursor.getInt(0) == 1;
        cursor.close();
        db.close();
        return flag;
    }

    public List<String> getFavouritedPlaylistIds() {
        return getPlaylistIdsByFlag(COLUMN_IS_FAVOURITE);
    }

    public List<String> getArchivedPlaylistIds() {
        return getPlaylistIdsByFlag(COLUMN_IS_ARCHIVED);
    }

    private List<String> getPlaylistIdsByFlag(String column) {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_PLAYLIST_ID + " FROM " + TABLE_PLAYLISTS +
                        " WHERE " + column + " = 1",
                null);
        if (cursor.moveToFirst()) {
            do { ids.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return ids;
    }

    //#endregion

    //#region ======== SONG–PLAYLIST LINKS ========

    public void upsertSongPlaylistLink(String songId, String playlistId, String addedAt) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SONG_ID, songId);
        values.put(COLUMN_PLAYLIST_ID, playlistId);
        values.put(COLUMN_ADDED_AT, addedAt);
        values.putNull(COLUMN_REMOVED_AT);
        long result = db.insertWithOnConflict(TABLE_SONG_PLAYLISTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (result == -1) {
            // Already exists — clear removed_at in case it was previously removed
            ContentValues update = new ContentValues();
            update.putNull(COLUMN_REMOVED_AT);
            db.update(TABLE_SONG_PLAYLISTS, update,
                    COLUMN_SONG_ID + " = ? AND " + COLUMN_PLAYLIST_ID + " = ?",
                    new String[]{songId, playlistId});
        }
        db.close();
    }

    public void markSongRemovedFromPlaylist(String songId, String playlistId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_REMOVED_AT, utcNow());
        db.update(TABLE_SONG_PLAYLISTS, values,
                COLUMN_SONG_ID + " = ? AND " + COLUMN_PLAYLIST_ID + " = ? AND " + COLUMN_REMOVED_AT + " IS NULL",
                new String[]{songId, playlistId});
        db.close();
    }

    // Returns all playlist IDs the song is linked to (both active and removed)
    public List<String> getPlaylistIdsForSong(String songId) {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_PLAYLIST_ID + " FROM " + TABLE_SONG_PLAYLISTS +
                        " WHERE " + COLUMN_SONG_ID + " = ?",
                new String[]{songId});
        if (cursor.moveToFirst()) {
            do { ids.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return ids;
    }

    // Returns playlist IDs still active (not removed) for a song
    public List<String> getActivePlaylistIdsForSong(String songId) {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_PLAYLIST_ID + " FROM " + TABLE_SONG_PLAYLISTS +
                        " WHERE " + COLUMN_SONG_ID + " = ? AND " + COLUMN_REMOVED_AT + " IS NULL",
                new String[]{songId});
        if (cursor.moveToFirst()) {
            do { ids.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return ids;
    }

    // Returns active song IDs for a given playlist
    public List<String> getActiveSongIdsForPlaylist(String playlistId) {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SONG_ID + " FROM " + TABLE_SONG_PLAYLISTS +
                        " WHERE " + COLUMN_PLAYLIST_ID + " = ? AND " + COLUMN_REMOVED_AT + " IS NULL",
                new String[]{playlistId});
        if (cursor.moveToFirst()) {
            do { ids.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return ids;
    }

    // Used during sync: marks any song–playlist links no longer in the current track list as removed
    public void reconcilePlaylistSongs(String playlistId, List<String> currentSongIds) {
        List<String> storedActive = getActiveSongIdsForPlaylist(playlistId);
        for (String storedId : storedActive) {
            if (!currentSongIds.contains(storedId)) {
                markSongRemovedFromPlaylist(storedId, playlistId);
            }
        }
    }

    public static class PlaylistLink {
        public final String playlistId;
        public final String name;
        public final String imageUrl;
        public final String removedAt; // null = currently active

        public PlaylistLink(String playlistId, String name, String imageUrl, String removedAt) {
            this.playlistId = playlistId;
            this.name = name;
            this.imageUrl = imageUrl;
            this.removedAt = removedAt;
        }

        public boolean isActive() { return removedAt == null; }
    }

    public List<PlaylistLink> getPlaylistsForSong(String songId) {
        List<PlaylistLink> result = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT sp." + COLUMN_PLAYLIST_ID + ", p." + COLUMN_PLAYLIST_NAME +
                        ", p." + COLUMN_IMAGE_URL + ", sp." + COLUMN_REMOVED_AT +
                        " FROM " + TABLE_SONG_PLAYLISTS + " sp" +
                        " JOIN " + TABLE_PLAYLISTS + " p ON sp." + COLUMN_PLAYLIST_ID + " = p." + COLUMN_PLAYLIST_ID +
                        " WHERE sp." + COLUMN_SONG_ID + " = ?" +
                        " ORDER BY sp." + COLUMN_REMOVED_AT + " IS NOT NULL ASC, p." + COLUMN_PLAYLIST_NAME + " ASC",
                new String[]{songId});
        if (cursor.moveToFirst()) {
            do {
                result.add(new PlaylistLink(
                        cursor.getString(0), cursor.getString(1),
                        cursor.getString(2), cursor.getString(3)));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return result;
    }

    //#endregion

    //#region ======== SONG NOTES ========

    private List<SongNote> getAllNotes() {
        List<SongNote> allNotes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_SONG_NOTES + " ORDER BY " + COLUMN_TIMESTAMP + " ASC", null);
        if (cursor.moveToFirst()) {
            do { allNotes.add(noteFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return allNotes;
    }

    public List<SongNote> getSongNotes(String songId) {
        List<SongNote> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_SONG_NOTES +
                        " WHERE " + COLUMN_SONG_ID + " = ?" +
                        " ORDER BY " + COLUMN_TIMESTAMP + " ASC",
                new String[]{songId});
        if (cursor.moveToFirst()) {
            do { notes.add(noteFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return notes;
    }

    public long addNote(SongNote note) {
        SQLiteDatabase db = this.getWritableDatabase();
        if (note.getUuid() != null && dataExistsByUUID(db, TABLE_SONG_NOTES, note.getUuid())) {
            db.close();
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_UUID, note.getUuid());
        values.put(COLUMN_SONG_ID, note.getSongId());
        values.put(COLUMN_NOTE_TYPE, note.getNoteType());
        values.put(COLUMN_CONTENT, note.getContent());
        long id = db.insert(TABLE_SONG_NOTES, null, values);
        note.setId(id);
        db.close();
        return id;
    }

    public void editNote(SongNote note) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_SONG_NOTES +
                " SET " + COLUMN_TIMESTAMP + " = strftime('%d-%m-%Y %H:%M', 'now', 'localtime')" +
                " WHERE " + COLUMN_ID + " = ?", new String[]{String.valueOf(note.getId())});
        ContentValues values = new ContentValues();
        values.put(COLUMN_NOTE_TYPE, note.getNoteType());
        values.put(COLUMN_CONTENT, note.getContent());
        db.update(TABLE_SONG_NOTES, values, COLUMN_ID + " = ?", new String[]{String.valueOf(note.getId())});
        db.close();
    }

    public void deleteNote(long noteId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SONG_NOTES, COLUMN_ID + " = ?", new String[]{String.valueOf(noteId)});
        db.close();
    }

    public boolean hasSongNotes(String songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SONG_NOTES, new String[]{COLUMN_ID},
                COLUMN_SONG_ID + " = ?", new String[]{songId}, null, null, null, "1");
        boolean has = cursor.moveToFirst();
        cursor.close();
        db.close();
        return has;
    }

    public List<String> getSongIdsWithNotes() {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT " + COLUMN_SONG_ID + " FROM " + TABLE_SONG_NOTES +
                        " ORDER BY " + COLUMN_TIMESTAMP + " DESC", null);
        if (cursor.moveToFirst()) {
            do { ids.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return ids;
    }

    public List<String> getRecentNoteTypes(String searchTerm) {
        List<String> noteTypes = new ArrayList<>();
        String query = "SELECT DISTINCT " + COLUMN_NOTE_TYPE + " FROM " + TABLE_SONG_NOTES;
        String[] selectionArgs = null;
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            query += " WHERE " + COLUMN_NOTE_TYPE + " LIKE ?";
            selectionArgs = new String[]{"%" + searchTerm + "%"};
        }
        query += " ORDER BY " + COLUMN_TIMESTAMP + " DESC LIMIT 5";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, selectionArgs);
        if (cursor.moveToFirst()) {
            do {
                String type = cursor.getString(0);
                if (!noteTypes.contains(type)) noteTypes.add(type);
            } while (cursor.moveToNext() && noteTypes.size() < 5);
        }
        cursor.close();
        db.close();
        return noteTypes;
    }

    private SongNote noteFromCursor(Cursor cursor) {
        return new SongNote(
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UUID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE_TYPE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
    }

    //#endregion

    //#region ======== SONG SNIPPETS ========

    private List<SongSnippet> getAllSnippets() {
        List<SongSnippet> all = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_SONG_SNIPPETS + " ORDER BY " + COLUMN_ID + " ASC", null);
        if (cursor.moveToFirst()) {
            do { all.add(snippetFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return all;
    }

    public List<SongSnippet> getSongSnippets(String songId) {
        List<SongSnippet> snippets = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_SONG_SNIPPETS +
                        " WHERE " + COLUMN_SONG_ID + " = ?" +
                        " ORDER BY " + COLUMN_SNIPPET_NO + " ASC",
                new String[]{songId});
        if (cursor.moveToFirst()) {
            do { snippets.add(snippetFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return snippets;
    }

    public long addSnippet(SongSnippet snippet) {
        SQLiteDatabase db = this.getWritableDatabase();
        if (snippet.getUuid() != null && dataExistsByUUID(db, TABLE_SONG_SNIPPETS, snippet.getUuid())) {
            db.close();
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_UUID, snippet.getUuid());
        values.put(COLUMN_SONG_ID, snippet.getSongId());
        values.put(COLUMN_SNIPPET_NO, snippet.getSnippetNo());
        values.put(COLUMN_TITLE, snippet.getTitle());
        values.put(COLUMN_START_TIME, snippet.getStartTime());
        values.put(COLUMN_END_TIME, snippet.getEndTime());
        values.put(COLUMN_INCLUDE_IN_RANKINGS, snippet.getIncludeInRankings() ? 1 : 0);
        long id = db.insert(TABLE_SONG_SNIPPETS, null, values);
        snippet.setId(id);
        db.close();
        return id;
    }

    public void editSnippet(SongSnippet snippet) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SNIPPET_NO, snippet.getSnippetNo());
        values.put(COLUMN_TITLE, snippet.getTitle());
        values.put(COLUMN_START_TIME, snippet.getStartTime());
        values.put(COLUMN_END_TIME, snippet.getEndTime());
        values.put(COLUMN_INCLUDE_IN_RANKINGS, snippet.getIncludeInRankings() ? 1 : 0);
        db.update(TABLE_SONG_SNIPPETS, values, COLUMN_ID + " = ?", new String[]{String.valueOf(snippet.getId())});
        db.close();
    }

    public void deleteSnippet(long snippetId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SONG_SNIPPETS, COLUMN_ID + " = ?", new String[]{String.valueOf(snippetId)});
        db.close();
    }

    public boolean hasSongSnippets(String songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SONG_SNIPPETS, new String[]{COLUMN_ID},
                COLUMN_SONG_ID + " = ?", new String[]{songId}, null, null, null, "1");
        boolean has = cursor.moveToFirst();
        cursor.close();
        db.close();
        return has;
    }

    public List<String> getSongIdsWithSnippets() {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT " + COLUMN_SONG_ID + " FROM " + TABLE_SONG_SNIPPETS +
                        " ORDER BY " + COLUMN_ID + " DESC", null);
        if (cursor.moveToFirst()) {
            do { ids.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return ids;
    }

    private SongSnippet snippetFromCursor(Cursor cursor) {
        return new SongSnippet(
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UUID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SNIPPET_NO)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_START_TIME)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_END_TIME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_INCLUDE_IN_RANKINGS)) == 1);
    }

    //#endregion

    //#region ======== LISTEN HISTORY ========

    private List<ListenHistoryEntry> getAllListenHistory() {
        List<ListenHistoryEntry> all = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_LISTEN_HISTORY + " ORDER BY " + COLUMN_LISTEN_TIMESTAMP + " ASC", null);
        if (cursor.moveToFirst()) {
            do {
                all.add(new ListenHistoryEntry(
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UUID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LISTEN_TIMESTAMP))));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return all;
    }

    public void addListenRecord(String songId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_UUID, java.util.UUID.randomUUID().toString());
        values.put(COLUMN_SONG_ID, songId);
        values.put(COLUMN_LISTEN_TIMESTAMP, utcNow());
        db.insert(TABLE_LISTEN_HISTORY, null, values);
        db.close();
    }

    public void addListenRecordWithTimestamp(String songId, String utcTimestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_UUID, java.util.UUID.randomUUID().toString());
        values.put(COLUMN_SONG_ID, songId);
        values.put(COLUMN_LISTEN_TIMESTAMP, utcTimestamp);
        db.insert(TABLE_LISTEN_HISTORY, null, values);
        db.close();
    }

    public List<String> getListenHistory(String songId) {
        List<String> timestamps = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_LISTEN_TIMESTAMP + " FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SONG_ID + " = ? ORDER BY " + COLUMN_LISTEN_TIMESTAMP + " ASC",
                new String[]{songId});
        if (cursor.moveToFirst()) {
            do { timestamps.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return timestamps;
    }

    public String getMostRecentListenTimestamp(String songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_LISTEN_TIMESTAMP + " FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SONG_ID + " = ? ORDER BY " + COLUMN_LISTEN_TIMESTAMP + " DESC LIMIT 1",
                new String[]{songId});
        String timestamp = cursor.moveToFirst() ? cursor.getString(0) : null;
        cursor.close();
        db.close();
        return timestamp;
    }

    public Map<String, String> getMostRecentListenTimestampsBatch(List<String> songIds) {
        Map<String, String> results = new HashMap<>();
        if (songIds == null || songIds.isEmpty()) return results;
        String placeholders = buildPlaceholders(songIds.size());
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SONG_ID + ", MAX(" + COLUMN_LISTEN_TIMESTAMP + ") as latest" +
                        " FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SONG_ID + " IN (" + placeholders + ")" +
                        " GROUP BY " + COLUMN_SONG_ID,
                songIds.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do {
                results.put(cursor.getString(0), cursor.getString(1));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return results;
    }

    public Map<String, Integer> getListenCountsBatch(List<String> songIds) {
        Map<String, Integer> results = new HashMap<>();
        if (songIds == null || songIds.isEmpty()) return results;
        String placeholders = buildPlaceholders(songIds.size());
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SONG_ID + ", COUNT(*) as count" +
                        " FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SONG_ID + " IN (" + placeholders + ")" +
                        " GROUP BY " + COLUMN_SONG_ID,
                songIds.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do {
                results.put(cursor.getString(0), cursor.getInt(1));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return results;
    }

    public boolean hasListenWithinDuration(String songId, long spotifyTimestamp, int songDurationMs) {
        SQLiteDatabase db = this.getReadableDatabase();
        java.text.SimpleDateFormat utcFormat = utcDateFormat();
        String windowStartStr = utcFormat.format(new java.util.Date(spotifyTimestamp - songDurationMs));
        String windowEndStr = utcFormat.format(new java.util.Date(spotifyTimestamp + songDurationMs));
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SONG_ID + " = ? AND " +
                        COLUMN_LISTEN_TIMESTAMP + " >= ? AND " + COLUMN_LISTEN_TIMESTAMP + " <= ?",
                new String[]{songId, windowStartStr, windowEndStr});
        boolean has = cursor.moveToFirst() && cursor.getInt(0) > 0;
        cursor.close();
        db.close();
        return has;
    }

    public boolean hasExactListen(String songId, String utcTimestamp) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SONG_ID + " = ? AND " + COLUMN_LISTEN_TIMESTAMP + " = ?",
                new String[]{songId, utcTimestamp});
        boolean exists = cursor.moveToFirst() && cursor.getInt(0) > 0;
        cursor.close();
        db.close();
        return exists;
    }

    // Holds state for bulk listen import — one open DB connection, in-memory dedup sets, pending rows.
    public static class ListenImportBatch {
        final SQLiteDatabase db;
        final java.util.Set<String> existingKeys; // "songId\0timestamp"
        final java.util.Set<String> existingUuids;
        final List<ContentValues> pending = new ArrayList<>();

        ListenImportBatch(SQLiteDatabase db, java.util.Set<String> existingKeys, java.util.Set<String> existingUuids) {
            this.db = db;
            this.existingKeys = existingKeys;
            this.existingUuids = existingUuids;
        }
    }

    public ListenImportBatch beginListenImportBatch() {
        SQLiteDatabase db = this.getWritableDatabase();
        java.util.Set<String> existingKeys = new java.util.HashSet<>();
        java.util.Set<String> existingUuids = new java.util.HashSet<>();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_UUID + ", " + COLUMN_SONG_ID + ", " + COLUMN_LISTEN_TIMESTAMP
                        + " FROM " + TABLE_LISTEN_HISTORY, null);
        if (cursor.moveToFirst()) {
            do {
                String uuid = cursor.getString(0);
                if (uuid != null) existingUuids.add(uuid);
                existingKeys.add(cursor.getString(1) + "\0" + cursor.getString(2));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return new ListenImportBatch(db, existingKeys, existingUuids);
    }

    public boolean hasExactListenInBatch(ListenImportBatch batch, String songId, String utcTimestamp) {
        return batch.existingKeys.contains(songId + "\0" + utcTimestamp);
    }

    public void addListenToBatch(ListenImportBatch batch, String uuid, String songId, String utcTimestamp) {
        // Skip if this UUID is already in the DB (e.g. from a previous partial import run)
        if (uuid != null && batch.existingUuids.contains(uuid)) return;
        batch.existingKeys.add(songId + "\0" + utcTimestamp);
        if (uuid != null) batch.existingUuids.add(uuid);
        ContentValues values = new ContentValues();
        values.put(COLUMN_UUID, uuid != null ? uuid : java.util.UUID.randomUUID().toString());
        values.put(COLUMN_SONG_ID, songId);
        values.put(COLUMN_LISTEN_TIMESTAMP, utcTimestamp);
        batch.pending.add(values);
    }

    public void flushListenBatch(ListenImportBatch batch) {
        if (batch.pending.isEmpty()) return;
        batch.db.beginTransaction();
        try {
            for (ContentValues values : batch.pending) {
                batch.db.insert(TABLE_LISTEN_HISTORY, null, values);
            }
            batch.db.setTransactionSuccessful();
        } finally {
            batch.db.endTransaction();
            batch.pending.clear();
        }
    }

    //#endregion

    //#region ======== SYNC CURSOR ========

    public void saveLastSyncCursor(Context context, String cursor) {
        context.getSharedPreferences("TunaroPrefs", Context.MODE_PRIVATE)
                .edit().putString(CURSOR_PREF_KEY, cursor).apply();
    }

    public String getLastSyncCursor(Context context) {
        return context.getSharedPreferences("TunaroPrefs", Context.MODE_PRIVATE)
                .getString(CURSOR_PREF_KEY, null);
    }

    public boolean hasLastSyncCursor(Context context) {
        return getLastSyncCursor(context) != null;
    }

    //#endregion

    //#region ======== EXPORT / IMPORT ========

    public static class ExportData {
        public List<SongNote> notes;
        public List<SongSnippet> snippets;
        public List<ListenHistoryEntry> listenHistory;
        public String lastSyncCursor;
        public String exportDate;
        public int databaseVersion;

        public ExportData() {
            this.exportDate = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            this.databaseVersion = DATABASE_VERSION;
        }
    }

    public static String generateExportJson(Context context) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        ExportData exportData = new ExportData();
        exportData.notes = dbHelper.getAllNotes();
        exportData.snippets = dbHelper.getAllSnippets();
        exportData.listenHistory = dbHelper.getAllListenHistory();
        exportData.lastSyncCursor = dbHelper.getLastSyncCursor(context);
        return new GsonBuilder().setPrettyPrinting().create().toJson(exportData);
    }

    public static void writeExportToUri(Context context, Uri uri) throws IOException {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(Objects.requireNonNull(outputStream))) {
            ExportData exportData = new ExportData();
            exportData.notes = dbHelper.getAllNotes();
            exportData.snippets = dbHelper.getAllSnippets();
            exportData.listenHistory = dbHelper.getAllListenHistory();
            exportData.lastSyncCursor = dbHelper.getLastSyncCursor(context);
            new GsonBuilder().setPrettyPrinting().create().toJson(exportData, writer);
        }
    }

    public static ImportStats importFromUri(Context context, Uri uri) throws IOException, IllegalArgumentException {
        String jsonData = readTextFromUri(context, uri);
        Gson gson = new Gson();
        ExportData importData = gson.fromJson(jsonData, ExportData.class);
        if (importData == null) throw new IllegalArgumentException("Invalid or corrupted backup file");

        DatabaseHelper dbHelper = new DatabaseHelper(context);
        ImportStats stats = new ImportStats();

        if (importData.notes != null) {
            for (SongNote note : importData.notes) {
                if (dbHelper.addNote(new SongNote(note.getUuid(), note.getSongId(), note.getNoteType(), note.getContent())) != -1)
                    stats.notesAdded++;
            }
        }

        if (importData.snippets != null) {
            for (SongSnippet snippet : importData.snippets) {
                if (dbHelper.addSnippet(new SongSnippet(
                        snippet.getUuid(), snippet.getSongId(), snippet.getSnippetNo(),
                        snippet.getTitle(), snippet.getStartTime(), snippet.getEndTime(),
                        snippet.getIncludeInRankings())) != -1)
                    stats.snippetsAdded++;
            }
        }

        if (importData.listenHistory != null) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            try {
                for (ListenHistoryEntry entry : importData.listenHistory) {
                    if (!dbHelper.dataExistsByUUID(db, TABLE_LISTEN_HISTORY, entry.getUuid())) {
                        ContentValues values = new ContentValues();
                        values.put(COLUMN_UUID, entry.getUuid());
                        values.put(COLUMN_SONG_ID, entry.getSongId());
                        values.put(COLUMN_LISTEN_TIMESTAMP, entry.getListenTimestamp());
                        if (db.insert(TABLE_LISTEN_HISTORY, null, values) != -1) stats.listenHistoryAdded++;
                    }
                }
            } finally {
                db.close();
            }
        }

        if (importData.lastSyncCursor != null) dbHelper.saveLastSyncCursor(context, importData.lastSyncCursor);
        return stats;
    }

    private static String readTextFromUri(Context context, Uri uri) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private boolean dataExistsByUUID(SQLiteDatabase db, String table, String uuid) {
        Cursor cursor = db.query(table, new String[]{"id"}, "uuid = ?", new String[]{uuid}, null, null, null);
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    public static class ImportStats {
        public int notesAdded = 0;
        public int snippetsAdded = 0;
        public int listenHistoryAdded = 0;

        public String getSummary() {
            List<String> parts = new ArrayList<>();
            if (notesAdded > 0) parts.add(notesAdded + " note" + (notesAdded == 1 ? "" : "s"));
            if (snippetsAdded > 0) parts.add(snippetsAdded + " snippet" + (snippetsAdded == 1 ? "" : "s"));
            if (listenHistoryAdded > 0) parts.add(listenHistoryAdded + " listen record" + (listenHistoryAdded == 1 ? "" : "s"));
            return parts.isEmpty() ? "No new data imported" : "Imported: " + String.join(", ", parts);
        }
    }

    //#endregion

    //#region ======== TIME UTILITIES ========

    private static String utcNow() {
        return utcDateFormat().format(new java.util.Date());
    }

    private static String utcDaysAgo(int days) {
        long ms = System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;
        return utcDateFormat().format(new java.util.Date(ms));
    }

    private static java.text.SimpleDateFormat utcDateFormat() {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return fmt;
    }

    private static String buildPlaceholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }

    public static String getRelativeTimeDescription(java.util.Date date) {
        if (date == null) return "Unknown time";
        long diff = System.currentTimeMillis() - date.getTime();
        long minutes = diff / 60000;
        long hours = diff / 3600000;
        long days = diff / 86400000;
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;
        if (minutes < 60) return minutes <= 1 ? "1 minute ago" : minutes + " minutes ago";
        if (hours < 24) return hours == 1 ? "1 hour ago" : hours + " hours ago";
        if (days < 7) return days == 1 ? "1 day ago" : days + " days ago";
        if (weeks < 4) return weeks == 1 ? "1 week ago" : weeks + " weeks ago";
        if (months < 12) return (weeks == 4 || months == 1) ? "1 month ago" : months + " months ago";
        return (months == 12 || years == 1) ? "1 year ago" : years + " years ago";
    }

    public static String getRelativeTimeDescription(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) return "Unknown time";
        try {
            java.util.Date date;
            try {
                date = utcDateFormat().parse(timestamp);
            } catch (java.text.ParseException e) {
                java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault());
                fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                date = fmt.parse(timestamp);
            }
            return getRelativeTimeDescription(date);
        } catch (java.text.ParseException e) {
            return "Unknown";
        }
    }

    //#endregion
}
