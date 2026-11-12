package no.trafonsjakk.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

/**
 * Boots the real application on a random port against PostgreSQL and talks to it over HTTP,
 * so security config, cookies, CSRF, transactions and SQL are all exercised for real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@RequiresDatabase
public abstract class IntegrationTest {

    protected static final String BOOTSTRAP_CODE = "test-bootstrap-code";
    protected static final String PASSWORD = "correct-horse-battery";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE rating_history, games, tournament_matches, tournament_participants, tournaments, "
                + "monthly_awards, comment_likes, game_comments, game_kudos, password_reset_tokens, invite_redemptions, "
                + "refresh_tokens, invite_codes, users RESTART IDENTITY CASCADE");
    }

    /** A user that is logged in. {@code id} and {@code username} come from the API. */
    public record Player(ApiSession api, long id, String username) {
    }

    protected ApiSession session() {
        return new ApiSession(port);
    }

    protected static String uniqueName(String base) {
        String name = base + "_" + SEQ.incrementAndGet();
        return name.length() <= 20 ? name : name.substring(name.length() - 20);
    }

    /** First account on an empty database, using the bootstrap code: becomes admin. */
    protected Player registerAdmin() {
        return register(BOOTSTRAP_CODE, uniqueName("admin"));
    }

    /** A regular member, invited by {@code inviter}. */
    protected Player registerMember(Player inviter, String name) {
        String code = inviter.api().post("/api/invites").json().path("code").asString();
        return register(code, uniqueName(name));
    }

    protected Player register(String inviteCode, String username) {
        ApiSession api = session();
        ApiSession.Response r = api.post("/api/auth/register", Map.of("username", username, "realName",
                "Real " + username, "password", PASSWORD, "inviteCode", inviteCode));
        assertThat(r.status()).as("register %s: %s", username, r).isEqualTo(201);
        return new Player(api, r.json().path("id").asLong(), username);
    }

    protected static Map<String, Object> game(Player white, Player black, String result) {
        return game(white.id(), black.id(), result, Instant.now().minus(1, ChronoUnit.HOURS));
    }

    protected static Map<String, Object> game(long whiteId, long blackId, String result, Instant playedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("whiteId", whiteId);
        m.put("blackId", blackId);
        m.put("result", result);
        m.put("playedAt", playedAt.toString());
        return m;
    }

    /** Logs a game as {@code reporter} and has {@code confirmer} confirm it. Returns the confirmed game. */
    protected JsonNode playConfirmed(Player reporter, Player confirmer, Map<String, Object> game) {
        ApiSession.Response created = reporter.api().post("/api/games", game);
        assertThat(created.status()).as("create game: %s", created).isEqualTo(201);
        long id = created.json().path("id").asLong();
        ApiSession.Response confirmed = confirmer.api().post("/api/games/" + id + "/confirm");
        assertThat(confirmed.status()).as("confirm game: %s", confirmed).isEqualTo(200);
        return confirmed.json();
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private static String passwordHash;

    /**
     * Inserts {@code n} members straight into the database (fast: no BCrypt per user, no HTTP).
     * They all have {@link #PASSWORD}; use {@link #login} to get a session for one.
     */
    protected java.util.List<Long> bulkUsers(int n, String prefix) {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        java.util.List<Long> ids = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = uniqueName(prefix);
            ids.add(jdbc.queryForObject("insert into users (username, real_name, password_hash) values (?, ?, ?) returning id",
                    Long.class, name, "Bulk " + name, passwordHash));
        }
        return ids;
    }

    protected Player login(long userId) {
        String username = jdbc.queryForObject("select username from users where id = ?", String.class, userId);
        ApiSession api = session();
        ApiSession.Response r = api.post("/api/auth/login", Map.of("username", username, "password", PASSWORD));
        assertThat(r.status()).as("login %s: %s", username, r).isEqualTo(200);
        return new Player(api, userId, username);
    }

    protected int rating(Player p) {
        return p.api().get("/api/users/" + p.username()).json().path("rating").asInt();
    }
}
