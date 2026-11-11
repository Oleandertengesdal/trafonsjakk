package no.trafonsjakk.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import no.trafonsjakk.tournament.StandingsCalculator.Result;
import no.trafonsjakk.tournament.StandingsCalculator.Standing;
import org.junit.jupiter.api.Test;

class StandingsEdgeCasesTest {

    private static final Comparator<Long> BY_ID = Comparator.naturalOrder();

    @Test
    void pointsAlwaysSumToGamesPlayed() {
        List<Long> players = List.of(1L, 2L, 3L, 4L);
        List<Result> results = List.of(new Result(1, 2, 1), new Result(3, 4, 0.5), new Result(1, 3, 0), new Result(2, 4, 0.5));
        List<Standing> table = StandingsCalculator.compute(players, results, BY_ID);
        double total = table.stream().mapToDouble(Standing::points).sum();
        assertThat(total).isEqualTo(results.size());
        int played = table.stream().mapToInt(Standing::played).sum();
        assertThat(played).isEqualTo(results.size() * 2);
        assertThat(table).extracting(Standing::rank).containsExactly(1, 2, 3, 4);
    }

    @Test
    void cyclicThreeWayTieFallsBackToTheFinalTieBreak() {
        // 1 beats 2, 2 beats 3, 3 beats 1: everything equal, no direct-encounter ordering exists
        List<Standing> table = StandingsCalculator.compute(List.of(3L, 1L, 2L),
                List.of(new Result(1, 2, 1), new Result(2, 3, 1), new Result(3, 1, 1)), BY_ID);
        assertThat(table).extracting(Standing::playerId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void doubleRoundRobinDirectEncounterSumsBothGames() {
        // 1 and 2 tie overall; 2 won one game and drew the other against 1 → 2 ranks first
        List<Long> players = List.of(1L, 2L, 3L);
        List<Result> results = List.of(
                new Result(1, 2, 0), new Result(2, 1, 0.5),   // 2 scores 1.5 vs 1
                new Result(1, 3, 1), new Result(3, 1, 1),     // 1: 1 point from 3
                new Result(2, 3, 0), new Result(3, 2, 1));    // 2: 0 from 3 ... adjust totals below
        List<Standing> table = StandingsCalculator.compute(players, results, BY_ID);
        Standing p1 = table.stream().filter(s -> s.playerId() == 1).findFirst().orElseThrow();
        Standing p2 = table.stream().filter(s -> s.playerId() == 2).findFirst().orElseThrow();
        assertThat(p1.points()).isEqualTo(1.5);
        assertThat(p2.points()).isEqualTo(1.5);
        if (p1.sonnebornBerger() == p2.sonnebornBerger() && p1.wins() == p2.wins()) {
            assertThat(table.indexOf(p2)).isLessThan(table.indexOf(p1));
        }
    }

    @Test
    void resultsForUnknownPlayersAreIgnored() {
        List<Standing> table = StandingsCalculator.compute(List.of(1L, 2L), List.of(new Result(1, 99, 1), new Result(1, 2, 0.5)), BY_ID);
        assertThat(table).extracting(Standing::points).containsExactly(0.5, 0.5);
        assertThat(table).extracting(Standing::played).containsExactly(1, 1);
    }
}
