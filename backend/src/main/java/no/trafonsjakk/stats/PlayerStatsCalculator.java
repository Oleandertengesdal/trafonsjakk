package no.trafonsjakk.stats;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes a player's statistics from their confirmed games. Pure logic; the service layer maps
 * opponent ids to user data. Games must be given in chronological order.
 */
public final class PlayerStatsCalculator {

    public enum Color { WHITE, BLACK }

    public enum Outcome { WIN, DRAW, LOSS }

    /** A confirmed game. {@code whiteScore} is 1, 0.5 or 0. Ratings are pre-game and may be null. */
    public record GameRecord(long gameId, Instant playedAt, long whiteId, long blackId, double whiteScore,
                             String opening, Double whiteRatingBefore, Double blackRatingBefore) {
    }

    public record Tally(int games, int wins, int draws, int losses) {
        public double winRate() {
            return games == 0 ? 0 : (double) wins / games;
        }

        public double scorePct() {
            return games == 0 ? 0 : (wins + 0.5 * draws) / games;
        }
    }

    public record Streaks(int longestWin, int longestLoss, int longestUnbeaten, Outcome currentType, int currentLength) {
    }

    public record HeadToHead(long opponentId, Tally record, Instant lastPlayedAt) {
    }

    public record BestWin(long gameId, long opponentId, double opponentRating, Instant playedAt) {
    }

    public record Opening(String name, int games, double scorePct) {
    }

    public record MonthCount(String month, int games) {
    }

    public record Result(Tally totals, Tally white, Tally black, Color favoriteColor, Streaks streaks,
                         List<HeadToHead> headToHead, Long nemesisId, int nemesisLosses,
                         Long favoriteVictimId, int favoriteVictimWins, BestWin bestWin,
                         Opening favoriteOpening, List<Outcome> recentForm, List<MonthCount> activity) {
    }

    private PlayerStatsCalculator() {
    }

    public static Result compute(long playerId, List<GameRecord> games, Instant now) {
        int[] tot = new int[3];
        int[] wh = new int[3];
        int[] bl = new int[3];
        Map<Long, int[]> h2h = new LinkedHashMap<>();
        Map<Long, Instant> h2hLast = new LinkedHashMap<>();
        Map<String, double[]> openings = new LinkedHashMap<>(); // name -> [games, score]
        BestWin best = null;

        int curWin = 0, curLoss = 0, curUnbeaten = 0;
        int maxWin = 0, maxLoss = 0, maxUnbeaten = 0;
        Outcome curType = null;
        int curLen = 0;

        for (GameRecord g : games) {
            boolean isWhite = g.whiteId() == playerId;
            if (!isWhite && g.blackId() != playerId) {
                continue;
            }
            double score = isWhite ? g.whiteScore() : 1.0 - g.whiteScore();
            Outcome o = score == 1.0 ? Outcome.WIN : score == 0.5 ? Outcome.DRAW : Outcome.LOSS;
            long opp = isWhite ? g.blackId() : g.whiteId();

            tot[o.ordinal()]++;
            (isWhite ? wh : bl)[o.ordinal()]++;
            h2h.computeIfAbsent(opp, k -> new int[3])[o.ordinal()]++;
            h2hLast.put(opp, g.playedAt());

            if (g.opening() != null && !g.opening().isBlank()) {
                double[] op = openings.computeIfAbsent(g.opening().trim(), k -> new double[2]);
                op[0]++;
                op[1] += score;
            }

            Double oppRating = isWhite ? g.blackRatingBefore() : g.whiteRatingBefore();
            if (o == Outcome.WIN && oppRating != null && (best == null || oppRating > best.opponentRating())) {
                best = new BestWin(g.gameId(), opp, oppRating, g.playedAt());
            }

            curWin = o == Outcome.WIN ? curWin + 1 : 0;
            curLoss = o == Outcome.LOSS ? curLoss + 1 : 0;
            curUnbeaten = o != Outcome.LOSS ? curUnbeaten + 1 : 0;
            maxWin = Math.max(maxWin, curWin);
            maxLoss = Math.max(maxLoss, curLoss);
            maxUnbeaten = Math.max(maxUnbeaten, curUnbeaten);
            curLen = o == curType ? curLen + 1 : 1;
            curType = o;
        }

        Tally totals = rec(tot);
        Tally white = rec(wh);
        Tally black = rec(bl);
        Color fav = null;
        if (white.games() > 0 && black.games() > 0 && white.scorePct() != black.scorePct()) {
            fav = white.scorePct() > black.scorePct() ? Color.WHITE : Color.BLACK;
        } else if (white.games() > 0 && black.games() == 0) {
            fav = Color.WHITE;
        } else if (black.games() > 0 && white.games() == 0) {
            fav = Color.BLACK;
        }

        List<HeadToHead> headToHead = new ArrayList<>();
        Long nemesis = null;
        int nemesisLosses = 0;
        Long victim = null;
        int victimWins = 0;
        for (Map.Entry<Long, int[]> e : h2h.entrySet()) {
            int[] r = e.getValue();
            headToHead.add(new HeadToHead(e.getKey(), rec(r), h2hLast.get(e.getKey())));
            if (r[Outcome.LOSS.ordinal()] > nemesisLosses) {
                nemesisLosses = r[Outcome.LOSS.ordinal()];
                nemesis = e.getKey();
            }
            if (r[Outcome.WIN.ordinal()] > victimWins) {
                victimWins = r[Outcome.WIN.ordinal()];
                victim = e.getKey();
            }
        }
        headToHead.sort(Comparator.comparingInt((HeadToHead h) -> -h.record().games())
                .thenComparing(HeadToHead::lastPlayedAt, Comparator.reverseOrder()));

        Opening favOpening = null;
        for (Map.Entry<String, double[]> e : openings.entrySet()) {
            int count = (int) e.getValue()[0];
            if (favOpening == null || count > favOpening.games()) {
                favOpening = new Opening(e.getKey(), count, e.getValue()[1] / count);
            }
        }

        List<Outcome> form = new ArrayList<>();
        for (int i = games.size() - 1; i >= 0 && form.size() < 10; i--) {
            GameRecord g = games.get(i);
            if (g.whiteId() != playerId && g.blackId() != playerId) {
                continue;
            }
            double s = g.whiteId() == playerId ? g.whiteScore() : 1.0 - g.whiteScore();
            form.add(s == 1.0 ? Outcome.WIN : s == 0.5 ? Outcome.DRAW : Outcome.LOSS);
        }

        YearMonth thisMonth = YearMonth.from(now.atZone(ZoneOffset.UTC));
        TreeMap<YearMonth, Integer> months = new TreeMap<>();
        for (int i = 11; i >= 0; i--) {
            months.put(thisMonth.minusMonths(i), 0);
        }
        for (GameRecord g : games) {
            if (g.whiteId() != playerId && g.blackId() != playerId) {
                continue;
            }
            YearMonth ym = YearMonth.from(g.playedAt().atZone(ZoneOffset.UTC));
            months.computeIfPresent(ym, (k, v) -> v + 1);
        }
        List<MonthCount> activity = new ArrayList<>();
        months.forEach((k, v) -> activity.add(new MonthCount(k.toString(), v)));

        return new Result(totals, white, black, fav,
                new Streaks(maxWin, maxLoss, maxUnbeaten, curType, curLen),
                headToHead, nemesis, nemesisLosses, victim, victimWins, best, favOpening, form, activity);
    }

    private static Tally rec(int[] r) {
        return new Tally(r[0] + r[1] + r[2], r[Outcome.WIN.ordinal()], r[Outcome.DRAW.ordinal()],
                r[Outcome.LOSS.ordinal()]);
    }
}
