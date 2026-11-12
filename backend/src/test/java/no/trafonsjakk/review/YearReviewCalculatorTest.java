package no.trafonsjakk.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import no.trafonsjakk.review.YearReviewCalculator.AwardKind;
import no.trafonsjakk.review.YearReviewCalculator.AwardRow;
import no.trafonsjakk.review.YearReviewCalculator.GameRow;
import no.trafonsjakk.review.YearReviewCalculator.GroupYear;
import no.trafonsjakk.review.YearReviewCalculator.PlayerYear;
import org.junit.jupiter.api.Test;

class YearReviewCalculatorTest {

    private final List<GameRow> games = new ArrayList<>();
    private long nextId = 1;
    /** Each player's current rating, so before/after values chain like the real history. */
    private final java.util.Map<Long, Double> rating = new java.util.HashMap<>();

    /** A game on {@code date}; white's expected score {@code exp}; each result moves ratings by 20 × (score − expected). */
    private GameRow play(String date, long white, long black, double whiteScore, double exp, String opening, int kudos, int comments, boolean moves) {
        LocalDate d = LocalDate.parse(date);
        double wb = rating.getOrDefault(white, 1500.0);
        double bb = rating.getOrDefault(black, 1500.0);
        double wa = wb + 20 * (whiteScore - exp);
        double ba = bb - 20 * (whiteScore - exp);
        rating.put(white, wa);
        rating.put(black, ba);
        GameRow g = new GameRow(nextId++, d.atTime(18, 0).toInstant(ZoneOffset.UTC), d, white, black, whiteScore, opening,
                wb, wa, bb, ba, exp, kudos, comments, moves);
        games.add(g);
        return g;
    }

    private GameRow play(String date, long white, long black, double whiteScore, double exp) {
        return play(date, white, black, whiteScore, exp, null, 0, 0, false);
    }

    @Test
    void aPlayerWithoutGamesHasNoYear() {
        play("2026-03-03", 1, 2, 1, 0.5);
        assertThat(YearReviewCalculator.player(3, games, List.of())).isNull();
    }

    @Test
    void countsTheRecordAndRatingFromFirstToLastGame() {
        play("2026-01-10", 1, 2, 1, 0.5);
        play("2026-02-10", 2, 1, 1, 0.5);   // 1 loses as black
        play("2026-02-12", 1, 3, 0.5, 0.5);
        PlayerYear p = YearReviewCalculator.player(1, games, List.of());
        assertThat(p.games()).isEqualTo(3);
        assertThat(p.wins()).isEqualTo(1);
        assertThat(p.losses()).isEqualTo(1);
        assertThat(p.draws()).isEqualTo(1);
        assertThat(p.points()).isEqualTo(1.5);
        assertThat(p.ratingStart()).isEqualTo(1500);
        assertThat(p.ratingEnd()).isCloseTo(1500 + 10 - 10, within(1e-9));
        assertThat(p.ratingPeak()).isCloseTo(1510, within(1e-9));
        assertThat(p.ratingByMonth()).extracting(YearReviewCalculator.MonthRating::month)
                .containsExactly(YearMonth.of(2026, 1), YearMonth.of(2026, 2));
        assertThat(p.monthsActive()).isEqualTo(2);
    }

    @Test
    void theBestMonthIsTheBestPerformanceWithAtLeastTwoGames() {
        play("2026-03-01", 1, 2, 1, 0.1);              // one huge upset in March, alone
        play("2026-05-01", 1, 2, 1, 0.5);
        play("2026-05-08", 1, 3, 1, 0.5);              // May: +1.0 over two games
        play("2026-06-01", 1, 3, 1, 0.6);
        play("2026-06-08", 1, 3, 0, 0.6);              // June: -0.2
        PlayerYear p = YearReviewCalculator.player(1, games, List.of());
        assertThat(p.bestMonth().month()).isEqualTo(YearMonth.of(2026, 5));
        assertThat(p.bestMonth().wins()).isEqualTo(2);
        assertThat(p.bestMonth().overExpected()).isCloseTo(1.0, within(1e-9));

        // With only single-game months, any month can be best
        games.clear();
        play("2026-03-01", 4, 2, 1, 0.1);
        play("2026-04-01", 4, 2, 1, 0.5);
        assertThat(YearReviewCalculator.player(4, games, List.of()).bestMonth().month()).isEqualTo(YearMonth.of(2026, 3));
    }

    @Test
    void theBiggestUpsetIsTheWinWithTheLowestOdds() {
        play("2026-01-01", 1, 2, 1, 0.4);
        GameRow big = play("2026-01-02", 3, 1, 0, 0.8);   // 1 wins as black with 0.2
        play("2026-01-03", 1, 4, 0, 0.05);               // a loss doesn't count, however unlikely a win was
        PlayerYear p = YearReviewCalculator.player(1, games, List.of());
        assertThat(p.biggestUpset().gameId()).isEqualTo(big.id());
        assertThat(p.biggestUpset().expected()).isCloseTo(0.2, within(1e-9));
        assertThat(p.biggestUpset().loserId()).isEqualTo(3);
        assertThat(YearReviewCalculator.player(4, games, List.of()).biggestUpset()).as("a win as favourite still counts")
                .isNotNull();
    }

    @Test
    void theRivalIsWhoYouPlayedMostAndTheClosestRecordBreaksTies() {
        play("2026-01-01", 1, 2, 1, 0.5);
        play("2026-01-02", 1, 2, 1, 0.5);   // vs 2: 2-0
        play("2026-01-03", 1, 3, 1, 0.5);
        play("2026-01-04", 3, 1, 1, 0.5);   // vs 3: 1-1
        play("2026-01-05", 1, 4, 1, 0.5);
        PlayerYear p = YearReviewCalculator.player(1, games, List.of());
        assertThat(p.rival().opponentId()).isEqualTo(3);
        assertThat(p.rival().games()).isEqualTo(2);
        assertThat(p.rival().wins()).isEqualTo(1);
        assertThat(p.rival().losses()).isEqualTo(1);
    }

    @Test
    void winStreaksEndOnADrawOrLoss() {
        play("2026-01-01", 1, 2, 1, 0.5);   // W
        play("2026-01-02", 2, 1, 0, 0.5);   // W (as black)
        play("2026-01-03", 1, 2, 0.5, 0.5); // D
        play("2026-01-04", 1, 3, 1, 0.5);   // W
        play("2026-01-05", 3, 1, 0, 0.5);   // W
        play("2026-01-06", 1, 3, 1, 0.5);   // W
        play("2026-01-07", 1, 3, 0, 0.5);   // L
        play("2026-01-08", 1, 3, 1, 0.5);   // W
        PlayerYear p = YearReviewCalculator.player(1, games, List.of());
        assertThat(p.longestWinStreak().length()).isEqualTo(3);
        assertThat(p.longestWinStreak().from()).isEqualTo(games.get(3).playedAt());
        assertThat(p.longestWinStreak().to()).isEqualTo(games.get(5).playedAt());
        assertThat(YearReviewCalculator.player(2, games, List.of()).longestWinStreak()).as("never won").isNull();
    }

    @Test
    void theFavouriteOpeningIgnoresCaseAndBlankNames() {
        play("2026-01-01", 1, 2, 1, 0.5, "Italian Game", 0, 0, false);
        play("2026-01-02", 1, 2, 0, 0.5, "italian game ", 0, 0, false);
        play("2026-01-03", 1, 2, 1, 0.5, "Italian Game", 0, 0, false);
        play("2026-01-04", 1, 2, 1, 0.5, "Sicilian", 0, 0, false);
        play("2026-01-05", 1, 2, 1, 0.5, "  ", 0, 0, false);
        PlayerYear p = YearReviewCalculator.player(1, games, List.of());
        assertThat(p.favoriteOpening().name()).isEqualTo("Italian Game");
        assertThat(p.favoriteOpening().games()).isEqualTo(3);
        assertThat(p.favoriteOpening().scorePct()).isCloseTo(2.0 / 3, within(1e-9));

        games.clear();
        play("2026-01-01", 1, 2, 1, 0.5);
        assertThat(YearReviewCalculator.player(1, games, List.of()).favoriteOpening()).as("nobody has to record openings").isNull();
    }

    @Test
    void ranksByGamesAndCountsMovesAndKudos() {
        play("2026-01-01", 1, 2, 1, 0.5, null, 2, 1, true);
        play("2026-01-02", 1, 2, 1, 0.5, null, 3, 0, false);
        play("2026-01-03", 3, 2, 1, 0.5, null, 0, 0, false);
        PlayerYear two = YearReviewCalculator.player(2, games, List.of());
        PlayerYear one = YearReviewCalculator.player(1, games, List.of());
        PlayerYear three = YearReviewCalculator.player(3, games, List.of());
        assertThat(two.rankByGames()).isEqualTo(1);
        assertThat(one.rankByGames()).isEqualTo(2);
        assertThat(three.rankByGames()).isEqualTo(3);
        assertThat(one.players()).isEqualTo(3);
        assertThat(one.gamesWithMoves()).isEqualTo(1);
        assertThat(one.mostKudos().kudos()).isEqualTo(3);
        assertThat(three.mostKudos()).isNull();
        assertThat(one.busiestWeekday().games()).isEqualTo(1);
    }

    @Test
    void theGroupYear() {
        play("2026-01-05", 1, 2, 1, 0.5, null, 1, 4, true);       // Monday
        play("2026-01-12", 2, 1, 1, 0.5, null, 6, 0, false);      // Monday
        play("2026-01-14", 1, 2, 0.5, 0.5);                      // Wednesday
        GameRow upset = play("2026-02-03", 3, 1, 1, 0.15);       // the giant killer
        play("2026-02-04", 3, 2, 0, 0.45);                       // 2 wins with 0.55: not an upset
        play("2026-02-05", 3, 4, 1, 0.5);
        play("2026-02-06", 3, 4, 1, 0.5);
        List<AwardRow> awards = List.of(
                new AwardRow(YearMonth.of(2026, 1), AwardKind.PLAYER_OF_MONTH, 1),
                new AwardRow(YearMonth.of(2026, 1), AwardKind.MOST_ACTIVE, 2),
                new AwardRow(YearMonth.of(2026, 2), AwardKind.PLAYER_OF_MONTH, 3),
                new AwardRow(YearMonth.of(2026, 3), AwardKind.PLAYER_OF_MONTH, 3));

        GroupYear y = YearReviewCalculator.group(games, awards);
        assertThat(y.games()).isEqualTo(7);
        assertThat(y.players()).isEqualTo(4);
        assertThat(y.whiteWins() + y.draws() + y.blackWins()).isEqualTo(7);
        assertThat(y.draws()).isEqualTo(1);
        assertThat(y.gamesWithMoves()).isEqualTo(1);
        assertThat(y.kudos()).isEqualTo(7);
        assertThat(y.comments()).isEqualTo(4);
        assertThat(y.busiestMonth().month()).isEqualTo(YearMonth.of(2026, 2));
        assertThat(y.busiestWeekday().day()).isEqualTo(1);
        assertThat(y.gameOfTheYear().gameId()).as("kudos + comments: 6 vs 5").isEqualTo(2);
        assertThat(y.giantKiller().gameId()).isEqualTo(upset.id());
        assertThat(y.giantKiller().winnerId()).isEqualTo(3);
        assertThat(y.giantKiller().expected()).isCloseTo(0.15, within(1e-9));
        assertThat(y.awardLeaders().getFirst().userId()).isEqualTo(3);
        assertThat(y.awardLeaders().getFirst().playerOfMonth()).isEqualTo(2);
        assertThat(y.mostGames().getFirst().userId()).isIn(1L, 2L, 3L);
        assertThat(y.mostGames()).hasSizeLessThanOrEqualTo(3);
        assertThat(y.climbers()).allMatch(c -> c.change() > 0);
        assertThat(y.topRivalry().games()).isEqualTo(3);   // 1 and 2
        assertThat(y.topRivalry().aWins()).isGreaterThanOrEqualTo(y.topRivalry().bWins());
        assertThat(y.longestWinStreak().length()).isEqualTo(2); // 3: won, lost, won, won
    }

    @Test
    void anEmptyGroupYear() {
        GroupYear y = YearReviewCalculator.group(List.of(), List.of());
        assertThat(y.games()).isZero();
        assertThat(y.gameOfTheYear()).isNull();
        assertThat(y.giantKiller()).isNull();
        assertThat(y.topRivalry()).isNull();
        assertThat(y.busiestMonth()).isNull();
        assertThat(y.mostGames()).isEmpty();
    }
}
