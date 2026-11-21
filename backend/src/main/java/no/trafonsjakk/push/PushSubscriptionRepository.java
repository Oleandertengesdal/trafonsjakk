package no.trafonsjakk.push;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Browsers subscribed to push notifications ({@code push_subscriptions}). */
@Repository
public class PushSubscriptionRepository {

    public record Subscription(long id, long userId, String endpoint, String p256dh, String auth) {
    }

    private static final RowMapper<Subscription> ROW = (rs, i) -> new Subscription(rs.getLong("id"), rs.getLong("user_id"),
            rs.getString("endpoint"), rs.getString("p256dh"), rs.getString("auth"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public PushSubscriptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    /** Adds the browser, or moves it to this user with its new keys (someone else logged in on it). */
    public void save(long userId, String endpoint, String p256dh, String auth, Instant now) {
        jdbc.update("""
                insert into push_subscriptions (user_id, endpoint, p256dh, auth, created_at) values (?, ?, ?, ?, ?)
                on conflict (endpoint) do update set user_id = excluded.user_id, p256dh = excluded.p256dh,
                                                     auth = excluded.auth, created_at = excluded.created_at""",
                userId, endpoint, p256dh, auth, Timestamp.from(now));
    }

    public int count(long userId) {
        Integer n = jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** Removes the user's oldest subscriptions beyond {@code keep}. */
    public void trim(long userId, int keep) {
        jdbc.update("""
                delete from push_subscriptions where user_id = ? and id not in (
                    select id from push_subscriptions where user_id = ? order by created_at desc, id desc limit ?)""",
                userId, userId, keep);
    }

    public boolean delete(long userId, String endpoint) {
        return jdbc.update("delete from push_subscriptions where user_id = ? and endpoint = ?", userId, endpoint) > 0;
    }

    /** The push service says this browser is gone. */
    public void forget(long id) {
        jdbc.update("delete from push_subscriptions where id = ?", id);
    }

    public List<Subscription> forUsers(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return named.query("select * from push_subscriptions where user_id in (:ids)", new MapSqlParameterSource("ids", userIds), ROW);
    }

    public boolean has(long userId, String endpoint) {
        Integer n = jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ? and endpoint = ?",
                Integer.class, userId, endpoint);
        return n != null && n > 0;
    }
}
