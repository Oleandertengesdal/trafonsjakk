package no.trafonsjakk.online;

import no.trafonsjakk.chess.ChessGame;
import no.trafonsjakk.chess.Color;
import no.trafonsjakk.chess.Position;
import no.trafonsjakk.game.GameResult;

/** How an online game ended: the result and why. */
public record Ending(GameResult result, EndReason reason) {

    private static GameResult winFor(Color c) {
        return c == Color.WHITE ? GameResult.WHITE_WIN : GameResult.BLACK_WIN;
    }

    /** The board ended it (mate, stalemate, repetition, fifty moves, no mating material), or null. */
    public static Ending onBoard(ChessGame game) {
        return game.outcome().map(o -> new Ending(o.result(), EndReason.valueOf(o.reason().name()))).orElse(null);
    }

    /**
     * {@code flagged} ran out of time. The opponent wins, unless the opponent couldn't possibly mate
     * (a lone king, or a king with one minor piece): then it's a draw (FIDE art. 6.9).
     */
    public static Ending timeout(Position position, Color flagged) {
        Color other = flagged.other();
        return position.canMate(other) ? new Ending(winFor(other), EndReason.TIMEOUT)
                : new Ending(GameResult.DRAW, EndReason.TIMEOUT_VS_INSUFFICIENT);
    }

    public static Ending resignation(Color resigned) {
        return new Ending(winFor(resigned.other()), EndReason.RESIGNATION);
    }

    public static Ending agreement() {
        return new Ending(GameResult.DRAW, EndReason.AGREEMENT);
    }
}
