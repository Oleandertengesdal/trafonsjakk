package no.trafonsjakk.tournament;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.trafonsjakk.tournament.KnockoutBracket.Slot;
import org.junit.jupiter.api.Test;

class KnockoutBracketTest {

    @Test
    void standardSeedOrder() {
        assertArrayEquals(new int[]{1, 2}, KnockoutBracket.seedOrder(2));
        assertArrayEquals(new int[]{1, 4, 2, 3}, KnockoutBracket.seedOrder(4));
        assertArrayEquals(new int[]{1, 8, 4, 5, 2, 7, 3, 6}, KnockoutBracket.seedOrder(8));
    }

    @Test
    void sizesAndRounds() {
        assertEquals(2, KnockoutBracket.bracketSize(2));
        assertEquals(8, KnockoutBracket.bracketSize(5));
        assertEquals(16, KnockoutBracket.bracketSize(16));
        assertEquals(3, KnockoutBracket.roundCount(5));
        assertEquals(4, KnockoutBracket.matchesInRound(5, 1));
        assertEquals(1, KnockoutBracket.matchesInRound(5, 3));
    }

    @Test
    void byesGoToTopSeeds() {
        // seeds 1..5 -> ids 101..105
        List<Slot> slots = KnockoutBracket.firstRound(List.of(101L, 102L, 103L, 104L, 105L));
        assertEquals(4, slots.size());
        // board 0: 1 v 8(bye)
        assertEquals(101L, slots.get(0).white());
        assertNull(slots.get(0).black());
        assertTrue(slots.get(0).isBye());
        // board 1: 4 v 5 (real match, higher seed white)
        assertEquals(104L, slots.get(1).white());
        assertEquals(105L, slots.get(1).black());
        assertFalse(slots.get(1).isBye());
        // boards 2 and 3: seeds 2 and 3 get byes
        assertEquals(102L, slots.get(2).soloPlayer());
        assertEquals(103L, slots.get(3).soloPlayer());
        assertTrue(slots.get(2).isBye() && slots.get(3).isBye());
    }

    @Test
    void advancement() {
        assertEquals(0, KnockoutBracket.nextBoard(1));
        assertEquals(1, KnockoutBracket.nextBoard(2));
        assertTrue(KnockoutBracket.advancesAsWhite(2));
        assertFalse(KnockoutBracket.advancesAsWhite(3));
    }

    @Test
    void roundNames() {
        assertEquals("Final", KnockoutBracket.roundName(3, 3));
        assertEquals("Semifinals", KnockoutBracket.roundName(2, 3));
        assertEquals("Quarterfinals", KnockoutBracket.roundName(1, 3));
        assertEquals("Round 1", KnockoutBracket.roundName(1, 4));
    }
}
