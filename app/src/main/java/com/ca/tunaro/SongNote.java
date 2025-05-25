package com.ca.tunaro;

public class SongNote {
    private String uuid;
    private long id;
    private final String songId;
    private String noteType;
    private String content;
    private String timestamp;

    public SongNote(String uuid, String songId, String noteType, String content) {
        if (uuid == null) this.uuid = java.util.UUID.randomUUID().toString(); // Generate UUID
        else this.uuid = uuid;
        this.songId = songId;
        this.noteType = noteType;
        this.content = content;
    }

    // Constructor with ID for database operations
    public SongNote(String uuid, long id, String songId, String noteType, String content, String timestamp) {
        if (uuid == null) this.uuid = java.util.UUID.randomUUID().toString(); // Generate UUID
        else this.uuid = uuid;
        this.id = id;
        this.songId = songId;
        this.noteType = noteType;
        this.content = content;
        this.timestamp = timestamp;
    }

    // Getters
    public String getUuid() { return uuid; }
    public long getId() { return id; }
    public String getSongId() { return songId; }
    public String getNoteType() { return noteType; }
    public String getContent() { return content; }
    public String getTimestamp() { return timestamp; }

    // Setters for editing
    public void setUuid(String uuid) { this.uuid = uuid; }
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