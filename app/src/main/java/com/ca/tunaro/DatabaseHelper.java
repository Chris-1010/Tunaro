package com.ca.tunaro;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "SongNotesDB";
    private static final int DATABASE_VERSION = 3;

    // Table name
    private static final String TABLE_SONG_NOTES = "song_notes";
    private static final String TABLE_ARCHIVED_PLAYLISTS = "archived_playlists";

    // Column names
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_PLAYLIST_ID = "playlist_id";
    private static final String COLUMN_SONG_ID = "song_id";
    private static final String COLUMN_NOTE_TYPE = "note_type";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    // SQL to upgrade from old timestamp format
    private static final String UPGRADE_TIMESTAMP_FORMAT =
            "UPDATE " + TABLE_SONG_NOTES +
                    " SET " + COLUMN_TIMESTAMP + " = strftime('%d-%m-%Y %H:%M', " + COLUMN_TIMESTAMP + ", 'localtime')";

    // Create table query
    private static final String CREATE_TABLE_SONG_NOTES =
            "CREATE TABLE " + TABLE_SONG_NOTES + "("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_SONG_ID + " TEXT NOT NULL,"
                    + COLUMN_NOTE_TYPE + " TEXT NOT NULL,"
                    + COLUMN_CONTENT + " TEXT NOT NULL,"
                    + COLUMN_TIMESTAMP + " TEXT DEFAULT (strftime('%d-%m-%Y %H:%M', 'now', 'localtime'))"
                    + ")";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SONG_NOTES);
        db.execSQL("CREATE TABLE " + TABLE_ARCHIVED_PLAYLISTS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_PLAYLIST_ID + " TEXT UNIQUE NOT NULL"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Handle previous upgrade from version 1 to 2
            try {
                db.execSQL(UPGRADE_TIMESTAMP_FORMAT);
            } catch (Exception e) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONG_NOTES);
                onCreate(db);
            }
        }

        if (oldVersion < 3) {
            // Create the archived_playlists table
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ARCHIVED_PLAYLISTS + "("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_PLAYLIST_ID + " TEXT UNIQUE NOT NULL"
                    + ")");
        }
    }

    // Add a new note
    public long addNote(SongNote note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_SONG_ID, note.getSongId());
        values.put(COLUMN_NOTE_TYPE, note.getNoteType());
        values.put(COLUMN_CONTENT, note.getContent());

        long id = db.insert(TABLE_SONG_NOTES, null, values);
        db.close();
        return id;
    }

    // Edit a note
    public void editNote(SongNote note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_NOTE_TYPE, note.getNoteType());
        values.put(COLUMN_CONTENT, note.getContent());

        // Execute update with current timestamp
        String updateQuery = "UPDATE " + TABLE_SONG_NOTES +
                " SET " + COLUMN_NOTE_TYPE + " = ?, " +
                COLUMN_CONTENT + " = ?, " +
                COLUMN_TIMESTAMP + " = strftime('%d-%m-%Y %H:%M', 'now', 'localtime')" +
                " WHERE " + COLUMN_ID + " = ?";

        db.execSQL(updateQuery,
                new String[]{note.getNoteType(),
                        note.getContent(),
                        String.valueOf(note.getId())});
        db.close();
    }

    // Delete a note
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
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC";

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
}