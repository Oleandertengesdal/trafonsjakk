package no.trafonsjakk.stats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.rating.LeaderboardService;
import no.trafonsjakk.rating.RatingHistory;
import no.trafonsjakk.rating.RatingHistoryRepository;
import no.trafonsjakk.stats.PlayerStatsCalculator.GameRecord;
import no.trafonsjakk.stats.PlayerStatsCalculator.Outcome;
import no.trafonsjakk.stats.PlayerStatsCalculator.Tally;
import no.trafonsjakk.tournament.TournamentRepository;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserDtos.Profile;
import no.trafonsjakk.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

    public record Totals(int games, int wins, int draws, int losses, double winRate, double scorePct) {
    }

    public record ColorStats(int games, int wins, int draws, int losses, double scorePct) {
    }

    public record Current(Outcome type, int length) {
    }

    public record Streaks(int longestWin, int longestLoss, int longestUnbeaten, Current current) {
    }

    public record RatingPoint(Instant date, int rating, int rd, Long gameId, String opponent, String result) {
    }

    public record HeadToHead(PlayerRef opponent, int games, int wins, int draws, int losses, double scorePct,
                             Instant lastPlayedAt) {
    }

    public record Nemesis(PlayerRef opponent, int losses) {
    }

    public record Victim(PlayerRef opponent, int wins) {
    }

    public record BestWin(long gameId, PlayerRef opponent, int opponentRating, Instant playedAt) {
    }

    public record Opening(String name, int games, double scorePct) {
    }

    public record Month(String month, int games) {
    }

    public record Stats(Profile player, Totals totals, ColorStats white, ColorStats black, String favoriteColor,
                        Streaks streaks, List<RatingPoint> ratingHistory, int peakRating, int lowestRating,
                        List<HeadToHead> headToHead, Nemesis nemesis, Victim favoriteVictim, BestWin bestWin,
                        Opening favoriteOpening, List<String> recentForm, List<Month> activity,
                        long tournamentWins) {
    }

    /** A confirmed game as the statistics need it: no moves, no notes, no loaded players. */
    private record PlayedGame(long id, Instant playedAt, long whiteId, long blackId, GameResult result, String opening) {
    }

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final RatingHistoryRepository history;
    private final TournamentRepository tournaments;
    private final LeaderboardService leaderboard;

    public StatsService(UserRepository users, JdbcTemplate jdbc, RatingHistoryRepository history,
                        TournamentRepository tournaments, LeaderboardService leaderboard) {
        this.users = users;
        this.jdbc = jdbc;
        this.history = history;
        this.tournaments = tournaments;
        this.leaderboard = leaderboard;
    }

    @Transactional(readOnly = true)
    public Profile profile(String username) {
        return profile(find(username));
    }

    private Profile profile(User u) {
        PlayerRef p = PlayerRef.of(u);
        int peak = (int) Math.round(Math.max(u.getPeakRating(), u.isProvisional() ? 0 : u.getRating()));
        return new Profile(p.id(), p.username(), p.realName(), p.rating(), p.rd(), p.provisional(), u.getRole(),
                leaderboard.rankOf(u.getId()), u.getCreatedAt(), u.getRatedGames(), peak);
    }

    @Transactional(readOnly = true)
    public Stats stats(String username) {
        User me = find(username);
        long myId = me.getId();
        List<PlayedGame> myGames = jdbc.query("""
                        select id, played_at, white_id, black_id, result, opening from games
                        where status = 'CONFIRMED' and (white_id = ? or black_id = ?)
                        order by played_at, id""",
                (rs, i) -> new PlayedGame(rs.getLong("id"), rs.getTimestamp("played_at").toInstant(), rs.getLong("white_id"),
                        rs.getLong("black_id"), GameResult.valueOf(rs.getString("result")), rs.getString("opening")),
                myId, myId);
        Map<Long, User> people = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));

        // Pre-game ratings of both players for every game (for "best win")
        Map<Long, Map<Long, Double>> before = new HashMap<>();
        if (!myGames.isEmpty()) {
            for (RatingHistory h : history.findByGameIdIn(myGames.stream().map(PlayedGame::id).toList())) {
                before.computeIfAbsent(h.getGameId(), k -> new HashMap<>()).put(h.getUserId(), h.getRatingBefore());
            }
        }

        List<GameRecord> records = new ArrayList<>(myGames.size());
        Map<Long, PlayedGame> gameById = new HashMap<>();
        for (PlayedGame g : myGames) {
            Map<Long, Double> b = before.getOrDefault(g.id(), Map.of());
            long w = g.whiteId();
            long bl = g.blackId();
            records.add(new GameRecord(g.id(), g.playedAt(), w, bl, g.result().whiteScore(),
                    g.opening(), b.get(w), b.get(bl)));
            gameById.put(g.id(), g);
        }
        PlayerStatsCalculator.Result r = PlayerStatsCalculator.compute(myId, records, Instant.now());

        List<RatingPoint> points = new ArrayList<>();
        // Everyone starts at 1500, so that's the baseline for peak/lowest.
        int peak = 1500;
        int lowest = 1500;
        for (RatingHistory h : history.findForUser(myId)) {
            PlayedGame g = gameById.get(h.getGameId());
            String opponent = null;
            String res = null;
            if (g != null) {
                boolean white = g.whiteId() == myId;
                User opp = people.get(white ? g.blackId() : g.whiteId());
                opponent = opp == null ? null : opp.getUsername();
                double s = white ? g.result().whiteScore() : 1 - g.result().whiteScore();
                res = s == 1 ? "W" : s == 0.5 ? "D" : "L";
            }
            int rating = (int) Math.round(h.getRatingAfter());
            peak = Math.max(peak, rating);
            lowest = Math.min(lowest, rating);
            points.add(new RatingPoint(h.getPlayedAt(), rating, (int) Math.round(h.getRdAfter()), h.getGameId(),
                    opponent, res));
        }

        Tally t = r.totals();
        return new Stats(
                profile(me),
                new Totals(t.games(), t.wins(), t.draws(), t.losses(), t.winRate(), t.scorePct()),
                color(r.white()),
                color(r.black()),
                r.favoriteColor() == null ? null : r.favoriteColor().name(),
                new Streaks(r.streaks().longestWin(), r.streaks().longestLoss(), r.streaks().longestUnbeaten(),
                        new Current(r.streaks().currentType(), r.streaks().currentLength())),
                points,
                peak,
                lowest,
                r.headToHead().stream()
                        .map(h -> new HeadToHead(PlayerRef.of(people.get(h.opponentId())), h.record().games(),
                                h.record().wins(), h.record().draws(), h.record().losses(), h.record().scorePct(),
                                h.lastPlayedAt()))
                        .toList(),
                r.nemesisId() == null ? null : new Nemesis(PlayerRef.of(people.get(r.nemesisId())), r.nemesisLosses()),
                r.favoriteVictimId() == null ? null
                        : new Victim(PlayerRef.of(people.get(r.favoriteVictimId())), r.favoriteVictimWins()),
                r.bestWin() == null ? null
                        : new BestWin(r.bestWin().gameId(), PlayerRef.of(people.get(r.bestWin().opponentId())),
                        (int) Math.round(r.bestWin().opponentRating()), r.bestWin().playedAt()),
                r.favoriteOpening() == null ? null
                        : new Opening(r.favoriteOpening().name(), r.favoriteOpening().games(),
                        r.favoriteOpening().scorePct()),
                r.recentForm().stream().map(o -> switch (o) {
                    case WIN -> "W";
                    case DRAW -> "D";
                    case LOSS -> "L";
                }).toList(),
                r.activity().stream().map(m -> new Month(m.month(), m.games())).toList(),
                tournaments.countWins(myId));
    }

    private static ColorStats color(Tally t) {
        return new ColorStats(t.games(), t.wins(), t.draws(), t.losses(), t.scorePct());
    }

    private User find(String username) {
        return users.findByUsernameIgnoreCase(username).orElseThrow(() -> ApiException.notFound("player.not_found", "Player not found"));
    }
}
