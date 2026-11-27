package no.trafonsjakk.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import no.trafonsjakk.chess.ChessGame.Outcome;
import no.trafonsjakk.chess.ChessGame.Reason;
import no.trafonsjakk.game.GameResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChessGameTest {

    private static ChessGame play(String... uci) {
        ChessGame g = ChessGame.start();
        for (String m : uci) {
            assertThat(g.play(m)).as("legal: %s", m).isPresent();
        }
        return g;
    }

    // ---- notation ---------------------------------------------------------------------------------------

    @Test
    void writesMovesInTheSameNotationAsTheWebsite() {
        ChessGame g = play("e2e4", "e7e5", "g1f3", "b8c6", "f1b5");
        assertThat(g.sans()).containsExactly("e4", "e5", "Nf3", "Nc6", "Bb5");
        assertThat(g.moveText()).isEqualTo("1. e4 e5 2. Nf3 Nc6 3. Bb5");
    }

    @Test
    void marksCheckAndMate() {
        ChessGame g = play("f2f3", "e7e5", "g2g4", "d8h4");
        assertThat(g.sans()).containsExactly("f3", "e5", "g4", "Qh4#");
        ChessGame check = play("e2e4", "f7f6", "d1h5");
        assertThat(check.sans().getLast()).isEqualTo("Qh5+");
    }

    @ParameterizedTest(name = "{1} in {0}")
    @CsvSource(delimiter = '|', value = {
            // Two knights can reach d2: the file tells them apart
            "4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1 | b1d2 | Nbd2",
            // Two rooks on the same file: the rank tells them apart
            "4k3/8/8/R7/8/8/8/R3K3 w - - 0 1 | a1a3 | R1a3",
            // Three queens: neither file nor rank alone is enough
            "8/8/k7/8/4Q2Q/8/2K5/7Q w - - 0 1 | h4e1 | Qh4e1",
            // A pinned knight doesn't count as a second candidate
            "7k/4r3/8/8/8/2N1N3/8/4K3 w - - 0 1 | c3d5 | Nd5",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1 | e1g1 | O-O",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1 | e1c1 | O-O-O",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1 | e8c8 | O-O-O",
            "4k3/1P6/8/8/8/8/8/4K3 w - - 0 1 | b7b8q | b8=Q+",
            "1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1 | a7b8n | axb8=N",
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2 | e5d6 | exd6",
    })
    void disambiguatesLikeStandardNotation(String fen, String uci, String san) {
        Position p = Position.fromFen(fen);
        Move m = p.parseUci(uci).orElseThrow();
        assertThat(p.san(m)).isEqualTo(san);
        assertThat(p.parseSan(san)).contains(m);
    }

    @Test
    void readsBackStoredMoveText() {
        ChessGame g = ChessGame.fromMoveText("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. O-O");
        assertThat(g.sans()).hasSize(7);
        assertThat(g.position().turn()).isEqualTo(Color.BLACK);
        assertThat(ChessGame.fromMoveText(null).sans()).isEmpty();
        assertThat(ChessGame.fromMoveText("").sans()).isEmpty();
        assertThatThrownBy(() -> ChessGame.fromMoveText("1. e4 e4")).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- what's legal -----------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"e2e5", "e7e5", "e1e2", "a1a2", "zz99", "e2", "e2e4qq", "e2e4q", "''"})
    void refusesIllegalOrMalformedMoves(String uci) {
        ChessGame g = ChessGame.start();
        assertThat(g.play(uci)).isEmpty();
        assertThat(g.sans()).isEmpty();
    }

    @Test
    void aPawnReachingTheLastRankMustSayWhatItBecomes() {
        Position p = Position.fromFen("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1");
        assertThat(p.parseUci("b7b8")).isEmpty();
        assertThat(p.parseUci("b7b8k")).isEmpty();
        assertThat(p.parseUci("b7b8r")).isPresent();
    }

    @Test
    void cannotCastleOutOfThroughOrIntoCheck() {
        // A black rook on f8 covers f1: no short castling. The queen side is free.
        Position p = Position.fromFen("4kr2/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertThat(p.parseUci("e1g1")).isEmpty();
        assertThat(p.parseUci("e1c1")).isPresent();
        // In check: no castling at all
        Position checked = Position.fromFen("4r1k1/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertThat(checked.parseUci("e1g1")).isEmpty();
        assertThat(checked.parseUci("e1c1")).isEmpty();
    }

    @Test
    void movingTheKingOrARookLosesTheRightToCastle() {
        ChessGame g = play("e2e4", "e7e5", "g1f3", "g8f6", "f1e2", "f8e7", "h1g1", "h8g8", "g1h1", "g8h8");
        assertThat(g.position().fen()).startsWith("rnbqk2r/ppppbppp/5n2/4p3/4P3/5N2/PPPPBPPP/RNBQK2R w Qq ");
        assertThat(g.play("e1g1")).isEmpty();
    }

    // ---- how a game ends --------------------------------------------------------------------------------

    @Test
    void checkmate() {
        ChessGame g = play("f2f3", "e7e5", "g2g4", "d8h4");
        assertThat(g.outcome()).contains(new Outcome(GameResult.BLACK_WIN, Reason.CHECKMATE));
        assertThat(g.play("a2a3")).isEmpty();
    }

    @Test
    void stalemate() {
        ChessGame g = ChessGame.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        assertThat(g.outcome()).contains(new Outcome(GameResult.DRAW, Reason.STALEMATE));
    }

    @ParameterizedTest
    @CsvSource({
            "8/8/4k3/8/8/4K3/8/8 w - - 0 1",        // king against king
            "8/8/4k3/8/8/4KB2/8/8 w - - 0 1",       // and a bishop
            "8/8/4k3/8/8/4KN2/8/8 b - - 0 1",       // and a knight
            "8/8/2b1k3/8/8/4KB2/8/8 w - - 0 1",     // bishops on the same colour (c6 and f3 are light)
    })
    void drawnWhenNeitherSideCanMate(String fen) {
        assertThat(ChessGame.fromFen(fen).outcome()).contains(new Outcome(GameResult.DRAW, Reason.INSUFFICIENT_MATERIAL));
    }

    @ParameterizedTest
    @CsvSource({
            "8/8/1b2k3/8/8/4KB2/8/8 w - - 0 1",     // bishops on opposite colours: a mate exists
            "8/8/4k3/8/8/4KNN1/8/8 w - - 0 1",      // two knights
            "8/8/4k3/8/8/4K3/4P3/8 w - - 0 1",      // a pawn
            "8/8/4kn2/8/8/4KB2/8/8 w - - 0 1",      // knight against bishop
    })
    void playsOnWhileAMateIsStillPossible(String fen) {
        assertThat(ChessGame.fromFen(fen).outcome()).isEmpty();
    }

    @Test
    void threefoldRepetitionIsADraw() {
        ChessGame g = play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1");
        assertThat(g.outcome()).isEmpty();
        g.play("f6g8"); // the starting position for the third time
        assertThat(g.outcome()).contains(new Outcome(GameResult.DRAW, Reason.REPETITION));
    }

    @Test
    void positionsOnlyRepeatWithTheSameRights() {
        // After the kings walk out and back, the position looks the same but nobody can castle any more,
        // so it isn't a repetition of the start.
        ChessGame g = play("e2e4", "e7e5", "e1e2", "e8e7", "e2e1", "e7e8", "e1e2", "e8e7", "e2e1", "e7e8");
        assertThat(g.outcome()).isEmpty();
        g.play("e1e2");
        g.play("e8e7");
        assertThat(g.outcome()).contains(new Outcome(GameResult.DRAW, Reason.REPETITION));
    }

    @Test
    void fiftyMovesWithoutACaptureOrPawnMoveIsADraw() {
        ChessGame g = ChessGame.fromFen("4k3/8/8/8/8/8/R7/4K3 w - - 99 80");
        assertThat(g.outcome()).isEmpty();
        g.play("a2b2");
        assertThat(g.outcome()).contains(new Outcome(GameResult.DRAW, Reason.FIFTY_MOVES));
    }

    @Test
    void mateOnTheFiftiethMoveStillWins() {
        ChessGame g = ChessGame.fromFen("k7/8/1K6/8/8/8/8/7R w - - 99 80");
        g.play("h1h8");
        assertThat(g.outcome()).contains(new Outcome(GameResult.WHITE_WIN, Reason.CHECKMATE));
    }

    // ---- running out of time ------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}: {1} can mate = {2}")
    @CsvSource({
            "8/8/4k3/8/8/4K3/8/7q w - - 0 1, WHITE, false",  // a lone king can never mate
            "8/8/4k3/8/8/4KB2/8/7q w - - 0 1, WHITE, false", // nor king and bishop
            "8/8/4k3/8/8/4KN2/8/7q w - - 0 1, WHITE, false", // nor king and knight
            "8/8/4k3/8/8/4KR2/8/7q w - - 0 1, WHITE, true",
            "8/8/4k3/8/8/4K3/4P3/7q w - - 0 1, WHITE, true",
            "8/8/4k3/8/8/4KNN1/8/7q w - - 0 1, WHITE, true",
            "8/8/4k3/8/8/4K3/8/7q w - - 0 1, BLACK, true",
    })
    void knowsWhoCanStillMate(String fen, Color side, boolean canMate) {
        assertThat(Position.fromFen(fen).canMate(side)).isEqualTo(canMate);
    }

    @Test
    void listsLegalMovesForTheBoard() {
        List<String> uci = ChessGame.start().position().legalMoves().stream().map(Move::uci).toList();
        assertThat(uci).hasSize(20).contains("e2e4", "g1f3").doesNotContain("e1e2");
    }
}
