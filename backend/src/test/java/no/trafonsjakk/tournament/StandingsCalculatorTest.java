package no.trafonsjakk.tournament;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Comparator;
import java.util.List;
import no.trafonsjakk.tournament.StandingsCalculator.Result;
import no.trafonsjakk.tournament.StandingsCalculator.Standing;
import org.junit.jupiter.api.Test;

class StandingsCalculatorTest {

    private static final Comparator<Long> BY_ID = Comparator.naturalOrder();

    @Test
    void pointsThenSonnebornBerger() {
        // A beats B, B beats C, C draws A, D loses to all, A beats D... simple 4-player table
        List<Long> players = List.of(1L, 2L, 3L, 4L);
        List<Result> results = List.of(
                new Result(1, 2, 1),    // A beats B
                new Result(2, 3, 1),    // B beats C
                new Result(3, 1, 0.5),  // C draws A
                new Result(1, 4, 1),    // A beats D
                new Result(2, 4, 1),    // B beats D
                new Result(4, 3, 0));   // C beats D
        List<Standing> table = StandingsCalculator.compute(players, results, BY_ID);

        assertEquals(1L, table.get(0).playerId()); // A: 2.5
        assertEquals(2.5, table.get(0).points());
        assertEquals(2L, table.get(1).playerId()); // B: 2
        assertEquals(3L, table.get(2).playerId()); // C: 1.5
        assertEquals(4L, table.get(3).playerId()); // D: 0
        assertEquals(3, table.get(0).played());
        assertEquals(1, table.get(0).draws());
        // SB for A: beat B (2) + beat D (0) + half of C (1.5) = 2.75
        assertEquals(2.75, table.get(0).sonnebornBerger(), 1e-9);
    }

    @Test
    void directEncounterBreaksTwoWayTie() {
        // Players 2 and 4 finish level on points (2), Sonneborn–Berger (4) and wins (1),
        // but 4 beat 2 in their game, so 4 must rank ahead even though the id tie-break prefers 2.
        List<Long> players = List.of(1L, 2L, 3L, 4L, 5L);
        List<Result> results = List.of(
                new Result(1, 2, 0.5), new Result(1, 3, 0), new Result(1, 4, 0.5), new Result(1, 5, 0),
                new Result(2, 3, 0.5), new Result(2, 4, 0), new Result(2, 5, 1),
                new Result(3, 4, 0.5), new Result(3, 5, 1),
                new Result(4, 5, 0));
        List<Long> order = StandingsCalculator.compute(players, results, BY_ID).stream()
                .map(Standing::playerId).toList();
        assertEquals(3L, order.get(0));
        int idx2 = order.indexOf(2L);
        int idx4 = order.indexOf(4L);
        assertEquals(1, Math.abs(idx2 - idx4));
        assertEquals(true, idx4 < idx2);
    }

    @Test
    void emptyTableUsesFinalTieBreak() {
        List<Standing> table = StandingsCalculator.compute(List.of(9L, 3L, 5L), List.of(), BY_ID);
        assertEquals(List.of(3L, 5L, 9L), table.stream().map(Standing::playerId).toList());
    }
}
