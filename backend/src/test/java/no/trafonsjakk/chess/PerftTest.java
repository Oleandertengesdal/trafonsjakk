package no.trafonsjakk.chess;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Perft: the number of move sequences of a given length from a position. The counts below are the
 * published reference values (chessprogramming.org/Perft_Results); a move generator that gets any of
 * them right has castling, en passant, promotion, pins and checks right in those positions. If the
 * generator drops or invents a single legal move anywhere in the tree, the count is off.
 */
class PerftTest {

    static long perft(Position p, int depth) {
        if (depth == 0) {
            return 1;
        }
        long n = 0;
        for (Move m : p.legalMoves()) {
            n += depth == 1 ? 1 : perft(p.play(m), depth - 1);
        }
        return n;
    }

    @ParameterizedTest(name = "{0} depth {1}")
    @CsvSource(delimiter = '|', value = {
            // The starting position
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 1 | 20",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 2 | 400",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 3 | 8902",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 4 | 197281",
            // "Kiwipete": castling both ways, pins, en passant, promotions
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 1 | 48",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 2 | 2039",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 3 | 97862",
            // Position 3: en passant that would expose the king along a rank
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1 | 1 | 14",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1 | 2 | 191",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1 | 3 | 2812",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1 | 4 | 43238",
            // Position 4: promotions with check, castling out of reach
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1 | 1 | 6",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1 | 2 | 264",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1 | 3 | 9467",
            // Position 5
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8 | 1 | 44",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8 | 2 | 1486",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8 | 3 | 62379",
            // Position 6
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10 | 1 | 46",
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10 | 2 | 2079",
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10 | 3 | 89890",
    })
    void matchesThePublishedCounts(String fen, int depth, long expected) {
        assertThat(perft(Position.fromFen(fen), depth)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 b - - 13 40",
    })
    void readsAndWritesFen(String fen) {
        assertThat(Position.fromFen(fen).fen()).isEqualTo(fen);
    }
}
