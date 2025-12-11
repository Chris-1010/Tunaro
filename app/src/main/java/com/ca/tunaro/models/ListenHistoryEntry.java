package com.ca.tunaro.models;

public class ListenHistoryEntry {
    private String uuid;
    private long id;
    private final String songId;
    private String listenTimestamp;

    public ListenHistoryEntry(String uuid, String songId, String listenTimestamp) {
        if (uuid == null) this.uuid = java.util.UUID.randomUUID().toString(); // Generate UUID
        else this.uuid = uuid;
        this.songId = songId;
        this.listenTimestamp = listenTimestamp;
    }

    // Constructor with ID for database operations
    public ListenHistoryEntry(String uuid, long id, String songId, String listenTimestamp) {
        if (uuid == null) this.uuid = java.util.UUID.randomUUID().toString(); // Generate UUID
        else this.uuid = uuid;
        this.id = id;
        this.songId = songId;
        this.listenTimestamp = listenTimestamp;
    }

    // Getters
    public String getUuid() { return uuid; }
    public long getId() { return id; }
    public String getSongId() { return songId; }
    public String getListenTimestamp() { return listenTimestamp; }

    // Setters
    public void setUuid(String uuid) { this.uuid = uuid; }
    public void setId(long id) { this.id = id; }
    public void setListenTimestamp(String listenTimestamp) { this.listenTimestamp = listenTimestamp; }
}
