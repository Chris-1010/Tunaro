package com.ca.tunaro.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

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
import java.util.List;
import java.util.Objects;

public class DatabaseHelper extends SQLiteOpenHelper {

    //#region Initialisations

    private static final String DATABASE_NAME = "TunaroDB";
    private static final int DATABASE_VERSION = 7;

    // Tables
    private static final String TABLE_FAVOURITE_PLAYLISTS = "favourite_playlists";
    private static final String TABLE_ARCHIVED_PLAYLISTS = "archived_playlists";
    private static final String TABLE_SONG_NOTES = "song_notes";
    private static final String TABLE_SONG_SNIPPETS = "song_snippets";
    private static final String TABLE_LISTEN_HISTORY = "listen_history";

    // Column names
    private static final String COLUMN_UUID = "uuid";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_SONG_ID = "song_id";

    private static final String COLUMN_PLAYLIST_ID = "playlist_id";

    // Notes Columns
    private static final String COLUMN_NOTE_TYPE = "note_type";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    // Snippets Columns
    private static final String COLUMN_SNIPPET_NO = "snippet_no";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_START_TIME = "start_time";
    private static final String COLUMN_END_TIME = "end_time";
    private static final String COLUMN_INCLUDE_IN_RANKINGS = "include_in_rankings";

    // Listen History Columns
    private static final String COLUMN_LISTEN_TIMESTAMP = "listen_timestamp";

    // SQL to upgrade from old timestamp format
    private static final String UPGRADE_TIMESTAMP_FORMAT =
            "UPDATE " + TABLE_SONG_NOTES +
                    " SET " + COLUMN_TIMESTAMP + " = strftime('%d-%m-%Y %H:%M', " + COLUMN_TIMESTAMP + ", 'localtime')";

    //#region Create table queries
    private static final String CREATE_TABLE_FAVOURITE_PLAYLISTS =
            "CREATE TABLE " + TABLE_FAVOURITE_PLAYLISTS + "("
                    + COLUMN_ID +           " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_PLAYLIST_ID +  " TEXT UNIQUE NOT NULL"
                    + ")";
    private static final String CREATE_TABLE_ARCHIVED_PLAYLISTS =
            "CREATE TABLE " + TABLE_ARCHIVED_PLAYLISTS + "("
                    + COLUMN_ID +           " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_PLAYLIST_ID +  " TEXT UNIQUE NOT NULL"
                    + ")";
    private static final String CREATE_TABLE_SONG_NOTES =
            "CREATE TABLE " + TABLE_SONG_NOTES + "("
                    + COLUMN_UUID +         " TEXT UNIQUE NOT NULL,"
                    + COLUMN_ID +           " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SONG_ID +      " TEXT NOT NULL,"
                    + COLUMN_NOTE_TYPE +    " TEXT NOT NULL,"
                    + COLUMN_CONTENT +      " TEXT NOT NULL,"
                    + COLUMN_TIMESTAMP +    " TEXT DEFAULT (strftime('%d-%m-%Y %H:%M', 'now', 'localtime'))"
                    + ")";

    private static final String CREATE_TABLE_SONG_SNIPPETS =
            "CREATE TABLE " + TABLE_SONG_SNIPPETS + "("
                    + COLUMN_UUID +                 " TEXT UNIQUE NOT NULL,"
                    + COLUMN_ID +                   " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SONG_ID +              " TEXT NOT NULL,"
                    + COLUMN_SNIPPET_NO +           " INTEGER NOT NULL,"
                    + COLUMN_TITLE +                " TEXT,"
                    + COLUMN_START_TIME +           " INTEGER NOT NULL,"
                    + COLUMN_END_TIME +             " INTEGER NOT NULL,"
                    + COLUMN_INCLUDE_IN_RANKINGS +  " INTEGER DEFAULT 1"
                    + ")";
    private static final String CREATE_TABLE_LISTEN_HISTORY =
            "CREATE TABLE " + TABLE_LISTEN_HISTORY + "("
                    + COLUMN_UUID +             " TEXT UNIQUE NOT NULL,"
                    + COLUMN_ID +               " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SONG_ID +          " TEXT NOT NULL,"
                    + COLUMN_LISTEN_TIMESTAMP + " TEXT NOT NULL"
                    + ")";
    //#endregion

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_FAVOURITE_PLAYLISTS);
        db.execSQL(CREATE_TABLE_ARCHIVED_PLAYLISTS);
        db.execSQL(CREATE_TABLE_SONG_NOTES);
        db.execSQL(CREATE_TABLE_SONG_SNIPPETS);
        db.execSQL(CREATE_TABLE_LISTEN_HISTORY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 7) {
            // Create the favourite playlists table for version 7
            db.execSQL(CREATE_TABLE_FAVOURITE_PLAYLISTS);
        }
    }

    private void generateUUIDsForExistingRecords(SQLiteDatabase db) {
        // Update notes with UUIDs
        Cursor notesCursor = db.rawQuery("SELECT id FROM " + TABLE_SONG_NOTES + " WHERE uuid IS NULL", null);
        if (notesCursor.moveToFirst()) {
            do {
                long id = notesCursor.getLong(0);
                String uuid = java.util.UUID.randomUUID().toString();
                db.execSQL("UPDATE " + TABLE_SONG_NOTES + " SET uuid = ? WHERE id = ?", new Object[]{uuid, id});
            } while (notesCursor.moveToNext());
        }
        notesCursor.close();

        // Update snippets with UUIDs
        Cursor snippetsCursor = db.rawQuery("SELECT id FROM " + TABLE_SONG_SNIPPETS + " WHERE uuid IS NULL", null);
        if (snippetsCursor.moveToFirst()) {
            do {
                long id = snippetsCursor.getLong(0);
                String uuid = java.util.UUID.randomUUID().toString();
                db.execSQL("UPDATE " + TABLE_SONG_SNIPPETS + " SET uuid = ? WHERE id = ?", new Object[]{uuid, id});
            } while (snippetsCursor.moveToNext());
        }
        snippetsCursor.close();
    }

    //#endregion

    //#region ======== NOTES METHODS ========

    // Get all notes
    private List<SongNote> getAllNotes() {
        List<SongNote> allNotes = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_SONG_NOTES + " ORDER BY " + COLUMN_TIMESTAMP + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                SongNote note = new SongNote(
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UUID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE_TYPE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                );
                allNotes.add(note);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return allNotes;
    }

    // Get all notes for a specific song
    public List<SongNote> getSongNotes(String songId) {
        List<SongNote> notes = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_SONG_NOTES +
                " WHERE " + COLUMN_SONG_ID + " = ?" +
                " ORDER BY " + COLUMN_TIMESTAMP + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, new String[]{songId});

        if (cursor.moveToFirst()) {
            do {
                SongNote note = new SongNote(
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UUID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE_TYPE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                );
                notes.add(note);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return notes;
    }

    // Add
    public long addNote(SongNote note) {
        SQLiteDatabase db = this.getWritableDatabase();

        if (note.getUuid() != null && dataExistsByUUID(db, TABLE_SONG_NOTES, note.getUuid())) {
            db.close();
            return -1; // Already exists
        }

        ContentValues values = new ContentValues();

        values.put(COLUMN_UUID, note.getUuid());
        values.put(COLUMN_SONG_ID, note.getSongId());
        values.put(COLUMN_NOTE_TYPE, note.getNoteType());
        values.put(COLUMN_CONTENT, note.getContent());
        // COLUMN_TIMESTAMP is automatically set by the database as default

        long id = db.insert(TABLE_SONG_NOTES, null, values);
        note.setId(id);
        db.close();
        return id;
    }

    // Edit
    public void editNote(SongNote note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_NOTE_TYPE, note.getNoteType());
        values.put(COLUMN_CONTENT, note.getContent());

        // Update with current timestamp first
        db.execSQL("UPDATE " + TABLE_SONG_NOTES +
                        " SET " + COLUMN_TIMESTAMP + " = strftime('%d-%m-%Y %H:%M', 'now', 'localtime')" +
                        " WHERE " + COLUMN_ID + " = ?",
                new String[]{String.valueOf(note.getId())});

        // Update other fields
        db.update(TABLE_SONG_NOTES, values, COLUMN_ID + " = ?",
                new String[]{String.valueOf(note.getId())});
        db.close();
    }

    // Delete
    public void deleteNote(long noteId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SONG_NOTES, COLUMN_ID + " = ?",
                new String[]{String.valueOf(noteId)});
        db.close();
    }

    // Check if a song has any notes
    public boolean hasSongNotes(String songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SONG_NOTES,
                new String[]{COLUMN_ID},
                COLUMN_SONG_ID + " = ?",
                new String[]{songId},
                null, null, null, "1");

        boolean hasNotes = cursor.moveToFirst();
        cursor.close();
        db.close();
        return hasNotes;
    }

    // Get all songs that have notes
    public List<String> getSongIdsWithNotes() {
        List<String> songIds = new ArrayList<>();

        String selectQuery =
                "SELECT DISTINCT " + COLUMN_SONG_ID +
                        " FROM " + TABLE_SONG_NOTES +
                        " ORDER BY " + COLUMN_TIMESTAMP + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                String songId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_ID));
                songIds.add(songId);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return songIds;
    }

    // Get recent note types for autocomplete (max 5 unique types)
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
                String noteType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE_TYPE));
                if (!noteTypes.contains(noteType)) {
                    noteTypes.add(noteType);
                }
            } while (cursor.moveToNext() && noteTypes.size() < 5);
        }

        cursor.close();
        db.close();
        return noteTypes;
    }

    //#endregion

    //#region ======== ARCHIVED PLAYLISTS METHODS ========

    // Add
    public void archivePlaylist(String playlistId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PLAYLIST_ID, playlistId);
        db.insert(TABLE_ARCHIVED_PLAYLISTS, null, values);
        db.close();
    }

    // Delete
    public void unarchivePlaylist(String playlistId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_ARCHIVED_PLAYLISTS, COLUMN_PLAYLIST_ID + " = ?",
                new String[]{playlistId});
        db.close();
    }

    // Check if a playlist is archived
    public boolean isPlaylistArchived(String playlistId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_ARCHIVED_PLAYLISTS,
                new String[]{COLUMN_PLAYLIST_ID},
                COLUMN_PLAYLIST_ID + " = ?",
                new String[]{playlistId},
                null, null, null);
        boolean isArchived = cursor.moveToFirst();
        cursor.close();
        db.close();
        return isArchived;
    }

    // Get all archived playlists
    public List<String> getArchivedPlaylistIds() {
        List<String> playlistIds = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_ARCHIVED_PLAYLISTS,
                new String[]{COLUMN_PLAYLIST_ID},
                null, null, null, null, null);
        if (cursor.moveToFirst()) {
            do {
                playlistIds.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return playlistIds;
    }

    //#endregion

    //#region ======== SNIPPETS METHODS ========

    // Get all snippets
    private List<SongSnippet> getAllSnippets() {
        List<SongSnippet> allSnippets = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_SONG_SNIPPETS + " ORDER BY " + COLUMN_ID + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                SongSnippet snippet = new SongSnippet(
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UUID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SNIPPET_NO)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_START_TIME)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_END_TIME)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_INCLUDE_IN_RANKINGS)) == 1
                );
                allSnippets.add(snippet);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return allSnippets;
    }

    // Get all snippets for a specific song
    public List<SongSnippet> getSongSnippets(String songId) {
        List<SongSnippet> snippets = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_SONG_SNIPPETS +
                " WHERE " + COLUMN_SONG_ID + " = ?" +
                " ORDER BY " + COLUMN_SNIPPET_NO + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, new String[]{songId});

        if (cursor.moveToFirst()) {
            do {
                SongSnippet snippet = new SongSnippet(
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UUID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SNIPPET_NO)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_START_TIME)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_END_TIME)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_INCLUDE_IN_RANKINGS)) == 1
                );
                snippets.add(snippet);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return snippets;
    }

    // Add
    public long addSnippet(SongSnippet snippet) {
        SQLiteDatabase db = this.getWritableDatabase();

        if (snippet.getUuid() != null && dataExistsByUUID(db, TABLE_SONG_SNIPPETS, snippet.getUuid())) {
            db.close();
            return -1; // Already exists
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

    // Edit
    public void editSnippet(SongSnippet snippet) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_SNIPPET_NO, snippet.getSongId());
        values.put(COLUMN_TITLE, snippet.getTitle());
        values.put(COLUMN_START_TIME, snippet.getStartTime());
        values.put(COLUMN_END_TIME, snippet.getEndTime());
        values.put(COLUMN_INCLUDE_IN_RANKINGS, snippet.getIncludeInRankings() ? 1 : 0);

        db.update(TABLE_SONG_SNIPPETS, values, COLUMN_ID + " = ?",
                new String[]{String.valueOf(snippet.getId())});
        db.close();
    }

    // Delete
    public void deleteSnippet(long snippetId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SONG_SNIPPETS, COLUMN_ID + " = ?",
                new String[]{String.valueOf(snippetId)});
        db.close();
    }

    // Check if a song has any snippets
    public boolean hasSongSnippets(String songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SONG_SNIPPETS,
                new String[]{COLUMN_ID},
                COLUMN_SONG_ID + " = ?",
                new String[]{songId},
                null, null, null, "1");

        boolean hasSnippets = cursor.moveToFirst();
        cursor.close();
        db.close();
        return hasSnippets;
    }

    // Get all songs that have snippets
    public List<String> getSongIdsWithSnippets() {
        List<String> songIds = new ArrayList<>();

        String selectQuery =
                "SELECT DISTINCT " + COLUMN_SONG_ID +
                        " FROM " + TABLE_SONG_SNIPPETS +
                        " ORDER BY " + COLUMN_ID + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                String songId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SONG_ID));
                songIds.add(songId);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return songIds;
    }

    //#endregion

    //#region ======== LISTEN HISTORY METHODS ========

    public void addListenRecord(String songId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        String uuid = java.util.UUID.randomUUID().toString();

        // Use UTC timezone to ensure consistent timestamps
        java.text.SimpleDateFormat utcFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
        utcFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String timestamp = utcFormat.format(new java.util.Date());

        values.put(COLUMN_UUID, uuid);
        values.put(COLUMN_SONG_ID, songId);
        values.put(COLUMN_LISTEN_TIMESTAMP, timestamp);

        db.insert(TABLE_LISTEN_HISTORY, null, values);
        db.close();

        Log.d("ListenHistory", "Added listen record for song ID: " + songId);
    }

    public List<String> getListenHistory(String songId) {
        List<String> timestamps = new ArrayList<>();
        String selectQuery = "SELECT " + COLUMN_LISTEN_TIMESTAMP + " FROM " + TABLE_LISTEN_HISTORY +
                " WHERE " + COLUMN_SONG_ID + " = ?" +
                " ORDER BY " + COLUMN_LISTEN_TIMESTAMP + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, new String[]{songId});

        if (cursor.moveToFirst()) {
            do {
                timestamps.add(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LISTEN_TIMESTAMP)));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return timestamps;
    }

    public String getMostRecentListenTimestamp(String songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COLUMN_LISTEN_TIMESTAMP + " FROM " + TABLE_LISTEN_HISTORY +
                " WHERE " + COLUMN_SONG_ID + " = ?" +
                " ORDER BY " + COLUMN_LISTEN_TIMESTAMP + " DESC LIMIT 1", new String[]{songId});

        String timestamp = null;
        if (cursor.moveToFirst()) {
            timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LISTEN_TIMESTAMP));
        }
        cursor.close();
        db.close();
        return timestamp;
    }

    public int getListenCount(String songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_LISTEN_HISTORY +
                " WHERE " + COLUMN_SONG_ID + " = ?", new String[]{songId});

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return count;
    }

    //#endregion

    //#region ======== EXPORT/IMPORT METHODS ========
    public static class ExportData {
        public List<SongNote> notes;
        public List<String> archivedPlaylists;
        public List<SongSnippet> snippets;
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

        // Export all notes
        exportData.notes = dbHelper.getAllNotes();

        // Export archived playlists
        exportData.archivedPlaylists = dbHelper.getArchivedPlaylistIds();

        // Export all snippets
        exportData.snippets = dbHelper.getAllSnippets();

        // Return JSON string
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(exportData);
    }

    public static void writeExportToUri(Context context, Uri uri, String jsonData) throws IOException {
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(Objects.requireNonNull(outputStream))) {
            writer.write(jsonData);
        }
    }

    public static ImportStats importFromUri(Context context, Uri uri) throws IOException, IllegalArgumentException {
        String jsonData = readTextFromUri(context, uri);

        Gson gson = new Gson();
        ExportData importData = gson.fromJson(jsonData, ExportData.class);

        if (importData == null) {
            throw new IllegalArgumentException("Invalid or corrupted backup file");
        }

        DatabaseHelper dbHelper = new DatabaseHelper(context);
        ImportStats stats = new ImportStats();

        // Import notes
        if (importData.notes != null) {
            for (SongNote note : importData.notes) {
                // Create new note without ID to avoid conflicts
                SongNote newNote = new SongNote(note.getUuid(), note.getSongId(), note.getNoteType(), note.getContent());
                long result = dbHelper.addNote(newNote);
                if (result != -1) {
                    stats.notesAdded++;
                }
            }
        }

        // Import archived playlists
        if (importData.archivedPlaylists != null) {
            for (String playlistId : importData.archivedPlaylists) {
                if (!dbHelper.isPlaylistArchived(playlistId)) {
                    dbHelper.archivePlaylist(playlistId);
                    stats.playlistsArchived++;
                }
            }
        }

        // Import snippets
        if (importData.snippets != null) {
            for (SongSnippet snippet : importData.snippets) {
                // Create new snippet without ID to avoid conflicts
                SongSnippet newSnippet = new SongSnippet(
                        snippet.getUuid(),
                        snippet.getSongId(),
                        snippet.getSnippetNo(),
                        snippet.getTitle(),
                        snippet.getStartTime(),
                        snippet.getEndTime(),
                        snippet.getIncludeInRankings()
                );
                long result = dbHelper.addSnippet(newSnippet);
                if (result != -1) {
                    stats.snippetsAdded++;
                }
            }
        }

        return stats;
    }

    private static String readTextFromUri(Context context, Uri uri) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append('\n');
            }
        }
        return stringBuilder.toString();
    }

    private boolean dataExistsByUUID(SQLiteDatabase db, String table, String uuid) {
        Cursor cursor = db.query(table, new String[]{"id"}, "uuid = ?", new String[]{uuid}, null, null, null);
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }


    public static class ImportStats {
        public int notesAdded = 0;
        public int playlistsArchived = 0;
        public int snippetsAdded = 0;

        public String getSummary() {
            List<String> parts = new ArrayList<>();
            if (notesAdded > 0) parts.add(notesAdded + " note" + (notesAdded == 1 ? "" : "s"));
            if (playlistsArchived > 0) parts.add(playlistsArchived + " playlist" + (playlistsArchived == 1 ? "" : "s") + " archived");
            if (snippetsAdded > 0) parts.add(snippetsAdded + " snippet" + (snippetsAdded == 1 ? "" : "s"));

            if (parts.isEmpty()) {
                return "No new data imported";
            }

            return "Imported: " + String.join(", ", parts);
        }
    }

    //#endregion
}