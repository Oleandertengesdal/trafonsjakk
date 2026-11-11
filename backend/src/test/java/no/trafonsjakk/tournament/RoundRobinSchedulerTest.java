package no.trafonsjakk.tournament;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import no.trafonsjakk.tournament.RoundRobinScheduler.Pairing;
import org.junit.jupiter.api.Test;

class RoundRobinSchedulerTest {

    private static List<Long> players(int n) {
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= n; i++) {
            ids.add(i * 10);
        }
        return ids;
    }

    @Test
    void everyPairMeetsExactlyOnceAndColoursAreBalanced() {
        for (int n = 2; n <= 16; n++) {
            List<Long> ids = players(n);
            List<Pairing> pairings = RoundRobinScheduler.schedule(ids, false);

            assertEquals(n * (n - 1) / 2, pairings.size(), "pairings for n=" + n);

            Set<String> pairs = new HashSet<>();
            Map<Long, Integer> balance = new HashMap<>();
            Map<Integer, Set<Long>> perRound = new HashMap<>();
            for (Pairing p : pairings) {
                long lo = Math.min(p.white(), p.black());
                long hi = Math.max(p.white(), p.black());
                assertTrue(pairs.add(lo + "-" + hi), "duplicate pair " + lo + "-" + hi + " for n=" + n);
                Set<Long> round = perRound.computeIfAbsent(p.round(), k -> new HashSet<>());
                assertTrue(round.add(p.white()) && round.add(p.black()), "player twice in a round, n=" + n);
                balance.merge(p.white(), 1, Integer::sum);
                balance.merge(p.black(), -1, Integer::sum);
            }
            assertEquals(RoundRobinScheduler.roundCount(n, false), perRound.size(), "rounds for n=" + n);
            for (Map.Entry<Long, Integer> e : balance.entrySet()) {
                assertTrue(Math.abs(e.getValue()) <= 1,
                        "colour imbalance " + e.getValue() + " for player " + e.getKey() + ", n=" + n);
            }
        }
    }

    @Test
    void doubleRoundRobinSwapsColours() {
        List<Pairing> pairings = RoundRobinScheduler.schedule(players(4), true);
        assertEquals(12, pairings.size());
        assertEquals(6, RoundRobinScheduler.roundCount(4, true));
        Set<String> directed = new HashSet<>();
        for (Pairing p : pairings) {
            assertTrue(directed.add(p.white() + ">" + p.black()), "same colours twice");
        }
    }

    @Test
    void oddCountGivesEachPlayerOneBye() {
        List<Pairing> pairings = RoundRobinScheduler.schedule(players(5), false);
        assertEquals(10, pairings.size());
        assertEquals(5, RoundRobinScheduler.roundCount(5, false));
        Map<Long, Integer> games = new HashMap<>();
        for (Pairing p : pairings) {
            games.merge(p.white(), 1, Integer::sum);
            games.merge(p.black(), 1, Integer::sum);
        }
        games.values().forEach(g -> assertEquals(4, g));
    }

    @Test
    void rejectsTooFewOrDuplicatePlayers() {
        assertThrows(IllegalArgumentException.class, () -> RoundRobinScheduler.schedule(List.of(1L), false));
        assertThrows(IllegalArgumentException.class, () -> RoundRobinScheduler.schedule(List.of(1L, 1L), false));
    }
}
