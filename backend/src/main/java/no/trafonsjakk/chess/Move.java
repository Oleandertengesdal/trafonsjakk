package no.trafonsjakk.chess;

/**
 * A move from one square to another. Squares are 0–63: a1 = 0, b1 = 1, …, h8 = 63.
 * {@code promotion} is the piece a pawn becomes ('q', 'r', 'b', 'n'), or 0.
 */
public record Move(int from, int to, char promotion) {

    public Move(int from, int to) {
        this(from, to, (char) 0);
    }

    /** "e2e4", "e7e8q": the notation a board sends. */
    public String uci() {
        return Position.squareName(from) + Position.squareName(to) + (promotion == 0 ? "" : String.valueOf(promotion));
    }

    @Override
    public String toString() {
        return uci();
    }
}
