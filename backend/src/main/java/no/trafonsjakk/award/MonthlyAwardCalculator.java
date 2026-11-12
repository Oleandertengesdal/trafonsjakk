package no.trafonsjakk.award;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules for "Månedens spiller", as pure functions.
 * <p>
 * <b>Player of the month</b> scores the most points over expectation: for every game, your score (1, ½ or 0)
 * minus the expected score from Glicko-2 at the ratings you both had before it. Beating a stronger player
 * is worth a lot, beating a weaker one a little, and every game counts, so playing more helps if you play well.
 * Over everyone it adds up to zero. You need {@code minGames} games, and a positive total, to win.
 * Ties go to more games, then the bigger rating gain, then the older account.
 * <p>
 * <b>Most active</b> played the most games (at least {@code minGames}); ties go to the better performance.
 */
public final class MonthlyAwardCalculator {

    /** LIVE until the month ends, DECIDING during the grace period, then DECIDED. */
    public enum Status { LIVE, DECIDING, DECIDED }

    /**
     * One confirmed game. {@code whiteExpected} is white's expected score (black's is 1 minus it, as on the game page);
     * the rating changes are each side's after minus before.
     */
    public record GameRow(long whiteId, long blackId, double whiteScore, double whiteExpected,
                          double whiteRatingChange, double blackRatingChange) {
    }

    public record Standing(long userId, int games, int wins, int draws, int losses, double points, double expected,
                           double ratingChange, boolean qualified) {
        public double overExpected() {
            return points - expected;
        }
    }

    /** Both orderings of everyone who played, and the winners (null: nobody qualified). */
    public record Result(List<Standing> playerOfMonth, Long playerOfMonthWinner, List<Standing> mostActive, Long mostActiveWinner) {
    }

    private MonthlyAwardCalculator() {
    }

    public static Result compute(List<GameRow> games, int minGames) {
        Map<Long, Acc> acc = new HashMap<>();
        for (GameRow g : games) {
            acc.computeIfAbsent(g.whiteId(), Acc::new).add(g.whiteScore(), g.whiteExpected(), g.whiteRatingChange());
            acc.computeIfAbsent(g.blackId(), Acc::new).add(1 - g.whiteScore(), 1 - g.whiteExpected(), g.blackRatingChange());
        }
        List<Standing> all = new ArrayList<>();
        for (Acc a : acc.values()) {
            all.add(new Standing(a.id, a.games, a.wins, a.draws, a.losses, a.points, a.expected, a.change, a.games >= minGames));
        }

        Comparator<Standing> byPerformance = Comparator
                .comparing(Standing::qualified).reversed()
                .thenComparing(s -> -rounded(s.overExpected()))
                .thenComparing(s -> -s.games())
                .thenComparing(s -> -rounded(s.ratingChange()))
                .thenComparing(Standing::userId);
        List<Standing> performance = all.stream().sorted(byPerformance).toList();
        Long pom = !performance.isEmpty() && performance.getFirst().qualified() && rounded(performance.getFirst().overExpected()) > 0
                ? performance.getFirst().userId() : null;

        Comparator<Standing> byGames = Comparator
                .comparing((Standing s) -> -s.games())
                .thenComparing(s -> -rounded(s.overExpected()))
                .thenComparing(Standing::userId);
        List<Standing> active = all.stream().sorted(byGames).toList();
        Long mostActive = !active.isEmpty() && active.getFirst().qualified() ? active.getFirst().userId() : null;

        return new Result(performance, pom, active, mostActive);
    }

    /** Compares at a millionth of a point, so 0.1 + 0.2 and 0.3 are a tie. */
    private static long rounded(double v) {
        return Math.round(v * 1_000_000);
    }

    public static Instant startsAt(YearMonth month, ZoneId zone) {
        return month.atDay(1).atStartOfDay(zone).toInstant();
    }

    public static Instant endsAt(YearMonth month, ZoneId zone) {
        return startsAt(month.plusMonths(1), zone);
    }

    public static Status status(YearMonth month, Instant now, ZoneId zone, Duration decideAfter) {
        Instant ends = endsAt(month, zone);
        if (now.isBefore(ends)) {
            return Status.LIVE;
        }
        return now.isBefore(ends.plus(decideAfter)) ? Status.DECIDING : Status.DECIDED;
    }

    private static final class Acc {
        final long id;
        int games;
        int wins;
        int draws;
        int losses;
        double points;
        double expected;
        double change;

        Acc(long id) {
            this.id = id;
        }

        void add(double score, double exp, double ratingChange) {
            games++;
            if (score == 1) {
                wins++;
            } else if (score == 0) {
                losses++;
            } else {
                draws++;
            }
            points += score;
            expected += exp;
            change += ratingChange;
        }
    }
}
