package no.trafonsjakk.user;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.auth.LoginAttemptService;
import no.trafonsjakk.auth.TokenService;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.ClientAddress;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.live.LiveGameRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's own data: download all of it, or delete the account.
 * <p>
 * Deleting keeps the games, because they're the opponents' history and ratings too, but removes the person:
 * the name becomes {@link #DELETED_NAME}, the username {@code deleted_<id>}, the password can never match,
 * and their comments, likes and kudos go. Comments that others replied to keep their place without text,
 * the same as when a comment is deleted normally.
 */
@Service
public class AccountService {

    public static final String DELETED_NAME = "Slettet spiller";
    public static final String DELETED_PREFIX = "deleted_";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService attempts;
    private final TokenService tokens;
    private final LiveGameRepository live;

    public AccountService(UserRepository users, JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                          LoginAttemptService attempts, TokenService tokens, LiveGameRepository live) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.attempts = attempts;
        this.tokens = tokens;
        this.live = live;
    }

    /** Everything stored about the person, as plain JSON-friendly maps. */
    @Transactional(readOnly = true)
    public Map<String, Object> export(CurrentUser actor) {
        long id = actor.id();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now());
        out.put("account", rows("""
                select username, real_name, role, created_at, rating, rating_deviation, rated_games, peak_rating, last_game_at
                from users where id = ?""", id).getFirst());
        out.put("games", rows("""
                select g.id, w.username as white, b.username as black, g.result, g.status, g.played_at, g.opening, g.eco,
                       g.notes, g.moves, g.dispute_reason, r.username as reported_by, g.created_at
                from games g join users w on w.id = g.white_id join users b on b.id = g.black_id
                join users r on r.id = g.reported_by
                where g.white_id = ? or g.black_id = ? order by g.played_at, g.id""", id, id));
        out.put("ratingHistory", rows("""
                select game_id, rating_before, rating_after, rd_after, played_at
                from rating_history where user_id = ? order by played_at, id""", id));
        out.put("comments", rows("""
                select id, game_id, parent_id, body, created_at, edited_at, deleted_at
                from game_comments where author_id = ? order by created_at, id""", id));
        out.put("commentLikes", rows("select comment_id, created_at from comment_likes where user_id = ? order by created_at", id));
        out.put("kudosGiven", rows("select game_id, created_at from game_kudos where user_id = ? order by created_at", id));
        out.put("monthlyAwards", rows("""
                select month, kind, games, points, rating_change from monthly_awards where user_id = ? order by month, kind""", id));
        out.put("tournaments", rows("""
                select t.id, t.name, t.format, t.status, p.seed, p.joined_at
                from tournament_participants p join tournaments t on t.id = p.tournament_id
                where p.user_id = ? order by p.joined_at""", id));
        out.put("liveGames", rows("""
                select l.id, w.username as white, b.username as black, l.status, l.moves, l.game_id, l.started_at, l.updated_at
                from live_games l join users w on w.id = l.white_id join users b on b.id = l.black_id
                where l.white_id = ? or l.black_id = ? order by l.started_at""", id, id));
        out.put("invitesCreated", rows("""
                select created_at, expires_at, max_uses, use_count, revoked_at
                from invite_codes where created_by = ? order by created_at""", id));
        return out;
    }

    /** Needs the current password, guarded by the same lockout as logging in. */
    @Transactional
    public void delete(CurrentUser actor, String password, ClientAddress from) {
        User u = users.findById(actor.id()).orElseThrow(() -> ApiException.unauthorized("auth.not_logged_in", "Not logged in"));
        attempts.assertLoginAllowed(u.getUsername(), from);
        if (password == null || !passwordEncoder.matches(password, u.getPasswordHash())) {
            attempts.recordFailure(u.getUsername(), from);
            throw ApiException.badRequest("auth.wrong_current_password", "Your current password is incorrect");
        }
        if (u.getRole() == Role.ADMIN && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw ApiException.conflict("account.last_admin", "Make someone else an admin before you delete your account");
        }
        long id = u.getId();
        Instant now = Instant.now();

        jdbc.update("delete from comment_likes where user_id = ? or comment_id in (select id from game_comments where author_id = ?)", id, id);
        // A thread start that someone else replied to keeps its place, without text, so the replies still make sense
        jdbc.update("""
                update game_comments c set body = '', deleted_at = coalesce(c.deleted_at, ?)
                where c.author_id = ? and c.parent_id is null
                  and exists (select 1 from game_comments r where r.parent_id = c.id and r.author_id <> ?)""",
                Timestamp.from(now), id, id);
        jdbc.update("delete from game_comments where author_id = ? and (parent_id is not null or deleted_at is null)", id);
        jdbc.update("""
                delete from game_comments c where c.parent_id is null and c.deleted_at is not null
                  and not exists (select 1 from game_comments r where r.parent_id = c.id)""");
        jdbc.update("delete from game_kudos where user_id = ?", id);
        jdbc.update("delete from password_reset_tokens where user_id = ?", id);
        jdbc.update("update invite_codes set revoked_at = ? where created_by = ? and revoked_at is null", Timestamp.from(now), id);
        live.abandonAllFor(id, now);
        tokens.revokeAllForUser(id);

        byte[] noise = new byte[16];
        RANDOM.nextBytes(noise);
        // Not a BCrypt hash, so no password can ever match it
        u.anonymise(DELETED_PREFIX + id, DELETED_NAME, "!deleted!" + HexFormat.of().formatHex(noise), now);
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().map(AccountService::plain).toList();
    }

    /** JDBC hands back SQL date types; turn them into values JSON shows as readable dates. */
    private static Map<String, Object> plain(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> out.put(camel(k), switch (v) {
            case Timestamp t -> t.toInstant();
            case java.sql.Date d -> d.toLocalDate();
            case null -> null;
            default -> v;
        }));
        return out;
    }

    private static String camel(String column) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : column.toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return sb.toString();
    }
}
