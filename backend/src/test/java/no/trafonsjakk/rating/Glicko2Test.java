package no.trafonsjakk.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.trafonsjakk.rating.Glicko2.Outcome;
import no.trafonsjakk.rating.Glicko2.Rating;
import org.junit.jupiter.api.Test;

class Glicko2Test {

    private final Glicko2 glicko = new Glicko2(0.5);

    @Test
    void matchesGlickmansWorkedExample() {
        Rating player = new Rating(1500, 200, 0.06);
        Rating updated = glicko.update(player, List.of(
                new Outcome(new Rating(1400, 30, 0.06), 1),
                new Outcome(new Rating(1550, 100, 0.06), 0),
                new Outcome(new Rating(1700, 300, 0.06), 0)));

        assertEquals(1464.06, updated.rating(), 0.01);
        assertEquals(151.52, updated.rd(), 0.01);
        assertEquals(0.05999, updated.volatility(), 0.00001);
    }

    @Test
    void drawBetweenEqualPlayersKeepsRatingAndShrinksRd() {
        Rating a = Rating.initial();
        Rating after = glicko.update(a, a, 0.5);
        assertEquals(1500, after.rating(), 1e-9);
        assertTrue(after.rd() < a.rd());
    }

    @Test
    void winRaisesAndLossLowersSymmetrically() {
        Rating a = Rating.initial();
        Rating winner = glicko.update(a, a, 1);
        Rating loser = glicko.update(a, a, 0);
        assertTrue(winner.rating() > 1500);
        assertTrue(loser.rating() < 1500);
        assertEquals(winner.rating() - 1500, 1500 - loser.rating(), 1e-6);
    }

    @Test
    void upsetWinGainsMoreThanExpectedWin() {
        Rating strong = new Rating(1900, 60, 0.06);
        Rating weak = new Rating(1500, 60, 0.06);
        double upsetGain = glicko.update(weak, strong, 1).rating() - weak.rating();
        double expectedGain = glicko.update(strong, weak, 1).rating() - strong.rating();
        assertTrue(upsetGain > expectedGain * 3, "upset should be worth much more");
    }

    @Test
    void decayGrowsRdButIsCapped() {
        Rating settled = new Rating(1600, 60, 0.06);
        Rating afterMonth = glicko.decay(settled, 30 / Glicko2.RATING_PERIOD_DAYS);
        assertTrue(afterMonth.rd() > 60);
        assertEquals(1600, afterMonth.rating(), 1e-9);
        Rating afterCentury = glicko.decay(settled, 100_000);
        assertEquals(Glicko2.DEFAULT_RD, afterCentury.rd(), 1e-9);
    }

    @Test
    void expectedScoreIsHalfForEqualPlayers() {
        assertEquals(0.5, Glicko2.expectedScore(Rating.initial(), Rating.initial()), 1e-9);
    }
}
