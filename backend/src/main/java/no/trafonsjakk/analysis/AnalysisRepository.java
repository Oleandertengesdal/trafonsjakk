package no.trafonsjakk.analysis;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Saved engine analyses ({@code game_analyses}). One per game; the first one saved stays. */
@Repository
public class AnalysisRepository {

    public record Row(long gameId, String movesHash, int depth, int plies, String evals, double whiteAccuracy,
                      double blackAccuracy, Instant createdAt) {
    }

    /** An analysed, confirmed game, for statistics. Rating fields come from the rating history (null if missing). */
    public record GameRow(long gameId, long whiteId, long blackId, String result, Instant playedAt, int plies,
                          double whiteAccuracy, double blackAccuracy, Double whiteRatingBefore, Double blackRatingBefore) {

        public int whiteMoves() {
            return (plies + 1) / 2;
        }

        public int blackMoves() {
            return plies / 2;
        }
    }

    private static final RowMapper<Row> ROW = (rs, i) -> new Row(rs.getLong("game_id"), rs.getString("moves_hash"),
            rs.getInt("depth"), rs.getInt("plies"), rs.getString("evals"), rs.getDouble("white_accuracy"),
            rs.getDouble("black_accuracy"), rs.getTimestamp("created_at").toInstant());

    private static final RowMapper<GameRow> GAME_ROW = (rs, i) -> new GameRow(rs.getLong("game_id"), rs.getLong("white_id"),
            rs.getLong("black_id"), rs.getString("result"), rs.getTimestamp("played_at").toInstant(), rs.getInt("plies"),
            rs.getDouble("white_accuracy"), rs.getDouble("black_accuracy"),
            (Double) rs.getObject("white_before", Double.class), (Double) rs.getObject("black_before", Double.class));

    private static final String CONFIRMED_GAMES = """
            select a.game_id, g.white_id, g.black_id, g.result, g.played_at, a.plies, a.white_accuracy, a.black_accuracy,
                   wh.rating_before as white_before, bh.rating_before as black_before
            from game_analyses a
            join games g on g.id = a.game_id
            left join rating_history wh on wh.game_id = g.id and wh.user_id = g.white_id
            left join rating_history bh on bh.game_id = g.id and bh.user_id = g.black_id
            where g.status = 'CONFIRMED'
            """;

    private final JdbcTemplate jdbc;

    public AnalysisRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Row> find(long gameId) {
        return jdbc.query("select * from game_analyses where game_id = ?", ROW, gameId).stream().findFirst();
    }

    /** False when the game already has one (someone else was first). */
    public boolean insert(long gameId, String movesHash, int depth, int plies, String evals, double whiteAccuracy,
                          double blackAccuracy, long analysedBy, Instant now) {
        return jdbc.update("""
                insert into game_analyses (game_id, moves_hash, depth, plies, evals, white_accuracy, black_accuracy,
                                           analysed_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing""",
                gameId, movesHash, depth, plies, evals, whiteAccuracy, blackAccuracy, analysedBy, Timestamp.from(now)) == 1;
    }

    public void delete(long gameId) {
        jdbc.update("delete from game_analyses where game_id = ?", gameId);
    }

    /** One player's analysed, confirmed games, newest first. */
    public List<GameRow> forPlayer(long userId) {
        return jdbc.query(CONFIRMED_GAMES + " and (g.white_id = ? or g.black_id = ?) order by g.played_at desc, g.id desc",
                GAME_ROW, userId, userId);
    }

    /** Analysed, confirmed games played in [from, to). */
    public List<GameRow> playedBetween(Instant from, Instant to) {
        return jdbc.query(CONFIRMED_GAMES + " and g.played_at >= ? and g.played_at < ?", GAME_ROW,
                Timestamp.from(from), Timestamp.from(to));
    }

    /** Every analysed, confirmed game (for calibrating "played like"). */
    public List<GameRow> allConfirmed() {
        return jdbc.query(CONFIRMED_GAMES, GAME_ROW);
    }
}
