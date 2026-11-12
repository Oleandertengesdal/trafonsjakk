package no.trafonsjakk.award;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Decided monthly awards. Written once per (month, kind) with "on conflict do nothing", never updated. */
@Repository
public class AwardRepository {

    public enum Kind { PLAYER_OF_MONTH, MOST_ACTIVE }

    /** {@code userId} is null when the month was decided but nobody qualified. */
    public record Row(YearMonth month, Kind kind, Long userId, int games, double points, double expected,
                      double ratingChange, Instant decidedAt) {
    }

    private static final RowMapper<Row> MAPPER = (rs, i) -> new Row(
            YearMonth.from(rs.getObject("month", LocalDate.class)),
            Kind.valueOf(rs.getString("kind")),
            (Long) rs.getObject("user_id", Long.class),
            rs.getInt("games"),
            rs.getDouble("points"),
            rs.getDouble("expected"),
            rs.getDouble("rating_change"),
            rs.getTimestamp("decided_at").toInstant());

    private static final String ORDER = " order by month desc, case kind when 'PLAYER_OF_MONTH' then 0 else 1 end";

    private final JdbcTemplate jdbc;

    public AwardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<YearMonth> decidedMonths() {
        return new HashSet<>(jdbc.query("select distinct month from monthly_awards",
                (rs, i) -> YearMonth.from(rs.getObject("month", LocalDate.class))));
    }

    public void insert(YearMonth month, Kind kind, Long userId, int games, double points, double expected,
                       double ratingChange, Instant decidedAt) {
        jdbc.update("insert into monthly_awards (month, kind, user_id, games, points, expected, rating_change, decided_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing",
                month.atDay(1), kind.name(), userId, games, points, expected, ratingChange, Timestamp.from(decidedAt));
    }

    public List<Row> forMonth(YearMonth month) {
        return jdbc.query("select * from monthly_awards where month = ?" + ORDER, MAPPER, month.atDay(1));
    }

    /** Awards someone actually won, newest first; for one person when {@code userId} is set. */
    public List<Row> won(Long userId) {
        if (userId == null) {
            return jdbc.query("select * from monthly_awards where user_id is not null" + ORDER, MAPPER);
        }
        return jdbc.query("select * from monthly_awards where user_id = ?" + ORDER, MAPPER, userId);
    }
}
