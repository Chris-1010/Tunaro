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
import com.ca.tunaro.models.PlaylistModel;
import com.ca.tunaro.models.RankingGame;
import com.ca.tunaro.models.SongModel;
import com.ca.tunaro.models.SongRankInfo;
import com.ca.tunaro.models.SongNote;
import com.ca.tunaro.models.SongSnippet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Track;

public class DatabaseHelper extends SQLiteOpenHelper {

    //#region Initialisations

    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "TunaroDB";
    private static final int DATABASE_VERSION = 16;

    // Table names
    private static final String TABLE_ARTISTS = "artists";
    private static final String TABLE_ALBUMS = "albums";
    private static final String TABLE_SONGS = "songs";
    private static final String TABLE_SONG_ARTISTS = "song_artists";
    private static final String TABLE_SONG_ISRC_LINKS = "song_isrc_links";
    private static final String TABLE_PLAYLISTS = "playlists";
    private static final String TABLE_SONG_PLAYLISTS = "song_playlists";
    private static final String TABLE_SONG_NOTES = "song_notes";
    private static final String TABLE_SONG_SNIPPETS = "song_snippets";
    private static final String TABLE_LISTEN_HISTORY = "listen_history";
    private static final String TABLE_SONG_RATINGS = "song_ratings";
    private static final String TABLE_RANKING_GAMES = "ranking_games";

    // Shared columns
    private static final String COLUMN_UUID = "uuid";
    private static final String COLUMN_ID = "id";

    // Artists columns
    private static final String COLUMN_ARTIST_ID = "artist_id";
    private static final String COLUMN_ARTIST_NAME = "name";
    private static final String COLUMN_ARTIST_IMAGE_URL = "image_url";
    private static final String COLUMN_ARTIST_FOLLOWERS = "followers";
    private static final String COLUMN_ARTIST_POPULARITY = "popularity";
    private static final String COLUMN_ARTIST_GENRES = "genres";
    private static final String COLUMN_ARTIST_FETCHED_AT = "fetched_at";

    // Albums columns
    private static final String COLUMN_ALBUM_ID = "album_id";
    private static final String COLUMN_ALBUM_NAME = "name";
    private static final String COLUMN_ALBUM_TYPE = "album_type";
    private static final String COLUMN_RELEASE_DATE = "release_date";
    private static final String COLUMN_COVER_IMAGE_URL = "cover_image_url";

    // Songs columns
    private static final String COLUMN_SONG_NAME = "name";
    private static final String COLUMN_DURATION_MS = "duration_ms";
    private static final String COLUMN_SPOTIFY_URI = "spotify_uri";
    private static final String COLUMN_ISRC = "isrc";
    private static final String COLUMN_POPULARITY = "popularity";
    private static final String COLUMN_IS_PLAYABLE = "is_playable";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_LAST_REFRESHED_AT = "last_refreshed_at";

    // Song artists columns
    private static final String COLUMN_POSITION = "position";

    // Song variants columns

    // Playlists columns
    private static final String COLUMN_PLAYLIST_ID = "playlist_id";
    private static final String COLUMN_PLAYLIST_NAME = "name";
    private static final String COLUMN_DESCRIPTION = "description";
    private static final String COLUMN_IMAGE_URL = "image_url";
    private static final String COLUMN_TRACK_COUNT = "track_count";
    private static final String COLUMN_REMOTE_TRACK_COUNT = "remote_track_count";
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

    // Song ratings columns (Elo across ranking games)
    private static final String COLUMN_RATING = "rating";
    private static final String COLUMN_MATCHES_PLAYED = "matches_played";
    private static final String COLUMN_GAMES_PLAYED = "games_played";
    private static final String COLUMN_UPDATED_AT = "updated_at";

    // Ranking games columns
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_BRACKET_SIZE = "bracket_size";
    private static final String COLUMN_ENTRANT_IDS = "entrant_ids";
    private static final String COLUMN_DECISIONS = "decisions";
    private static final String COLUMN_FINAL_ORDER = "final_order";
    private static final String COLUMN_PLAYLIST_IDS = "playlist_ids";
    private static final String COLUMN_COMPLETED_AT = "completed_at";

    // Ranking game status values
    public static final String GAME_IN_PROGRESS = "in_progress";
    public static final String GAME_COMPLETED = "completed";

    // Starting Elo rating and update sensitivity.
    private static final double DEFAULT_RATING = 1500.0;
    private static final double ELO_K = 32.0;
    // One clear rung between adjacent placements when lifting a song to enforce a
    // game's final order (an even-match win's worth of rating).
    private static final double ELO_RANK_STEP = 16.0;

    //#region Create table SQL

    private static final String CREATE_TABLE_ARTISTS =
            "CREATE TABLE " + TABLE_ARTISTS + "("
                    + COLUMN_ARTIST_ID + " TEXT PRIMARY KEY,"
                    + COLUMN_ARTIST_NAME + " TEXT NOT NULL,"
                    + COLUMN_ARTIST_IMAGE_URL + " TEXT,"
                    + COLUMN_ARTIST_FOLLOWERS + " INTEGER,"
                    + COLUMN_ARTIST_POPULARITY + " INTEGER,"
                    + COLUMN_ARTIST_GENRES + " TEXT,"
                    + COLUMN_ARTIST_FETCHED_AT + " TEXT"
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
                    + COLUMN_SPOTIFY_URI + " TEXT PRIMARY KEY,"
                    + COLUMN_SONG_NAME + " TEXT NOT NULL,"
                    + COLUMN_DURATION_MS + " INTEGER NOT NULL,"
                    + COLUMN_ISRC + " TEXT,"
                    + COLUMN_POPULARITY + " INTEGER DEFAULT 0,"
                    + COLUMN_IS_PLAYABLE + " INTEGER DEFAULT 1,"
                    + COLUMN_ALBUM_ID + " TEXT,"
                    + COLUMN_CREATED_AT + " TEXT NOT NULL,"
                    + COLUMN_LAST_REFRESHED_AT + " TEXT"
                    + ")";

    private static final String CREATE_TABLE_SONG_ARTISTS =
            "CREATE TABLE " + TABLE_SONG_ARTISTS + "("
                    + COLUMN_SPOTIFY_URI + " TEXT NOT NULL,"
                    + COLUMN_ARTIST_ID + " TEXT NOT NULL,"
                    + COLUMN_POSITION + " INTEGER NOT NULL,"
                    + "PRIMARY KEY (" + COLUMN_SPOTIFY_URI + ", " + COLUMN_ARTIST_ID + ")"
                    + ")";

    private static final String CREATE_TABLE_SONG_ISRC_LINKS =
            "CREATE TABLE " + TABLE_SONG_ISRC_LINKS + "("
                    + COLUMN_ISRC + " TEXT NOT NULL,"
                    + COLUMN_SPOTIFY_URI + " TEXT NOT NULL,"
                    + "PRIMARY KEY (" + COLUMN_ISRC + ", " + COLUMN_SPOTIFY_URI + ")"
                    + ")";

    private static final String CREATE_TABLE_PLAYLISTS =
            "CREATE TABLE " + TABLE_PLAYLISTS + "("
                    + COLUMN_PLAYLIST_ID + " TEXT PRIMARY KEY,"
                    + COLUMN_PLAYLIST_NAME + " TEXT NOT NULL,"
                    + COLUMN_DESCRIPTION + " TEXT,"
                    + COLUMN_IMAGE_URL + " TEXT,"
                    + COLUMN_TRACK_COUNT + " INTEGER DEFAULT 0,"
                    + COLUMN_REMOTE_TRACK_COUNT + " INTEGER DEFAULT 0,"
                    + COLUMN_OWNER + " TEXT,"
                    + COLUMN_IS_FAVOURITE + " INTEGER DEFAULT 0,"
                    + COLUMN_IS_ARCHIVED + " INTEGER DEFAULT 0"
                    + ")";

    private static final String CREATE_TABLE_SONG_PLAYLISTS =
            "CREATE TABLE " + TABLE_SONG_PLAYLISTS + "("
                    + COLUMN_SPOTIFY_URI + " TEXT NOT NULL,"
                    + COLUMN_PLAYLIST_ID + " TEXT NOT NULL,"
                    + COLUMN_ADDED_AT + " TEXT,"
                    + COLUMN_REMOVED_AT + " TEXT,"
                    + "PRIMARY KEY (" + COLUMN_SPOTIFY_URI + ", " + COLUMN_PLAYLIST_ID + ")"
                    + ")";

    private static final String CREATE_TABLE_SONG_NOTES =
            "CREATE TABLE " + TABLE_SONG_NOTES + "("
                    + COLUMN_UUID + " TEXT UNIQUE NOT NULL,"
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SPOTIFY_URI + " TEXT NOT NULL,"
                    + COLUMN_NOTE_TYPE + " TEXT NOT NULL,"
                    + COLUMN_CONTENT + " TEXT NOT NULL,"
                    + COLUMN_TIMESTAMP + " TEXT DEFAULT (strftime('%d-%m-%Y %H:%M', 'now', 'localtime'))"
                    + ")";

    private static final String CREATE_TABLE_SONG_SNIPPETS =
            "CREATE TABLE " + TABLE_SONG_SNIPPETS + "("
                    + COLUMN_UUID + " TEXT UNIQUE NOT NULL,"
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SPOTIFY_URI + " TEXT NOT NULL,"
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
                    + COLUMN_SPOTIFY_URI + " TEXT NOT NULL,"
                    + COLUMN_LISTEN_TIMESTAMP + " TEXT NOT NULL"
                    + ")";

    private static final String CREATE_TABLE_SONG_RATINGS =
            "CREATE TABLE " + TABLE_SONG_RATINGS + "("
                    + COLUMN_SPOTIFY_URI + " TEXT PRIMARY KEY,"
                    + COLUMN_RATING + " REAL NOT NULL DEFAULT " + DEFAULT_RATING + ","
                    + COLUMN_MATCHES_PLAYED + " INTEGER NOT NULL DEFAULT 0,"
                    + COLUMN_GAMES_PLAYED + " INTEGER NOT NULL DEFAULT 0,"
                    + COLUMN_UPDATED_AT + " TEXT"
                    + ")";

    private static final String CREATE_TABLE_RANKING_GAMES =
            "CREATE TABLE " + TABLE_RANKING_GAMES + "("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_STATUS + " TEXT NOT NULL,"
                    + COLUMN_BRACKET_SIZE + " INTEGER NOT NULL,"
                    + COLUMN_ENTRANT_IDS + " TEXT NOT NULL,"
                    + COLUMN_DECISIONS + " TEXT,"
                    + COLUMN_FINAL_ORDER + " TEXT,"
                    + COLUMN_PLAYLIST_IDS + " TEXT,"
                    + COLUMN_CREATED_AT + " TEXT,"
                    + COLUMN_UPDATED_AT + " TEXT,"
                    + COLUMN_COMPLETED_AT + " TEXT"
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
        db.execSQL(CREATE_TABLE_SONG_ISRC_LINKS);
        db.execSQL(CREATE_TABLE_PLAYLISTS);
        db.execSQL(CREATE_TABLE_SONG_PLAYLISTS);
        db.execSQL(CREATE_TABLE_SONG_NOTES);
        db.execSQL(CREATE_TABLE_SONG_SNIPPETS);
        db.execSQL(CREATE_TABLE_LISTEN_HISTORY);
        db.execSQL(CREATE_TABLE_SONG_RATINGS);
        db.execSQL(CREATE_TABLE_RANKING_GAMES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 11) {
            // v11→v12: Migrate primary key from composite name|artist|duration to spotify_uri.
            // Only runs from v11 — earlier versions lack the spotify_uri column on songs and fall
            // through to the drop-and-recreate path below.
            //
            // Ordering is critical: listen_history rows use the old composite song_id, so the
            // songs table (which maps song_id → spotify_uri) must stay available during that step.
            // All child tables are kept as _old until everything needed is in place, then swapped.
            //
            // Steps:
            //  1. Merge duplicate songs that share a spotify_uri into the oldest row
            //  2. Rename all old tables to _old
            //  3. Create all new tables
            //  4. Copy data, resolving song_id → spotify_uri via songs_old where needed
            //  5. Drop _old tables, create song_isrc_links, populate from songs data

            // Step 1 — merge duplicate songs sharing the same spotify_uri
            android.database.Cursor dupes = db.rawQuery(
                "SELECT spotify_uri FROM songs GROUP BY spotify_uri HAVING COUNT(*) > 1", null);
            java.util.List<String> dupeUris = new java.util.ArrayList<>();
            try { while (dupes.moveToNext()) dupeUris.add(dupes.getString(0)); } finally { dupes.close(); }

            for (String uri : dupeUris) {
                android.database.Cursor wc = db.rawQuery(
                    "SELECT song_id FROM songs WHERE spotify_uri=? ORDER BY created_at ASC, rowid ASC LIMIT 1",
                    new String[]{uri});
                String winner = null;
                try { if (wc.moveToFirst()) winner = wc.getString(0); } finally { wc.close(); }
                if (winner == null) continue;

                android.database.Cursor lc = db.rawQuery(
                    "SELECT song_id FROM songs WHERE spotify_uri=? AND song_id!=?",
                    new String[]{uri, winner});
                java.util.List<String> losers = new java.util.ArrayList<>();
                try { while (lc.moveToNext()) losers.add(lc.getString(0)); } finally { lc.close(); }

                for (String loser : losers) {
                    db.execSQL("UPDATE song_notes SET song_id=? WHERE song_id=?", new String[]{winner, loser});
                    db.execSQL("UPDATE song_snippets SET song_id=? WHERE song_id=?", new String[]{winner, loser});
                    db.execSQL("UPDATE listen_history SET song_id=? WHERE song_id=?", new String[]{winner, loser});
                    db.execSQL(
                        "UPDATE song_playlists SET song_id=? WHERE song_id=? " +
                        "AND NOT EXISTS (SELECT 1 FROM song_playlists sp2 WHERE sp2.song_id=? AND sp2.playlist_id=song_playlists.playlist_id)",
                        new String[]{winner, loser, winner});
                    db.execSQL("DELETE FROM song_playlists WHERE song_id=?", new String[]{loser});
                    db.execSQL("DELETE FROM song_artists WHERE song_id=?", new String[]{loser});
                    db.execSQL("DELETE FROM songs WHERE song_id=?", new String[]{loser});
                }
            }
            Log.i(TAG, "DB v12: merged " + dupeUris.size() + " duplicate URI groups");

            // Step 2 — rename all tables to _old (keep them available for data copy)
            db.execSQL("ALTER TABLE songs RENAME TO songs_old");
            db.execSQL("ALTER TABLE song_artists RENAME TO song_artists_old");
            db.execSQL("ALTER TABLE song_playlists RENAME TO song_playlists_old");
            db.execSQL("ALTER TABLE song_notes RENAME TO song_notes_old");
            db.execSQL("ALTER TABLE song_snippets RENAME TO song_snippets_old");
            db.execSQL("ALTER TABLE listen_history RENAME TO listen_history_old");

            // Step 3 — create new tables
            db.execSQL(CREATE_TABLE_SONGS);
            db.execSQL(CREATE_TABLE_SONG_ARTISTS);
            db.execSQL(CREATE_TABLE_SONG_PLAYLISTS);
            db.execSQL(CREATE_TABLE_SONG_NOTES);
            db.execSQL(CREATE_TABLE_SONG_SNIPPETS);
            db.execSQL(CREATE_TABLE_LISTEN_HISTORY);
            db.execSQL(CREATE_TABLE_SONG_ISRC_LINKS);

            // Step 4 — copy data

            // songs: spotify_uri becomes PK, drop song_id and user_canonical_variant_id
            db.execSQL(
                "INSERT INTO songs (spotify_uri, name, duration_ms, isrc, popularity, is_playable, album_id, created_at, last_refreshed_at) " +
                "SELECT spotify_uri, name, duration_ms, isrc, popularity, is_playable, album_id, created_at, last_refreshed_at FROM songs_old");

            // song_artists: song_id → spotify_uri via songs_old
            db.execSQL(
                "INSERT OR IGNORE INTO song_artists (spotify_uri, artist_id, position) " +
                "SELECT so.spotify_uri, sa.artist_id, sa.position " +
                "FROM song_artists_old sa JOIN songs_old so ON so.song_id = sa.song_id");

            // song_playlists: song_id → spotify_uri via songs_old
            db.execSQL(
                "INSERT OR IGNORE INTO song_playlists (spotify_uri, playlist_id, added_at, removed_at) " +
                "SELECT so.spotify_uri, sp.playlist_id, sp.added_at, sp.removed_at " +
                "FROM song_playlists_old sp JOIN songs_old so ON so.song_id = sp.song_id");

            // song_notes: song_id → spotify_uri via songs_old; keep URI-format placeholders as-is
            db.execSQL(
                "INSERT OR IGNORE INTO song_notes (uuid, id, spotify_uri, note_type, content, timestamp) " +
                "SELECT sn.uuid, sn.id, so.spotify_uri, sn.note_type, sn.content, sn.timestamp " +
                "FROM song_notes_old sn JOIN songs_old so ON so.song_id = sn.song_id");
            db.execSQL(
                "INSERT OR IGNORE INTO song_notes (uuid, id, spotify_uri, note_type, content, timestamp) " +
                "SELECT sn.uuid, sn.id, sn.song_id, sn.note_type, sn.content, sn.timestamp " +
                "FROM song_notes_old sn WHERE sn.song_id LIKE 'spotify:track:%' " +
                "AND NOT EXISTS (SELECT 1 FROM song_notes WHERE uuid = sn.uuid)");

            // song_snippets: same pattern
            db.execSQL(
                "INSERT OR IGNORE INTO song_snippets (uuid, id, spotify_uri, snippet_no, title, start_time, end_time, include_in_rankings) " +
                "SELECT ss.uuid, ss.id, so.spotify_uri, ss.snippet_no, ss.title, ss.start_time, ss.end_time, ss.include_in_rankings " +
                "FROM song_snippets_old ss JOIN songs_old so ON so.song_id = ss.song_id");
            db.execSQL(
                "INSERT OR IGNORE INTO song_snippets (uuid, id, spotify_uri, snippet_no, title, start_time, end_time, include_in_rankings) " +
                "SELECT ss.uuid, ss.id, ss.song_id, ss.snippet_no, ss.title, ss.start_time, ss.end_time, ss.include_in_rankings " +
                "FROM song_snippets_old ss WHERE ss.song_id LIKE 'spotify:track:%' " +
                "AND NOT EXISTS (SELECT 1 FROM song_snippets WHERE uuid = ss.uuid)");

            // listen_history: song_id is either the old composite key or already spotify:track: format
            // Composite key rows: join via songs_old to get the spotify_uri
            db.execSQL(
                "INSERT OR IGNORE INTO listen_history (uuid, id, spotify_uri, listen_timestamp) " +
                "SELECT lh.uuid, lh.id, so.spotify_uri, lh.listen_timestamp " +
                "FROM listen_history_old lh JOIN songs_old so ON so.song_id = lh.song_id " +
                "WHERE lh.song_id NOT LIKE 'spotify:track:%'");
            // URI-format rows: pass through directly
            db.execSQL(
                "INSERT OR IGNORE INTO listen_history (uuid, id, spotify_uri, listen_timestamp) " +
                "SELECT lh.uuid, lh.id, lh.song_id, lh.listen_timestamp " +
                "FROM listen_history_old lh WHERE lh.song_id LIKE 'spotify:track:%'");

            // song_isrc_links: populate from songs that have an ISRC
            db.execSQL(
                "INSERT OR IGNORE INTO song_isrc_links (isrc, spotify_uri) " +
                "SELECT isrc, spotify_uri FROM songs WHERE isrc IS NOT NULL AND isrc != ''");

            // Step 5 — drop old tables and legacy variant tables
            db.execSQL("DROP TABLE songs_old");
            db.execSQL("DROP TABLE song_artists_old");
            db.execSQL("DROP TABLE song_playlists_old");
            db.execSQL("DROP TABLE song_notes_old");
            db.execSQL("DROP TABLE song_snippets_old");
            db.execSQL("DROP TABLE listen_history_old");
            db.execSQL("DROP TABLE IF EXISTS song_variants");
            db.execSQL("DROP TABLE IF EXISTS song_variant_artists");
            db.execSQL("DROP TABLE IF EXISTS favourite_playlists");
            db.execSQL("DROP TABLE IF EXISTS archived_playlists");

            Log.i(TAG, "DB v12 migration complete");
            oldVersion = 12;
        }

        if (oldVersion == 12) {
            db.execSQL("ALTER TABLE " + TABLE_PLAYLISTS + " ADD COLUMN " + COLUMN_REMOTE_TRACK_COUNT + " INTEGER DEFAULT 0");
            Log.i(TAG, "DB v13 migration complete");
            oldVersion = 13;
        }

        if (oldVersion == 13) {
            // v13→v14: cache ArtistView header stats on the artists table so a revisit can render
            // instantly while a fresh getArtist call refreshes in the background. fetched_at marks
            // when the stats were last written so first-ever visits can be told apart (NULL) and shimmer.
            db.execSQL("ALTER TABLE " + TABLE_ARTISTS + " ADD COLUMN " + COLUMN_ARTIST_IMAGE_URL + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_ARTISTS + " ADD COLUMN " + COLUMN_ARTIST_FOLLOWERS + " INTEGER");
            db.execSQL("ALTER TABLE " + TABLE_ARTISTS + " ADD COLUMN " + COLUMN_ARTIST_POPULARITY + " INTEGER");
            db.execSQL("ALTER TABLE " + TABLE_ARTISTS + " ADD COLUMN " + COLUMN_ARTIST_GENRES + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_ARTISTS + " ADD COLUMN " + COLUMN_ARTIST_FETCHED_AT + " TEXT");
            Log.i(TAG, "DB v14 migration complete");
            oldVersion = 14;
        }

        if (oldVersion == 14) {
            // v14→v15: rankings gain a persistent Elo rating per song and a saved
            // game table (one resumable game + completed history). Purely additive,
            // so existing data is untouched.
            db.execSQL(CREATE_TABLE_SONG_RATINGS);
            db.execSQL(CREATE_TABLE_RANKING_GAMES);
            Log.i(TAG, "DB v15 migration complete");
            oldVersion = 15;
        }

        if (oldVersion == 15) {
            // v15→v16: remember which playlists a ranking game drew its entrants
            // from. Additive, so existing saved games keep their data (playlist_ids
            // simply stays null for them).
            db.execSQL("ALTER TABLE " + TABLE_RANKING_GAMES + " ADD COLUMN " + COLUMN_PLAYLIST_IDS + " TEXT");
            Log.i(TAG, "DB v16 migration complete");
            oldVersion = 16;
        }

        if (oldVersion < newVersion) {
            db.execSQL("DROP TABLE IF EXISTS favourite_playlists");
            db.execSQL("DROP TABLE IF EXISTS archived_playlists");
            db.execSQL("DROP TABLE IF EXISTS song_variant_artists");
            db.execSQL("DROP TABLE IF EXISTS song_variants");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ARTISTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ALBUMS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_ARTISTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_ISRC_LINKS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_PLAYLISTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLISTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONGS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_NOTES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_SNIPPETS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_LISTEN_HISTORY);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_RATINGS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_RANKING_GAMES);
            onCreate(db);
        }
    }

    //#endregion

    //#region ======== ARTISTS ========

    public void upsertArtist(String artistId, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        // Insert the id/name pair without clobbering any cached stats an earlier ArtistView
        // visit may have written. CONFLICT_REPLACE would wipe followers/popularity/etc here.
        ContentValues values = new ContentValues();
        values.put(COLUMN_ARTIST_ID, artistId);
        values.put(COLUMN_ARTIST_NAME, name);
        long result = db.insertWithOnConflict(TABLE_ARTISTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (result == -1) {
            ContentValues update = new ContentValues();
            update.put(COLUMN_ARTIST_NAME, name);
            db.update(TABLE_ARTISTS, update, COLUMN_ARTIST_ID + " = ?", new String[]{artistId});
        }
        db.close();
    }

    /** Cached ArtistView header stats. {@code fetchedAt} is null only on a first-ever visit. */
    public static class ArtistStats {
        public final String artistId;
        public final String name;
        public final String imageUrl;
        public final Integer followers;
        public final Integer popularity;
        public final List<String> genres;
        public final String fetchedAt;

        public ArtistStats(String artistId, String name, String imageUrl, Integer followers,
                           Integer popularity, List<String> genres, String fetchedAt) {
            this.artistId = artistId;
            this.name = name;
            this.imageUrl = imageUrl;
            this.followers = followers;
            this.popularity = popularity;
            this.genres = genres;
            this.fetchedAt = fetchedAt;
        }
    }

    // Persist the header stats fetched from getArtist so a later revisit renders instantly.
    public void upsertArtistStats(String artistId, String name, String imageUrl, Integer followers,
                                  Integer popularity, List<String> genres) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ARTIST_ID, artistId);
        if (name != null) values.put(COLUMN_ARTIST_NAME, name);
        values.put(COLUMN_ARTIST_IMAGE_URL, imageUrl);
        values.put(COLUMN_ARTIST_FOLLOWERS, followers);
        values.put(COLUMN_ARTIST_POPULARITY, popularity);
        values.put(COLUMN_ARTIST_GENRES, genres != null ? String.join(",", genres) : null);
        values.put(COLUMN_ARTIST_FETCHED_AT, utcNow());

        long result = db.insertWithOnConflict(TABLE_ARTISTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (result == -1) {
            db.update(TABLE_ARTISTS, values, COLUMN_ARTIST_ID + " = ?", new String[]{artistId});
        }
        db.close();
    }

    // Returns cached header stats, or null if the artist row doesn't exist yet.
    public ArtistStats getArtistStats(String artistId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_ARTIST_ID + ", " + COLUMN_ARTIST_NAME + ", " + COLUMN_ARTIST_IMAGE_URL +
                        ", " + COLUMN_ARTIST_FOLLOWERS + ", " + COLUMN_ARTIST_POPULARITY +
                        ", " + COLUMN_ARTIST_GENRES + ", " + COLUMN_ARTIST_FETCHED_AT +
                        " FROM " + TABLE_ARTISTS + " WHERE " + COLUMN_ARTIST_ID + " = ?",
                new String[]{artistId});
        ArtistStats stats = null;
        if (cursor.moveToFirst()) {
            String genresRaw = cursor.getString(5);
            List<String> genres = new ArrayList<>();
            if (genresRaw != null && !genresRaw.isEmpty()) {
                for (String g : genresRaw.split(",")) {
                    if (!g.isEmpty()) genres.add(g);
                }
            }
            stats = new ArtistStats(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.isNull(3) ? null : cursor.getInt(3),
                    cursor.isNull(4) ? null : cursor.getInt(4),
                    genres,
                    cursor.getString(6));
        }
        cursor.close();
        db.close();
        return stats;
    }

    /**
     * The artist's locally-known songs that are actively in a favourite OR archived playlist.
     * Backs the ArtistView "added songs" sheet. Deduplicated by URI and ordered by name.
     */
    public List<SongModel> getArtistSongsInFavOrArchivedPlaylists(String artistId) {
        List<SongModel> songs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT s." + COLUMN_SPOTIFY_URI + ", s." + COLUMN_SONG_NAME +
                        ", s." + COLUMN_DURATION_MS + ", s." + COLUMN_SPOTIFY_URI +
                        ", s." + COLUMN_POPULARITY + ", a." + COLUMN_COVER_IMAGE_URL +
                        ", a." + COLUMN_ALBUM_NAME + ", a." + COLUMN_RELEASE_DATE +
                        ", ar." + COLUMN_ARTIST_NAME +
                        " FROM " + TABLE_SONG_ARTISTS + " tgt" +
                        " JOIN " + TABLE_SONGS + " s ON s." + COLUMN_SPOTIFY_URI + " = tgt." + COLUMN_SPOTIFY_URI +
                        " JOIN " + TABLE_SONG_PLAYLISTS + " sp ON sp." + COLUMN_SPOTIFY_URI + " = s." + COLUMN_SPOTIFY_URI +
                        " AND sp." + COLUMN_REMOVED_AT + " IS NULL" +
                        " JOIN " + TABLE_PLAYLISTS + " p ON p." + COLUMN_PLAYLIST_ID + " = sp." + COLUMN_PLAYLIST_ID +
                        " AND (p." + COLUMN_IS_FAVOURITE + " = 1 OR p." + COLUMN_IS_ARCHIVED + " = 1)" +
                        " LEFT JOIN " + TABLE_ALBUMS + " a ON s." + COLUMN_ALBUM_ID + " = a." + COLUMN_ALBUM_ID +
                        " LEFT JOIN " + TABLE_SONG_ARTISTS + " sa ON s." + COLUMN_SPOTIFY_URI + " = sa." + COLUMN_SPOTIFY_URI + " AND sa." + COLUMN_POSITION + " = 0" +
                        " LEFT JOIN " + TABLE_ARTISTS + " ar ON sa." + COLUMN_ARTIST_ID + " = ar." + COLUMN_ARTIST_ID +
                        " WHERE tgt." + COLUMN_ARTIST_ID + " = ?" +
                        " ORDER BY s." + COLUMN_SONG_NAME + " ASC",
                new String[]{artistId});
        if (cursor.moveToFirst()) {
            do { songs.add(leanSongFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return songs;
    }

    /** Full song rows for the artist's songs that are actively in at least one of the user's
     *  playlists (song_playlists row with no removed_at). Backs the Songs tab's added-only filter
     *  so added songs show even when they aren't among the loaded top tracks / discography.
     *  Merely viewing a song records it in the DB but does not add it to a playlist, so such songs
     *  are correctly excluded here. */
    public List<SongModel> getArtistLocalSongs(String artistId) {
        List<SongModel> songs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT s." + COLUMN_SPOTIFY_URI + ", s." + COLUMN_SONG_NAME +
                        ", s." + COLUMN_DURATION_MS + ", s." + COLUMN_SPOTIFY_URI +
                        ", s." + COLUMN_POPULARITY + ", a." + COLUMN_COVER_IMAGE_URL +
                        ", a." + COLUMN_ALBUM_NAME + ", a." + COLUMN_RELEASE_DATE +
                        ", ar." + COLUMN_ARTIST_NAME +
                        " FROM " + TABLE_SONG_ARTISTS + " tgt" +
                        " JOIN " + TABLE_SONGS + " s ON s." + COLUMN_SPOTIFY_URI + " = tgt." + COLUMN_SPOTIFY_URI +
                        " JOIN " + TABLE_SONG_PLAYLISTS + " sp ON sp." + COLUMN_SPOTIFY_URI + " = s." + COLUMN_SPOTIFY_URI +
                        " AND sp." + COLUMN_REMOVED_AT + " IS NULL" +
                        " LEFT JOIN " + TABLE_ALBUMS + " a ON s." + COLUMN_ALBUM_ID + " = a." + COLUMN_ALBUM_ID +
                        " LEFT JOIN " + TABLE_SONG_ARTISTS + " sa ON s." + COLUMN_SPOTIFY_URI + " = sa." + COLUMN_SPOTIFY_URI + " AND sa." + COLUMN_POSITION + " = 0" +
                        " LEFT JOIN " + TABLE_ARTISTS + " ar ON sa." + COLUMN_ARTIST_ID + " = ar." + COLUMN_ARTIST_ID +
                        " WHERE tgt." + COLUMN_ARTIST_ID + " = ?" +
                        " ORDER BY s." + COLUMN_SONG_NAME + " ASC",
                new String[]{artistId});
        if (cursor.moveToFirst()) {
            do { songs.add(leanSongFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return songs;
    }

    /** URIs of the artist's songs that are actively in at least one of the user's playlists
     *  (song_playlists row with no removed_at). Used to flag "added" songs in the discography
     *  Songs tab. A song merely viewed (and thus recorded in the DB) is not "added" and is excluded. */
    public java.util.Set<String> getArtistLocalSongUris(String artistId) {
        java.util.Set<String> uris = new java.util.HashSet<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT sa." + COLUMN_SPOTIFY_URI +
                        " FROM " + TABLE_SONG_ARTISTS + " sa" +
                        " JOIN " + TABLE_SONG_PLAYLISTS + " sp ON sp." + COLUMN_SPOTIFY_URI + " = sa." + COLUMN_SPOTIFY_URI +
                        " AND sp." + COLUMN_REMOVED_AT + " IS NULL" +
                        " WHERE sa." + COLUMN_ARTIST_ID + " = ?",
                new String[]{artistId});
        if (cursor.moveToFirst()) {
            do { uris.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return uris;
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
        values.put(COLUMN_SPOTIFY_URI, song.getId());
        values.put(COLUMN_SONG_NAME, song.getName());
        values.put(COLUMN_DURATION_MS, song.getDuration());
        values.put(COLUMN_ISRC, song.getIsrc());
        values.put(COLUMN_ALBUM_ID, song.getAlbumId());
        values.put(COLUMN_IS_PLAYABLE, song.isPlayable() ? 1 : 0);
        values.put(COLUMN_CREATED_AT, now);

        long result = db.insertWithOnConflict(TABLE_SONGS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (result == -1) {
            ContentValues update = new ContentValues();
            update.put(COLUMN_ISRC, song.getIsrc());
            update.put(COLUMN_ALBUM_ID, song.getAlbumId());
            update.put(COLUMN_IS_PLAYABLE, song.isPlayable() ? 1 : 0);
            db.update(TABLE_SONGS, update, COLUMN_SPOTIFY_URI + " = ?", new String[]{song.getId()});
        }

        db.close();

        // Upsert ISRC link if present
        if (song.getIsrc() != null && !song.getIsrc().isEmpty()) {
            upsertIsrcLink(song.getIsrc(), song.getId());
        }
    }

    // Persists a Web API track together with its album row and artist links.
    // Every writer with full API data must use this rather than upsertSong alone:
    // upsertSong only stores the album_id column, and SongView relies on the album
    // join and artist links being present to tell complete songs from stubs.
    public void upsertFullTrack(Track track, SongModel songModel) {
        AlbumSimplified trackAlbum = track.getAlbum();
        if (trackAlbum != null && trackAlbum.getId() != null) {
            Image[] images = trackAlbum.getImages();
            String imageUrl = images != null && images.length > 0 ? images[0].getUrl() : null;
            upsertAlbum(
                    trackAlbum.getId(),
                    trackAlbum.getName(),
                    trackAlbum.getAlbumType() != null ? trackAlbum.getAlbumType().getType() : null,
                    trackAlbum.getReleaseDate(),
                    imageUrl
            );
        }

        upsertSong(songModel);

        ArtistSimplified[] artists = track.getArtists();
        if (artists != null) {
            for (int i = 0; i < artists.length; i++) {
                upsertArtist(artists[i].getId(), artists[i].getName());
                upsertSongArtistLink(songModel.getId(), artists[i].getId(), i);
            }
        }
    }

    public void upsertSongArtistLink(String spotifyUri, String artistId, int position) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SPOTIFY_URI, spotifyUri);
        values.put(COLUMN_ARTIST_ID, artistId);
        values.put(COLUMN_POSITION, position);
        db.insertWithOnConflict(TABLE_SONG_ARTISTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public void upsertIsrcLink(String isrc, String spotifyUri) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ISRC, isrc);
        values.put(COLUMN_SPOTIFY_URI, spotifyUri);
        db.insertWithOnConflict(TABLE_SONG_ISRC_LINKS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();
    }

    public void updateSongPopularity(String spotifyUri, int popularity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_POPULARITY, popularity);
        values.put(COLUMN_LAST_REFRESHED_AT, utcNow());
        db.update(TABLE_SONGS, values, COLUMN_SPOTIFY_URI + " = ?", new String[]{spotifyUri});
        db.close();
    }

    public void updateSongIsPlayable(String spotifyUri, boolean isPlayable) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_PLAYABLE, isPlayable ? 1 : 0);
        values.put(COLUMN_LAST_REFRESHED_AT, utcNow());
        db.update(TABLE_SONGS, values, COLUMN_SPOTIFY_URI + " = ?", new String[]{spotifyUri});
        db.close();
    }

    public void refreshSong(String spotifyUri, int popularity, boolean isPlayable) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_POPULARITY, popularity);
        values.put(COLUMN_IS_PLAYABLE, isPlayable ? 1 : 0);
        values.put(COLUMN_LAST_REFRESHED_AT, utcNow());
        db.update(TABLE_SONGS, values, COLUMN_SPOTIFY_URI + " = ?", new String[]{spotifyUri});
        db.close();
    }

    // Returns spotifyUri → spotifyUri for songs not refreshed in the last 7 days
    // (both columns are now the same — kept as Map for API compatibility with SongRefreshService)
    public Map<String, String> getSongsNeedingRefresh() {
        Map<String, String> result = new HashMap<>();
        String sevenDaysAgo = utcDaysAgo(7);
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SPOTIFY_URI + " FROM " + TABLE_SONGS +
                        " WHERE " + COLUMN_LAST_REFRESHED_AT + " IS NULL OR " +
                        COLUMN_LAST_REFRESHED_AT + " < ?",
                new String[]{sevenDaysAgo});
        if (cursor.moveToFirst()) {
            do {
                String uri = cursor.getString(0);
                result.put(uri, uri);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return result;
    }

    // Lean SongModel — for list views (PlaylistView, LibraryActivity)
    public SongModel getLeanSong(String spotifyUri) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT s." + COLUMN_SPOTIFY_URI + ", s." + COLUMN_SONG_NAME +
                        ", s." + COLUMN_DURATION_MS + ", s." + COLUMN_SPOTIFY_URI +
                        ", s." + COLUMN_POPULARITY + ", a." + COLUMN_COVER_IMAGE_URL +
                        ", a." + COLUMN_ALBUM_NAME + ", a." + COLUMN_RELEASE_DATE +
                        ", ar." + COLUMN_ARTIST_NAME +
                        " FROM " + TABLE_SONGS + " s" +
                        " LEFT JOIN " + TABLE_ALBUMS + " a ON s." + COLUMN_ALBUM_ID + " = a." + COLUMN_ALBUM_ID +
                        " LEFT JOIN " + TABLE_SONG_ARTISTS + " sa ON s." + COLUMN_SPOTIFY_URI + " = sa." + COLUMN_SPOTIFY_URI + " AND sa." + COLUMN_POSITION + " = 0" +
                        " LEFT JOIN " + TABLE_ARTISTS + " ar ON sa." + COLUMN_ARTIST_ID + " = ar." + COLUMN_ARTIST_ID +
                        " WHERE s." + COLUMN_SPOTIFY_URI + " = ?",
                new String[]{spotifyUri});

        SongModel song = null;
        if (cursor.moveToFirst()) {
            song = leanSongFromCursor(cursor);
        }
        cursor.close();
        db.close();
        return song;
    }

    // Full SongModel — for SongView
    public SongModel getFullSong(String spotifyUri) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT s.*, a." + COLUMN_ALBUM_NAME + " AS album_name, a." + COLUMN_ALBUM_TYPE +
                        ", a." + COLUMN_RELEASE_DATE + ", a." + COLUMN_COVER_IMAGE_URL + " AS album_cover" +
                        " FROM " + TABLE_SONGS + " s" +
                        " LEFT JOIN " + TABLE_ALBUMS + " a ON s." + COLUMN_ALBUM_ID + " = a." + COLUMN_ALBUM_ID +
                        " WHERE s." + COLUMN_SPOTIFY_URI + " = ?",
                new String[]{spotifyUri});

        SongModel song = null;
        if (cursor.moveToFirst()) {
            String uri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SPOTIFY_URI));
            String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_NAME));
            int duration = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DURATION_MS));
            String isrc = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ISRC));
            int popularity = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_POPULARITY));
            boolean isPlayable = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_PLAYABLE)) == 1;
            String albumId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ALBUM_ID));
            String createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
            String albumName = cursor.getString(cursor.getColumnIndexOrThrow("album_name"));
            String albumType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ALBUM_TYPE));
            String releaseDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RELEASE_DATE));
            String coverImageUrl = cursor.getString(cursor.getColumnIndexOrThrow("album_cover"));

            List<Artist> artistList = getSongArtists(spotifyUri);
            String primaryArtist = !artistList.isEmpty() ? artistList.get(0).getName() : null;
            String[] artistNames = new String[artistList.size()];
            for (int i = 0; i < artistList.size(); i++) artistNames[i] = artistList.get(i).getName();

            song = new SongModel(uri, name, primaryArtist, artistNames, duration, uri, coverImageUrl,
                    popularity, albumId, albumName, albumType, releaseDate, isrc, isPlayable, createdAt);
        }
        cursor.close();
        db.close();

        return song;
    }

    public boolean songExists(String spotifyUri) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT 1 FROM " + TABLE_SONGS + " WHERE " + COLUMN_SPOTIFY_URI + " = ?",
                new String[]{spotifyUri});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        db.close();
        return exists;
    }

    private SongModel leanSongFromCursor(Cursor cursor) {
        String uri = cursor.getString(0);
        String name = cursor.getString(1);
        int duration = cursor.getInt(2);
        // column 3 is also spotify_uri (same value, selected twice for compatibility)
        int popularity = cursor.getInt(4);
        String coverImageUrl = cursor.getString(5);
        String albumName = cursor.getString(6);
        String releaseDate = cursor.getString(7);
        String primaryArtist = cursor.getString(8);
        return new SongModel(uri, name, primaryArtist, duration, uri, coverImageUrl, popularity, albumName, releaseDate);
    }

    //#endregion

    //#region ======== SONG ARTISTS ========

    public List<Artist> getSongArtists(String spotifyUri) {
        List<Artist> artists = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT ar." + COLUMN_ARTIST_ID + ", ar." + COLUMN_ARTIST_NAME +
                        " FROM " + TABLE_SONG_ARTISTS + " sa" +
                        " JOIN " + TABLE_ARTISTS + " ar ON sa." + COLUMN_ARTIST_ID + " = ar." + COLUMN_ARTIST_ID +
                        " WHERE sa." + COLUMN_SPOTIFY_URI + " = ?" +
                        " ORDER BY sa." + COLUMN_POSITION + " ASC",
                new String[]{spotifyUri});

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

    //#region ======== SONG ISRC LINKS ========

    // Returns all spotify URIs that share the same ISRC as the given URI (i.e. same recording, different releases)
    public List<String> getIsrcLinkedUris(String spotifyUri) {
        List<String> uris = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT sil2." + COLUMN_SPOTIFY_URI +
                        " FROM " + TABLE_SONG_ISRC_LINKS + " sil1" +
                        " JOIN " + TABLE_SONG_ISRC_LINKS + " sil2 ON sil1." + COLUMN_ISRC + " = sil2." + COLUMN_ISRC +
                        " WHERE sil1." + COLUMN_SPOTIFY_URI + " = ? AND sil2." + COLUMN_SPOTIFY_URI + " != ?",
                new String[]{spotifyUri, spotifyUri});
        if (cursor.moveToFirst()) {
            do { uris.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return uris;
    }

    // Returns the max popularity across all variant URIs (used to show the best known value)
    public int getMaxPopularityForUris(List<String> uris) {
        if (uris == null || uris.isEmpty()) return 0;
        String placeholders = makePlaceholders(uris.size());
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT MAX(" + COLUMN_POPULARITY + ") FROM " + TABLE_SONGS +
                        " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + placeholders + ")",
                uris.toArray(new String[0]));
        int max = 0;
        if (cursor.moveToFirst()) max = cursor.getInt(0);
        cursor.close();
        db.close();
        return max;
    }

    public List<String> getListenHistoryForUris(List<String> uris) {
        List<String> timestamps = new ArrayList<>();
        if (uris == null || uris.isEmpty()) return timestamps;
        String placeholders = makePlaceholders(uris.size());
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_LISTEN_TIMESTAMP + " FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + placeholders + ")" +
                        " ORDER BY " + COLUMN_LISTEN_TIMESTAMP + " ASC",
                uris.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do { timestamps.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return timestamps;
    }

    public List<SongNote> getSongNotesForUris(List<String> uris) {
        List<SongNote> notes = new ArrayList<>();
        if (uris == null || uris.isEmpty()) return notes;
        String placeholders = makePlaceholders(uris.size());
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_SONG_NOTES +
                        " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + placeholders + ")" +
                        " ORDER BY " + COLUMN_TIMESTAMP + " ASC",
                uris.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do { notes.add(noteFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return notes;
    }

    public List<SongSnippet> getSongSnippetsForUris(List<String> uris) {
        List<SongSnippet> snippets = new ArrayList<>();
        if (uris == null || uris.isEmpty()) return snippets;
        String placeholders = makePlaceholders(uris.size());
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_SONG_SNIPPETS +
                        " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + placeholders + ")" +
                        " ORDER BY " + COLUMN_SNIPPET_NO + " ASC",
                uris.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do { snippets.add(snippetFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return snippets;
    }

    public List<PlaylistLink> getPlaylistsForUris(List<String> uris) {
        List<PlaylistLink> result = new ArrayList<>();
        if (uris == null || uris.isEmpty()) return result;
        String placeholders = makePlaceholders(uris.size());
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT sp." + COLUMN_PLAYLIST_ID + ", p." + COLUMN_PLAYLIST_NAME +
                        ", p." + COLUMN_IMAGE_URL + ", sp." + COLUMN_REMOVED_AT +
                        " FROM " + TABLE_SONG_PLAYLISTS + " sp" +
                        " JOIN " + TABLE_PLAYLISTS + " p ON sp." + COLUMN_PLAYLIST_ID + " = p." + COLUMN_PLAYLIST_ID +
                        " WHERE sp." + COLUMN_SPOTIFY_URI + " IN (" + placeholders + ")" +
                        " ORDER BY sp." + COLUMN_REMOVED_AT + " IS NOT NULL ASC, p." + COLUMN_PLAYLIST_NAME + " ASC",
                uris.toArray(new String[0]));
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

    private static String makePlaceholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
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
        values.put(COLUMN_REMOTE_TRACK_COUNT, trackCount);
        values.put(COLUMN_OWNER, owner);
        // track_count is managed by updatePlaylistTrackCount after scanning; remote_track_count always reflects latest Spotify total
        long result = db.insertWithOnConflict(TABLE_PLAYLISTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (result == -1) {
            ContentValues update = new ContentValues();
            update.put(COLUMN_PLAYLIST_NAME, name);
            update.put(COLUMN_DESCRIPTION, description);
            update.put(COLUMN_IMAGE_URL, imageUrl);
            update.put(COLUMN_REMOTE_TRACK_COUNT, trackCount);
            update.put(COLUMN_OWNER, owner);
            db.update(TABLE_PLAYLISTS, update, COLUMN_PLAYLIST_ID + " = ?", new String[]{playlistId});
        }
        db.close();
    }

    public void updatePlaylistTrackCount(String playlistId, int trackCount) {
        SQLiteDatabase db = this.getWritableDatabase();
        // Read the current remote_track_count to stamp track_count to match it.
        // This way the skip check (track_count == remote_track_count) will pass on the next
        // launch even when the playlist has duplicates (remote=1143, distinct=1128).
        int remoteCount = trackCount;
        Cursor c = db.rawQuery(
                "SELECT " + COLUMN_REMOTE_TRACK_COUNT + " FROM " + TABLE_PLAYLISTS +
                " WHERE " + COLUMN_PLAYLIST_ID + " = ?", new String[]{playlistId});
        if (c.moveToFirst()) remoteCount = c.getInt(0);
        c.close();

        ContentValues values = new ContentValues();
        values.put(COLUMN_TRACK_COUNT, remoteCount);
        db.update(TABLE_PLAYLISTS, values, COLUMN_PLAYLIST_ID + " = ?", new String[]{playlistId});
        db.close();
    }

    public void upsertPlaylistStub(String playlistId, boolean isArchived, boolean isFavourite) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PLAYLIST_ID, playlistId);
        values.put(COLUMN_PLAYLIST_NAME, "");
        values.put(COLUMN_IS_ARCHIVED, isArchived ? 1 : 0);
        values.put(COLUMN_IS_FAVOURITE, isFavourite ? 1 : 0);
        long result = db.insertWithOnConflict(TABLE_PLAYLISTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (result == -1) {
            // Row exists — just update the flag, don't touch name/image/etc
            ContentValues update = new ContentValues();
            if (isArchived)  update.put(COLUMN_IS_ARCHIVED, 1);
            if (isFavourite) update.put(COLUMN_IS_FAVOURITE, 1);
            if (!update.isEmpty()) {
                db.update(TABLE_PLAYLISTS, update, COLUMN_PLAYLIST_ID + " = ?", new String[]{playlistId});
            }
        }
        db.close();
    }

    public PlaylistModel getPlaylistById(String playlistId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_PLAYLIST_NAME + ", " + COLUMN_IMAGE_URL + ", " + COLUMN_TRACK_COUNT +
                        ", " + COLUMN_IS_FAVOURITE +
                        " FROM " + TABLE_PLAYLISTS +
                        " WHERE " + COLUMN_PLAYLIST_ID + " = ?",
                new String[]{playlistId});
        PlaylistModel result = null;
        if (cursor.moveToFirst()) {
            String name = cursor.getString(0);
            String imageUrl = cursor.getString(1);
            int trackCount = cursor.getInt(2);
            boolean isFavourite = cursor.getInt(3) == 1;
            result = new PlaylistModel(playlistId, name, trackCount, null, new ArrayList<>());
            result.setImageUrl(imageUrl);
            result.setFavourite(isFavourite);
        }
        cursor.close();
        db.close();
        return result;
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

    public void deletePlaylist(String playlistId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SONG_PLAYLISTS, COLUMN_PLAYLIST_ID + " = ?", new String[]{playlistId});
        db.delete(TABLE_PLAYLISTS, COLUMN_PLAYLIST_ID + " = ?", new String[]{playlistId});
        db.close();
    }

    //#endregion

    //#region ======== SONG–PLAYLIST LINKS ========

    public List<String> getAllPlaylistIds() {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COLUMN_PLAYLIST_ID + " FROM " + TABLE_PLAYLISTS, null);
        if (cursor.moveToFirst()) {
            do { ids.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return ids;
    }

    public static class PlaylistScanInfo {
        public final int remoteCount;   // latest Spotify tracks.total
        public final int scannedCount;  // remote total at the time of last scan (== remoteCount when up to date)
        public final String name;
        public PlaylistScanInfo(int remoteCount, int scannedCount, String name) {
            this.remoteCount = remoteCount;
            this.scannedCount = scannedCount;
            this.name = name;
        }
    }

    public Map<String, PlaylistScanInfo> getPlaylistTrackCounts() {
        Map<String, PlaylistScanInfo> result = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT p." + COLUMN_PLAYLIST_ID + ", p." + COLUMN_REMOTE_TRACK_COUNT +
                ", p." + COLUMN_TRACK_COUNT +
                ", p." + COLUMN_PLAYLIST_NAME +
                " FROM " + TABLE_PLAYLISTS + " p",
                null);
        if (cursor.moveToFirst()) {
            do {
                result.put(cursor.getString(0),
                        new PlaylistScanInfo(cursor.getInt(1), cursor.getInt(2), cursor.getString(3)));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return result;
    }

    public void upsertSongPlaylistLink(String spotifyUri, String playlistId, String addedAt) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SPOTIFY_URI, spotifyUri);
        values.put(COLUMN_PLAYLIST_ID, playlistId);
        values.put(COLUMN_ADDED_AT, addedAt);
        values.putNull(COLUMN_REMOVED_AT);
        long result = db.insertWithOnConflict(TABLE_SONG_PLAYLISTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (result == -1) {
            ContentValues update = new ContentValues();
            update.putNull(COLUMN_REMOVED_AT);
            if (addedAt != null) update.put(COLUMN_ADDED_AT, addedAt);
            db.update(TABLE_SONG_PLAYLISTS, update,
                    COLUMN_SPOTIFY_URI + " = ? AND " + COLUMN_PLAYLIST_ID + " = ?",
                    new String[]{spotifyUri, playlistId});
        }
        db.close();
    }

    public Map<String, Date> getAddedAtMapForPlaylist(String playlistId) {
        Map<String, Date> result = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SPOTIFY_URI + ", " + COLUMN_ADDED_AT +
                        " FROM " + TABLE_SONG_PLAYLISTS +
                        " WHERE " + COLUMN_PLAYLIST_ID + " = ? AND " + COLUMN_REMOVED_AT + " IS NULL",
                new String[]{playlistId});
        java.text.SimpleDateFormat fmt1 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
        java.text.SimpleDateFormat fmt2 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        fmt1.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        fmt2.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        if (cursor.moveToFirst()) {
            do {
                String uri = cursor.getString(0);
                String ts = cursor.getString(1);
                if (ts != null) {
                    try {
                        try { result.put(uri, fmt2.parse(ts)); } catch (java.text.ParseException e2) { result.put(uri, fmt1.parse(ts)); }
                    } catch (java.text.ParseException ignored) {}
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return result;
    }

    public void markSongRemovedFromPlaylist(String spotifyUri, String playlistId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_REMOVED_AT, utcNow());
        db.update(TABLE_SONG_PLAYLISTS, values,
                COLUMN_SPOTIFY_URI + " = ? AND " + COLUMN_PLAYLIST_ID + " = ? AND " + COLUMN_REMOVED_AT + " IS NULL",
                new String[]{spotifyUri, playlistId});
        db.close();
    }

    public List<String> getPlaylistIdsForSong(String spotifyUri) {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_PLAYLIST_ID + " FROM " + TABLE_SONG_PLAYLISTS +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ?",
                new String[]{spotifyUri});
        if (cursor.moveToFirst()) {
            do { ids.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return ids;
    }

    public List<String> getActivePlaylistIdsForSong(String spotifyUri) {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_PLAYLIST_ID + " FROM " + TABLE_SONG_PLAYLISTS +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ? AND " + COLUMN_REMOVED_AT + " IS NULL",
                new String[]{spotifyUri});
        if (cursor.moveToFirst()) {
            do { ids.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return ids;
    }

    public List<String> getActiveSongUrisForPlaylist(String playlistId) {
        List<String> uris = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SPOTIFY_URI + " FROM " + TABLE_SONG_PLAYLISTS +
                        " WHERE " + COLUMN_PLAYLIST_ID + " = ? AND " + COLUMN_REMOVED_AT + " IS NULL",
                new String[]{playlistId});
        if (cursor.moveToFirst()) {
            do { uris.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return uris;
    }

    public void reconcilePlaylistSongs(String playlistId, List<String> currentSongUris) {
        List<String> storedActive = getActiveSongUrisForPlaylist(playlistId);
        for (String storedUri : storedActive) {
            if (!currentSongUris.contains(storedUri)) {
                markSongRemovedFromPlaylist(storedUri, playlistId);
            }
        }
    }

    public static class PlaylistLink {
        public final String playlistId;
        public final String name;
        public final String imageUrl;
        public final String removedAt;

        public PlaylistLink(String playlistId, String name, String imageUrl, String removedAt) {
            this.playlistId = playlistId;
            this.name = name;
            this.imageUrl = imageUrl;
            this.removedAt = removedAt;
        }

        public boolean isActive() { return removedAt == null; }
    }

    /** A playlist row for the "Add to playlist" manage sheet. */
    public static class ManagablePlaylist {
        public final String playlistId;
        public final String name;
        public final String imageUrl;
        public final boolean favourite;
        /** True if any of the song's variant URIs is actively linked to this playlist. */
        public final boolean containsSong;

        public ManagablePlaylist(String playlistId, String name, String imageUrl,
                                 boolean favourite, boolean containsSong) {
            this.playlistId = playlistId;
            this.name = name;
            this.imageUrl = imageUrl;
            this.favourite = favourite;
            this.containsSong = containsSong;
        }
    }

    /**
     * Playlists for the manage sheet, ordered: playlists the song is currently in first,
     * then favourites, then the rest by most recently added-to. {@code containsSong} is
     * variant-aware: it's true when any of the given
     * variant URIs is actively (not removed) in the playlist, matching the panel above.
     */
    public List<ManagablePlaylist> getManagablePlaylists(List<String> variantUris, boolean includeArchived) {
        List<ManagablePlaylist> result = new ArrayList<>();
        if (variantUris == null || variantUris.isEmpty()) return result;
        String placeholders = makePlaceholders(variantUris.size());

        String containsCase =
                "MAX(CASE WHEN sp." + COLUMN_SPOTIFY_URI + " IN (" + placeholders + ")" +
                " AND sp." + COLUMN_REMOVED_AT + " IS NULL THEN 1 ELSE 0 END)";

        StringBuilder sql = new StringBuilder()
                .append("SELECT p.").append(COLUMN_PLAYLIST_ID)
                .append(", p.").append(COLUMN_PLAYLIST_NAME)
                .append(", p.").append(COLUMN_IMAGE_URL)
                .append(", p.").append(COLUMN_IS_FAVOURITE)
                .append(", ").append(containsCase).append(" AS contains_song")
                .append(", MAX(r.last_added) AS last_added")
                .append(" FROM ").append(TABLE_PLAYLISTS).append(" p")
                .append(" LEFT JOIN ").append(TABLE_SONG_PLAYLISTS).append(" sp")
                .append(" ON sp.").append(COLUMN_PLAYLIST_ID).append(" = p.").append(COLUMN_PLAYLIST_ID)
                .append(" LEFT JOIN (SELECT ").append(COLUMN_PLAYLIST_ID)
                .append(", MAX(").append(COLUMN_ADDED_AT).append(") last_added FROM ").append(TABLE_SONG_PLAYLISTS)
                .append(" GROUP BY ").append(COLUMN_PLAYLIST_ID).append(") r")
                .append(" ON r.").append(COLUMN_PLAYLIST_ID).append(" = p.").append(COLUMN_PLAYLIST_ID);
        if (!includeArchived) {
            sql.append(" WHERE p.").append(COLUMN_IS_ARCHIVED).append(" = 0");
        }
        sql.append(" GROUP BY p.").append(COLUMN_PLAYLIST_ID)
                .append(" ORDER BY contains_song DESC, p.").append(COLUMN_IS_FAVOURITE).append(" DESC, last_added DESC");

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(sql.toString(), variantUris.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do {
                result.add(new ManagablePlaylist(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getInt(3) == 1,
                        cursor.getInt(4) == 1));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return result;
    }

    /**
     * Of the given variant URIs, the subset that is actively (not removed) linked to the
     * playlist. Used on untick to remove every variant of the recording that's present.
     */
    public List<String> getActiveVariantsInPlaylist(List<String> variantUris, String playlistId) {
        List<String> result = new ArrayList<>();
        if (variantUris == null || variantUris.isEmpty()) return result;
        String placeholders = makePlaceholders(variantUris.size());
        String[] args = new String[variantUris.size() + 1];
        for (int i = 0; i < variantUris.size(); i++) args[i] = variantUris.get(i);
        args[variantUris.size()] = playlistId;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SPOTIFY_URI + " FROM " + TABLE_SONG_PLAYLISTS +
                        " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + placeholders + ")" +
                        " AND " + COLUMN_PLAYLIST_ID + " = ? AND " + COLUMN_REMOVED_AT + " IS NULL",
                args);
        if (cursor.moveToFirst()) {
            do { result.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return result;
    }

    public List<PlaylistLink> getPlaylistsForSong(String spotifyUri) {
        List<PlaylistLink> result = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT sp." + COLUMN_PLAYLIST_ID + ", p." + COLUMN_PLAYLIST_NAME +
                        ", p." + COLUMN_IMAGE_URL + ", sp." + COLUMN_REMOVED_AT +
                        " FROM " + TABLE_SONG_PLAYLISTS + " sp" +
                        " JOIN " + TABLE_PLAYLISTS + " p ON sp." + COLUMN_PLAYLIST_ID + " = p." + COLUMN_PLAYLIST_ID +
                        " WHERE sp." + COLUMN_SPOTIFY_URI + " = ?" +
                        " ORDER BY sp." + COLUMN_REMOVED_AT + " IS NOT NULL ASC, p." + COLUMN_PLAYLIST_NAME + " ASC",
                new String[]{spotifyUri});
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

    public List<SongNote> getSongNotes(String spotifyUri) {
        List<SongNote> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_SONG_NOTES +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ?" +
                        " ORDER BY " + COLUMN_TIMESTAMP + " ASC",
                new String[]{spotifyUri});
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
        values.put(COLUMN_SPOTIFY_URI, note.getSongId());
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

    public boolean hasSongNotes(String spotifyUri) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SONG_NOTES, new String[]{COLUMN_ID},
                COLUMN_SPOTIFY_URI + " = ?", new String[]{spotifyUri}, null, null, null, "1");
        boolean has = cursor.moveToFirst();
        cursor.close();
        db.close();
        return has;
    }

    public List<String> getSongIdsWithNotes() {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT " + COLUMN_SPOTIFY_URI + " FROM " + TABLE_SONG_NOTES +
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
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SPOTIFY_URI)),
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

    public List<SongSnippet> getSongSnippets(String spotifyUri) {
        List<SongSnippet> snippets = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_SONG_SNIPPETS +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ?" +
                        " ORDER BY " + COLUMN_SNIPPET_NO + " ASC",
                new String[]{spotifyUri});
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
        values.put(COLUMN_SPOTIFY_URI, snippet.getSongId());
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

    public boolean hasSongSnippets(String spotifyUri) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SONG_SNIPPETS, new String[]{COLUMN_ID},
                COLUMN_SPOTIFY_URI + " = ?", new String[]{spotifyUri}, null, null, null, "1");
        boolean has = cursor.moveToFirst();
        cursor.close();
        db.close();
        return has;
    }

    public List<String> getSongIdsWithSnippets() {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT " + COLUMN_SPOTIFY_URI + " FROM " + TABLE_SONG_SNIPPETS +
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
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SPOTIFY_URI)),
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
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SPOTIFY_URI)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LISTEN_TIMESTAMP))));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return all;
    }

    public void addListenRecord(String spotifyUri) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_UUID, java.util.UUID.randomUUID().toString());
        values.put(COLUMN_SPOTIFY_URI, spotifyUri);
        values.put(COLUMN_LISTEN_TIMESTAMP, utcNow());
        db.insert(TABLE_LISTEN_HISTORY, null, values);
        db.close();
    }

    public void addListenRecordWithTimestamp(String spotifyUri, String utcTimestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_UUID, java.util.UUID.randomUUID().toString());
        values.put(COLUMN_SPOTIFY_URI, spotifyUri);
        values.put(COLUMN_LISTEN_TIMESTAMP, utcTimestamp);
        db.insert(TABLE_LISTEN_HISTORY, null, values);
        db.close();
    }

    public List<String> getListenHistory(String spotifyUri) {
        List<String> timestamps = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_LISTEN_TIMESTAMP + " FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ? ORDER BY " + COLUMN_LISTEN_TIMESTAMP + " ASC",
                new String[]{spotifyUri});
        if (cursor.moveToFirst()) {
            do { timestamps.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return timestamps;
    }

    public List<String> getOrphanedListenUris() {
        List<String> uris = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT lh." + COLUMN_SPOTIFY_URI +
                " FROM " + TABLE_LISTEN_HISTORY + " lh" +
                " LEFT JOIN " + TABLE_SONGS + " s ON s." + COLUMN_SPOTIFY_URI + " = lh." + COLUMN_SPOTIFY_URI +
                " WHERE s." + COLUMN_SPOTIFY_URI + " IS NULL" +
                " AND lh." + COLUMN_SPOTIFY_URI + " LIKE 'spotify:track:______________________'",
                null);
        if (cursor.moveToFirst()) {
            do { uris.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return uris;
    }

    public void deleteOrphanedListens() {
        SQLiteDatabase db = this.getWritableDatabase();
        int deleted = db.delete(TABLE_LISTEN_HISTORY,
                COLUMN_SPOTIFY_URI + " NOT IN (SELECT " + COLUMN_SPOTIFY_URI + " FROM " + TABLE_SONGS + ")",
                null);
        db.close();
        if (deleted > 0) Log.d("DatabaseHelper", "Deleted " + deleted + " unresolvable orphaned listen entries");
    }

    public String getMostRecentListenTimestamp(String spotifyUri) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_LISTEN_TIMESTAMP + " FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ? ORDER BY " + COLUMN_LISTEN_TIMESTAMP + " DESC LIMIT 1",
                new String[]{spotifyUri});
        String timestamp = cursor.moveToFirst() ? cursor.getString(0) : null;
        cursor.close();
        db.close();
        return timestamp;
    }

    public Map<String, String> getMostRecentListenTimestampsBatch(List<String> spotifyUris) {
        Map<String, String> results = new HashMap<>();
        if (spotifyUris == null || spotifyUris.isEmpty()) return results;
        String placeholders = buildPlaceholders(spotifyUris.size());
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SPOTIFY_URI + ", MAX(" + COLUMN_LISTEN_TIMESTAMP + ") as latest" +
                        " FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + placeholders + ")" +
                        " GROUP BY " + COLUMN_SPOTIFY_URI,
                spotifyUris.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do {
                results.put(cursor.getString(0), cursor.getString(1));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return results;
    }

    public Map<String, Integer> getListenCountsBatch(List<String> spotifyUris) {
        Map<String, Integer> results = new HashMap<>();
        if (spotifyUris == null || spotifyUris.isEmpty()) return results;
        String placeholders = buildPlaceholders(spotifyUris.size());
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SPOTIFY_URI + ", COUNT(*) as count" +
                        " FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + placeholders + ")" +
                        " GROUP BY " + COLUMN_SPOTIFY_URI,
                spotifyUris.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do {
                results.put(cursor.getString(0), cursor.getInt(1));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return results;
    }

    /**
     * Like getListenCountsBatch but sums listens across all ISRC variants of each URI.
     * Songs with no ISRC entry count only their own listens.
     */
    public Map<String, Integer> getVariantListenCountsBatch(List<String> spotifyUris) {
        Map<String, Integer> results = new HashMap<>();
        if (spotifyUris == null || spotifyUris.isEmpty()) return results;

        SQLiteDatabase db = this.getReadableDatabase();

        // Step 1: resolve each requested URI to its ISRC and collect all sibling URIs
        String placeholders = buildPlaceholders(spotifyUris.size());
        Cursor isrcCursor = db.rawQuery(
                "SELECT sil_req.spotify_uri, sil_sib.spotify_uri" +
                        " FROM " + TABLE_SONG_ISRC_LINKS + " sil_req" +
                        " JOIN " + TABLE_SONG_ISRC_LINKS + " sil_sib ON sil_sib.isrc = sil_req.isrc" +
                        " WHERE sil_req.spotify_uri IN (" + placeholders + ")",
                spotifyUris.toArray(new String[0]));

        // uri → all variant uris (including self)
        Map<String, List<String>> variantMap = new HashMap<>();
        if (isrcCursor.moveToFirst()) {
            do {
                String reqUri = isrcCursor.getString(0);
                String sibUri = isrcCursor.getString(1);
                variantMap.computeIfAbsent(reqUri, k -> new ArrayList<>()).add(sibUri);
            } while (isrcCursor.moveToNext());
        }
        isrcCursor.close();

        // URIs not in song_isrc_links map only to themselves
        for (String uri : spotifyUris) {
            if (!variantMap.containsKey(uri)) {
                variantMap.put(uri, java.util.Collections.singletonList(uri));
            }
        }

        // Step 2: query listen_history for all sibling URIs at once
        Set<String> allSiblings = new java.util.HashSet<>();
        for (List<String> siblings : variantMap.values()) allSiblings.addAll(siblings);

        Map<String, Integer> siblingCounts = new HashMap<>();
        if (!allSiblings.isEmpty()) {
            List<String> siblingList = new ArrayList<>(allSiblings);
            String sibPlaceholders = buildPlaceholders(siblingList.size());
            Cursor lhCursor = db.rawQuery(
                    "SELECT " + COLUMN_SPOTIFY_URI + ", COUNT(*) FROM " + TABLE_LISTEN_HISTORY +
                            " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + sibPlaceholders + ")" +
                            " GROUP BY " + COLUMN_SPOTIFY_URI,
                    siblingList.toArray(new String[0]));
            if (lhCursor.moveToFirst()) {
                do {
                    siblingCounts.put(lhCursor.getString(0), lhCursor.getInt(1));
                } while (lhCursor.moveToNext());
            }
            lhCursor.close();
        }

        db.close();

        // Step 3: aggregate per requested URI
        for (String uri : spotifyUris) {
            int total = 0;
            for (String sib : variantMap.getOrDefault(uri, java.util.Collections.singletonList(uri))) {
                total += siblingCounts.getOrDefault(sib, 0);
            }
            results.put(uri, total);
        }
        return results;
    }

    /**
     * Like getMostRecentListenTimestampsBatch but considers all ISRC variants of each URI.
     */
    public Map<String, String> getVariantMostRecentListenTimestampsBatch(List<String> spotifyUris) {
        Map<String, String> results = new HashMap<>();
        if (spotifyUris == null || spotifyUris.isEmpty()) return results;

        SQLiteDatabase db = this.getReadableDatabase();

        String placeholders = buildPlaceholders(spotifyUris.size());
        Cursor isrcCursor = db.rawQuery(
                "SELECT sil_req.spotify_uri, sil_sib.spotify_uri" +
                        " FROM " + TABLE_SONG_ISRC_LINKS + " sil_req" +
                        " JOIN " + TABLE_SONG_ISRC_LINKS + " sil_sib ON sil_sib.isrc = sil_req.isrc" +
                        " WHERE sil_req.spotify_uri IN (" + placeholders + ")",
                spotifyUris.toArray(new String[0]));

        Map<String, List<String>> variantMap = new HashMap<>();
        if (isrcCursor.moveToFirst()) {
            do {
                String reqUri = isrcCursor.getString(0);
                String sibUri = isrcCursor.getString(1);
                variantMap.computeIfAbsent(reqUri, k -> new ArrayList<>()).add(sibUri);
            } while (isrcCursor.moveToNext());
        }
        isrcCursor.close();

        for (String uri : spotifyUris) {
            if (!variantMap.containsKey(uri)) {
                variantMap.put(uri, java.util.Collections.singletonList(uri));
            }
        }

        Set<String> allSiblings = new java.util.HashSet<>();
        for (List<String> siblings : variantMap.values()) allSiblings.addAll(siblings);

        Map<String, String> siblingLatest = new HashMap<>();
        if (!allSiblings.isEmpty()) {
            List<String> siblingList = new ArrayList<>(allSiblings);
            String sibPlaceholders = buildPlaceholders(siblingList.size());
            Cursor lhCursor = db.rawQuery(
                    "SELECT " + COLUMN_SPOTIFY_URI + ", MAX(" + COLUMN_LISTEN_TIMESTAMP + ")" +
                            " FROM " + TABLE_LISTEN_HISTORY +
                            " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + sibPlaceholders + ")" +
                            " GROUP BY " + COLUMN_SPOTIFY_URI,
                    siblingList.toArray(new String[0]));
            if (lhCursor.moveToFirst()) {
                do {
                    siblingLatest.put(lhCursor.getString(0), lhCursor.getString(1));
                } while (lhCursor.moveToNext());
            }
            lhCursor.close();
        }

        db.close();

        for (String uri : spotifyUris) {
            String best = null;
            for (String sib : variantMap.getOrDefault(uri, java.util.Collections.singletonList(uri))) {
                String ts = siblingLatest.get(sib);
                if (ts != null && (best == null || ts.compareTo(best) > 0)) best = ts;
            }
            if (best != null) results.put(uri, best);
        }
        return results;
    }

    /**
     * Like getMaxPopularityForUris but keyed per requested URI (returns the max across variants for each).
     * Returns a map from each requested URI to its variant-max popularity.
     */
    public Map<String, Integer> getVariantPopularityBatch(List<String> spotifyUris) {
        Map<String, Integer> results = new HashMap<>();
        if (spotifyUris == null || spotifyUris.isEmpty()) return results;

        SQLiteDatabase db = this.getReadableDatabase();

        String placeholders = buildPlaceholders(spotifyUris.size());
        Cursor isrcCursor = db.rawQuery(
                "SELECT sil_req.spotify_uri, sil_sib.spotify_uri" +
                        " FROM " + TABLE_SONG_ISRC_LINKS + " sil_req" +
                        " JOIN " + TABLE_SONG_ISRC_LINKS + " sil_sib ON sil_sib.isrc = sil_req.isrc" +
                        " WHERE sil_req.spotify_uri IN (" + placeholders + ")",
                spotifyUris.toArray(new String[0]));

        Map<String, List<String>> variantMap = new HashMap<>();
        if (isrcCursor.moveToFirst()) {
            do {
                String reqUri = isrcCursor.getString(0);
                String sibUri = isrcCursor.getString(1);
                variantMap.computeIfAbsent(reqUri, k -> new ArrayList<>()).add(sibUri);
            } while (isrcCursor.moveToNext());
        }
        isrcCursor.close();

        for (String uri : spotifyUris) {
            if (!variantMap.containsKey(uri)) {
                variantMap.put(uri, java.util.Collections.singletonList(uri));
            }
        }

        Set<String> allSiblings = new java.util.HashSet<>();
        for (List<String> siblings : variantMap.values()) allSiblings.addAll(siblings);

        Map<String, Integer> siblingPop = new HashMap<>();
        if (!allSiblings.isEmpty()) {
            List<String> siblingList = new ArrayList<>(allSiblings);
            String sibPlaceholders = buildPlaceholders(siblingList.size());
            Cursor popCursor = db.rawQuery(
                    "SELECT " + COLUMN_SPOTIFY_URI + ", " + COLUMN_POPULARITY +
                            " FROM " + TABLE_SONGS +
                            " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + sibPlaceholders + ")",
                    siblingList.toArray(new String[0]));
            if (popCursor.moveToFirst()) {
                do {
                    siblingPop.put(popCursor.getString(0), popCursor.getInt(1));
                } while (popCursor.moveToNext());
            }
            popCursor.close();
        }

        db.close();

        for (String uri : spotifyUris) {
            int max = 0;
            for (String sib : variantMap.getOrDefault(uri, java.util.Collections.singletonList(uri))) {
                max = Math.max(max, siblingPop.getOrDefault(sib, 0));
            }
            results.put(uri, max);
        }
        return results;
    }

    public boolean hasListenWithinDuration(String spotifyUri, long spotifyTimestamp, int songDurationMs) {
        SQLiteDatabase db = this.getReadableDatabase();
        java.text.SimpleDateFormat utcFormat = utcDateFormat();
        String windowStartStr = utcFormat.format(new java.util.Date(spotifyTimestamp - songDurationMs));
        String windowEndStr = utcFormat.format(new java.util.Date(spotifyTimestamp + songDurationMs));
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ? AND " +
                        COLUMN_LISTEN_TIMESTAMP + " >= ? AND " + COLUMN_LISTEN_TIMESTAMP + " <= ?",
                new String[]{spotifyUri, windowStartStr, windowEndStr});
        boolean has = cursor.moveToFirst() && cursor.getInt(0) > 0;
        cursor.close();
        db.close();
        return has;
    }

    public boolean hasExactListen(String spotifyUri, String utcTimestamp) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_LISTEN_HISTORY +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ? AND " + COLUMN_LISTEN_TIMESTAMP + " = ?",
                new String[]{spotifyUri, utcTimestamp});
        boolean exists = cursor.moveToFirst() && cursor.getInt(0) > 0;
        cursor.close();
        db.close();
        return exists;
    }

    public static class ListenImportBatch {
        final SQLiteDatabase db;
        final List<ContentValues> pending = new ArrayList<>();

        ListenImportBatch(SQLiteDatabase db) {
            this.db = db;
        }
    }

    public ListenImportBatch beginListenImportBatch() {
        return new ListenImportBatch(this.getWritableDatabase());
    }

    public boolean hasExactListenInBatch(ListenImportBatch batch, String spotifyUri, String utcTimestamp) {
        Cursor c = batch.db.rawQuery(
                "SELECT 1 FROM " + TABLE_LISTEN_HISTORY
                        + " WHERE " + COLUMN_SPOTIFY_URI + "=? AND " + COLUMN_LISTEN_TIMESTAMP + "=? LIMIT 1",
                new String[]{spotifyUri, utcTimestamp});
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    public void addListenToBatch(ListenImportBatch batch, String uuid, String spotifyUri, String utcTimestamp) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_UUID, uuid != null ? uuid : java.util.UUID.randomUUID().toString());
        values.put(COLUMN_SPOTIFY_URI, spotifyUri);
        values.put(COLUMN_LISTEN_TIMESTAMP, utcTimestamp);
        batch.pending.add(values);
    }

    public void flushListenBatch(ListenImportBatch batch) {
        if (batch.pending.isEmpty()) return;
        batch.db.beginTransaction();
        try {
            for (ContentValues values : batch.pending) {
                batch.db.insertWithOnConflict(TABLE_LISTEN_HISTORY, null, values, SQLiteDatabase.CONFLICT_IGNORE);
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
                        values.put(COLUMN_SPOTIFY_URI, entry.getSongId());
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

    //#region ======== RANKINGS ========

    /**
     * All non-archived playlists (favourite first, then by name), for the ranking
     * setup's playlist picker. Each entrant pool is the union of the selected
     * playlists' active songs.
     */
    public List<PlaylistModel> getNonArchivedPlaylists() {
        List<PlaylistModel> playlists = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_PLAYLIST_ID + ", " + COLUMN_PLAYLIST_NAME + ", " + COLUMN_IMAGE_URL +
                        ", " + COLUMN_TRACK_COUNT + ", " + COLUMN_IS_FAVOURITE +
                        " FROM " + TABLE_PLAYLISTS +
                        " WHERE " + COLUMN_IS_ARCHIVED + " = 0" +
                        " ORDER BY " + COLUMN_IS_FAVOURITE + " DESC, " + COLUMN_PLAYLIST_NAME + " ASC", null);
        if (cursor.moveToFirst()) {
            do {
                PlaylistModel playlist = new PlaylistModel(
                        cursor.getString(0), cursor.getString(1), cursor.getInt(3), null, new ArrayList<>());
                playlist.setImageUrl(cursor.getString(2));
                playlist.setFavourite(cursor.getInt(4) == 1);
                playlists.add(playlist);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return playlists;
    }

    // ----- Elo ratings -----

    /**
     * Apply one match outcome to the Elo ratings of the two songs. Increase-only:
     * the winner gains the standard amount (larger for an upset), the loser is never
     * penalised. Ratings therefore ratchet upward and a single hard draw can't sink a
     * song — order is instead enforced by lifting via {@link #enforceRankingOrder}.
     * Creates a rating row for any song not yet rated.
     */
    public void applyMatchResult(String winnerUri, String loserUri) {
        if (winnerUri == null || loserUri == null || winnerUri.equals(loserUri)) return;
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            double winnerRating = readRating(db, winnerUri);
            double loserRating = readRating(db, loserUri);

            double expectedWinner = 1.0 / (1.0 + Math.pow(10.0, (loserRating - winnerRating) / 400.0));
            double gain = ELO_K * (1.0 - expectedWinner);

            writeRating(db, winnerUri, winnerRating + gain);
            bumpMatchesPlayed(db, loserUri);   // the loser played, but its rating holds
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    // Count a match against a song without changing its rating (seeding a row first).
    private void bumpMatchesPlayed(SQLiteDatabase db, String uri) {
        ContentValues seed = new ContentValues();
        seed.put(COLUMN_SPOTIFY_URI, uri);
        seed.put(COLUMN_RATING, DEFAULT_RATING);
        seed.put(COLUMN_MATCHES_PLAYED, 0);
        db.insertWithOnConflict(TABLE_SONG_RATINGS, null, seed, SQLiteDatabase.CONFLICT_IGNORE);

        db.execSQL("UPDATE " + TABLE_SONG_RATINGS +
                        " SET " + COLUMN_MATCHES_PLAYED + " = " + COLUMN_MATCHES_PLAYED + " + 1, " +
                        COLUMN_UPDATED_AT + " = ?" +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ?",
                new Object[]{nowUtc(), uri});
    }

    /**
     * Seed a rating row for any entrant not yet rated, at the current lowest rating
     * across all rated songs (or {@link #DEFAULT_RATING} when none exist yet). New
     * songs therefore enter at the bottom and climb by winning.
     */
    public void seedNewEntrants(List<String> uris) {
        if (uris == null || uris.isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            double seed = DEFAULT_RATING;
            Cursor cursor = db.rawQuery(
                    "SELECT MIN(" + COLUMN_RATING + ") FROM " + TABLE_SONG_RATINGS, null);
            try {
                if (cursor.moveToFirst() && !cursor.isNull(0)) seed = cursor.getDouble(0);
            } finally {
                cursor.close();
            }
            for (String uri : uris) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_SPOTIFY_URI, uri);
                values.put(COLUMN_RATING, seed);
                values.put(COLUMN_MATCHES_PLAYED, 0);
                db.insertWithOnConflict(TABLE_SONG_RATINGS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /** Raw Elo per uri, defaulting to the seed rating for any song not yet rated. */
    public Map<String, Double> getRawRatings(List<String> uris) {
        Map<String, Double> result = new HashMap<>();
        if (uris == null || uris.isEmpty()) return result;
        for (String uri : uris) result.put(uri, DEFAULT_RATING);

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SPOTIFY_URI + ", " + COLUMN_RATING +
                        " FROM " + TABLE_SONG_RATINGS +
                        " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + buildPlaceholders(uris.size()) + ")",
                uris.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do { result.put(cursor.getString(0), cursor.getDouble(1)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return result;
    }

    /**
     * Matches played per uri, defaulting to 0 for any song not yet rated. Used to
     * weight new-game sampling toward songs that have been ranked the least, so
     * repeated games over a large pool spread coverage rather than re-facing the
     * same songs.
     */
    public Map<String, Integer> getMatchesPlayed(List<String> uris) {
        Map<String, Integer> result = new HashMap<>();
        if (uris == null || uris.isEmpty()) return result;
        for (String uri : uris) result.put(uri, 0);

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_SPOTIFY_URI + ", " + COLUMN_MATCHES_PLAYED +
                        " FROM " + TABLE_SONG_RATINGS +
                        " WHERE " + COLUMN_SPOTIFY_URI + " IN (" + buildPlaceholders(uris.size()) + ")",
                uris.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do { result.put(cursor.getString(0), cursor.getInt(1)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return result;
    }

    /** Record that a completed game was played, for each entrant that is rated. */
    public void incrementGamesPlayed(List<String> entrantUris) {
        if (entrantUris == null || entrantUris.isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            for (String uri : entrantUris) {
                db.execSQL("UPDATE " + TABLE_SONG_RATINGS +
                                " SET " + COLUMN_GAMES_PLAYED + " = " + COLUMN_GAMES_PLAYED + " + 1" +
                                " WHERE " + COLUMN_SPOTIFY_URI + " = ?",
                        new String[]{uri});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    private double readRating(SQLiteDatabase db, String uri) {
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_RATING + " FROM " + TABLE_SONG_RATINGS +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ?",
                new String[]{uri});
        double rating = DEFAULT_RATING;
        try {
            if (cursor.moveToFirst()) rating = cursor.getDouble(0);
        } finally {
            cursor.close();
        }
        return rating;
    }

    private void writeRating(SQLiteDatabase db, String uri, double rating) {
        // Seed a row on first sighting (ignored if one already exists), then set
        // the new rating and bump matches_played in place.
        ContentValues seed = new ContentValues();
        seed.put(COLUMN_SPOTIFY_URI, uri);
        seed.put(COLUMN_RATING, DEFAULT_RATING);
        seed.put(COLUMN_MATCHES_PLAYED, 0);
        db.insertWithOnConflict(TABLE_SONG_RATINGS, null, seed, SQLiteDatabase.CONFLICT_IGNORE);

        db.execSQL("UPDATE " + TABLE_SONG_RATINGS +
                        " SET " + COLUMN_RATING + " = ?, " +
                        COLUMN_MATCHES_PLAYED + " = " + COLUMN_MATCHES_PLAYED + " + 1, " +
                        COLUMN_UPDATED_AT + " = ?" +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ?",
                new Object[]{rating, nowUtc(), uri});
    }

    // Set a rating outright without touching matches_played, seeding a row first.
    private void setRatingValue(SQLiteDatabase db, String uri, double rating) {
        ContentValues seed = new ContentValues();
        seed.put(COLUMN_SPOTIFY_URI, uri);
        seed.put(COLUMN_RATING, DEFAULT_RATING);
        seed.put(COLUMN_MATCHES_PLAYED, 0);
        db.insertWithOnConflict(TABLE_SONG_RATINGS, null, seed, SQLiteDatabase.CONFLICT_IGNORE);

        db.execSQL("UPDATE " + TABLE_SONG_RATINGS +
                        " SET " + COLUMN_RATING + " = ?, " + COLUMN_UPDATED_AT + " = ?" +
                        " WHERE " + COLUMN_SPOTIFY_URI + " = ?",
                new Object[]{rating, nowUtc(), uri});
    }

    /**
     * Keep Elo from contradicting a game's final placement, raising only. Walking the
     * standings from last to first, any song not already rated above the one placed
     * behind it is lifted an {@link #ELO_RANK_STEP} above it. Ratings are only raised
     * here, never lowered, so an order recorded in an earlier game survives — a song
     * that placed lower this time keeps its rating and stays ahead of songs it beat
     * before.
     */
    public void enforceRankingOrder(List<String> orderedUris) {
        if (orderedUris == null || orderedUris.size() < 2) return;
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            int last = orderedUris.size() - 1;
            double floor = readRating(db, orderedUris.get(last));
            for (int i = last - 1; i >= 0; i--) {
                String uri = orderedUris.get(i);
                double rating = readRating(db, uri);
                if (rating <= floor) {
                    rating = floor + ELO_RANK_STEP;
                    setRatingValue(db, uri, rating);
                }
                floor = rating;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /**
     * Global standing for each requested song: dense rank among all rated songs
     * (1 = highest Elo) and the raw rating. Songs with no rating — directly or
     * via an ISRC-linked variant — are absent from the result.
     */
    public Map<String, SongRankInfo> getRankInfoBatch(List<String> uris) {
        Map<String, SongRankInfo> results = new HashMap<>();
        if (uris == null || uris.isEmpty()) return results;

        SQLiteDatabase db = this.getReadableDatabase();

        // Every rated song in Elo order → dense unique global rank.
        Map<String, SongRankInfo> globalInfo = new HashMap<>();
        Cursor rankCursor = db.rawQuery(
                "SELECT " + COLUMN_SPOTIFY_URI + ", " + COLUMN_RATING +
                        " FROM " + TABLE_SONG_RATINGS +
                        " ORDER BY " + COLUMN_RATING + " DESC, " + COLUMN_SPOTIFY_URI + " ASC", null);
        int rank = 0;
        if (rankCursor.moveToFirst()) {
            do {
                rank++;
                globalInfo.put(rankCursor.getString(0), new SongRankInfo(rank, rankCursor.getDouble(1)));
            } while (rankCursor.moveToNext());
        }
        rankCursor.close();

        // Map each requested uri to its ISRC siblings (including itself).
        String placeholders = buildPlaceholders(uris.size());
        Cursor isrcCursor = db.rawQuery(
                "SELECT sil_req." + COLUMN_SPOTIFY_URI + ", sil_sib." + COLUMN_SPOTIFY_URI +
                        " FROM " + TABLE_SONG_ISRC_LINKS + " sil_req" +
                        " JOIN " + TABLE_SONG_ISRC_LINKS + " sil_sib ON sil_sib." + COLUMN_ISRC + " = sil_req." + COLUMN_ISRC +
                        " WHERE sil_req." + COLUMN_SPOTIFY_URI + " IN (" + placeholders + ")",
                uris.toArray(new String[0]));
        Map<String, List<String>> variantMap = new HashMap<>();
        if (isrcCursor.moveToFirst()) {
            do {
                variantMap.computeIfAbsent(isrcCursor.getString(0), k -> new ArrayList<>())
                        .add(isrcCursor.getString(1));
            } while (isrcCursor.moveToNext());
        }
        isrcCursor.close();
        db.close();

        for (String uri : uris) {
            List<String> siblings = variantMap.getOrDefault(uri, java.util.Collections.singletonList(uri));
            SongRankInfo best = null;
            for (String sib : siblings) {
                SongRankInfo info = globalInfo.get(sib);
                // Best = highest rating (lowest rank number).
                if (info != null && (best == null || info.rating > best.rating)) best = info;
            }
            if (best != null) results.put(uri, best);
        }
        return results;
    }

    // ----- Saved games -----

    /** The single resumable game, or null if none is in progress. */
    public RankingGame getInProgressGame() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_RANKING_GAMES +
                        " WHERE " + COLUMN_STATUS + " = ?" +
                        " ORDER BY " + COLUMN_ID + " DESC LIMIT 1",
                new String[]{GAME_IN_PROGRESS});
        RankingGame game = cursor.moveToFirst() ? gameFromCursor(cursor) : null;
        cursor.close();
        db.close();
        return game;
    }

    public RankingGame getGame(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_RANKING_GAMES + " WHERE " + COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)});
        RankingGame game = cursor.moveToFirst() ? gameFromCursor(cursor) : null;
        cursor.close();
        db.close();
        return game;
    }

    public List<RankingGame> getCompletedGames() {
        List<RankingGame> games = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_RANKING_GAMES +
                        " WHERE " + COLUMN_STATUS + " = ?" +
                        " ORDER BY " + COLUMN_ID + " DESC",
                new String[]{GAME_COMPLETED});
        if (cursor.moveToFirst()) {
            do { games.add(gameFromCursor(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return games;
    }

    /**
     * Create a fresh in-progress game. Only one is allowed at a time, so any
     * existing in-progress game is discarded first. Returns the new game id.
     */
    public long startInProgressGame(int bracketSize, List<String> entrantIds, List<String> playlistIds) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RANKING_GAMES, COLUMN_STATUS + " = ?", new String[]{GAME_IN_PROGRESS});
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, GAME_IN_PROGRESS);
        values.put(COLUMN_BRACKET_SIZE, bracketSize);
        values.put(COLUMN_ENTRANT_IDS, joinUris(entrantIds));
        values.put(COLUMN_PLAYLIST_IDS, joinUris(playlistIds));
        values.put(COLUMN_DECISIONS, "");
        values.put(COLUMN_CREATED_AT, nowUtc());
        values.put(COLUMN_UPDATED_AT, nowUtc());
        long id = db.insert(TABLE_RANKING_GAMES, null, values);
        db.close();
        return id;
    }

    /** Persist the decisions made so far in the in-progress game. */
    public void updateGameDecisions(long id, List<String> decisions) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_DECISIONS, joinUris(decisions));
        values.put(COLUMN_UPDATED_AT, nowUtc());
        db.update(TABLE_RANKING_GAMES, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    /** Mark a game completed and store its final 1..N ranking. */
    public void completeGame(long id, List<String> decisions, List<String> finalOrder) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, GAME_COMPLETED);
        values.put(COLUMN_DECISIONS, joinUris(decisions));
        values.put(COLUMN_FINAL_ORDER, joinUris(finalOrder));
        values.put(COLUMN_UPDATED_AT, nowUtc());
        values.put(COLUMN_COMPLETED_AT, nowUtc());
        db.update(TABLE_RANKING_GAMES, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void deleteGame(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RANKING_GAMES, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    private RankingGame gameFromCursor(Cursor cursor) {
        return new RankingGame(
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_BRACKET_SIZE)),
                splitUris(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ENTRANT_IDS))),
                splitUris(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DECISIONS))),
                splitUris(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FINAL_ORDER))),
                splitUris(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PLAYLIST_IDS))),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_AT)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COMPLETED_AT)));
    }

    // Spotify uris contain colons but never commas, so comma-joining is safe.
    private static String joinUris(List<String> uris) {
        if (uris == null || uris.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < uris.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(uris.get(i));
        }
        return sb.toString();
    }

    private static List<String> splitUris(String joined) {
        List<String> uris = new ArrayList<>();
        if (joined == null || joined.isEmpty()) return uris;
        for (String part : joined.split(",")) {
            if (!part.isEmpty()) uris.add(part);
        }
        return uris;
    }

    private static String nowUtc() {
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault());
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date());
    }

    //#endregion
}
