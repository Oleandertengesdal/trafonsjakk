package no.trafonsjakk.social;

import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Kudos are a plain (game, person) pair, so this uses SQL directly. Giving is "insert … on conflict do
 * nothing": a double click or two tabs can never create two rows or an error.
 */
@Repository
public class KudosRepository {

    /** Kudos on one game: how many, whether the viewer gave one, and who did. */
    public record GameKudos(long gameId, int count, boolean givenByMe) {
    }

    private final JdbcTemplate jdbc;

    public KudosRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void give(long gameId, long userId) {
        jdbc.update("insert into game_kudos (game_id, user_id, created_at) values (?, ?, now()) on conflict do nothing",
                gameId, userId);
    }

    public void take(long gameId, long userId) {
        jdbc.update("delete from game_kudos where game_id = ? and user_id = ?", gameId, userId);
    }

    public List<GameKudos> forGames(Collection<Long> gameIds, long viewerId) {
        if (gameIds.isEmpty()) {
            return List.of();
        }
        Long[] ids = gameIds.toArray(Long[]::new);
        return jdbc.query("select game_id, count(*) n, bool_or(user_id = ?) mine from game_kudos "
                        + "where game_id = any (?) group by game_id",
                (rs, i) -> new GameKudos(rs.getLong("game_id"), rs.getInt("n"), rs.getBoolean("mine")),
                viewerId, ids);
    }

    /** Who gave kudos, earliest first. */
    public List<Long> giverIds(long gameId) {
        return jdbc.queryForList("select user_id from game_kudos where game_id = ? order by created_at, user_id",
                Long.class, gameId);
    }
}
