package no.trafonsjakk.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import no.trafonsjakk.tournament.SwissPairing.Player;
import org.junit.jupiter.api.Test;

class SwissPairingTest {

    private static Player fresh(long id, int seed) {
        return new Player(id, 0, seed, Set.of(), 0, 0, null, false);
    }

    @Test
    void theFirstRoundIsTopHalfAgainstBottomHalf() {
        List<Player> eight = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            eight.add(fresh(i, i));
        }
        SwissPairing.Round r = SwissPairing.pair(eight);
        assertThat(r.bye()).isNull();
        assertThat(r.pairings()).extracting(p -> Set.of(p.white(), p.black()))
                .containsExactly(Set.of(1L, 5L), Set.of(2L, 6L), Set.of(3L, 7L), Set.of(4L, 8L));
        // Colours alternate by board in the first round
        assertThat(r.pairings().get(0).white()).isEqualTo(1L);
        assertThat(r.pairings().get(1).white()).isEqualTo(6L);
    }

    @Test
    void theLowestRankedWithoutAByeSitsOut() {
        List<Player> five = List.of(
                new Player(1, 2, 1, Set.of(2L, 3L), 1, 1, false, false),
                new Player(2, 1, 2, Set.of(1L, 4L), 1, 1, true, false),
                new Player(3, 1, 3, Set.of(1L), 1, 0, true, false),
                new Player(4, 1, 4, Set.of(2L), 0, 1, false, true),   // had the bye
                new Player(5, 0, 5, Set.of(), 0, 0, null, false));
        SwissPairing.Round r = SwissPairing.pair(five);
        assertThat(r.bye()).isEqualTo(5L);
        for (SwissPairing.Pairing p : r.pairings()) {
            Player w = five.stream().filter(x -> x.id() == p.white()).findFirst().orElseThrow();
            assertThat(w.played()).doesNotContain(p.black());
        }
    }

    @Test
    void whoeverHadWhiteLessGetsWhite() {
        SwissPairing.Round r = SwissPairing.pair(List.of(
                new Player(1, 1, 1, Set.of(3L), 1, 0, true, false),
                new Player(2, 1, 2, Set.of(4L), 0, 1, false, false),
                new Player(3, 0, 3, Set.of(1L), 0, 1, false, false),
                new Player(4, 0, 4, Set.of(2L), 1, 0, true, false)));
        assertThat(r.pairings().get(0).white()).isEqualTo(2L);
        assertThat(r.pairings().get(0).black()).isEqualTo(1L);
        assertThat(r.pairings().get(1).white()).isEqualTo(3L);
    }

    @Test
    void noPairingWhenEveryoneHasMetEveryone() {
        assertThat(SwissPairing.pair(List.of(new Player(1, 1, 1, Set.of(2L), 1, 0, true, false),
                new Player(2, 0, 2, Set.of(1L), 0, 1, false, false)))).isNull();
    }

    @Test
    void roundsDependOnThePlayers() {
        assertThat(Map.of(3, SwissPairing.defaultRounds(3), 8, SwissPairing.defaultRounds(8), 9, SwissPairing.defaultRounds(9),
                32, SwissPairing.defaultRounds(32))).isEqualTo(Map.of(3, 2, 8, 4, 9, 5, 32, 6));
        assertThat(SwissPairing.maxRounds(40)).isEqualTo(15);
    }

    @Test
    void standingsUseByesAndBuchholz() {
        // 1 beat 2, 3 drew with 4, 3 beat 2; 4 had a bye
        List<SwissStandings.Standing> s = SwissStandings.compute(Map.of(1L, 1, 2L, 2, 3L, 3, 4L, 4),
                List.of(new StandingsCalculator.Result(1, 2, 1), new StandingsCalculator.Result(3, 4, 0.5),
                        new StandingsCalculator.Result(2, 3, 0)), List.of(4L));
        SwissStandings.Standing top = s.get(0);
        assertThat(top.playerId()).isEqualTo(3L);
        assertThat(top.points()).isEqualTo(1.5);
        SwissStandings.Standing four = s.stream().filter(x -> x.playerId() == 4L).findFirst().orElseThrow();
        assertThat(four.points()).isEqualTo(1.5);
        assertThat(four.byes()).isEqualTo(1);
        assertThat(four.played()).isEqualTo(1);
        assertThat(four.buchholz()).as("only the real opponent").isEqualTo(1.5);
        assertThat(top.buchholz()).isEqualTo(1.5 + 0); // 4 and 2
        // 3 and 4: equal points, Buchholz and Sonneborn–Berger; 3 has more wins
        assertThat(s.indexOf(top)).isLessThan(s.indexOf(four));
        assertThat(s.get(2).playerId()).isEqualTo(1L);
    }
}
