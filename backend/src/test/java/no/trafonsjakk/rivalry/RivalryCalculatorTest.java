package no.trafonsjakk.rivalry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.rivalry.RivalryCalculator.Game;
import no.trafonsjakk.rivalry.RivalryCalculator.Side;
import org.junit.jupiter.api.Test;

class RivalryCalculatorTest {

    private static Game g(long id, boolean aWhite, GameResult r, Double aBefore, Double bBefore, Double aAcc, Double bAcc) {
        return new Game(id, Instant.ofEpochSecond(1_000_000 + id * 3600), aWhite, r, aBefore, bBefore, aAcc, bAcc);
    }

    @Test
    void recordsStreaksUpsetAndBestGames() {
        List<Game> games = List.of(
                g(1, true, GameResult.WHITE_WIN, 1500.0, 1600.0, 80.0, 70.0),   // A wins as white, 100 below: upset
                g(2, false, GameResult.WHITE_WIN, 1510.0, 1590.0, null, null),  // B (white) wins
                g(3, false, GameResult.BLACK_WIN, 1500.0, 1700.0, 95.0, 91.0),  // A (black) wins, 200 below: bigger upset
                g(4, true, GameResult.WHITE_WIN, 1520.0, 1680.0, 60.0, 50.0),   // A wins
                g(5, true, GameResult.DRAW, 1530.0, 1670.0, 99.0, 99.0),
                g(6, true, GameResult.BLACK_WIN, 1530.0, 1670.0, null, null));  // B wins
        RivalryCalculator.Result r = RivalryCalculator.compute(games);
        assertThat(r.overall()).isEqualTo(new RivalryCalculator.Record(6, 3, 1, 2, 3.5));
        assertThat(r.aAsWhite()).isEqualTo(new RivalryCalculator.Record(4, 2, 1, 1, 2.5));
        assertThat(r.aAsBlack()).isEqualTo(new RivalryCalculator.Record(2, 1, 0, 1, 1));
        assertThat(r.longest()).isEqualTo(new RivalryCalculator.Streak(Side.A, 2));
        assertThat(r.current()).isEqualTo(new RivalryCalculator.Streak(Side.B, 1));
        assertThat(r.upset().gameId()).isEqualTo(3);
        assertThat(r.upset().gap()).isEqualTo(200);
        assertThat(r.upset().winner()).isEqualTo(Side.A);
        assertThat(r.best()).extracting(RivalryCalculator.BestGame::gameId).containsExactly(5L, 3L, 1L);
    }

    @Test
    void aDrawEndsTheStreakAndNoGamesMeansNothing() {
        RivalryCalculator.Result r = RivalryCalculator.compute(List.of(g(1, true, GameResult.WHITE_WIN, null, null, null, null),
                g(2, true, GameResult.DRAW, null, null, null, null)));
        assertThat(r.current()).isEqualTo(new RivalryCalculator.Streak(null, 0));
        assertThat(r.upset()).isNull();
        RivalryCalculator.Result none = RivalryCalculator.compute(List.of());
        assertThat(none.overall().games()).isZero();
        assertThat(none.longest().length()).isZero();
        assertThat(none.best()).isEmpty();
    }
}
