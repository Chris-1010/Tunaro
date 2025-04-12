package com.ca.tunaro;

public class SongNote {
    private long id;
    private String songId;
    private String noteType;
    private String content;
    private String timestamp;

    public SongNote(String songId, String noteType, String content) {
        this.songId = songId;
        this.noteType = noteType;
        this.content = content;
    }

    public SongNote(String songId, String noteType, String content, String timestamp) {
        this.songId = songId;
        this.noteType = noteType;
        this.content = content;
        this.timestamp = timestamp;
    }

    // Constructor with ID for editing/retrieving from database
    public SongNote(long id, String songId, String noteType, String content, String timestamp) {
        this.id = id;
        this.songId = songId;
        this.noteType = noteType;
        this.content = content;
        this.timestamp = timestamp;
    }

    // Getters
    public long getId() { return id; }
    public String getSongId() { return songId; }
    public String getNoteType() { return noteType; }
    public String getContent() { return content; }
    public String getTimestamp() { return timestamp; }

    // Setters for editing
    public void setId(long id) { this.id = id; }
    public void setNoteType(String noteType) { this.noteType = noteType; }
    public void setContent(String content) { this.content = content; }

    // Enum for note types
    public enum NoteType {
        GENERAL_NOTE("General Note"),
        DATE_LISTENED("Date Listened"),
        FIRST_HEARD("Where First Heard"),
        FAVORITE_PART("Favorite Part"),
        RATING("Rating");

        private final String displayName;

        NoteType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}