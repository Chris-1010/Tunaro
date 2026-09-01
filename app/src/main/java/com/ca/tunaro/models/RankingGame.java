package com.ca.tunaro.models;

import java.util.List;

/**
 * A saved ranking game — either the single resumable in-progress game or a
 * completed one kept for history. State is stored by replay: the ordered
 * entrant ids plus the winner id of each decided match reconstruct the bracket.
 */
public class RankingGame {
    public final long id;
    public final String status;            // GAME_IN_PROGRESS or GAME_COMPLETED
    public final int bracketSize;
    public final List<String> entrantIds;  // ordered entrants (spotify uris)
    public final List<String> decisions;   // winner uri per decided match, in order
    public final List<String> finalOrder;  // ranked uris 1..N; empty until completed
    public final List<String> playlistIds; // playlists the entrants were drawn from
    public final String createdAt;
    public final String updatedAt;
    public final String completedAt;

    public RankingGame(long id, String status, int bracketSize,
                       List<String> entrantIds, List<String> decisions, List<String> finalOrder,
                       List<String> playlistIds,
                       String createdAt, String updatedAt, String completedAt) {
        this.id = id;
        this.status = status;
        this.bracketSize = bracketSize;
        this.entrantIds = entrantIds;
        this.decisions = decisions;
        this.finalOrder = finalOrder;
        this.playlistIds = playlistIds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }

    public boolean isCompleted() {
        return RankingGame.GAME_COMPLETED_STATUS.equals(status);
    }

    // Mirror of DatabaseHelper.GAME_COMPLETED, duplicated here so the model has no
    // dependency on the database layer.
    public static final String GAME_COMPLETED_STATUS = "completed";
}
