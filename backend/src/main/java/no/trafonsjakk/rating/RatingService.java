package no.trafonsjakk.rating;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.rating.Glicko2.Rating;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rebuilds every rating from scratch by replaying all CONFIRMED games in order of play.
 * <p>
 * Called whenever the set of confirmed games changes. Correct for back-dated, late-confirmed,
 * corrected and voided games. The replay itself is in memory (milliseconds for thousands of games);
 * {@link #recomputeFrom} then stores only the part of the history that can have changed.
 * A PostgreSQL advisory lock serialises concurrent recomputes.
 */
@Service
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);
    private static final long ADVISORY_LOCK_KEY = 727_274_001L;

    public record RecomputeSummary(int games, int players, long millis) {
    }

    private record GameRow(long id, long white, long black, double whiteScore, Instant playedAt) {
    }

    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Glicko2 glicko = new Glicko2();

    public RatingService(JdbcTemplate jdbc, EntityManager em) {
        this.jdbc = jdbc;
        this.em = em;
    }

    /**
     * Must run inside the caller's transaction so the change and the new ratings commit together.
     * Clears the persistence context afterwards: callers must re-load entities they still need.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RecomputeSummary recomputeAll() {
        return recompute(null);
    }

    /**
     * The same replay, but only the stored history from {@code since} onwards is rewritten. A game can't
     * change anything that happened before it was played, so the earlier rows are already right; this
     * keeps a confirmation cheap however long the history gets. Pass the time the changed game was played
     * (the earliest, if several). Same transaction rules as {@link #recomputeAll()}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RecomputeSummary recomputeFrom(Instant since) {
        // The database keeps microseconds; rounding down can only widen what is rewritten
        return recompute(since.truncatedTo(ChronoUnit.MICROS));
    }

    private RecomputeSummary recompute(Instant since) {
        long start = System.nanoTime();
        em.flush();
        jdbc.execute("SELECT pg_advisory_xact_lock(" + ADVISORY_LOCK_KEY + ")");

        List<GameRow> games = jdbc.query(
                "SELECT id, white_id, black_id, result, played_at FROM games WHERE status = 'CONFIRMED' "
                        + "ORDER BY played_at, id",
                (rs, i) -> new GameRow(rs.getLong("id"), rs.getLong("white_id"), rs.getLong("black_id"),
                        switch (rs.getString("result")) {
                            case "WHITE_WIN" -> 1.0;
                            case "BLACK_WIN" -> 0.0;
                            default -> 0.5;
                        },
                        rs.getTimestamp("played_at").toInstant()));
        List<Long> userIds = jdbc.queryForList("SELECT id FROM users", Long.class);

        Map<Long, Rating> ratings = new HashMap<>();
        Map<Long, Instant> lastPlayed = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Double> peaks = new HashMap<>();
        for (Long id : userIds) {
            ratings.put(id, Rating.initial());
            peaks.put(id, Glicko2.DEFAULT_RATING);
        }

        List<Object[]> history = new ArrayList<>(games.size() * 2);
        for (GameRow g : games) {
            Rating w = withInactivity(ratings.get(g.white()), lastPlayed.get(g.white()), g.playedAt());
            Rating b = withInactivity(ratings.get(g.black()), lastPlayed.get(g.black()), g.playedAt());
            Rating w2 = glicko.update(w, b, g.whiteScore());
            Rating b2 = glicko.update(b, w, 1.0 - g.whiteScore());

            if (since == null || !g.playedAt().isBefore(since)) {
                history.add(row(g, g.white(), w, w2));
                history.add(row(g, g.black(), b, b2));
            }

            ratings.put(g.white(), w2);
            ratings.put(g.black(), b2);
            lastPlayed.put(g.white(), g.playedAt());
            lastPlayed.put(g.black(), g.playedAt());
            counts.merge(g.white(), 1, Integer::sum);
            counts.merge(g.black(), 1, Integer::sum);
            // Peak only counts once the rating is established
            if (!w2.provisional()) {
                peaks.merge(g.white(), w2.rating(), Math::max);
            }
            if (!b2.provisional()) {
                peaks.merge(g.black(), b2.rating(), Math::max);
            }
        }

        if (since == null) {
            jdbc.update("DELETE FROM rating_history");
        } else {
            jdbc.update("DELETE FROM rating_history WHERE played_at >= ?", Timestamp.from(since));
        }
        jdbc.batchUpdate("INSERT INTO rating_history (game_id, user_id, rating_before, rating_after, rd_before, "
                + "rd_after, volatility_after, played_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", history);

        List<Object[]> userRows = new ArrayList<>(userIds.size());
        for (Long id : userIds) {
            Rating r = ratings.get(id);
            Instant last = lastPlayed.get(id);
            userRows.add(new Object[]{r.rating(), r.rd(), r.volatility(), counts.getOrDefault(id, 0),
                    peaks.get(id), last == null ? null : Timestamp.from(last), id});
        }
        jdbc.batchUpdate("UPDATE users SET rating = ?, rating_deviation = ?, volatility = ?, rated_games = ?, "
                + "peak_rating = ?, last_game_at = ? WHERE id = ?", userRows);

        em.clear();
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        log.debug("Recomputed ratings: {} games, {} players in {} ms", games.size(), userIds.size(), millis);
        return new RecomputeSummary(games.size(), userIds.size(), millis);
    }

    /** Admin entry point: runs a recompute in its own transaction. */
    @Transactional
    public RecomputeSummary recomputeNow() {
        return recomputeAll();
    }

    /** The rating a player would have right now, including inactivity RD growth (for display/seeding). */
    public Rating currentWithInactivity(double rating, double rd, double volatility, Instant lastGameAt) {
        return withInactivity(new Rating(rating, rd, volatility), lastGameAt, Instant.now());
    }

    private Rating withInactivity(Rating r, Instant last, Instant now) {
        if (last == null || !now.isAfter(last)) {
            return r;
        }
        double days = Duration.between(last, now).toMinutes() / (60.0 * 24.0);
        return glicko.decay(r, days / Glicko2.RATING_PERIOD_DAYS);
    }

    private static Object[] row(GameRow g, long userId, Rating before, Rating after) {
        return new Object[]{g.id(), userId, before.rating(), after.rating(), before.rd(), after.rd(),
                after.volatility(), Timestamp.from(g.playedAt())};
    }
}
