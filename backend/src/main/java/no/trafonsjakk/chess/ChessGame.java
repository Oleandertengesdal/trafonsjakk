package no.trafonsjakk.chess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.trafonsjakk.game.GameResult;

/**
 * A game from its first position: the moves so far, the position they lead to, and whether the board
 * has ended it (checkmate, stalemate, threefold repetition, the fifty-move rule, insufficient material).
 * Repetition and the fifty-move rule end the game straight away, as on lichess, rather than waiting for
 * a claim.
 */
public final class ChessGame {

    public enum Reason { CHECKMATE, STALEMATE, REPETITION, FIFTY_MOVES, INSUFFICIENT_MATERIAL }

    public record Outcome(GameResult result, Reason reason) {
    }

    private final List<String> sans = new ArrayList<>();
    private final List<String> ucis = new ArrayList<>();
    private final Map<String, Integer> seen = new HashMap<>();
    private Position position;
    private boolean repeated;

    private ChessGame(Position start) {
        this.position = start;
        seen.put(start.repetitionKey(), 1);
    }

    public static ChessGame start() {
        return new ChessGame(Position.start());
    }

    /** A game from a set-up position (tests and puzzles). */
    public static ChessGame fromFen(String fen) {
        return new ChessGame(Position.fromFen(fen));
    }

    /**
     * Replays stored moves ("1. e4 e5 2. Nf3"). Move numbers and a result at the end are skipped.
     * Throws {@link IllegalArgumentException} at the first move that isn't legal.
     */
    public static ChessGame fromMoveText(String text) {
        ChessGame g = start();
        if (text == null || text.isBlank()) {
            return g;
        }
        for (String token : text.trim().split("\\s+")) {
            String t = token.replaceFirst("^\\d+\\.+", "");
            if (t.isEmpty() || t.equals("1-0") || t.equals("0-1") || t.equals("1/2-1/2") || t.equals("*")) {
                continue;
            }
            Move m = g.position.parseSan(t).orElseThrow(() -> new IllegalArgumentException("Illegal move: " + token));
            g.apply(m);
        }
        return g;
    }

    /** Plays a move sent as "e2e4" / "e7e8q". Returns its notation, or empty if it isn't legal (or the game is over). */
    public Optional<String> play(String uci) {
        if (outcome().isPresent()) {
            return Optional.empty();
        }
        return position.parseUci(uci).map(this::apply);
    }

    private String apply(Move m) {
        String san = position.san(m);
        position = position.play(m);
        sans.add(san);
        ucis.add(m.uci());
        if (seen.merge(position.repetitionKey(), 1, Integer::sum) >= 3) {
            repeated = true;
        }
        return san;
    }

    /** How the board has ended the game, if it has. */
    public Optional<Outcome> outcome() {
        if (position.legalMoves().isEmpty()) {
            if (position.inCheck()) {
                return Optional.of(new Outcome(position.turn() == Color.WHITE ? GameResult.BLACK_WIN : GameResult.WHITE_WIN,
                        Reason.CHECKMATE));
            }
            return Optional.of(new Outcome(GameResult.DRAW, Reason.STALEMATE));
        }
        if (position.insufficientMaterial()) {
            return Optional.of(new Outcome(GameResult.DRAW, Reason.INSUFFICIENT_MATERIAL));
        }
        if (repeated) {
            return Optional.of(new Outcome(GameResult.DRAW, Reason.REPETITION));
        }
        if (position.halfmoveClock() >= 100) {
            return Optional.of(new Outcome(GameResult.DRAW, Reason.FIFTY_MOVES));
        }
        return Optional.empty();
    }

    public Position position() {
        return position;
    }

    public List<String> sans() {
        return List.copyOf(sans);
    }

    /** The last move as "e2e4", or null before the first. */
    public String lastUci() {
        return ucis.isEmpty() ? null : ucis.getLast();
    }

    /** "1. e4 e5 2. Nf3", the way games are stored and shown on the website. Null with no moves. */
    public String moveText() {
        if (sans.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sans.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            if (i % 2 == 0) {
                sb.append(i / 2 + 1).append(". ");
            }
            sb.append(sans.get(i));
        }
        return sb.toString();
    }
}
