package no.trafonsjakk.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A chess position and the rules of chess: legal moves (castling, en passant, promotion, pins and
 * checks), standard algebraic notation and FEN. Immutable: {@link #play} returns a new position.
 * <p>
 * Plain Java, no dependencies. The website checks moves with chess.js; the server uses this so a game
 * played online can't contain an illegal move whatever a browser sends. {@code PerftTest} checks the
 * move generator against the published move counts.
 */
public final class Position {

    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final char EMPTY = '.';
    private static final int WHITE_SHORT = 1;
    private static final int WHITE_LONG = 2;
    private static final int BLACK_SHORT = 4;
    private static final int BLACK_LONG = 8;
    private static final int[][] KNIGHT = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] ROOK_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final char[] PROMOTIONS = {'q', 'r', 'b', 'n'};
    private static final Pattern UCI = Pattern.compile("^[a-h][1-8][a-h][1-8][qrbn]?$");

    private final char[] board;
    private final Color turn;
    private final int castling;
    private final int enPassant;
    private final int halfmoveClock;
    private final int fullmove;
    private List<Move> legal;

    private Position(char[] board, Color turn, int castling, int enPassant, int halfmoveClock, int fullmove) {
        this.board = board;
        this.turn = turn;
        this.castling = castling;
        this.enPassant = enPassant;
        this.halfmoveClock = halfmoveClock;
        this.fullmove = fullmove;
    }

    public static Position start() {
        return fromFen(START_FEN);
    }

    // ---- FEN ----------------------------------------------------------------------------------------------

    /** Reads a FEN. Throws {@link IllegalArgumentException} if it isn't one. */
    public static Position fromFen(String fen) {
        String[] f = fen == null ? new String[0] : fen.trim().split("\\s+");
        if (f.length < 4) {
            throw new IllegalArgumentException("Not a FEN: " + fen);
        }
        char[] board = new char[64];
        Arrays.fill(board, EMPTY);
        String[] ranks = f[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("Not a FEN: " + fen);
        }
        int whiteKings = 0;
        int blackKings = 0;
        for (int r = 0; r < 8; r++) {
            int file = 0;
            for (char c : ranks[7 - r].toCharArray()) {
                if (c >= '1' && c <= '8') {
                    file += c - '0';
                } else if ("PNBRQKpnbrqk".indexOf(c) >= 0 && file < 8) {
                    board[r * 8 + file++] = c;
                    whiteKings += c == 'K' ? 1 : 0;
                    blackKings += c == 'k' ? 1 : 0;
                } else {
                    throw new IllegalArgumentException("Not a FEN: " + fen);
                }
            }
            if (file != 8) {
                throw new IllegalArgumentException("Not a FEN: " + fen);
            }
        }
        if (whiteKings != 1 || blackKings != 1) {
            throw new IllegalArgumentException("A position needs one king each: " + fen);
        }
        Color turn = switch (f[1]) {
            case "w" -> Color.WHITE;
            case "b" -> Color.BLACK;
            default -> throw new IllegalArgumentException("Not a FEN: " + fen);
        };
        int castling = 0;
        if (!f[2].equals("-")) {
            for (char c : f[2].toCharArray()) {
                castling |= switch (c) {
                    case 'K' -> WHITE_SHORT;
                    case 'Q' -> WHITE_LONG;
                    case 'k' -> BLACK_SHORT;
                    case 'q' -> BLACK_LONG;
                    default -> throw new IllegalArgumentException("Not a FEN: " + fen);
                };
            }
        }
        int ep = f[3].equals("-") ? -1 : square(f[3]);
        int halfmove = f.length > 4 ? Integer.parseInt(f[4]) : 0;
        int fullmove = f.length > 5 ? Integer.parseInt(f[5]) : 1;
        return new Position(board, turn, castling, ep, halfmove, fullmove);
    }

    public String fen() {
        return placement() + " " + (turn == Color.WHITE ? "w" : "b") + " " + castlingText() + " "
                + (enPassant < 0 ? "-" : squareName(enPassant)) + " " + halfmoveClock + " " + fullmove;
    }

    /**
     * What makes two positions "the same" for repetition: the pieces, who is to move, castling rights,
     * and en passant only when it can actually be played (FIDE art. 9.2).
     */
    public String repetitionKey() {
        boolean epPlayable = enPassant >= 0 && legalMoves().stream()
                .anyMatch(m -> m.to() == enPassant && Character.toLowerCase(board[m.from()]) == 'p');
        return placement() + " " + turn + " " + castlingText() + " " + (epPlayable ? squareName(enPassant) : "-");
    }

    private String placement() {
        StringBuilder sb = new StringBuilder();
        for (int r = 7; r >= 0; r--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                char c = board[r * 8 + file];
                if (c == EMPTY) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(c);
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (r > 0) {
                sb.append('/');
            }
        }
        return sb.toString();
    }

    private String castlingText() {
        String s = ((castling & WHITE_SHORT) != 0 ? "K" : "") + ((castling & WHITE_LONG) != 0 ? "Q" : "")
                + ((castling & BLACK_SHORT) != 0 ? "k" : "") + ((castling & BLACK_LONG) != 0 ? "q" : "");
        return s.isEmpty() ? "-" : s;
    }

    // ---- state --------------------------------------------------------------------------------------------

    public Color turn() {
        return turn;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public boolean inCheck() {
        return attacked(king(turn), turn.other());
    }

    public boolean isCheckmate() {
        return inCheck() && legalMoves().isEmpty();
    }

    public boolean isStalemate() {
        return !inCheck() && legalMoves().isEmpty();
    }

    /**
     * Whether {@code side} could still mate by any series of legal moves, as far as it's usually judged
     * when a flag falls: a lone king can't, nor a king with just one knight or one bishop.
     */
    public boolean canMate(Color side) {
        int minors = 0;
        for (char c : board) {
            if (c == EMPTY || colorOf(c) != side) {
                continue;
            }
            switch (Character.toLowerCase(c)) {
                case 'k' -> {
                }
                case 'n', 'b' -> minors++;
                default -> {
                    return true; // a pawn, rook or queen
                }
            }
        }
        return minors >= 2;
    }

    /**
     * Neither side can mate at all: king against king, a single minor piece against a king, or only
     * bishops that all stand on squares of one colour.
     */
    public boolean insufficientMaterial() {
        int knights = 0;
        int lightBishops = 0;
        int darkBishops = 0;
        for (int sq = 0; sq < 64; sq++) {
            switch (Character.toLowerCase(board[sq])) {
                case 'p', 'r', 'q' -> {
                    return false;
                }
                case 'n' -> knights++;
                case 'b' -> {
                    if (((sq & 7) + (sq >> 3)) % 2 == 0) {
                        darkBishops++;
                    } else {
                        lightBishops++;
                    }
                }
                default -> {
                }
            }
        }
        int bishops = lightBishops + darkBishops;
        if (knights + bishops <= 1) {
            return true;
        }
        return knights == 0 && (lightBishops == 0 || darkBishops == 0);
    }

    // ---- moves --------------------------------------------------------------------------------------------

    /** Every legal move in this position. */
    public List<Move> legalMoves() {
        if (legal == null) {
            List<Move> out = new ArrayList<>();
            for (Move m : pseudoLegal()) {
                Position next = play(m);
                if (!next.attacked(next.king(turn), turn.other())) {
                    out.add(m);
                }
            }
            legal = List.copyOf(out);
        }
        return legal;
    }

    /** A legal move written as "e2e4" / "e7e8q", if it is one. A promotion must name its piece. */
    public Optional<Move> parseUci(String uci) {
        if (uci == null || !UCI.matcher(uci).matches()) {
            return Optional.empty();
        }
        Move wanted = new Move(square(uci.substring(0, 2)), square(uci.substring(2, 4)),
                uci.length() == 5 ? uci.charAt(4) : (char) 0);
        return legalMoves().stream().filter(wanted::equals).findFirst();
    }

    /** A legal move written in standard notation ("Nf3", "exd6", "O-O", "e8=Q+"), if it is one. */
    public Optional<Move> parseSan(String san) {
        if (san == null) {
            return Optional.empty();
        }
        String wanted = stripSuffix(san.trim().replace("0-0-0", "O-O-O").replace("0-0", "O-O"));
        for (Move m : legalMoves()) {
            if (stripSuffix(san(m)).equals(wanted)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    private static String stripSuffix(String s) {
        return s.replaceAll("[+#!?]+$", "");
    }

    /** Standard algebraic notation for a legal move in this position, with + or # if it gives check or mate. */
    public String san(Move m) {
        char piece = Character.toLowerCase(board[m.from()]);
        StringBuilder sb = new StringBuilder();
        int fileDiff = (m.to() & 7) - (m.from() & 7);
        if (piece == 'k' && Math.abs(fileDiff) == 2) {
            sb.append(fileDiff > 0 ? "O-O" : "O-O-O");
        } else {
            boolean capture = board[m.to()] != EMPTY || (piece == 'p' && m.to() == enPassant);
            if (piece == 'p') {
                if (capture) {
                    sb.append((char) ('a' + (m.from() & 7))).append('x');
                }
                sb.append(squareName(m.to()));
                if (m.promotion() != 0) {
                    sb.append('=').append(Character.toUpperCase(m.promotion()));
                }
            } else {
                sb.append(Character.toUpperCase(piece));
                boolean sameFile = false;
                boolean sameRank = false;
                boolean ambiguous = false;
                for (Move o : legalMoves()) {
                    if (o.to() == m.to() && o.from() != m.from() && board[o.from()] == board[m.from()]) {
                        ambiguous = true;
                        sameFile |= (o.from() & 7) == (m.from() & 7);
                        sameRank |= (o.from() >> 3) == (m.from() >> 3);
                    }
                }
                if (ambiguous) {
                    if (!sameFile) {
                        sb.append((char) ('a' + (m.from() & 7)));
                    } else if (!sameRank) {
                        sb.append((char) ('1' + (m.from() >> 3)));
                    } else {
                        sb.append(squareName(m.from()));
                    }
                }
                if (capture) {
                    sb.append('x');
                }
                sb.append(squareName(m.to()));
            }
        }
        Position next = play(m);
        if (next.inCheck()) {
            sb.append(next.legalMoves().isEmpty() ? '#' : '+');
        }
        return sb.toString();
    }

    /** The position after a move. The move must be legal (or at least pseudo-legal) here. */
    public Position play(Move m) {
        char[] b = board.clone();
        char piece = b[m.from()];
        char kind = Character.toLowerCase(piece);
        boolean capture = b[m.to()] != EMPTY;
        int ep = -1;
        b[m.to()] = piece;
        b[m.from()] = EMPTY;
        if (kind == 'p') {
            if (m.to() == enPassant) {
                b[m.to() + (turn == Color.WHITE ? -8 : 8)] = EMPTY;
                capture = true;
            }
            if (Math.abs(m.to() - m.from()) == 16) {
                ep = (m.from() + m.to()) / 2;
            }
            if (m.promotion() != 0) {
                b[m.to()] = turn == Color.WHITE ? Character.toUpperCase(m.promotion()) : m.promotion();
            }
        } else if (kind == 'k' && Math.abs((m.to() & 7) - (m.from() & 7)) == 2) {
            boolean shortSide = m.to() > m.from();
            int rookFrom = shortSide ? m.from() + 3 : m.from() - 4;
            int rookTo = shortSide ? m.from() + 1 : m.from() - 1;
            b[rookTo] = b[rookFrom];
            b[rookFrom] = EMPTY;
        }
        int rights = castling & ~lostRights(m.from()) & ~lostRights(m.to());
        int half = kind == 'p' || capture ? 0 : halfmoveClock + 1;
        int full = turn == Color.BLACK ? fullmove + 1 : fullmove;
        return new Position(b, turn.other(), rights, ep, half, full);
    }

    /** Castling rights that go when something moves from, or is captured on, a square. */
    private static int lostRights(int sq) {
        return switch (sq) {
            case 4 -> WHITE_SHORT | WHITE_LONG;   // e1
            case 0 -> WHITE_LONG;                 // a1
            case 7 -> WHITE_SHORT;                // h1
            case 60 -> BLACK_SHORT | BLACK_LONG;  // e8
            case 56 -> BLACK_LONG;                // a8
            case 63 -> BLACK_SHORT;               // h8
            default -> 0;
        };
    }

    private List<Move> pseudoLegal() {
        List<Move> moves = new ArrayList<>(48);
        for (int sq = 0; sq < 64; sq++) {
            char c = board[sq];
            if (c == EMPTY || colorOf(c) != turn) {
                continue;
            }
            switch (Character.toLowerCase(c)) {
                case 'p' -> pawnMoves(sq, moves);
                case 'n' -> steps(sq, KNIGHT, moves);
                case 'b' -> slides(sq, BISHOP_DIRS, moves);
                case 'r' -> slides(sq, ROOK_DIRS, moves);
                case 'q' -> {
                    slides(sq, ROOK_DIRS, moves);
                    slides(sq, BISHOP_DIRS, moves);
                }
                case 'k' -> {
                    steps(sq, KING, moves);
                    castles(sq, moves);
                }
                default -> throw new IllegalStateException("Unknown piece " + c);
            }
        }
        return moves;
    }

    private void pawnMoves(int sq, List<Move> moves) {
        int dir = turn == Color.WHITE ? 8 : -8;
        int startRank = turn == Color.WHITE ? 1 : 6;
        int lastRank = turn == Color.WHITE ? 7 : 0;
        int file = sq & 7;
        int one = sq + dir;
        if (one >= 0 && one < 64 && board[one] == EMPTY) {
            addPawn(sq, one, lastRank, moves);
            int two = one + dir;
            if ((sq >> 3) == startRank && board[two] == EMPTY) {
                moves.add(new Move(sq, two));
            }
        }
        for (int df : new int[]{-1, 1}) {
            int f = file + df;
            if (f < 0 || f > 7 || one < 0 || one >= 64) {
                continue;
            }
            int target = one + df;
            char t = board[target];
            if ((t != EMPTY && colorOf(t) != turn) || target == enPassant) {
                addPawn(sq, target, lastRank, moves);
            }
        }
    }

    private static void addPawn(int from, int to, int lastRank, List<Move> moves) {
        if ((to >> 3) == lastRank) {
            for (char p : PROMOTIONS) {
                moves.add(new Move(from, to, p));
            }
        } else {
            moves.add(new Move(from, to));
        }
    }

    private void steps(int sq, int[][] deltas, List<Move> moves) {
        int file = sq & 7;
        int rank = sq >> 3;
        for (int[] d : deltas) {
            int f = file + d[0];
            int r = rank + d[1];
            if (f < 0 || f > 7 || r < 0 || r > 7) {
                continue;
            }
            char t = board[r * 8 + f];
            if (t == EMPTY || colorOf(t) != turn) {
                moves.add(new Move(sq, r * 8 + f));
            }
        }
    }

    private void slides(int sq, int[][] dirs, List<Move> moves) {
        for (int[] d : dirs) {
            int f = (sq & 7) + d[0];
            int r = (sq >> 3) + d[1];
            while (f >= 0 && f <= 7 && r >= 0 && r <= 7) {
                char t = board[r * 8 + f];
                if (t == EMPTY) {
                    moves.add(new Move(sq, r * 8 + f));
                } else {
                    if (colorOf(t) != turn) {
                        moves.add(new Move(sq, r * 8 + f));
                    }
                    break;
                }
                f += d[0];
                r += d[1];
            }
        }
    }

    private void castles(int sq, List<Move> moves) {
        int home = turn == Color.WHITE ? 4 : 60;
        if (sq != home) {
            return;
        }
        Color enemy = turn.other();
        int shortRight = turn == Color.WHITE ? WHITE_SHORT : BLACK_SHORT;
        int longRight = turn == Color.WHITE ? WHITE_LONG : BLACK_LONG;
        char rook = turn == Color.WHITE ? 'R' : 'r';
        if ((castling & (shortRight | longRight)) == 0 || attacked(home, enemy)) {
            return;
        }
        if ((castling & shortRight) != 0 && board[home + 3] == rook && board[home + 1] == EMPTY
                && board[home + 2] == EMPTY && !attacked(home + 1, enemy) && !attacked(home + 2, enemy)) {
            moves.add(new Move(home, home + 2));
        }
        if ((castling & longRight) != 0 && board[home - 4] == rook && board[home - 1] == EMPTY
                && board[home - 2] == EMPTY && board[home - 3] == EMPTY
                && !attacked(home - 1, enemy) && !attacked(home - 2, enemy)) {
            moves.add(new Move(home, home - 2));
        }
    }

    /** Whether a piece of {@code by} attacks the square. */
    private boolean attacked(int sq, Color by) {
        int file = sq & 7;
        int rank = sq >> 3;
        // Pawns attack diagonally forward, so look one rank back from the square
        int pawnRank = by == Color.WHITE ? rank - 1 : rank + 1;
        char pawn = by == Color.WHITE ? 'P' : 'p';
        if (pawnRank >= 0 && pawnRank <= 7) {
            for (int df : new int[]{-1, 1}) {
                int f = file + df;
                if (f >= 0 && f <= 7 && board[pawnRank * 8 + f] == pawn) {
                    return true;
                }
            }
        }
        if (hits(file, rank, KNIGHT, by == Color.WHITE ? 'N' : 'n')
                || hits(file, rank, KING, by == Color.WHITE ? 'K' : 'k')) {
            return true;
        }
        char queen = by == Color.WHITE ? 'Q' : 'q';
        return rays(file, rank, ROOK_DIRS, by == Color.WHITE ? 'R' : 'r', queen)
                || rays(file, rank, BISHOP_DIRS, by == Color.WHITE ? 'B' : 'b', queen);
    }

    private boolean hits(int file, int rank, int[][] deltas, char piece) {
        for (int[] d : deltas) {
            int f = file + d[0];
            int r = rank + d[1];
            if (f >= 0 && f <= 7 && r >= 0 && r <= 7 && board[r * 8 + f] == piece) {
                return true;
            }
        }
        return false;
    }

    private boolean rays(int file, int rank, int[][] dirs, char slider, char queen) {
        for (int[] d : dirs) {
            int f = file + d[0];
            int r = rank + d[1];
            while (f >= 0 && f <= 7 && r >= 0 && r <= 7) {
                char t = board[r * 8 + f];
                if (t != EMPTY) {
                    if (t == slider || t == queen) {
                        return true;
                    }
                    break;
                }
                f += d[0];
                r += d[1];
            }
        }
        return false;
    }

    private int king(Color c) {
        char k = c == Color.WHITE ? 'K' : 'k';
        for (int sq = 0; sq < 64; sq++) {
            if (board[sq] == k) {
                return sq;
            }
        }
        throw new IllegalStateException("No king");
    }

    private static Color colorOf(char piece) {
        return Character.isUpperCase(piece) ? Color.WHITE : Color.BLACK;
    }

    static int square(String name) {
        if (name.length() != 2 || name.charAt(0) < 'a' || name.charAt(0) > 'h' || name.charAt(1) < '1' || name.charAt(1) > '8') {
            throw new IllegalArgumentException("Not a square: " + name);
        }
        return (name.charAt(1) - '1') * 8 + (name.charAt(0) - 'a');
    }

    static String squareName(int sq) {
        return "" + (char) ('a' + (sq & 7)) + (char) ('1' + (sq >> 3));
    }
}
