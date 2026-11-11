package no.trafonsjakk.tournament;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import no.trafonsjakk.tournament.KnockoutBracket.Slot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KnockoutBracketInvariantsTest {

    @ParameterizedTest(name = "{0} players")
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8, 9, 12, 15, 16, 17, 31, 32})
    void everyPlayerAppearsOnceAndByesGoToTheTop(int n) {
        List<Long> seeded = LongStream.rangeClosed(1, n).boxed().toList(); // id == seed
        List<Slot> slots = KnockoutBracket.firstRound(seeded);
        int size = KnockoutBracket.bracketSize(n);

        assertThat(slots).hasSize(size / 2);
        Set<Long> seen = new HashSet<>();
        List<Long> byePlayers = new ArrayList<>();
        for (Slot s : slots) {
            assertThat(s.white() == null && s.black() == null).as("empty match at board %d", s.board()).isFalse();
            if (s.white() != null) assertThat(seen.add(s.white())).isTrue();
            if (s.black() != null) assertThat(seen.add(s.black())).isTrue();
            if (s.isBye()) byePlayers.add(s.soloPlayer());
            if (!s.isBye()) assertThat(s.white()).as("higher seed takes white").isLessThan(s.black());
        }
        assertThat(seen).hasSize(n);
        assertThat(byePlayers).hasSize(size - n);
        // The byes are exactly the top (size - n) seeds
        assertThat(byePlayers).containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, size - n).boxed().toList());
    }

    @ParameterizedTest(name = "bracket of {0}")
    @ValueSource(ints = {4, 8, 16, 32})
    void topTwoSeedsAreInOppositeHalvesAndTopFourInDifferentQuarters(int size) {
        int[] order = KnockoutBracket.seedOrder(size);
        int half = size / 2;
        int pos1 = indexOf(order, 1);
        int pos2 = indexOf(order, 2);
        assertThat(pos1 < half).isNotEqualTo(pos2 < half);
        int quarter = size / 4;
        Set<Integer> quarters = new HashSet<>();
        for (int seed = 1; seed <= 4; seed++) quarters.add(indexOf(order, seed) / quarter);
        assertThat(quarters).hasSize(4);
    }

    @Test
    void roundCountsAndMatchCountsAddUp() {
        for (int n = 2; n <= 32; n++) {
            int rounds = KnockoutBracket.roundCount(n);
            int matches = 0;
            for (int r = 1; r <= rounds; r++) matches += KnockoutBracket.matchesInRound(n, r);
            assertThat(matches).as("n=%d", n).isEqualTo(KnockoutBracket.bracketSize(n) - 1);
            assertThat(KnockoutBracket.matchesInRound(n, rounds)).isEqualTo(1);
        }
    }

    @Test
    void rejectsFewerThanTwoPlayers() {
        assertThatThrownBy(() -> KnockoutBracket.bracketSize(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KnockoutBracket.firstRound(List.of(1L))).isInstanceOf(IllegalArgumentException.class);
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }
}
