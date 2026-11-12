package no.trafonsjakk.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import no.trafonsjakk.award.MonthlyAwardCalculator.GameRow;
import no.trafonsjakk.award.MonthlyAwardCalculator.Result;
import no.trafonsjakk.award.MonthlyAwardCalculator.Standing;
import org.junit.jupiter.api.Test;

class MonthlyAwardCalculatorTest {

    private static final int MIN = 3;

    /** White scores {@code score} against an expectation of {@code expected}; rating changes are symmetric for simplicity. */
    private static GameRow g(long white, long black, double score, double expected) {
        double change = 30 * (score - expected);
        return new GameRow(white, black, score, expected, change, -change);
    }

    private static Standing of(List<Standing> list, long id) {
        return list.stream().filter(s -> s.userId() == id).findFirst().orElseThrow();
    }

    @Test
    void scoresPointsOverWhatTheRatingsExpected() {
        Result r = MonthlyAwardCalculator.compute(List.of(
                g(1, 2, 1, 0.8),   // 1 beats a weaker 2: +0.2
                g(3, 1, 1, 0.3),   // 3 beats a stronger 1 as white: +0.7, 1 gets -0.3
                g(2, 3, 0.5, 0.5),
                g(1, 3, 0.5, 0.6)), MIN);
        Standing one = of(r.playerOfMonth(), 1);
        assertThat(one.games()).isEqualTo(3);
        assertThat(one.wins()).isEqualTo(1);
        assertThat(one.draws()).isEqualTo(1);
        assertThat(one.losses()).isEqualTo(1);
        assertThat(one.points()).isEqualTo(1.5);
        assertThat(one.expected()).isCloseTo(0.8 + 0.7 + 0.6, within(1e-9));
        assertThat(one.overExpected()).isCloseTo(1.5 - 2.1, within(1e-9));
        Standing three = of(r.playerOfMonth(), 3);
        assertThat(three.overExpected()).isCloseTo(0.7 + 0 + 0.1, within(1e-9));
        assertThat(three.ratingChange()).isCloseTo(30 * 0.7 + 0 + 30 * 0.1, within(1e-9));
    }

    @Test
    void pointsOverExpectationAddUpToZero() {
        List<GameRow> games = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            games.add(g(i % 5, (i * 3 + 1) % 5 == i % 5 ? (i + 1) % 5 : (i * 3 + 1) % 5, (i % 3) / 2.0, 0.2 + (i % 7) / 10.0));
        }
        double sum = MonthlyAwardCalculator.compute(games, MIN).playerOfMonth().stream().mapToDouble(Standing::overExpected).sum();
        assertThat(sum).isCloseTo(0, within(1e-9));
    }

    @Test
    void youNeedEnoughGamesToWinButStillShowInTheStandings() {
        Result r = MonthlyAwardCalculator.compute(List.of(
                g(1, 2, 1, 0.1), g(1, 3, 1, 0.1),               // 1: two huge upsets, but only two games
                g(4, 2, 1, 0.5), g(4, 3, 1, 0.5), g(4, 2, 1, 0.5)), MIN); // 4: three wins
        assertThat(r.playerOfMonth().get(0).userId()).as("qualified players first").isEqualTo(4);
        assertThat(r.playerOfMonthWinner()).isEqualTo(4L);
        Standing one = of(r.playerOfMonth(), 1);
        assertThat(one.qualified()).isFalse();
        assertThat(one.overExpected()).isGreaterThan(of(r.playerOfMonth(), 4).overExpected());
    }

    @Test
    void nobodyWinsPlayerOfTheMonthWithoutBeatingExpectations() {
        Result r = MonthlyAwardCalculator.compute(List.of(
                g(1, 2, 0.5, 0.5), g(1, 2, 0.5, 0.5), g(2, 1, 0.5, 0.5)), MIN);
        assertThat(r.playerOfMonthWinner()).isNull();
        assertThat(r.mostActiveWinner()).as("but someone still played the most").isEqualTo(1L);
    }

    @Test
    void nobodyWinsAnythingWhenNobodyPlayedEnough() {
        Result r = MonthlyAwardCalculator.compute(List.of(g(1, 2, 1, 0.5), g(1, 2, 1, 0.5)), MIN);
        assertThat(r.playerOfMonthWinner()).isNull();
        assertThat(r.mostActiveWinner()).isNull();
        assertThat(r.playerOfMonth()).hasSize(2).allMatch(s -> !s.qualified());
    }

    @Test
    void anEmptyMonthHasNoStandings() {
        Result r = MonthlyAwardCalculator.compute(List.of(), MIN);
        assertThat(r.playerOfMonth()).isEmpty();
        assertThat(r.mostActive()).isEmpty();
        assertThat(r.playerOfMonthWinner()).isNull();
    }

    @Test
    void tiesGoToMoreGamesThenBiggerRatingGainThenTheOlderAccount() {
        // 1 and 2 both +0.5 over expectation; 2 played four games, 1 three
        Result r = MonthlyAwardCalculator.compute(List.of(
                g(1, 9, 1, 0.5), g(1, 9, 0.5, 0.5), g(1, 9, 0.5, 0.5),
                g(2, 8, 1, 0.5), g(2, 8, 0.5, 0.5), g(2, 8, 0.5, 0.5), g(2, 8, 0.5, 0.5)), MIN);
        assertThat(r.playerOfMonthWinner()).isEqualTo(2L);

        // Same points over expectation and games, different rating gain
        r = MonthlyAwardCalculator.compute(List.of(
                new GameRow(1, 9, 1, 0.5, 10, -10), new GameRow(1, 9, 0.5, 0.5, 0, 0), new GameRow(1, 9, 0.5, 0.5, 0, 0),
                new GameRow(2, 8, 1, 0.5, 20, -20), new GameRow(2, 8, 0.5, 0.5, 0, 0), new GameRow(2, 8, 0.5, 0.5, 0, 0)), MIN);
        assertThat(r.playerOfMonthWinner()).isEqualTo(2L);

        // Everything equal: the lower user id (the older account)
        r = MonthlyAwardCalculator.compute(List.of(
                g(7, 9, 1, 0.5), g(7, 9, 0.5, 0.5), g(7, 9, 0.5, 0.5),
                g(5, 8, 1, 0.5), g(5, 8, 0.5, 0.5), g(5, 8, 0.5, 0.5)), MIN);
        assertThat(r.playerOfMonthWinner()).isEqualTo(5L);
    }

    @Test
    void floatingPointNoiseIsNotATieBreaker() {
        // 0.1 + 0.2 != 0.3 in binary; these must still count as equal and fall through to games played
        Result r = MonthlyAwardCalculator.compute(List.of(
                g(1, 9, 1, 0.7), g(1, 9, 0, 0.1), g(1, 9, 0.5, 0.4),          // 0.3 - 0.1 + 0.1 = 0.3 (ish)
                g(2, 8, 0.5, 0.2), g(2, 8, 0.5, 0.5), g(2, 8, 0.5, 0.5), g(2, 8, 0.5, 0.5)), MIN); // 0.3 + 0 + 0 + 0
        assertThat(of(r.playerOfMonth(), 1).overExpected()).isCloseTo(of(r.playerOfMonth(), 2).overExpected(), within(1e-9));
        assertThat(r.playerOfMonthWinner()).as("more games").isEqualTo(2L);
    }

    @Test
    void mostActiveIsMostGamesThenBestPerformance() {
        Result r = MonthlyAwardCalculator.compute(List.of(
                g(1, 2, 0, 0.5), g(1, 2, 0, 0.5), g(1, 2, 0, 0.5), g(1, 3, 0, 0.5), // 1: four games, all lost
                g(3, 4, 1, 0.5), g(3, 4, 1, 0.5), g(3, 4, 1, 0.5)), MIN);           // 2, 3 and 4 three or four games
        List<Standing> active = r.mostActive();
        assertThat(active.get(0).games()).isEqualTo(4);
        // 1 and 3 both have four games; 3 did better
        assertThat(r.mostActiveWinner()).isEqualTo(3L);
        assertThat(active).extracting(Standing::games).isSortedAccordingTo((a, b) -> b - a);
    }

    @Test
    void monthStatusFollowsTheClockInNorway() {
        ZoneId oslo = ZoneId.of("Europe/Oslo");
        YearMonth oct = YearMonth.of(2026, 10);
        Duration grace = Duration.ofDays(3);
        Instant ends = Instant.parse("2026-10-31T23:00:00Z"); // midnight in Oslo (CET, after the DST switch)
        assertThat(MonthlyAwardCalculator.endsAt(oct, oslo)).isEqualTo(ends);
        assertThat(MonthlyAwardCalculator.status(oct, ends.minusSeconds(1), oslo, grace)).isEqualTo(MonthlyAwardCalculator.Status.LIVE);
        assertThat(MonthlyAwardCalculator.status(oct, ends, oslo, grace)).isEqualTo(MonthlyAwardCalculator.Status.DECIDING);
        assertThat(MonthlyAwardCalculator.status(oct, ends.plus(grace).minusSeconds(1), oslo, grace)).isEqualTo(MonthlyAwardCalculator.Status.DECIDING);
        assertThat(MonthlyAwardCalculator.status(oct, ends.plus(grace), oslo, grace)).isEqualTo(MonthlyAwardCalculator.Status.DECIDED);
        // Summer time: July ends at 22:00 UTC
        assertThat(MonthlyAwardCalculator.endsAt(YearMonth.of(2026, 7), oslo)).isEqualTo(Instant.parse("2026-07-31T22:00:00Z"));
    }
}
