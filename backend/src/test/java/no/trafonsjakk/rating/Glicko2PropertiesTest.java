package no.trafonsjakk.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import no.trafonsjakk.rating.Glicko2.Rating;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class Glicko2PropertiesTest {

    private final Glicko2 glicko = new Glicko2();

    @Test
    void rejectsScoresThatAreNotWinDrawOrLoss() {
        assertThatThrownBy(() -> glicko.update(Rating.initial(), Rating.initial(), 0.7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Glicko2(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noGamesInAPeriodOnlyGrowsDeviation() {
        Rating r = new Rating(1650, 80, 0.06);
        Rating after = glicko.update(r, List.of());
        assertThat(after.rating()).isEqualTo(1650);
        assertThat(after.rd()).isGreaterThan(80);
        assertThat(glicko.decay(r, 0)).isEqualTo(r);
        assertThat(glicko.decay(r, -3)).isEqualTo(r);
    }

    @ParameterizedTest(name = "{0} (±{1}) vs {2} (±{3}), score {4}")
    @CsvSource({
            "1500, 350, 1500, 350, 1",
            "2400, 30, 800, 350, 0",     // huge upset against a provisional player
            "800, 350, 2400, 30, 1",
            "1500, 30, 1500, 30, 0.5",
            "3000, 50, 100, 50, 1",      // absurd gap
            "100, 50, 3000, 50, 1",
    })
    void extremeInputsStayFiniteAndBounded(double r1, double rd1, double r2, double rd2, double score) {
        Rating a = new Rating(r1, rd1, 0.06);
        Rating b = new Rating(r2, rd2, 0.06);
        Rating after = glicko.update(a, b, score);
        assertThat(after.rating()).isFinite().isBetween(-2000.0, 6000.0);
        assertThat(after.rd()).isFinite().isPositive().isLessThanOrEqualTo(Glicko2.DEFAULT_RD);
        assertThat(after.volatility()).isFinite().isPositive().isLessThan(0.2);
        // Winning never lowers a rating, losing never raises it
        if (score == 1) assertThat(after.rating()).isGreaterThanOrEqualTo(r1);
        if (score == 0) assertThat(after.rating()).isLessThanOrEqualTo(r1);
    }

    @Test
    void equalUncertaintyMeansZeroSumExchange() {
        Rating a = new Rating(1620, 90, 0.06);
        Rating b = new Rating(1480, 90, 0.06);
        double gainA = glicko.update(a, b, 0).rating() - a.rating();
        double gainB = glicko.update(b, a, 1).rating() - b.rating();
        assertThat(gainA + gainB).isCloseTo(0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void newPlayersMoveMoreThanEstablishedOnes() {
        Rating newcomer = Rating.initial();
        Rating veteran = new Rating(1500, 60, 0.06);
        double newcomerGain = glicko.update(newcomer, veteran, 1).rating() - 1500;
        double veteranLoss = 1500 - glicko.update(veteran, newcomer, 0).rating();
        assertThat(newcomerGain).isGreaterThan(veteranLoss * 5);
    }

    @Test
    void manyGamesConvergeAndBecomeEstablished() {
        Rating r = Rating.initial();
        Rating opponent = new Rating(1500, 50, 0.06);
        for (int i = 0; i < 30; i++) {
            r = glicko.update(r, opponent, i % 2 == 0 ? 1 : 0.5);
        }
        assertThat(r.provisional()).isFalse();
        assertThat(r.rating()).isBetween(1550.0, 1800.0);
    }
}
