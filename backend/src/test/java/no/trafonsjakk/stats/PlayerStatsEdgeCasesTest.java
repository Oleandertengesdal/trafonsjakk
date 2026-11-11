package no.trafonsjakk.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import no.trafonsjakk.stats.PlayerStatsCalculator.GameRecord;
import no.trafonsjakk.stats.PlayerStatsCalculator.Outcome;
import no.trafonsjakk.stats.PlayerStatsCalculator.Result;
import org.junit.jupiter.api.Test;

class PlayerStatsEdgeCasesTest {

    private static final long ME = 1;
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    private static GameRecord game(long id, String at, long white, long black, double whiteScore) {
        return new GameRecord(id, Instant.parse(at), white, black, whiteScore, null, null, null);
    }

    @Test
    void recentFormIsCappedAtTenNewestFirst() {
        List<GameRecord> games = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            games.add(game(i, "2026-10-%02dT18:00:00Z".formatted(i + 1), ME, 2, i < 5 ? 0 : 1));
        }
        Result r = PlayerStatsCalculator.compute(ME, games, NOW);
        assertThat(r.recentForm()).hasSize(10).containsOnly(Outcome.WIN);
        assertThat(r.streaks().longestWin()).isEqualTo(10);
        assertThat(r.streaks().longestLoss()).isEqualTo(5);
    }

    @Test
    void gamesOfOtherPlayersAreIgnored() {
        List<GameRecord> games = List.of(game(1, "2026-10-01T18:00:00Z", 5, 6, 1), game(2, "2026-10-02T18:00:00Z", ME, 5, 1));
        Result r = PlayerStatsCalculator.compute(ME, games, NOW);
        assertThat(r.totals().games()).isEqualTo(1);
        assertThat(r.headToHead()).hasSize(1);
        assertThat(r.recentForm()).containsExactly(Outcome.WIN);
    }

    @Test
    void equalScoreWithBothColoursHasNoFavourite() {
        List<GameRecord> games = List.of(game(1, "2026-10-01T18:00:00Z", ME, 2, 1), game(2, "2026-10-02T18:00:00Z", 2, ME, 0));
        assertThat(PlayerStatsCalculator.compute(ME, games, NOW).favoriteColor()).isNull();
    }

    @Test
    void activityCoversTheLastTwelveMonthsInUtc() {
        List<GameRecord> games = List.of(
                game(1, "2025-11-30T23:30:00Z", ME, 2, 1),   // 12 months back: outside the window
                game(2, "2025-12-01T00:30:00Z", ME, 2, 1),   // first month in the window
                game(3, "2026-11-10T20:00:00Z", ME, 2, 1));  // current month
        Result r = PlayerStatsCalculator.compute(ME, games, NOW);
        assertThat(r.activity()).hasSize(12);
        assertThat(r.activity().getFirst().month()).isEqualTo("2025-12");
        assertThat(r.activity().getFirst().games()).isEqualTo(1);
        assertThat(r.activity().getLast().month()).isEqualTo("2026-11");
        assertThat(r.activity().getLast().games()).isEqualTo(1);
        assertThat(r.activity().stream().mapToInt(PlayerStatsCalculator.MonthCount::games).sum()).isEqualTo(2);
    }

    @Test
    void drawsDoNotBreakAnUnbeatenRunButDoBreakAWinningRun() {
        List<GameRecord> games = List.of(
                game(1, "2026-10-01T18:00:00Z", ME, 2, 1), game(2, "2026-10-02T18:00:00Z", ME, 2, 1),
                game(3, "2026-10-03T18:00:00Z", ME, 2, 0.5), game(4, "2026-10-04T18:00:00Z", ME, 2, 1));
        Result r = PlayerStatsCalculator.compute(ME, games, NOW);
        assertThat(r.streaks().longestWin()).isEqualTo(2);
        assertThat(r.streaks().longestUnbeaten()).isEqualTo(4);
        assertThat(r.streaks().currentType()).isEqualTo(Outcome.WIN);
        assertThat(r.streaks().currentLength()).isEqualTo(1);
    }

    @Test
    void favouriteOpeningIgnoresBlankNamesAndTrimsWhitespace() {
        List<GameRecord> games = List.of(
                new GameRecord(1, Instant.parse("2026-10-01T18:00:00Z"), ME, 2, 1, "  Italian Game ", null, null),
                new GameRecord(2, Instant.parse("2026-10-02T18:00:00Z"), ME, 2, 0, "Italian Game", null, null),
                new GameRecord(3, Instant.parse("2026-10-03T18:00:00Z"), ME, 2, 1, "   ", null, null));
        Result r = PlayerStatsCalculator.compute(ME, games, NOW);
        assertThat(r.favoriteOpening().name()).isEqualTo("Italian Game");
        assertThat(r.favoriteOpening().games()).isEqualTo(2);
        assertThat(r.favoriteOpening().scorePct()).isEqualTo(0.5);
    }
}
