package com.ca.tunaro;

public class SongSnippet {
    private String uuid;
    private long id;
    private final String songId;
    private long snippetNo;
    private String title;
    private long startTime; // in milliseconds
    private long endTime; // in milliseconds
    private boolean includeInRankings;

    public SongSnippet(String uuid, String songId, long snippetNo, String title, long startTime, long endTime, boolean includeInRankings) {
        if (uuid == null) this.uuid = java.util.UUID.randomUUID().toString(); // Generate UUID
        else this.uuid = uuid;
        this.songId = songId;
        this.snippetNo = snippetNo;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.includeInRankings = includeInRankings;
    }

    // Constructor with id for database operations
    public SongSnippet(String uuid, long id, String songId, long snippetNo, String title, long startTime, long endTime, boolean includeInRankings) {
        if (uuid == null) this.uuid = java.util.UUID.randomUUID().toString(); // Generate UUID
        else this.uuid = uuid;
        this.id = id;
        this.songId = songId;
        this.snippetNo = snippetNo;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.includeInRankings = includeInRankings;
    }

    // Getters
    public String getUuid() { return uuid; }
    public long getId() { return id; }
    public String getSongId() { return songId; }
    public long getSnippetNo() { return snippetNo; }
    public String getTitle() { return title; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public boolean getIncludeInRankings() { return includeInRankings; }

    // Setters
    public void setUuid(String uuid) { this.uuid = uuid; }
    public void setId(long id) { this.id = id; }
    public void setSnippetNo(long snippetNo) { this.snippetNo = snippetNo; }
    public void setTitle(String title) { this.title = title; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public void setIncludeInRankings(boolean includeInRankings) { this.includeInRankings = includeInRankings; }
}