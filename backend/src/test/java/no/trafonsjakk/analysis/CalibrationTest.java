package no.trafonsjakk.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import no.trafonsjakk.award.MonthlyAwardCalculator;
import no.trafonsjakk.award.MonthlyAwardCalculator.AccuracyRow;
import org.junit.jupiter.api.Test;

class CalibrationTest {

    @Test
    void fitsRatingToAccuracyOnceThereAreEnoughSamples() {
        List<double[]> samples = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double accuracy = 60 + i;
            samples.add(new double[] {accuracy, 400 + 15 * accuracy + (i % 2 == 0 ? 30 : -30)});
        }
        AnalysisService.CalibrationDto c = AnalysisService.calibrate(samples);
        assertThat(c.samples()).isEqualTo(40);
        assertThat(c.slope()).isCloseTo(15, within(0.5));
        assertThat(c.intercept() + c.slope() * 80).isCloseTo(1600, within(20.0));
        assertThat(c.low()).as("lowest rating in the samples").isEqualTo(400 + 15 * 61 - 30);
        assertThat(c.high()).as("highest rating in the samples").isEqualTo(400 + 15 * 98 + 30);

        assertThat(AnalysisService.calibrate(samples.subList(0, 29)).slope()).as("too few").isNull();
    }

    @Test
    void noFitWhenAccuracyDoesntGoWithRating() {
        List<double[]> samples = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            samples.add(new double[] {60 + i, 2000 - 10.0 * i});
        }
        assertThat(AnalysisService.calibrate(samples).slope()).isNull();
        assertThat(AnalysisService.calibrate(samples).high()).isNull();
        List<double[]> same = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            same.add(new double[] {80, 1500 + i});
        }
        assertThat(AnalysisService.calibrate(same).slope()).isNull();
    }

    @Test
    void mostAccurateNeedsEnoughGamesAndBreaksTiesByGames() {
        List<AccuracyRow> rows = List.of(new AccuracyRow(1, 95), new AccuracyRow(1, 95),
                new AccuracyRow(2, 80), new AccuracyRow(2, 90), new AccuracyRow(2, 85),
                new AccuracyRow(3, 85), new AccuracyRow(3, 85), new AccuracyRow(3, 85), new AccuracyRow(3, 85));
        MonthlyAwardCalculator.AccuracyResult r = MonthlyAwardCalculator.mostAccurate(rows, 3);
        assertThat(r.winner()).as("1 has too few games; 2 and 3 tie on 85, 3 has more").isEqualTo(3L);
        assertThat(r.standings().stream().map(MonthlyAwardCalculator.AccuracyStanding::userId)).containsExactly(3L, 2L, 1L);
        assertThat(MonthlyAwardCalculator.mostAccurate(rows.subList(0, 2), 3).winner()).isNull();
    }
}
