package com.ca.tunaro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ca.tunaro.models.SongModel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RankingsTournamentTest {

    private static List<SongModel> songs(int count) {
        List<SongModel> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new SongModel("spotify:track:" + i, "Song " + i, "Artist " + i,
                    180000, "spotify:track:" + i, null, 0, "Album " + i, "2020-01-01"));
        }
        return list;
    }

    // Play every match to completion, always picking contender A, and return
    // the number of real matches presented.
    private static int playThrough(RankingsTournament tournament) {
        int matchesPlayed = 0;
        while (!tournament.isFinished()) {
            assertNotNull(tournament.getContenderA());
            assertNotNull(tournament.getContenderB());
            tournament.reportWinner(tournament.getContenderA());
            matchesPlayed++;
        }
        return matchesPlayed;
    }

    @Test
    public void powerOfTwoBracketPlaysAllMatches() {
        RankingsTournament tournament = new RankingsTournament(songs(8));
        assertEquals("Quarter-finals", tournament.getRoundName());
        assertEquals(4, tournament.getMatchesInRound());
        assertEquals(7, playThrough(tournament));
    }

    @Test
    public void byeBracketPresentsOnlyRealMatches() {
        // 5 entrants pad to a bracket of 8 with 3 byes: still n-1 real matches
        RankingsTournament tournament = new RankingsTournament(songs(5));
        assertEquals(1, tournament.getMatchesInRound());
        assertEquals(4, playThrough(tournament));
    }

    @Test
    public void twoSongsIsASingleFinal() {
        RankingsTournament tournament = new RankingsTournament(songs(2));
        assertEquals("Final", tournament.getRoundName());
        assertEquals(1, playThrough(tournament));
    }

    @Test
    public void leaderboardContainsEverySongOnce() {
        List<SongModel> entrants = songs(6);
        RankingsTournament tournament = new RankingsTournament(entrants);
        playThrough(tournament);

        List<RankingsTournament.LeaderboardEntry> leaderboard = tournament.getLeaderboard();
        assertEquals(entrants.size(), leaderboard.size());

        Set<SongModel> seen = new HashSet<>();
        for (RankingsTournament.LeaderboardEntry entry : leaderboard) {
            assertTrue(seen.add(entry.song));
        }
    }

    @Test
    public void leaderboardSharesRanksWithinARound() {
        RankingsTournament tournament = new RankingsTournament(songs(4));

        // Song 0 beats Song 3, Song 1 beats Song 2, Song 0 wins the final
        playThrough(tournament);

        List<RankingsTournament.LeaderboardEntry> leaderboard = tournament.getLeaderboard();
        assertEquals(1, leaderboard.get(0).rank);
        assertEquals(2, leaderboard.get(1).rank);
        assertEquals(3, leaderboard.get(2).rank);
        assertEquals(3, leaderboard.get(3).rank);
    }

    @Test
    public void championHeadsTheLeaderboard() {
        List<SongModel> entrants = songs(8);
        SongModel favourite = entrants.get(3);

        RankingsTournament tournament = new RankingsTournament(entrants);
        while (!tournament.isFinished()) {
            SongModel a = tournament.getContenderA();
            tournament.reportWinner(a == favourite || tournament.getContenderB() != favourite
                    ? a : favourite);
        }

        assertEquals(favourite, tournament.getLeaderboard().get(0).song);
    }
}
