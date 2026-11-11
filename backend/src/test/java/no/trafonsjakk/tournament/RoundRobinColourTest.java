package no.trafonsjakk.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import no.trafonsjakk.tournament.RoundRobinScheduler.Pairing;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Colour fairness beyond the overall balance: nobody should get the same colour three times in a row. */
class RoundRobinColourTest {

    private static List<Long> players(int n) {
        return LongStream.rangeClosed(1, n).boxed().toList();
    }

    /** Colour sequence per player in round order: true = white. */
    private static Map<Long, List<Boolean>> sequences(List<Pairing> pairings) {
        Map<Long, List<Boolean>> seq = new HashMap<>();
        pairings.stream().sorted(Comparator.comparingInt(Pairing::round)).forEach(p -> {
            seq.computeIfAbsent(p.white(), k -> new ArrayList<>()).add(true);
            seq.computeIfAbsent(p.black(), k -> new ArrayList<>()).add(false);
        });
        return seq;
    }

    @ParameterizedTest(name = "{0} players")
    @ValueSource(ints = {3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16, 20, 32})
    void noPlayerGetsTheSameColourThreeTimesInARow(int n) {
        sequences(RoundRobinScheduler.schedule(players(n), false)).forEach((player, colours) -> {
            for (int i = 2; i < colours.size(); i++) {
                boolean triple = colours.get(i) == colours.get(i - 1) && colours.get(i) == colours.get(i - 2);
                assertThat(triple).as("player %d has colours %s (n=%d)", player, colours, n).isFalse();
            }
        });
    }

    @ParameterizedTest(name = "{0} players, double")
    @ValueSource(ints = {3, 4, 5, 6, 7, 8, 10, 12})
    void doubleRoundRobinAlsoAvoidsThreeInARowAcrossBothCycles(int n) {
        sequences(RoundRobinScheduler.schedule(players(n), true)).forEach((player, colours) -> {
            for (int i = 2; i < colours.size(); i++) {
                boolean triple = colours.get(i) == colours.get(i - 1) && colours.get(i) == colours.get(i - 2);
                assertThat(triple).as("player %d has colours %s (double, n=%d)", player, colours, n).isFalse();
            }
        });
    }

    @ParameterizedTest(name = "{0} players, double")
    @ValueSource(ints = {3, 4, 5, 6, 8})
    void doubleRoundRobinGivesEveryoneExactlyEqualColours(int n) {
        sequences(RoundRobinScheduler.schedule(players(n), true)).forEach((player, colours) -> {
            long whites = colours.stream().filter(c -> c).count();
            assertThat(whites * 2).as("player %d colours %s", player, colours).isEqualTo(colours.size());
        });
    }

    @ParameterizedTest(name = "{0} players")
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8})
    void boardsAreNumberedFromZeroWithoutGapsEachRound(int n) {
        Map<Integer, List<Integer>> boards = new HashMap<>();
        for (Pairing p : RoundRobinScheduler.schedule(players(n), false)) {
            boards.computeIfAbsent(p.round(), k -> new ArrayList<>()).add(p.board());
        }
        boards.forEach((round, list) -> {
            List<Integer> sorted = list.stream().sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                assertThat(sorted.get(i)).as("round %d boards %s", round, sorted).isEqualTo(i);
            }
        });
    }
}
