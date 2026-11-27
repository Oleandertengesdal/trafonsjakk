package no.trafonsjakk.online;

import static org.assertj.core.api.Assertions.assertThat;

import no.trafonsjakk.chess.ChessGame;
import no.trafonsjakk.chess.Color;
import no.trafonsjakk.game.GameResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What the result is when a game ends other than on the board. */
class EndingTest {

    @ParameterizedTest(name = "{0}, {1} out of time: {2} by {3}")
    @CsvSource({
            // White runs out; Black has a queen: Black wins
            "8/8/4k3/8/8/4K3/8/7q w - - 0 1, WHITE, BLACK_WIN, TIMEOUT",
            // White runs out with a queen against a lone king: Black can never mate, so it's a draw
            "8/8/4k3/8/8/4K3/8/7Q w - - 0 1, WHITE, DRAW, TIMEOUT_VS_INSUFFICIENT",
            // Black runs out; White has only a knight: a draw
            "q7/8/4k3/8/8/4KN2/8/8 b - - 0 1, BLACK, DRAW, TIMEOUT_VS_INSUFFICIENT",
            // Black runs out; White has a pawn, which could still mate: White wins
            "q7/8/4k3/8/8/4K3/4P3/8 b - - 0 1, BLACK, WHITE_WIN, TIMEOUT",
    })
    void timeout(String fen, Color flagged, GameResult result, EndReason reason) {
        Ending e = Ending.timeout(ChessGame.fromFen(fen).position(), flagged);
        assertThat(e.result()).isEqualTo(result);
        assertThat(e.reason()).isEqualTo(reason);
    }

    @Test
    void resignationLosesForWhoeverResigns() {
        assertThat(Ending.resignation(Color.WHITE)).isEqualTo(new Ending(GameResult.BLACK_WIN, EndReason.RESIGNATION));
        assertThat(Ending.resignation(Color.BLACK)).isEqualTo(new Ending(GameResult.WHITE_WIN, EndReason.RESIGNATION));
    }

    @Test
    void endingsOnTheBoardKeepTheirReason() {
        ChessGame mate = ChessGame.fromMoveText("1. f3 e5 2. g4 Qh4#");
        assertThat(Ending.onBoard(mate)).isEqualTo(new Ending(GameResult.BLACK_WIN, EndReason.CHECKMATE));
        assertThat(Ending.onBoard(ChessGame.fromMoveText("1. e4 e5"))).isNull();
        assertThat(Ending.onBoard(ChessGame.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")))
                .isEqualTo(new Ending(GameResult.DRAW, EndReason.STALEMATE));
    }
}
