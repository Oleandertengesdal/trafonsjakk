package no.trafonsjakk.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Accuracy per player from the engine's evaluation of every position, exactly as the website computes it
 * ({@code frontend/src/chess/analysis.ts}), which follows lichess: each move's accuracy from how much winning
 * chance it gave away, and a player's game accuracy as the average of a volatility-weighted mean and a
 * harmonic mean of their moves. Pure functions.
 */
public final class GameAccuracy {

    /** An evaluation from White's side: centipawns, or a mate for {@code mateBy} ('w' or 'b'). */
    public record Score(Integer cp, Integer mate, Character mateBy) {
        public static Score cp(int cp) {
            return new Score(cp, null, null);
        }

        public static Score mate(int moves, char by) {
            return new Score(null, moves, by);
        }
    }

    /** One side's result: moves played and accuracy (0–100). */
    public record Side(int moves, double accuracy) {
    }

    public record Result(Side white, Side black) {
    }

    private GameAccuracy() {
    }

    /** White's chance of winning, 0–100 (lichess's curve). */
    public static double winPercent(Score s) {
        if (s.mate() != null) {
            return s.mateBy() == 'w' ? 100 : 0;
        }
        double cp = Math.max(-1000, Math.min(1000, s.cp()));
        return 50 + 50 * (2 / (1 + Math.exp(-0.00368208 * cp)) - 1);
    }

    public static double moveAccuracy(double winBefore, double winAfter) {
        double drop = Math.max(0, winBefore - winAfter);
        return Math.max(0, Math.min(100, 103.1668 * Math.exp(-0.04354 * drop) - 3.1669 + 1));
    }

    /** How much each move counts: the spread of White's winning chance around it (0.5–12). */
    public static double[] moveWeights(double[] win) {
        int moves = win.length - 1;
        if (moves < 1) {
            return new double[0];
        }
        int size = Math.max(2, Math.min(8, moves / 10));
        List<double[]> windows = new ArrayList<>();
        for (int i = 0; i < Math.min(size, win.length) - 2; i++) {
            windows.add(slice(win, 0, size));
        }
        if (win.length <= size) {
            windows.add(win);
        } else {
            for (int i = 0; i + size <= win.length; i++) {
                windows.add(slice(win, i, i + size));
            }
        }
        double[] out = new double[moves];
        for (int i = 0; i < moves; i++) {
            double[] w = windows.get(i);
            double mean = 0;
            for (double x : w) {
                mean += x;
            }
            mean /= w.length;
            double variance = 0;
            for (double x : w) {
                variance += (x - mean) * (x - mean);
            }
            out[i] = Math.max(0.5, Math.min(12, Math.sqrt(variance / w.length)));
        }
        return out;
    }

    public static double playerAccuracy(List<Double> accuracies, List<Double> weights) {
        if (accuracies.isEmpty()) {
            return 100;
        }
        double totalWeight = 0;
        double weighted = 0;
        double inverse = 0;
        for (int i = 0; i < accuracies.size(); i++) {
            totalWeight += weights.get(i);
            weighted += accuracies.get(i) * weights.get(i);
            inverse += 1 / Math.max(1, accuracies.get(i));
        }
        return (weighted / totalWeight + accuracies.size() / inverse) / 2;
    }

    /** {@code scores} has one entry per position: the start, then after every move. White moves first. */
    public static Result compute(List<Score> scores) {
        double[] win = new double[scores.size()];
        for (int i = 0; i < win.length; i++) {
            win[i] = winPercent(scores.get(i));
        }
        double[] weights = moveWeights(win);
        List<Double> wAcc = new ArrayList<>();
        List<Double> wWeight = new ArrayList<>();
        List<Double> bAcc = new ArrayList<>();
        List<Double> bWeight = new ArrayList<>();
        for (int ply = 1; ply < win.length; ply++) {
            boolean white = ply % 2 == 1;
            double before = white ? win[ply - 1] : 100 - win[ply - 1];
            double after = white ? win[ply] : 100 - win[ply];
            (white ? wAcc : bAcc).add(moveAccuracy(before, after));
            (white ? wWeight : bWeight).add(weights[ply - 1]);
        }
        return new Result(new Side(wAcc.size(), playerAccuracy(wAcc, wWeight)),
                new Side(bAcc.size(), playerAccuracy(bAcc, bWeight)));
    }

    private static double[] slice(double[] a, int from, int to) {
        double[] out = new double[to - from];
        System.arraycopy(a, from, out, 0, to - from);
        return out;
    }
}
