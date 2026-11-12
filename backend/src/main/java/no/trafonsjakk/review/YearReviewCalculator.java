package no.trafonsjakk.review;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * "Årets sjakk": a year of confirmed games, summed up per player and for the whole group. Pure functions:
 * the service hands in the games with the ratings and expected scores already looked up.
 */
public final class YearReviewCalculator {

    /**
     * One confirmed game. {@code date} is the local (Norwegian) date. {@code whiteExpected} is white's expected score
     * before the game (black's is 1 minus it). Ratings are before/after the game.
     */
    public record GameRow(long id, Instant playedAt, LocalDate date, long whiteId, long blackId, double whiteScore,
                          String opening, double whiteBefore, double whiteAfter, double blackBefore, double blackAfter,
                          double whiteExpected, int kudos, int comments, boolean hasMoves) {
    }

    public enum AwardKind { PLAYER_OF_MONTH, MOST_ACTIVE }

    public record AwardRow(YearMonth month, AwardKind kind, long userId) {
    }

    // ---- per player ----

    public record MonthStats(YearMonth month, int games, int wins, int draws, int losses, double overExpected, double ratingChange) {
    }

    /** A win against the odds: {@code expected} is the winner's expected score before the game. */
    public record Upset(long gameId, Instant playedAt, long winnerId, long loserId, double expected,
                        double winnerBefore, double loserBefore) {
    }

    public record Rival(long opponentId, int games, int wins, int draws, int losses) {
    }

    public record Streak(long userId, int length, Instant from, Instant to) {
    }

    public record OpeningStats(String name, int games, double scorePct) {
    }

    /** {@code day}: 1 = Monday … 7 = Sunday. */
    public record Weekday(int day, int games) {
    }

    public record MonthRating(YearMonth month, double rating) {
    }

    public record KudosGame(long gameId, int kudos, int comments) {
    }

    public record PlayerYear(long userId, int games, int wins, int draws, int losses, double points,
                             double ratingStart, double ratingEnd, double ratingPeak, List<MonthRating> ratingByMonth,
                             MonthStats bestMonth, Upset biggestUpset, Rival rival, Streak longestWinStreak,
                             OpeningStats favoriteOpening, Weekday busiestWeekday, int monthsActive, KudosGame mostKudos,
                             int gamesWithMoves, int rankByGames, int players, List<AwardRow> awards) {
    }

    // ---- group ----

    public record PlayerCount(long userId, int count) {
    }

    public record Climber(long userId, double change, double start, double end) {
    }

    public record AwardLeader(long userId, int playerOfMonth, int mostActive) {
    }

    /** The pair who met most often; {@code aId} has at least as many wins as {@code bId}. */
    public record Rivalry(long aId, long bId, int games, int aWins, int draws, int bWins) {
    }

    public record MonthCount(YearMonth month, int games) {
    }

    public record GroupYear(int games, int players, int whiteWins, int draws, int blackWins, int gamesWithMoves,
                            int kudos, int comments, MonthCount busiestMonth, Weekday busiestWeekday,
                            KudosGame gameOfTheYear, Upset giantKiller, List<AwardLeader> awardLeaders,
                            List<PlayerCount> mostGames, List<Climber> climbers, Streak longestWinStreak,
                            Rivalry topRivalry) {
    }

    /** Climbers need this many games in the year, so one lucky game doesn't top the list. */
    static final int CLIMBER_MIN_GAMES = 3;
    static final int TOP = 3;

    private YearReviewCalculator() {
    }

    /** Null when the player has no games that year. {@code games} must be the whole group's year, oldest first. */
    public static PlayerYear player(long me, List<GameRow> games, List<AwardRow> awards) {
        List<GameRow> mine = games.stream().filter(g -> g.whiteId() == me || g.blackId() == me).toList();
        if (mine.isEmpty()) {
            return null;
        }
        int wins = 0;
        int draws = 0;
        int losses = 0;
        double points = 0;
        double peak = before(mine.getFirst(), me);
        Map<YearMonth, double[]> months = new TreeMap<>(); // games, wins, draws, losses, overExpected, ratingChange
        TreeMap<YearMonth, Double> endOfMonth = new TreeMap<>();
        Map<Long, int[]> opponents = new LinkedHashMap<>();   // games, wins, draws, losses
        Map<Long, Instant> lastAgainst = new HashMap<>();
        Map<String, double[]> openings = new LinkedHashMap<>(); // games, points
        Map<String, Map<String, Integer>> openingSpellings = new HashMap<>();
        int[] weekdays = new int[8];
        Upset upset = null;
        KudosGame kudos = null;
        int withMoves = 0;

        for (GameRow g : mine) {
            boolean white = g.whiteId() == me;
            double s = white ? g.whiteScore() : 1 - g.whiteScore();
            double exp = white ? g.whiteExpected() : 1 - g.whiteExpected();
            double b = before(g, me);
            double a = white ? g.whiteAfter() : g.blackAfter();
            long opp = white ? g.blackId() : g.whiteId();
            points += s;
            if (s == 1) {
                wins++;
            } else if (s == 0) {
                losses++;
            } else {
                draws++;
            }
            peak = Math.max(peak, a);
            YearMonth ym = YearMonth.from(g.date());
            double[] m = months.computeIfAbsent(ym, k -> new double[6]);
            m[0]++;
            m[s == 1 ? 1 : s == 0 ? 3 : 2]++;
            m[4] += s - exp;
            m[5] += a - b;
            endOfMonth.put(ym, a);

            int[] o = opponents.computeIfAbsent(opp, k -> new int[4]);
            o[0]++;
            o[s == 1 ? 1 : s == 0 ? 3 : 2]++;
            lastAgainst.put(opp, g.playedAt());

            if (g.opening() != null && !g.opening().isBlank()) {
                String name = g.opening().trim();
                String key = name.toLowerCase(Locale.ROOT);
                double[] op = openings.computeIfAbsent(key, k -> new double[2]);
                op[0]++;
                op[1] += s;
                openingSpellings.computeIfAbsent(key, k -> new HashMap<>()).merge(name, 1, Integer::sum);
            }
            weekdays[g.date().getDayOfWeek().getValue()]++;
            if (s == 1 && (upset == null || exp < upset.expected())) {
                upset = new Upset(g.id(), g.playedAt(), me, opp, exp, b, white ? g.blackBefore() : g.whiteBefore());
            }
            if (g.kudos() > 0 && (kudos == null || g.kudos() > kudos.kudos()
                    || (g.kudos() == kudos.kudos() && g.comments() >= kudos.comments()))) {
                kudos = new KudosGame(g.id(), g.kudos(), g.comments());
            }
            if (g.hasMoves()) {
                withMoves++;
            }
        }

        // Best month: the best performance among months with at least two games (any month if none has two)
        int minGames = months.values().stream().anyMatch(m -> m[0] >= 2) ? 2 : 1;
        MonthStats best = null;
        for (Map.Entry<YearMonth, double[]> e : months.entrySet()) {
            double[] m = e.getValue();
            if (m[0] < minGames) {
                continue;
            }
            MonthStats ms = new MonthStats(e.getKey(), (int) m[0], (int) m[1], (int) m[2], (int) m[3], m[4], m[5]);
            if (best == null || better(ms, best)) {
                best = ms;
            }
        }

        // Rival: the opponent you met most; ties go to the closest record, then the most recent game
        Rival rival = null;
        Long rivalId = null;
        for (Map.Entry<Long, int[]> e : opponents.entrySet()) {
            int[] o = e.getValue();
            Rival r = new Rival(e.getKey(), o[0], o[1], o[2], o[3]);
            if (rival == null || r.games() > rival.games()
                    || (r.games() == rival.games() && Math.abs(r.wins() - r.losses()) < Math.abs(rival.wins() - rival.losses()))
                    || (r.games() == rival.games() && Math.abs(r.wins() - r.losses()) == Math.abs(rival.wins() - rival.losses())
                        && lastAgainst.get(r.opponentId()).isAfter(lastAgainst.get(rivalId)))) {
                rival = r;
                rivalId = e.getKey();
            }
        }

        OpeningStats opening = null;
        for (Map.Entry<String, double[]> e : openings.entrySet()) {
            double[] op = e.getValue();
            String name = openingSpellings.get(e.getKey()).entrySet().stream()
                    .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                    .orElseThrow().getKey();
            OpeningStats os = new OpeningStats(name, (int) op[0], op[1] / op[0]);
            if (opening == null || os.games() > opening.games() || (os.games() == opening.games() && os.scorePct() > opening.scorePct())) {
                opening = os;
            }
        }

        List<MonthRating> byMonth = new ArrayList<>();
        endOfMonth.forEach((k, v) -> byMonth.add(new MonthRating(k, v)));

        Map<Long, Integer> counts = gamesPerPlayer(games);
        int myGames = mine.size();
        int rank = 1 + (int) counts.values().stream().filter(n -> n > myGames).count();

        return new PlayerYear(me, myGames, wins, draws, losses, points,
                before(mine.getFirst(), me), after(mine.getLast(), me), peak, byMonth, best, upset, rival,
                longestWinStreaks(games).getOrDefault(me, null), opening, busiest(weekdays), months.size(), kudos,
                withMoves, rank, counts.size(), awards.stream().filter(a -> a.userId() == me).toList());
    }

    public static GroupYear group(List<GameRow> games, List<AwardRow> awards) {
        int whiteWins = 0;
        int draws = 0;
        int blackWins = 0;
        int withMoves = 0;
        int kudos = 0;
        int comments = 0;
        Map<YearMonth, Integer> perMonth = new TreeMap<>();
        int[] weekdays = new int[8];
        KudosGame gotY = null;
        Upset killer = null;
        Map<String, int[]> pairs = new LinkedHashMap<>(); // games, lowWins, draws, highWins
        Map<String, Instant> pairLast = new HashMap<>();
        Map<Long, Double> start = new HashMap<>();
        Map<Long, Double> end = new HashMap<>();

        for (GameRow g : games) {
            if (g.whiteScore() == 1) {
                whiteWins++;
            } else if (g.whiteScore() == 0) {
                blackWins++;
            } else {
                draws++;
            }
            if (g.hasMoves()) {
                withMoves++;
            }
            kudos += g.kudos();
            comments += g.comments();
            perMonth.merge(YearMonth.from(g.date()), 1, Integer::sum);
            weekdays[g.date().getDayOfWeek().getValue()]++;

            int buzz = g.kudos() + g.comments();
            if (buzz > 0 && (gotY == null || buzz > gotY.kudos() + gotY.comments()
                    || (buzz == gotY.kudos() + gotY.comments() && g.kudos() >= gotY.kudos()))) {
                gotY = new KudosGame(g.id(), g.kudos(), g.comments());
            }
            if (g.whiteScore() != 0.5) {
                boolean whiteWon = g.whiteScore() == 1;
                double exp = whiteWon ? g.whiteExpected() : 1 - g.whiteExpected();
                if (exp < 0.5 && (killer == null || exp < killer.expected())) {
                    killer = new Upset(g.id(), g.playedAt(), whiteWon ? g.whiteId() : g.blackId(),
                            whiteWon ? g.blackId() : g.whiteId(), exp,
                            whiteWon ? g.whiteBefore() : g.blackBefore(), whiteWon ? g.blackBefore() : g.whiteBefore());
                }
            }
            long lo = Math.min(g.whiteId(), g.blackId());
            long hi = Math.max(g.whiteId(), g.blackId());
            String key = lo + ":" + hi;
            int[] p = pairs.computeIfAbsent(key, k -> new int[4]);
            p[0]++;
            double loScore = g.whiteId() == lo ? g.whiteScore() : 1 - g.whiteScore();
            p[loScore == 1 ? 1 : loScore == 0 ? 3 : 2]++;
            pairLast.put(key, g.playedAt());

            start.putIfAbsent(g.whiteId(), g.whiteBefore());
            start.putIfAbsent(g.blackId(), g.blackBefore());
            end.put(g.whiteId(), g.whiteAfter());
            end.put(g.blackId(), g.blackAfter());
        }

        MonthCount busiestMonth = perMonth.entrySet().stream()
                .max(Map.Entry.<YearMonth, Integer>comparingByValue().thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .map(e -> new MonthCount(e.getKey(), e.getValue())).orElse(null);

        Map<Long, Integer> counts = gamesPerPlayer(games);
        List<PlayerCount> mostGames = counts.entrySet().stream()
                .map(e -> new PlayerCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(PlayerCount::count).reversed().thenComparingLong(PlayerCount::userId))
                .limit(TOP).toList();

        List<Climber> climbers = start.keySet().stream()
                .filter(id -> counts.get(id) >= CLIMBER_MIN_GAMES)
                .map(id -> new Climber(id, end.get(id) - start.get(id), start.get(id), end.get(id)))
                .filter(c -> c.change() > 0)
                .sorted(Comparator.comparingDouble(Climber::change).reversed().thenComparingLong(Climber::userId))
                .limit(TOP).toList();

        Map<Long, Streak> streaks = longestWinStreaks(games);
        Streak longest = streaks.values().stream()
                .max(Comparator.comparingInt(Streak::length).thenComparing(Streak::to, Comparator.reverseOrder()))
                .orElse(null);

        Rivalry rivalry = null;
        String rivalryKey = null;
        for (Map.Entry<String, int[]> e : pairs.entrySet()) {
            int[] p = e.getValue();
            if (p[0] < 2) {
                continue;
            }
            boolean replace = rivalry == null || p[0] > rivalry.games();
            if (!replace && p[0] == rivalry.games()) {
                int closeness = Math.abs(p[1] - p[3]);
                int current = Math.abs(rivalry.aWins() - rivalry.bWins());
                replace = closeness < current || (closeness == current && pairLast.get(e.getKey()).isAfter(pairLast.get(rivalryKey)));
            }
            if (replace) {
                String[] ids = e.getKey().split(":");
                long lo = Long.parseLong(ids[0]);
                long hi = Long.parseLong(ids[1]);
                rivalry = p[1] >= p[3] ? new Rivalry(lo, hi, p[0], p[1], p[2], p[3]) : new Rivalry(hi, lo, p[0], p[3], p[2], p[1]);
                rivalryKey = e.getKey();
            }
        }

        Map<Long, int[]> awardCounts = new HashMap<>();
        for (AwardRow a : awards) {
            awardCounts.computeIfAbsent(a.userId(), k -> new int[2])[a.kind() == AwardKind.PLAYER_OF_MONTH ? 0 : 1]++;
        }
        List<AwardLeader> leaders = awardCounts.entrySet().stream()
                .map(e -> new AwardLeader(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(AwardLeader::playerOfMonth).reversed()
                        .thenComparing(Comparator.comparingInt(AwardLeader::mostActive).reversed())
                        .thenComparingLong(AwardLeader::userId))
                .limit(5).toList();

        return new GroupYear(games.size(), counts.size(), whiteWins, draws, blackWins, withMoves, kudos, comments,
                busiestMonth, busiest(weekdays), gotY, killer, leaders, mostGames, climbers, longest, rivalry);
    }

    /** Longest run of consecutive wins per player (games in order; a draw or loss ends a run). */
    static Map<Long, Streak> longestWinStreaks(List<GameRow> games) {
        Map<Long, Streak> best = new HashMap<>();
        Map<Long, Streak> current = new HashMap<>();
        for (GameRow g : games) {
            for (long id : new long[] {g.whiteId(), g.blackId()}) {
                double s = id == g.whiteId() ? g.whiteScore() : 1 - g.whiteScore();
                if (s == 1) {
                    Streak c = current.get(id);
                    c = c == null ? new Streak(id, 1, g.playedAt(), g.playedAt()) : new Streak(id, c.length() + 1, c.from(), g.playedAt());
                    current.put(id, c);
                    Streak b = best.get(id);
                    if (b == null || c.length() > b.length()) {
                        best.put(id, c);
                    }
                } else {
                    current.remove(id);
                }
            }
        }
        return best;
    }

    private static Map<Long, Integer> gamesPerPlayer(List<GameRow> games) {
        Map<Long, Integer> counts = new HashMap<>();
        for (GameRow g : games) {
            counts.merge(g.whiteId(), 1, Integer::sum);
            counts.merge(g.blackId(), 1, Integer::sum);
        }
        return counts;
    }

    private static Weekday busiest(int[] weekdays) {
        Weekday w = null;
        for (int d = DayOfWeek.MONDAY.getValue(); d <= DayOfWeek.SUNDAY.getValue(); d++) {
            if (weekdays[d] > 0 && (w == null || weekdays[d] > w.games())) {
                w = new Weekday(d, weekdays[d]);
            }
        }
        return w;
    }

    private static boolean better(MonthStats a, MonthStats b) {
        long ao = Math.round(a.overExpected() * 1_000_000);
        long bo = Math.round(b.overExpected() * 1_000_000);
        if (ao != bo) {
            return ao > bo;
        }
        if (a.games() != b.games()) {
            return a.games() > b.games();
        }
        return Math.round(a.ratingChange()) > Math.round(b.ratingChange());
    }

    private static double before(GameRow g, long id) {
        return g.whiteId() == id ? g.whiteBefore() : g.blackBefore();
    }

    private static double after(GameRow g, long id) {
        return g.whiteId() == id ? g.whiteAfter() : g.blackAfter();
    }
}
