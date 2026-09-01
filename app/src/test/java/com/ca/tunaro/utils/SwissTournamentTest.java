package com.ca.tunaro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ca.tunaro.models.RankedSong;
import com.ca.tunaro.models.SongModel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SwissTournamentTest {

    private static List<SongModel> songs(int count) {
        List<SongModel> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new SongModel("spotify:track:" + i, "Song " + i, "Artist " + i,
                    180000, "spotify:track:" + i, null, 0, "Album " + i, "2020-01-01"));
        }
        return list;
    }

    // Play every match, always picking contender A; return the matches presented.
    private static int playThrough(SwissTournament tournament) {
        int matchesPlayed = 0;
        while (!tournament.isFinished()) {
            assertNotNull("contender A must be a real song", tournament.getContenderA());
            assertNotNull("contender B must be a real song", tournament.getContenderB());
            tournament.reportWinner(tournament.getContenderA());
            matchesPlayed++;
        }
        return matchesPlayed;
    }

    // Play through, always picking the lower-seeded (earlier) contender, which
    // spreads results more realistically than always-A.
    private static int playThroughLowerSeedWins(SwissTournament tournament) {
        int matchesPlayed = 0;
        while (!tournament.isFinished()) {
            SongModel a = tournament.getContenderA();
            SongModel b = tournament.getContenderB();
            int ai = Integer.parseInt(a.getId().substring(a.getId().lastIndexOf(':') + 1));
            int bi = Integer.parseInt(b.getId().substring(b.getId().lastIndexOf(':') + 1));
            tournament.reportWinner(ai <= bi ? a : b);
            matchesPlayed++;
        }
        return matchesPlayed;
    }

    private static int expectedRankingSize(int n) {
        return SwissTournament.blockFor(n);
    }

    @Test
    public void producesDenseRankingForVariousSizes() {
        for (int n : new int[]{2, 3, 4, 5, 6, 7, 8, 11, 16, 17, 20, 32, 64, 82}) {
            SwissTournament tournament = new SwissTournament(songs(n));
            playThrough(tournament);

            List<RankedSong> ranking = tournament.getFinalRanking();
            assertEquals("ranking size for n=" + n, expectedRankingSize(n), ranking.size());

            Set<SongModel> seen = new HashSet<>();
            for (int i = 0; i < ranking.size(); i++) {
                assertEquals("rank must be dense and unique for n=" + n, i + 1, ranking.get(i).rank);
                assertTrue("no song ranked twice for n=" + n, seen.add(ranking.get(i).song));
            }
        }
    }

    @Test
    public void advertisedTotalMatchesEqualsMatchesPlayed() {
        for (int n : new int[]{2, 3, 4, 5, 8, 13, 16, 17, 32, 64, 82}) {
            SwissTournament tournament = new SwissTournament(songs(n));
            int total = tournament.getTotalUserMatches();
            int played = playThrough(tournament);
            assertEquals("advertised == played for n=" + n, total, played);
            assertEquals("played counter for n=" + n, total, tournament.getUserMatchesPlayed());
        }
    }

    @Test
    public void matchCountStaysNearPlacementNotFullSwiss() {
        // For a large pool the progressive cut keeps the match count in the same
        // ballpark as a placement bracket (~98 for 82), well under a full Swiss
        // (~290). Guards against a schedule change blowing the budget.
        int played = playThrough(new SwissTournament(songs(82)));
        assertTrue("82-song game should stay well under full Swiss, was " + played, played < 180);
        assertTrue("82-song game should still be a real tournament, was " + played, played > 90);
    }

    @Test
    public void everyLiveMatchIsBetweenTwoDistinctRealSongs() {
        SwissTournament tournament = new SwissTournament(songs(37));
        while (!tournament.isFinished()) {
            SongModel a = tournament.getContenderA();
            SongModel b = tournament.getContenderB();
            assertNotNull(a);
            assertNotNull(b);
            assertFalse("a song never plays itself", a.getId().equals(b.getId()));
            tournament.reportWinner(a);
        }
    }

    @Test
    public void noRematchesInModestPools() {
        // Round-robin-sized pools must never repeat a pairing.
        for (int n : new int[]{4, 6, 8, 12, 16}) {
            SwissTournament tournament = new SwissTournament(songs(n));
            Set<String> seen = new HashSet<>();
            while (!tournament.isFinished()) {
                SongModel a = tournament.getContenderA();
                SongModel b = tournament.getContenderB();
                String key = pairKey(a, b);
                assertTrue("no rematch for n=" + n + " pair " + key, seen.add(key));
                tournament.reportWinner(tournament.getContenderA());
            }
        }
    }

    private static String pairKey(SongModel a, SongModel b) {
        String x = a.getId(), y = b.getId();
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }

    @Test
    public void fullFieldPlaysTwoOpeningRoundsBeforeAnyCut() {
        // Every entrant should appear as a contender across the first two rounds:
        // nobody is cut before proving themselves.
        SwissTournament tournament = new SwissTournament(songs(40));
        Set<String> playedInFirstTwoRounds = new HashSet<>();
        while (!tournament.isFinished() && tournament.getCurrentRound() <= 2) {
            playedInFirstTwoRounds.add(tournament.getContenderA().getId());
            playedInFirstTwoRounds.add(tournament.getContenderB().getId());
            tournament.reportWinner(tournament.getContenderA());
        }
        // 40 is even, so no byes in the opening rounds; all 40 play.
        assertEquals(40, playedInFirstTwoRounds.size());
    }

    @Test
    public void beatenFavouriteCanStillReachSecond() {
        // Two strongest songs meet in round one; the loser should climb back near
        // the top rather than being orphaned. Songs 0 and 1 are the "best": song 0
        // beats everyone, song 1 beats everyone except song 0. They are seeded to
        // meet early. Model consistent preferences: lower id always wins.
        int n = 40;
        SwissTournament tournament = new SwissTournament(songs(n));
        playThroughLowerSeedWins(tournament);

        List<RankedSong> ranking = tournament.getFinalRanking();
        assertEquals("champion is the strongest song", "spotify:track:0", ranking.get(0).song.getId());
        assertEquals("runner-up is the second strongest, not orphaned",
                "spotify:track:1", ranking.get(1).song.getId());
    }

    @Test
    public void restoreReplaysToTheSameState() {
        List<SongModel> entrants = songs(37);
        SwissTournament original = new SwissTournament(entrants);

        int half = original.getTotalUserMatches() / 2;
        for (int i = 0; i < half; i++) {
            original.reportWinner(original.getContenderB());
        }
        List<String> entrantIds = original.getEntrantIds();
        List<String> decisions = original.getDecisionWinnerIds();

        SwissTournament restored = SwissTournament.restore(entrants, decisions);
        assertFalse(restored.isFinished());
        assertEquals(entrantIds, restored.getEntrantIds());
        assertEquals(decisions, restored.getDecisionWinnerIds());
        assertEquals(original.getUserMatchesPlayed(), restored.getUserMatchesPlayed());
        assertEquals(original.getContenderA().getId(), restored.getContenderA().getId());
        assertEquals(original.getContenderB().getId(), restored.getContenderB().getId());
    }

    @Test
    public void restoredCompletedGameMatchesFullPlay() {
        List<SongModel> entrants = songs(50);
        SwissTournament original = new SwissTournament(entrants);
        playThroughLowerSeedWins(original);
        List<String> decisions = original.getDecisionWinnerIds();

        SwissTournament restored = SwissTournament.restore(entrants, decisions);
        assertTrue("restored game is finished", restored.isFinished());

        List<RankedSong> a = original.getFinalRanking();
        List<RankedSong> b = restored.getFinalRanking();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals("same order after restore at rank " + i,
                    a.get(i).song.getId(), b.get(i).song.getId());
        }
    }

    @Test
    public void userMatchupsCountEqualsMatchesPlayed() {
        SwissTournament tournament = new SwissTournament(songs(32));
        int played = playThroughLowerSeedWins(tournament);
        assertEquals(played, tournament.getUserMatchups().size());
        for (SwissTournament.Matchup m : tournament.getUserMatchups()) {
            assertNotNull(m.winner);
            assertNotNull(m.loser);
            assertFalse(m.winner.getId().equals(m.loser.getId()));
        }
    }

    // ---- Standings graphic ----

    private static List<SwissTournament.GraphMatch> allGraphMatches(SwissTournament t) {
        List<SwissTournament.GraphMatch> matches = new ArrayList<>();
        for (SwissTournament.GraphRound r : t.getGraphRounds()) {
            matches.addAll(r.matches);
        }
        return matches;
    }

    @Test
    public void graphHasExactlyOneCurrentMatchUntilFinished() {
        SwissTournament tournament = new SwissTournament(songs(20));
        while (!tournament.isFinished()) {
            int current = 0;
            SwissTournament.GraphMatch live = null;
            for (SwissTournament.GraphMatch m : allGraphMatches(tournament)) {
                if (m.current) {
                    current++;
                    live = m;
                }
            }
            assertEquals("exactly one live match while playing", 1, current);
            assertEquals(tournament.getContenderA().getId(), live.a.getId());
            assertEquals(tournament.getContenderB().getId(), live.b.getId());
            assertFalse("the live match is not yet decided", live.decided);
            assertFalse("the live match is not a bye", live.bye);
            tournament.reportWinner(tournament.getContenderA());
        }
        for (SwissTournament.GraphMatch m : allGraphMatches(tournament)) {
            assertFalse("no live match once finished", m.current);
        }
    }

    @Test
    public void graphDecidedRealMatchesTrackMatchesPlayed() {
        for (int n : new int[]{8, 13, 20, 40}) {
            SwissTournament tournament = new SwissTournament(songs(n));
            while (!tournament.isFinished()) {
                int decidedReal = 0;
                for (SwissTournament.GraphMatch m : allGraphMatches(tournament)) {
                    if (m.decided && !m.bye) {
                        decidedReal++;
                        assertNotNull(m.winnerId);
                    }
                }
                assertEquals("decided real matches track play count for n=" + n,
                        tournament.getUserMatchesPlayed(), decidedReal);
                tournament.reportWinner(tournament.getContenderA());
            }
        }
    }

    @Test
    public void standingsListEveryEntrantOnceInRankOrder() {
        SwissTournament tournament = new SwissTournament(songs(25));
        for (int i = 0; i < 10; i++) tournament.reportWinner(tournament.getContenderA());

        List<SwissTournament.StandingRow> rows = tournament.getStandings();
        assertEquals("every entrant appears once", 25, rows.size());
        Set<String> seen = new HashSet<>();
        int prevScore = Integer.MAX_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            SwissTournament.StandingRow row = rows.get(i);
            assertEquals("rank is dense", i + 1, row.rank);
            assertTrue("no song listed twice", seen.add(row.song.getId()));
            assertTrue("standings sorted by score descending", row.score <= prevScore);
            prevScore = row.score;
        }
    }
}
