package no.trafonsjakk.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Round-robin standings: 1 / ½ / 0 points, tie-breaks Sonneborn–Berger → wins → direct encounter
 * (two-way ties only) → the supplied final tie-break (e.g. name). Pure logic.
 */
public final class StandingsCalculator {

    /** A finished game. {@code whiteScore} is 1, 0.5 or 0. */
    public record Result(long white, long black, double whiteScore) {
    }

    public record Standing(int rank, long playerId, int played, int wins, int draws, int losses,
                           double points, double sonnebornBerger) {
    }

    private StandingsCalculator() {
    }

    public static List<Standing> compute(List<Long> players, List<Result> results, Comparator<Long> finalTieBreak) {
        Map<Long, Acc> acc = new HashMap<>();
        for (Long p : players) {
            acc.put(p, new Acc(p));
        }
        for (Result r : results) {
            Acc w = acc.get(r.white());
            Acc b = acc.get(r.black());
            if (w == null || b == null) {
                continue;
            }
            w.add(r.black(), r.whiteScore());
            b.add(r.white(), 1.0 - r.whiteScore());
        }
        // Sonneborn–Berger: sum of beaten opponents' points + half of drawn opponents' points
        for (Acc a : acc.values()) {
            double sb = 0;
            for (Map.Entry<Long, List<Double>> e : a.scoresVs.entrySet()) {
                double oppPoints = acc.get(e.getKey()).points;
                for (double s : e.getValue()) {
                    sb += s * oppPoints;
                }
            }
            a.sb = sb;
        }

        Comparator<Acc> primary = Comparator.<Acc>comparingDouble(a -> -a.points)
                .thenComparingDouble(a -> -a.sb)
                .thenComparingInt(a -> -a.wins)
                .thenComparing(a -> a.id, finalTieBreak);
        List<Acc> sorted = new ArrayList<>(acc.values());
        sorted.sort(primary);

        // Direct encounter, only for exact two-way ties on points/SB/wins (keeps sorting transitive).
        for (int i = 0; i + 1 < sorted.size(); i++) {
            Acc x = sorted.get(i);
            Acc y = sorted.get(i + 1);
            boolean tied = sameKey(x, y);
            boolean groupOfTwo = tied
                    && (i == 0 || !sameKey(sorted.get(i - 1), x))
                    && (i + 2 >= sorted.size() || !sameKey(y, sorted.get(i + 2)));
            if (groupOfTwo && x.scoreAgainst(y.id) < y.scoreAgainst(x.id)) {
                sorted.set(i, y);
                sorted.set(i + 1, x);
            }
        }

        List<Standing> out = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            Acc a = sorted.get(i);
            out.add(new Standing(i + 1, a.id, a.wins + a.draws + a.losses, a.wins, a.draws, a.losses, a.points, a.sb));
        }
        return out;
    }

    private static boolean sameKey(Acc a, Acc b) {
        return a.points == b.points && a.sb == b.sb && a.wins == b.wins;
    }

    private static final class Acc {
        final long id;
        int wins;
        int draws;
        int losses;
        double points;
        double sb;
        final Map<Long, List<Double>> scoresVs = new HashMap<>();

        Acc(long id) {
            this.id = id;
        }

        void add(long opponent, double score) {
            points += score;
            if (score == 1.0) {
                wins++;
            } else if (score == 0.5) {
                draws++;
            } else {
                losses++;
            }
            scoresVs.computeIfAbsent(opponent, k -> new ArrayList<>()).add(score);
        }

        double scoreAgainst(long opponent) {
            return scoresVs.getOrDefault(opponent, List.of()).stream().mapToDouble(Double::doubleValue).sum();
        }
    }
}
