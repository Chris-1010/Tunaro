package com.ca.tunaro;

import static java.lang.System.exit;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Environment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "TunaroDB";
    private static final int DATABASE_VERSION = 4;

    // Table name
    private static final String TABLE_ARCHIVED_PLAYLISTS = "archived_playlists";
    private static final String TABLE_SONG_NOTES = "song_notes";
    private static final String TABLE_SONG_SNIPPETS = "song_snippets";

    // Column names
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

    // SQL to upgrade from old timestamp format
    private static final String UPGRADE_TIMESTAMP_FORMAT =
            "UPDATE " + TABLE_SONG_NOTES +
                    " SET " + COLUMN_TIMESTAMP + " = strftime('%d-%m-%Y %H:%M', " + COLUMN_TIMESTAMP + ", 'localtime')";

    // Create table queries
    private static final String CREATE_TABLE_SONG_NOTES =
            "CREATE TABLE " + TABLE_SONG_NOTES + "("
                    + COLUMN_ID +           " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SONG_ID +      " TEXT NOT NULL,"
                    + COLUMN_NOTE_TYPE +    " TEXT NOT NULL,"
                    + COLUMN_CONTENT +      " TEXT NOT NULL,"
                    + COLUMN_TIMESTAMP +    " TEXT DEFAULT (strftime('%d-%m-%Y %H:%M', 'now', 'localtime'))"
                    + ")";
    private static final String CREATE_TABLE_ARCHIVED_PLAYLISTS =
            "CREATE TABLE " + TABLE_ARCHIVED_PLAYLISTS + "("
                    + COLUMN_ID +           " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_PLAYLIST_ID +  " TEXT UNIQUE NOT NULL"
                    + ")";
    private static final String CREATE_TABLE_SONG_SNIPPETS =
            "CREATE TABLE " + TABLE_SONG_SNIPPETS + "("
                    + COLUMN_ID +                   " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SONG_ID +              " TEXT NOT NULL,"
                    + COLUMN_SNIPPET_NO +           " INTEGER NOT NULL,"
                    + COLUMN_TITLE +                " TEXT,"
                    + COLUMN_START_TIME +           " INTEGER NOT NULL,"
                    + COLUMN_END_TIME +             " INTEGER NOT NULL,"
                    + COLUMN_INCLUDE_IN_RANKINGS +  " INTEGER DEFAULT 1"
                    + ")";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SONG_NOTES);
        db.execSQL(CREATE_TABLE_ARCHIVED_PLAYLISTS);
        db.execSQL(CREATE_TABLE_SONG_SNIPPETS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Handle previous upgrade from version 1 to 2
            try {
                db.execSQL(UPGRADE_TIMESTAMP_FORMAT);
            } catch (Exception e) {
//                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_NOTES);
//                onCreate(db);
                exit(1);
            }
        }

        if (oldVersion < 3) {
            // Create the archived_playlists table
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ARCHIVED_PLAYLISTS + "("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_PLAYLIST_ID + " TEXT UNIQUE NOT NULL"
                    + ")");
        }

        if (oldVersion < 4) {
            // Create the song_snippets table for version 4
            db.execSQL(CREATE_TABLE_SONG_SNIPPETS);
        }
    }

    // ======== NOTES METHODS ========
    // Add
    public long addNote(SongNote note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

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

    // ======== ARCHIVED PLAYLISTS METHODS ========
    // Add a playlist to the archived playlists table
    public void archivePlaylist(String playlistId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PLAYLIST_ID, playlistId);
        db.insert(TABLE_ARCHIVED_PLAYLISTS, null, values);
        db.close();
    }

    public void unarchivePlaylist(String playlistId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_ARCHIVED_PLAYLISTS, COLUMN_PLAYLIST_ID + " = ?",
                new String[]{playlistId});
        db.close();
    }

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

    // ======== SNIPPETS METHODS ========
    // Add
    public long addSnippet(SongSnippet snippet) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

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


    // ======== EXPORT/IMPORT METHODS (Updated for Storage Access Framework) ========
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
                SongNote newNote = new SongNote(note.getSongId(), note.getNoteType(), note.getContent());
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

    // Keep the existing helper methods
    private List<SongNote> getAllNotes() {
        List<SongNote> allNotes = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_SONG_NOTES + " ORDER BY " + COLUMN_TIMESTAMP + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                SongNote note = new SongNote(
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

    private List<SongSnippet> getAllSnippets() {
        List<SongSnippet> allSnippets = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_SONG_SNIPPETS + " ORDER BY " + COLUMN_ID + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                SongSnippet snippet = new SongSnippet(
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
}