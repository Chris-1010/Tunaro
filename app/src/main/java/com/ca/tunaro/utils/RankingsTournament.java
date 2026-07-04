package com.ca.tunaro.utils;

import com.ca.tunaro.models.SongModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-elimination tournament bracket over a list of songs.
 *
 * The entrant list is padded to the next power of two with byes (null slots),
 * paired front-to-back so every bye lands opposite a real song. Bye matches
 * resolve automatically, so callers only ever see matches between two real
 * songs. Losers are recorded round by round to build the final leaderboard:
 * champion first, then earlier eliminations grouped by the round they went
 * out in — songs knocked out in the same round share a rank.
 */
public class RankingsTournament {

    public static class LeaderboardEntry {
        public final int rank;
        public final SongModel song;

        LeaderboardEntry(int rank, SongModel song) {
            this.rank = rank;
            this.song = song;
        }
    }

    private List<SongModel> currentRound;   // slot list; match i = slots (2i, 2i+1), bye slots are null
    private List<SongModel> nextRound;
    private final List<List<SongModel>> losersByRound = new ArrayList<>();
    private int matchIndex;
    private int matchesPlayedInRound;
    private int totalMatchesInRound;        // real matches only, byes excluded
    private SongModel champion;

    public RankingsTournament(List<SongModel> entrants) {
        if (entrants == null || entrants.size() < 2) {
            throw new IllegalArgumentException("Tournament needs at least 2 songs");
        }

        int bracketSize = 1;
        while (bracketSize < entrants.size()) bracketSize *= 2;

        List<SongModel> padded = new ArrayList<>(entrants);
        while (padded.size() < bracketSize) padded.add(null);

        // Pair slot i against slot (size-1-i). Byes sit at the tail of the
        // padded list and there are always fewer than half a bracket of them,
        // so a bye can never meet another bye.
        currentRound = new ArrayList<>(bracketSize);
        for (int i = 0; i < bracketSize / 2; i++) {
            currentRound.add(padded.get(i));
            currentRound.add(padded.get(bracketSize - 1 - i));
        }

        beginRound();
    }

    private void beginRound() {
        nextRound = new ArrayList<>();
        matchIndex = 0;
        matchesPlayedInRound = 0;
        totalMatchesInRound = 0;
        losersByRound.add(new ArrayList<>());
        for (int i = 0; i < currentRound.size(); i += 2) {
            if (currentRound.get(i) != null && currentRound.get(i + 1) != null) {
                totalMatchesInRound++;
            }
        }
        skipByes();
    }

    // Advance past bye matches until a real match (or the end of the round).
    private void skipByes() {
        while (matchIndex * 2 < currentRound.size()) {
            SongModel a = currentRound.get(matchIndex * 2);
            SongModel b = currentRound.get(matchIndex * 2 + 1);
            if (a != null && b != null) return;
            nextRound.add(a != null ? a : b);
            matchIndex++;
        }
        finishRound();
    }

    private void finishRound() {
        if (nextRound.size() == 1) {
            champion = nextRound.get(0);
            return;
        }
        currentRound = nextRound;
        beginRound();
    }

    public boolean isFinished() {
        return champion != null;
    }

    public SongModel getContenderA() {
        return currentRound.get(matchIndex * 2);
    }

    public SongModel getContenderB() {
        return currentRound.get(matchIndex * 2 + 1);
    }

    public void reportWinner(SongModel winner) {
        SongModel a = getContenderA();
        SongModel b = getContenderB();
        SongModel loser = winner == a ? b : a;
        nextRound.add(winner);
        losersByRound.get(losersByRound.size() - 1).add(loser);
        matchesPlayedInRound++;
        matchIndex++;
        skipByes();
    }

    public String getRoundName() {
        int slots = currentRound.size();
        if (slots == 2) return "Final";
        if (slots == 4) return "Semi-finals";
        if (slots == 8) return "Quarter-finals";
        return "Round of " + slots;
    }

    public int getMatchNumber() {
        return matchesPlayedInRound + 1;
    }

    public int getMatchesInRound() {
        return totalMatchesInRound;
    }

    /**
     * Final standings, only valid once {@link #isFinished()}. The champion is
     * rank 1; every loser's rank is the position of their elimination round,
     * so both Semi-final losers share rank 3, Quarter-final losers rank 5, etc.
     */
    public List<LeaderboardEntry> getLeaderboard() {
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        leaderboard.add(new LeaderboardEntry(1, champion));
        int rank = 2;
        for (int round = losersByRound.size() - 1; round >= 0; round--) {
            List<SongModel> losers = losersByRound.get(round);
            for (int i = losers.size() - 1; i >= 0; i--) {
                leaderboard.add(new LeaderboardEntry(rank, losers.get(i)));
            }
            rank += losers.size();
        }
        return leaderboard;
    }
}
