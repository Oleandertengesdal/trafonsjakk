package no.trafonsjakk.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swiss standings: 1 / ½ / 0 points and 1 for a bye; tie-breaks Buchholz (the sum of your opponents'
 * points: whoever met the stronger field ranks higher) → Sonneborn–Berger → wins → seed. Pure logic.
 */
public final class SwissStandings {

    public record Standing(int rank, long playerId, int played, int wins, int draws, int losses, int byes,
                           double points, double buchholz, double sonnebornBerger) {
    }

    private SwissStandings() {
    }

    /** {@code seeds}: player → seed (1 = best rated at the start). */
    public static List<Standing> compute(Map<Long, Integer> seeds, List<StandingsCalculator.Result> results, List<Long> byes) {
        Map<Long, Acc> acc = new HashMap<>();
        seeds.keySet().forEach(id -> acc.put(id, new Acc(id)));
        for (StandingsCalculator.Result r : results) {
            Acc w = acc.get(r.white());
            Acc b = acc.get(r.black());
            if (w != null && b != null) {
                w.add(r.black(), r.whiteScore());
                b.add(r.white(), 1 - r.whiteScore());
            }
        }
        for (Long id : byes) {
            Acc a = acc.get(id);
            if (a != null) {
                a.byes++;
                a.points += 1;
            }
        }
        for (Acc a : acc.values()) {
            for (Map.Entry<Long, List<Double>> e : a.scoresVs.entrySet()) {
                double opp = acc.get(e.getKey()).points;
                for (double s : e.getValue()) {
                    a.buchholz += opp;
                    a.sb += s * opp;
                }
            }
        }
        List<Acc> sorted = new ArrayList<>(acc.values());
        sorted.sort(Comparator.<Acc>comparingDouble(a -> -a.points)
                .thenComparingDouble(a -> -a.buchholz)
                .thenComparingDouble(a -> -a.sb)
                .thenComparingInt(a -> -a.wins)
                .thenComparingInt(a -> seeds.getOrDefault(a.id, Integer.MAX_VALUE)));
        List<Standing> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Acc a = sorted.get(i);
            out.add(new Standing(i + 1, a.id, a.wins + a.draws + a.losses, a.wins, a.draws, a.losses, a.byes, a.points,
                    a.buchholz, a.sb));
        }
        return out;
    }

    private static final class Acc {
        final long id;
        int wins;
        int draws;
        int losses;
        int byes;
        double points;
        double buchholz;
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
    }
}
