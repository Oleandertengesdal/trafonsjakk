package no.trafonsjakk.rivalry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import no.trafonsjakk.game.GameResult;

/** The numbers of a rivalry between player A and player B, from their games (oldest first). Pure functions. */
public final class RivalryCalculator {

    /** One game between them, from A's side. Ratings before the game are null when unknown. */
    public record Game(long id, Instant playedAt, boolean aWhite, GameResult result, Double aBefore, Double bBefore,
                       Double aAccuracy, Double bAccuracy) {

        /** 1 when A won, ½ for a draw, 0 when B won. */
        double aPoints() {
            if (result == GameResult.DRAW) {
                return 0.5;
            }
            return (result == GameResult.WHITE_WIN) == aWhite ? 1 : 0;
        }
    }

    public enum Side { A, B }

    public record Record(int games, int aWins, int draws, int bWins, double aPoints) {
    }

    /** {@code side} won {@code length} in a row; null side when there's no streak (no games, or the last was drawn). */
    public record Streak(Side side, int length) {
    }

    /** The biggest win against a higher-rated opponent: by how many rating points they were behind. */
    public record Upset(long gameId, Side winner, int gap, Instant playedAt) {
    }

    /** A well-played game: both players' accuracy and their average. */
    public record BestGame(long gameId, Instant playedAt, double aAccuracy, double bAccuracy, double average) {
    }

    public record Result(Record overall, Record aAsWhite, Record aAsBlack, Streak current, Streak longest, Upset upset,
                         List<BestGame> best) {
    }

    static final int BEST = 3;

    private RivalryCalculator() {
    }

    public static Result compute(List<Game> games) {
        Record overall = record(games);
        Record asWhite = record(games.stream().filter(Game::aWhite).toList());
        Record asBlack = record(games.stream().filter(g -> !g.aWhite()).toList());

        Side runSide = null;
        int run = 0;
        Streak longest = new Streak(null, 0);
        for (Game g : games) {
            Side winner = g.aPoints() == 1 ? Side.A : g.aPoints() == 0 ? Side.B : null;
            if (winner == null) {
                runSide = null;
                run = 0;
                continue;
            }
            run = winner == runSide ? run + 1 : 1;
            runSide = winner;
            if (run > longest.length()) {
                longest = new Streak(winner, run);
            }
        }
        Streak current = new Streak(runSide, runSide == null ? 0 : run);

        Upset upset = null;
        for (Game g : games) {
            if (g.aBefore() == null || g.bBefore() == null || g.aPoints() == 0.5) {
                continue;
            }
            Side winner = g.aPoints() == 1 ? Side.A : Side.B;
            double gap = winner == Side.A ? g.bBefore() - g.aBefore() : g.aBefore() - g.bBefore();
            if (gap > 0 && (upset == null || Math.round(gap) > upset.gap())) {
                upset = new Upset(g.id(), winner, (int) Math.round(gap), g.playedAt());
            }
        }

        List<BestGame> best = new ArrayList<>();
        for (Game g : games) {
            if (g.aAccuracy() != null && g.bAccuracy() != null) {
                best.add(new BestGame(g.id(), g.playedAt(), g.aAccuracy(), g.bAccuracy(), (g.aAccuracy() + g.bAccuracy()) / 2));
            }
        }
        best.sort(Comparator.comparingDouble(BestGame::average).reversed().thenComparing(BestGame::playedAt, Comparator.reverseOrder()));
        return new Result(overall, asWhite, asBlack, current, longest, upset, List.copyOf(best.subList(0, Math.min(BEST, best.size()))));
    }

    static Record record(List<Game> games) {
        int a = 0;
        int d = 0;
        int b = 0;
        double points = 0;
        for (Game g : games) {
            double p = g.aPoints();
            points += p;
            if (p == 1) {
                a++;
            } else if (p == 0) {
                b++;
            } else {
                d++;
            }
        }
        return new Record(games.size(), a, d, b, points);
    }
}
