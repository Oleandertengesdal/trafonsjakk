package no.trafonsjakk.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import no.trafonsjakk.stats.PlayerStatsCalculator.Color;
import no.trafonsjakk.stats.PlayerStatsCalculator.GameRecord;
import no.trafonsjakk.stats.PlayerStatsCalculator.Outcome;
import no.trafonsjakk.stats.PlayerStatsCalculator.Result;
import org.junit.jupiter.api.Test;

class PlayerStatsCalculatorTest {

    private static final long ME = 1;
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    private final List<GameRecord> games = new ArrayList<>();
    private long nextId = 1;

    /** score from MY perspective: 1 win, 0.5 draw, 0 loss. */
    private void play(long opponent, boolean iAmWhite, double score, String opening, Double oppRating) {
        Instant at = NOW.minus(30 - nextId, ChronoUnit.DAYS);
        double whiteScore = iAmWhite ? score : 1 - score;
        games.add(new GameRecord(nextId++, at, iAmWhite ? ME : opponent, iAmWhite ? opponent : ME, whiteScore,
                opening, iAmWhite ? 1500.0 : oppRating, iAmWhite ? oppRating : 1500.0));
    }

    @Test
    void emptyHistory() {
        Result r = PlayerStatsCalculator.compute(ME, List.of(), NOW);
        assertEquals(0, r.totals().games());
        assertEquals(0, r.totals().winRate());
        assertNull(r.favoriteColor());
        assertNull(r.bestWin());
        assertNull(r.streaks().currentType());
        assertEquals(12, r.activity().size());
    }

    @Test
    void recordsStreaksColoursAndRivals() {
        play(2, true, 1, "Italian Game", 1550.0);   // W
        play(3, false, 1, "Sicilian", 1700.0);      // W
        play(2, true, 1, "Italian Game", 1560.0);   // W
        play(3, false, 0, "Sicilian", 1710.0);      // L
        play(3, true, 0.5, null, 1705.0);           // D
        play(3, false, 0, null, 1720.0);            // L
        play(2, true, 0, "Italian Game", 1500.0);   // L

        Result r = PlayerStatsCalculator.compute(ME, games, NOW);

        assertEquals(7, r.totals().games());
        assertEquals(3, r.totals().wins());
        assertEquals(1, r.totals().draws());
        assertEquals(3, r.totals().losses());
        assertEquals(3.5 / 7, r.totals().scorePct(), 1e-9);

        assertEquals(4, r.white().games());   // games 1,3,5,7: W W D L
        assertEquals(2.5 / 4, r.white().scorePct(), 1e-9);
        assertEquals(3, r.black().games());   // W L L
        assertEquals(Color.WHITE, r.favoriteColor());

        assertEquals(3, r.streaks().longestWin());
        assertEquals(2, r.streaks().longestLoss());   // L, L at the end (6, 7)
        assertEquals(3, r.streaks().longestUnbeaten());
        assertEquals(Outcome.LOSS, r.streaks().currentType());
        assertEquals(2, r.streaks().currentLength());

        assertEquals(3L, r.nemesisId());
        assertEquals(2, r.nemesisLosses());
        assertEquals(2L, r.favoriteVictimId());
        assertEquals(2, r.favoriteVictimWins());

        assertEquals(1700.0, r.bestWin().opponentRating());
        assertEquals(3L, r.bestWin().opponentId());

        assertEquals("Italian Game", r.favoriteOpening().name());
        assertEquals(3, r.favoriteOpening().games());

        assertEquals(List.of(Outcome.LOSS, Outcome.LOSS, Outcome.DRAW, Outcome.LOSS, Outcome.WIN, Outcome.WIN,
                Outcome.WIN), r.recentForm());
        assertEquals(2, r.headToHead().size());
        assertEquals(3L, r.headToHead().get(0).opponentId()); // 4 games vs player 3
    }
}
