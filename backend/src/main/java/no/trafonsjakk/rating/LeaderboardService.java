package no.trafonsjakk.rating;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leaderboard. Established players (RD ≤ 110) are ranked by rating; provisional players follow, unranked.
 * Wins, draws, losses and the recent trend are counted by the database, so a request never loads the
 * games themselves (or their moves).
 */
@Service
public class LeaderboardService {

    public record Row(Integer rank, PlayerRef player, int games, int wins, int draws, int losses,
                      Instant lastPlayedAt, int trend) {
    }

    private static final int TREND_GAMES = 5;

    /** Wins, draws and losses per player over all confirmed games. */
    private static final String RECORD_SQL = """
            select user_id,
                   count(*) filter (where score = 1)   as wins,
                   count(*) filter (where score = 0.5) as draws,
                   count(*) filter (where score = 0)   as losses
            from (select white_id as user_id,
                         case result when 'WHITE_WIN' then 1 when 'DRAW' then 0.5 else 0 end as score
                  from games where status = 'CONFIRMED'
                  union all
                  select black_id as user_id,
                         case result when 'BLACK_WIN' then 1 when 'DRAW' then 0.5 else 0 end as score
                  from games where status = 'CONFIRMED') scores
            group by user_id""";

    /** Per player: the rating after their latest game and the rating before the first of their last few. */
    private static final String TREND_SQL = """
            select user_id,
                   max(rating_after) filter (where rn = 1)    as last_after,
                   max(rating_before) filter (where rn = span) as first_before
            from (select user_id, rating_before, rating_after,
                         row_number() over (partition by user_id order by played_at desc, game_id desc) as rn,
                         least(count(*) over (partition by user_id), ?) as span
                  from rating_history) recent
            where rn <= span
            group by user_id""";

    /** Empty when the player is disabled or provisional. */
    private static final String RANK_SQL = """
            select 1 + (select count(*) from users o
                        where o.enabled and o.rating_deviation <= ? and o.rating > u.rating) as rank
            from users u
            where u.id = ? and u.enabled and u.rating_deviation <= ?""";

    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public LeaderboardService(UserRepository users, JdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Row> leaderboard() {
        Map<Long, int[]> wdl = new HashMap<>();
        jdbc.query(RECORD_SQL, rs -> {
            wdl.put(rs.getLong("user_id"), new int[]{rs.getInt("wins"), rs.getInt("draws"), rs.getInt("losses")});
        });
        Map<Long, Integer> trends = new HashMap<>();
        jdbc.query(TREND_SQL, rs -> {
            trends.put(rs.getLong("user_id"), (int) Math.round(rs.getDouble("last_after") - rs.getDouble("first_before")));
        }, TREND_GAMES);

        List<User> all = new ArrayList<>(users.findByEnabledTrueOrderByRatingDesc());
        all.sort(Comparator.comparing(User::isProvisional).thenComparing(Comparator.comparingDouble(User::getRating).reversed()));

        List<Row> rows = new ArrayList<>(all.size());
        int rank = 0;
        for (User u : all) {
            int[] r = wdl.getOrDefault(u.getId(), new int[3]);
            Integer rk = u.isProvisional() ? null : ++rank;
            rows.add(new Row(rk, PlayerRef.of(u), r[0] + r[1] + r[2], r[0], r[1], r[2], u.getLastGameAt(),
                    trends.getOrDefault(u.getId(), 0)));
        }
        return rows;
    }

    /** Rank among established players, or null if provisional/disabled. */
    @Transactional(readOnly = true)
    public Integer rankOf(long userId) {
        List<Integer> rank = jdbc.query(RANK_SQL, (rs, i) -> rs.getInt("rank"),
                Glicko2.PROVISIONAL_RD, userId, Glicko2.PROVISIONAL_RD);
        return rank.isEmpty() ? null : rank.getFirst();
    }
}
