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
 * There are two pools with the same rules: over the board and online ({@code games.mode}). A game only
 * moves the ratings of its own pool. A player's online rating starts from their over-the-board rating
 * at the time of their first online game (with at least {@link #ONLINE_SEED_RD} of uncertainty, since
 * playing with a clock on a screen is a different game), so it doesn't take a dozen games to be right.
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

    /** The least uncertainty a new online rating starts with. Provisional (RD > 110) until a few games. */
    static final double ONLINE_SEED_RD = 200.0;

    private record GameRow(long id, long white, long black, double whiteScore, Instant playedAt, boolean online) {
    }

    /** One pool's state during the replay. */
    private static final class Pool {
        final String name;
        final Map<Long, Rating> ratings = new HashMap<>();
        final Map<Long, Instant> lastPlayed = new HashMap<>();
        final Map<Long, Integer> counts = new HashMap<>();
        final Map<Long, Double> peaks = new HashMap<>();

        Pool(String name, List<Long> userIds) {
            this.name = name;
            for (Long id : userIds) {
                ratings.put(id, Rating.initial());
                peaks.put(id, Glicko2.DEFAULT_RATING);
            }
        }
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
                "SELECT id, white_id, black_id, result, played_at, mode FROM games WHERE status = 'CONFIRMED' "
                        + "ORDER BY played_at, id",
                (rs, i) -> new GameRow(rs.getLong("id"), rs.getLong("white_id"), rs.getLong("black_id"),
                        switch (rs.getString("result")) {
                            case "WHITE_WIN" -> 1.0;
                            case "BLACK_WIN" -> 0.0;
                            default -> 0.5;
                        },
                        rs.getTimestamp("played_at").toInstant(), "ONLINE".equals(rs.getString("mode"))));
        List<Long> userIds = jdbc.queryForList("SELECT id FROM users", Long.class);

        Pool otb = new Pool("OTB", userIds);
        Pool online = new Pool("ONLINE", userIds);
        List<Object[]> history = new ArrayList<>(games.size() * 2);
        for (GameRow g : games) {
            Pool pool = g.online() ? online : otb;
            if (g.online()) {
                seedOnline(otb, online, g.white(), g.playedAt());
                seedOnline(otb, online, g.black(), g.playedAt());
            }
            Rating w = withInactivity(pool.ratings.get(g.white()), pool.lastPlayed.get(g.white()), g.playedAt());
            Rating b = withInactivity(pool.ratings.get(g.black()), pool.lastPlayed.get(g.black()), g.playedAt());
            Rating w2 = glicko.update(w, b, g.whiteScore());
            Rating b2 = glicko.update(b, w, 1.0 - g.whiteScore());

            if (since == null || !g.playedAt().isBefore(since)) {
                history.add(row(g, g.white(), w, w2, pool.name));
                history.add(row(g, g.black(), b, b2, pool.name));
            }

            pool.ratings.put(g.white(), w2);
            pool.ratings.put(g.black(), b2);
            pool.lastPlayed.put(g.white(), g.playedAt());
            pool.lastPlayed.put(g.black(), g.playedAt());
            pool.counts.merge(g.white(), 1, Integer::sum);
            pool.counts.merge(g.black(), 1, Integer::sum);
            // Peak only counts once the rating is established
            if (!w2.provisional()) {
                pool.peaks.merge(g.white(), w2.rating(), Math::max);
            }
            if (!b2.provisional()) {
                pool.peaks.merge(g.black(), b2.rating(), Math::max);
            }
        }

        if (since == null) {
            jdbc.update("DELETE FROM rating_history");
        } else {
            jdbc.update("DELETE FROM rating_history WHERE played_at >= ?", Timestamp.from(since));
        }
        jdbc.batchUpdate("INSERT INTO rating_history (game_id, user_id, rating_before, rating_after, rd_before, "
                + "rd_after, volatility_after, played_at, pool) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", history);

        List<Object[]> userRows = new ArrayList<>(userIds.size());
        for (Long id : userIds) {
            Rating r = otb.ratings.get(id);
            Rating o = online.ratings.get(id);
            Instant last = otb.lastPlayed.get(id);
            Instant lastOnline = online.lastPlayed.get(id);
            userRows.add(new Object[]{r.rating(), r.rd(), r.volatility(), otb.counts.getOrDefault(id, 0),
                    otb.peaks.get(id), last == null ? null : Timestamp.from(last),
                    o.rating(), o.rd(), o.volatility(), online.counts.getOrDefault(id, 0),
                    online.peaks.get(id), lastOnline == null ? null : Timestamp.from(lastOnline), id});
        }
        jdbc.batchUpdate("UPDATE users SET rating = ?, rating_deviation = ?, volatility = ?, rated_games = ?, "
                + "peak_rating = ?, last_game_at = ?, online_rating = ?, online_rating_deviation = ?, "
                + "online_volatility = ?, online_rated_games = ?, online_peak_rating = ?, online_last_game_at = ? "
                + "WHERE id = ?", userRows);

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

    /**
     * Before a player's first online game: start their online rating from where their over-the-board
     * rating stands at that moment (its uncertainty grown by inactivity, and at least {@link #ONLINE_SEED_RD}).
     */
    private void seedOnline(Pool otb, Pool online, long userId, Instant at) {
        if (online.counts.containsKey(userId)) {
            return;
        }
        Rating board = withInactivity(otb.ratings.get(userId), otb.lastPlayed.get(userId), at);
        online.ratings.put(userId, new Rating(board.rating(), Math.max(board.rd(), ONLINE_SEED_RD), Glicko2.DEFAULT_VOLATILITY));
    }

    private static Object[] row(GameRow g, long userId, Rating before, Rating after, String pool) {
        return new Object[]{g.id(), userId, before.rating(), after.rating(), before.rd(), after.rd(),
                after.volatility(), Timestamp.from(g.playedAt()), pool};
    }
}
