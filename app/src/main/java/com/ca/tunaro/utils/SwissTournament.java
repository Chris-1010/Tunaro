package com.ca.tunaro.utils;

import com.ca.tunaro.models.RankedSong;
import com.ca.tunaro.models.SongModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Swiss-system tournament with a progressive cut, producing a ranking of the top
 * songs in a pool.
 *
 * <p>Every song plays a series of rounds. Each round pairs songs of equal record
 * that have not yet met; the odd one out takes a bye (a free point). Unlike a
 * single-elimination bracket, one loss never dooms a song: a beaten favourite
 * drops into the group of other losers, wins its way back up, and its strength of
 * schedule (Buchholz — the summed scores of the songs it faced) rewards having
 * lost only to the eventual champion. Two strong songs colliding in round one no
 * longer orphans the loser.
 *
 * <p>To keep the number of matches near a single-elimination placement bracket's
 * rather than a full Swiss's, the field is <em>progressively cut</em>: after two
 * opening rounds (enough for a round-one loser to prove itself) the lowest-standing
 * songs are dropped each round until only the contending top block remains, which
 * then plays a few finishing rounds to settle its order. The block scales with the
 * pool ({@link #blockFor}), so a bigger game ranks a deeper slice.
 *
 * <p>The opening order is the entrant list order, so the caller decides seeding —
 * pass entrants sorted by cross-game Elo to seed by past strength, or shuffled for
 * a fresh pool. Final rank is score, then Buchholz, then seed.
 *
 * <p>State is serialisable by replay: persist the ordered entrant ids and the
 * winner id of each decided match, then reconstruct with {@link #restore}. Byes and
 * pairings are recomputed deterministically, so they are never stored.
 */
public class SwissTournament {

    // The contending block the finishing rounds resolve scales with the pool: a
    // quarter of the field, floored so a small pool still ranks a meaningful slice
    // and capped so a huge pool does not drag the finishing rounds out. A 128-song
    // game (the sampling cap) lands exactly on the maximum.
    public static final int MIN_RANKED_PLACES = 16;
    public static final int MAX_RANKED_PLACES = 32;
    private static final double BLOCK_FRACTION = 0.25;

    /** Size of the finely-ranked contending block for a pool of {@code n} songs. */
    public static int blockFor(int n) {
        int block = Math.max(MIN_RANKED_PLACES, (int) Math.ceil(n * BLOCK_FRACTION));
        return Math.min(Math.min(block, MAX_RANKED_PLACES), n);
    }

    // Two full opening rounds before any cut, so a round-one loser has a chance to
    // win a match and stay in contention rather than being dropped for one defeat.
    private static final int SEED_ROUNDS = 2;
    // Rounds played once the field has been cut down to the contending block, to
    // settle its internal order.
    private static final int FINAL_ROUNDS = 2;
    // Fraction of the field kept at each cut round (the rest are dropped).
    private static final double KEEP_FRACTION = 0.5;

    /** One decided real-vs-real match: the song the user picked and the one they didn't. */
    public static class Matchup {
        public final SongModel winner;
        public final SongModel loser;

        Matchup(SongModel winner, SongModel loser) {
            this.winner = winner;
            this.loser = loser;
        }
    }

    // A round's pairings and its bye, recorded as it is set up so the standings
    // graphic can redraw the whole tournament at any point.
    private static class RoundRecord {
        final List<SongModel> a = new ArrayList<>();
        final List<SongModel> b = new ArrayList<>();
        final List<String> winnerIds = new ArrayList<>();  // null entries until decided
        SongModel byeSong;                                 // null when the field was even
    }

    private final List<SongModel> entrants;                // seed order (index = seed)
    private final Map<String, Integer> seedOf = new HashMap<>();
    private final Map<String, Integer> score = new HashMap<>();
    private final Map<String, Set<String>> met = new HashMap<>();
    private final Set<String> hadBye = new HashSet<>();

    private final int rankedPlaces;                        // finely-ranked block size
    private final List<Integer> schedule;                  // active count per round
    private final int totalUserMatches;

    private final List<String> decisionWinnerIds = new ArrayList<>();
    private final List<Matchup> userMatchups = new ArrayList<>();
    private int userMatchesPlayed = 0;

    // The songs still in contention, in standing order as of the current round.
    private List<SongModel> active;
    private final List<RoundRecord> rounds = new ArrayList<>();
    private int roundIndex = 0;
    private int pairIndex = 0;
    private boolean finished = false;

    public SwissTournament(List<SongModel> entrants) {
        if (entrants == null || entrants.size() < 2) {
            throw new IllegalArgumentException("Tournament needs at least 2 songs");
        }
        this.entrants = new ArrayList<>(entrants);
        for (int i = 0; i < this.entrants.size(); i++) {
            String id = this.entrants.get(i).getId();
            seedOf.put(id, i);
            score.put(id, 0);
            met.put(id, new HashSet<>());
        }
        this.rankedPlaces = blockFor(this.entrants.size());
        this.schedule = computeSchedule(this.entrants.size(), rankedPlaces);
        int total = 0;
        for (int count : schedule) total += count / 2;   // one bye when odd, else none
        this.totalUserMatches = total;

        this.active = new ArrayList<>(this.entrants);
        startRoundAt(0);
    }

    /**
     * Reconstruct a tournament from persisted state: the same ordered entrants and
     * the winner id recorded for each decided match, replayed in order.
     */
    public static SwissTournament restore(List<SongModel> entrants, List<String> decisionWinnerIds) {
        SwissTournament tournament = new SwissTournament(entrants);
        if (decisionWinnerIds == null) return tournament;
        for (String winnerId : decisionWinnerIds) {
            if (tournament.isFinished()) break;
            SongModel a = tournament.getContenderA();
            SongModel winner = a.getId().equals(winnerId) ? a : tournament.getContenderB();
            tournament.reportWinner(winner);
        }
        return tournament;
    }

    // The active-set size for each round: two full opening rounds, then halve the
    // field each round down to the contending block, then a few finishing rounds.
    // Tiny pools are capped at a round robin's worth so pairings never repeat.
    private static List<Integer> computeSchedule(int n, int rankedPlaces) {
        List<Integer> s = new ArrayList<>();
        int active = n;
        for (int i = 0; i < SEED_ROUNDS; i++) s.add(active);
        while (active > rankedPlaces) {
            active = Math.max(rankedPlaces, (int) Math.ceil(active * KEEP_FRACTION));
            s.add(active);
        }
        int finalActive = Math.min(n, rankedPlaces);
        for (int i = 0; i < FINAL_ROUNDS; i++) s.add(finalActive);

        int maxRounds = Math.max(1, n - 1);
        while (s.size() > maxRounds) s.remove(s.size() - 1);
        return s;
    }

    // ---- Round setup ----

    private void startRoundAt(int r) {
        while (r < schedule.size()) {
            RoundRecord record = buildRound(r);
            if (!record.a.isEmpty()) {
                rounds.add(record);
                roundIndex = r;
                pairIndex = 0;
                return;
            }
            // A round with no real pairings (a degenerate tiny field): skip it.
            r++;
        }
        finished = true;
        roundIndex = schedule.size();
    }

    private RoundRecord buildRound(int r) {
        // Cut to this round's active count by current standing, then keep that order.
        active.sort(standingComparator());
        int keep = Math.min(schedule.get(r), active.size());
        if (active.size() > keep) {
            active = new ArrayList<>(active.subList(0, keep));
        }

        RoundRecord record = new RoundRecord();

        // Odd field: the lowest-standing song without a prior bye sits out for a
        // free point.
        if (active.size() % 2 == 1) {
            SongModel bye = null;
            for (int i = active.size() - 1; i >= 0; i--) {
                if (!hadBye.contains(active.get(i).getId())) {
                    bye = active.get(i);
                    break;
                }
            }
            if (bye == null) bye = active.get(active.size() - 1);
            score.merge(bye.getId(), 1, Integer::sum);
            hadBye.add(bye.getId());
            record.byeSong = bye;
        }

        // Pair the rest top-down. Prefer a set of pairings in which no two songs
        // have met before; only if no rematch-free pairing exists at all does the
        // greedy fallback allow a repeat.
        List<SongModel> pool = new ArrayList<>();
        for (SongModel s : active) {
            if (s != record.byeSong) pool.add(s);
        }
        List<int[]> pairs = pairNoRematch(pool);
        if (pairs == null) pairs = pairGreedyAllowRematch(pool);
        for (int[] pair : pairs) {
            record.a.add(pool.get(pair[0]));
            record.b.add(pool.get(pair[1]));
            record.winnerIds.add(null);
        }
        return record;
    }

    // Depth-first search for a complete pairing of the pool in which no pair has met
    // before. Always pairs the highest-standing unpaired song with its nearest
    // unmet partner first, so the result is deterministic and keeps strong songs
    // near the top. Returns null when no rematch-free pairing of the whole pool
    // exists.
    private List<int[]> pairNoRematch(List<SongModel> pool) {
        List<int[]> result = new ArrayList<>();
        return backtrackPair(pool, new boolean[pool.size()], result) ? result : null;
    }

    private boolean backtrackPair(List<SongModel> pool, boolean[] used, List<int[]> result) {
        int i = -1;
        for (int k = 0; k < pool.size(); k++) {
            if (!used[k]) { i = k; break; }
        }
        if (i == -1) return true;   // everyone paired
        used[i] = true;
        for (int j = i + 1; j < pool.size(); j++) {
            if (used[j] || hasMet(pool.get(i), pool.get(j))) continue;
            used[j] = true;
            result.add(new int[]{i, j});
            if (backtrackPair(pool, used, result)) return true;
            result.remove(result.size() - 1);
            used[j] = false;
        }
        used[i] = false;
        return false;
    }

    // Last resort when the whole pool has met before: pair each song with its
    // nearest still-unpaired neighbour regardless, accepting a rematch.
    private List<int[]> pairGreedyAllowRematch(List<SongModel> pool) {
        List<int[]> pairs = new ArrayList<>();
        boolean[] used = new boolean[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            if (used[i]) continue;
            for (int j = i + 1; j < pool.size(); j++) {
                if (used[j]) continue;
                used[i] = true;
                used[j] = true;
                pairs.add(new int[]{i, j});
                break;
            }
        }
        return pairs;
    }

    private Comparator<SongModel> standingComparator() {
        return (x, y) -> {
            int sx = score.getOrDefault(x.getId(), 0);
            int sy = score.getOrDefault(y.getId(), 0);
            if (sx != sy) return Integer.compare(sy, sx);
            int bx = buchholz(x.getId());
            int by = buchholz(y.getId());
            if (bx != by) return Integer.compare(by, bx);
            return Integer.compare(seedOf.get(x.getId()), seedOf.get(y.getId()));
        };
    }

    private int buchholz(String id) {
        int sum = 0;
        for (String opp : met.get(id)) sum += score.getOrDefault(opp, 0);
        return sum;
    }

    private boolean hasMet(SongModel a, SongModel b) {
        return met.get(a.getId()).contains(b.getId());
    }

    // ---- Play ----

    public boolean isFinished() {
        return finished;
    }

    public SongModel getContenderA() {
        return rounds.get(roundIndex).a.get(pairIndex);
    }

    public SongModel getContenderB() {
        return rounds.get(roundIndex).b.get(pairIndex);
    }

    public void reportWinner(SongModel winner) {
        RoundRecord record = rounds.get(roundIndex);
        SongModel a = record.a.get(pairIndex);
        SongModel b = record.b.get(pairIndex);
        // Identify the loser by id so a winner reconstructed from persisted state
        // still matches the correct contender.
        SongModel loser = winner.getId().equals(a.getId()) ? b : a;

        score.merge(winner.getId(), 1, Integer::sum);
        met.get(a.getId()).add(b.getId());
        met.get(b.getId()).add(a.getId());
        record.winnerIds.set(pairIndex, winner.getId());

        decisionWinnerIds.add(winner.getId());
        userMatchups.add(new Matchup(winner, loser));
        userMatchesPlayed++;

        pairIndex++;
        if (pairIndex >= record.a.size()) {
            startRoundAt(roundIndex + 1);
        }
    }

    /**
     * Final standings, valid once {@link #isFinished()}. The surviving contenders,
     * ordered by score, then Buchholz, then seed, densely ranked 1..K.
     */
    public List<RankedSong> getFinalRanking() {
        List<SongModel> ordered = new ArrayList<>(active);
        ordered.sort(standingComparator());
        List<RankedSong> ranking = new ArrayList<>();
        int rank = 1;
        for (SongModel song : ordered) ranking.add(new RankedSong(rank++, song));
        return ranking;
    }

    public int getUserMatchesPlayed() {
        return userMatchesPlayed;
    }

    public int getTotalUserMatches() {
        return totalUserMatches;
    }

    public int getMatchNumber() {
        return userMatchesPlayed + 1;
    }

    /** Human-readable label for the current round. */
    public String getSegmentLabel() {
        if (finished) return "";
        return "Round " + (roundIndex + 1) + " of " + schedule.size();
    }

    public List<String> getEntrantIds() {
        List<String> ids = new ArrayList<>();
        for (SongModel song : entrants) ids.add(song.getId());
        return ids;
    }

    public List<String> getDecisionWinnerIds() {
        return new ArrayList<>(decisionWinnerIds);
    }

    /**
     * Every real-vs-real match the user has decided, in order. Used to roll a
     * finished game's results into the cross-game Elo ratings in one pass (ratings
     * are applied only once the game completes, so an undo mid-game leaves nothing
     * to unwind).
     */
    public List<Matchup> getUserMatchups() {
        return new ArrayList<>(userMatchups);
    }

    // ---- Read-only standings graphic ----
    //
    // The tournament laid out for display: a column of pairings per round the user
    // has reached, and a live ladder of every entrant by current standing.

    /** A single pairing box: the two songs, the winner once decided, and flags for
     *  a bye and for the live match awaiting the user. */
    public static class GraphMatch {
        public final SongModel a;          // the bye song when {@code bye} is set
        public final SongModel b;          // null for a bye
        public final String winnerId;      // null until decided
        public final boolean decided;
        public final boolean bye;          // this song sat the round out for a free point
        public final boolean current;      // the match the user is on right now
        public final int round;            // 0-based round index

        GraphMatch(SongModel a, SongModel b, String winnerId,
                   boolean decided, boolean bye, boolean current, int round) {
            this.a = a;
            this.b = b;
            this.winnerId = winnerId;
            this.decided = decided;
            this.bye = bye;
            this.current = current;
            this.round = round;
        }
    }

    /** One round's pairings, top-to-bottom by standing. */
    public static class GraphRound {
        public final int index;
        public final String label;
        public final List<GraphMatch> matches = new ArrayList<>();

        GraphRound(int index, String label) {
            this.index = index;
            this.label = label;
        }
    }

    /** A snapshot of every reached round for the pairings columns. */
    public List<GraphRound> getGraphRounds() {
        List<GraphRound> out = new ArrayList<>();
        for (int r = 0; r < rounds.size(); r++) {
            RoundRecord record = rounds.get(r);
            GraphRound round = new GraphRound(r, "Round " + (r + 1));
            for (int k = 0; k < record.a.size(); k++) {
                String winnerId = record.winnerIds.get(k);
                boolean decided = winnerId != null;
                boolean current = !finished && r == roundIndex && k == pairIndex;
                round.matches.add(new GraphMatch(record.a.get(k), record.b.get(k),
                        winnerId, decided, false, current, r));
            }
            if (record.byeSong != null) {
                round.matches.add(new GraphMatch(record.byeSong, null, record.byeSong.getId(),
                        true, true, false, r));
            }
            out.add(round);
        }
        return out;
    }

    /** One rung of the live standings ladder. */
    public static class StandingRow {
        public final int rank;             // 1-based, by current standing
        public final SongModel song;
        public final int score;
        public final int played;           // real matches this song has completed
        public final boolean active;       // still in contention (not yet cut)

        StandingRow(int rank, SongModel song, int score, int played, boolean active) {
            this.rank = rank;
            this.song = song;
            this.score = score;
            this.played = played;
            this.active = active;
        }
    }

    /** Every entrant, in current standing order, for the live ladder. */
    public List<StandingRow> getStandings() {
        Set<String> activeIds = new HashSet<>();
        for (SongModel s : active) activeIds.add(s.getId());

        List<SongModel> ordered = new ArrayList<>(entrants);
        ordered.sort(standingComparator());
        List<StandingRow> rows = new ArrayList<>();
        int rank = 1;
        for (SongModel song : ordered) {
            String id = song.getId();
            rows.add(new StandingRow(rank++, song, score.getOrDefault(id, 0),
                    met.get(id).size(), activeIds.contains(id)));
        }
        return rows;
    }

    /** Total rounds the schedule will play. */
    public int getTotalRounds() {
        return schedule.size();
    }

    /** 1-based current round, or the total once finished. */
    public int getCurrentRound() {
        return Math.min(roundIndex + 1, schedule.size());
    }
}
