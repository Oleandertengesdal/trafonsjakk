package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import no.trafonsjakk.support.ApiSession;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import no.trafonsjakk.support.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Attacks and abuse: forged tokens, stolen sessions, enumeration, over-posting, odd requests. */
class SecurityApiTest extends IntegrationTest {

    Player admin;
    Player alice;
    Player bob;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
    }

    private ApiSession withAccessToken(String token) {
        ApiSession s = session();
        s.setCookie("tf_at", token, "/api");
        return s;
    }

    // ---- forged and stale access tokens --------------------------------------------------------

    @Test
    void tokensSignedWithAnotherKeyAreIgnored() {
        String forged = Jwts.sign(Jwts.claims(admin.id(), admin.username(), "ADMIN").build(),
                "attacker-chosen-secret-that-is-at-least-32-bytes-long");
        assertThat(withAccessToken(forged).get("/api/auth/me").status()).isEqualTo(401);
    }

    @Test
    void unsignedAlgNoneTokensAreIgnored() {
        String none = Jwts.unsigned(Jwts.claims(admin.id(), admin.username(), "ADMIN").build());
        assertThat(withAccessToken(none).get("/api/admin/users").status()).isEqualTo(401);
    }

    @Test
    void expiredTokensAreIgnored() {
        Instant past = Instant.now().minusSeconds(3600);
        String expired = Jwts.sign(Jwts.claims(alice.id(), alice.username(), "USER")
                .issueTime(Date.from(past.minusSeconds(900))).expirationTime(Date.from(past)).build());
        assertThat(withAccessToken(expired).get("/api/auth/me").status()).isEqualTo(401);
    }

    @Test
    void tokensFromAnotherIssuerAreIgnored() {
        String other = Jwts.sign(Jwts.claims(alice.id(), alice.username(), "USER").issuer("someone-else").build());
        assertThat(withAccessToken(other).get("/api/auth/me").status()).isEqualTo(401);
    }

    @Test
    void aValidlySignedTokenStillNeedsAnExistingSession() {
        // Even with the signing key (e.g. leaked from a backup), a token must match a live login session
        String selfPromoted = Jwts.sign(Jwts.claims(alice.id(), alice.username(), "ADMIN").build());
        assertThat(withAccessToken(selfPromoted).get("/api/admin/users").status()).isIn(401, 403);
        String ghost = Jwts.sign(Jwts.claims(424242, "ghost", "ADMIN").build());
        assertThat(withAccessToken(ghost).get("/api/admin/users").status()).isEqualTo(401);
        String sameRole = Jwts.sign(Jwts.claims(alice.id(), alice.username(), "USER").build());
        assertThat(withAccessToken(sameRole).get("/api/auth/me").status())
                .as("a token that isn't tied to a session (no sid) must not work").isEqualTo(401);
    }

    @Test
    void garbageAccessCookieDoesNotBreakPublicEndpoints() {
        ApiSession s = withAccessToken("not.a.jwt");
        assertThat(s.get("/api/auth/me").status()).isEqualTo(401);
        Response login = s.post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD));
        assertThat(login.status()).isEqualTo(200);
        assertThat(s.get("/api/auth/me").status()).isEqualTo(200);
    }

    // ---- stolen sessions ----------------------------------------------------------------------

    @Test
    void anAccessTokenStopsWorkingWhenItsSessionLogsOut() {
        String stolen = alice.api().cookie("tf_at").orElseThrow();
        assertThat(withAccessToken(stolen).get("/api/auth/me").status()).isEqualTo(200);

        assertThat(alice.api().post("/api/auth/logout").status()).isEqualTo(204);
        assertThat(withAccessToken(stolen).get("/api/auth/me").status())
                .as("logging out must kill the access token, not just the refresh token").isEqualTo(401);
    }

    @Test
    void changingThePasswordEndsOtherSessionsImmediately() {
        ApiSession laptop = session();
        assertThat(laptop.post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD)).status()).isEqualTo(200);
        assertThat(laptop.get("/api/auth/me").status()).isEqualTo(200);

        assertThat(alice.api().post("/api/users/me/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "a-brand-new-passphrase")).status()).isEqualTo(204);

        assertThat(laptop.get("/api/auth/me").status()).as("other device is signed out at once").isEqualTo(401);
        assertThat(alice.api().get("/api/auth/me").status()).as("this device stays signed in").isEqualTo(200);
    }

    @Test
    void refreshTokenTheftIsContainedOnceTheRealUserRefreshes() {
        String stolenRefresh = alice.api().cookie("tf_rt").orElseThrow();
        ApiSession thief = session();
        thief.setCookie("tf_rt", stolenRefresh, "/api/auth");
        assertThat(thief.post("/api/auth/refresh").status()).isEqualTo(204);
        assertThat(thief.get("/api/auth/me").status()).isEqualTo(200);
        // A real thief keeps the token even when our server tells their browser to drop it
        String thiefAccess = thief.cookie("tf_at").orElseThrow();

        // Later (outside the two-tab grace period) Alice's browser refreshes with the same token
        jdbc.update("update refresh_tokens set revoked_at = ? where revoked_at is not null",
                Timestamp.from(Instant.now().minusSeconds(120)));
        assertThat(alice.api().post("/api/auth/refresh").status()).isEqualTo(401);

        assertThat(thief.post("/api/auth/refresh").status()).as("thief's refresh token is revoked").isEqualTo(401);
        assertThat(withAccessToken(thiefAccess).get("/api/auth/me").status()).as("and so is the thief's access token").isEqualTo(401);
    }

    @Test
    void logoutClearsBothCookiesAndNeedsTheCsrfHeader() {
        assertThat(alice.api().postWithoutCsrf("/api/auth/logout", null).status()).isEqualTo(403);
        Response out = alice.api().post("/api/auth/logout");
        assertThat(out.setCookie("tf_at")).hasValueSatisfying(c -> assertThat(c).contains("Max-Age=0"));
        assertThat(out.setCookie("tf_rt")).hasValueSatisfying(c -> assertThat(c).contains("Max-Age=0").contains("Path=/api/auth"));
    }

    // ---- account enumeration ------------------------------------------------------------------

    @Test
    void registrationWithoutAValidInviteDoesNotRevealWhichUsernamesExist() {
        Response taken = session().post("/api/auth/register", Map.of("username", alice.username(), "realName", "X",
                "password", PASSWORD, "inviteCode", "WRONG-CODE1"));
        Response free = session().post("/api/auth/register", Map.of("username", "nobody_has_this", "realName", "X",
                "password", PASSWORD, "inviteCode", "WRONG-CODE1"));
        assertThat(taken.code()).isEqualTo("invite.invalid");
        assertThat(free.code()).isEqualTo("invite.invalid");
        assertThat(taken.status()).isEqualTo(free.status());
    }

    @Test
    void aDisabledAccountWithTheWrongPasswordLooksLikeAnyFailedLogin() {
        admin.api().patch("/api/admin/users/" + bob.id(), Map.of("enabled", false));
        Response r = session().post("/api/auth/login", Map.of("username", bob.username(), "password", "wrong-password-1"));
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.code()).isEqualTo("auth.bad_credentials");
    }

    @Test
    void unknownUsernamesLockOutExactlyLikeRealOnes() {
        Response real = null;
        Response ghost = null;
        for (int i = 0; i < 6; i++) {
            real = session().post("/api/auth/login", Map.of("username", alice.username(), "password", "wrong-password-1"));
            ghost = session().post("/api/auth/login", Map.of("username", "ghost_user_42", "password", "wrong-password-1"));
        }
        assertThat(ghost.status()).isEqualTo(real.status()).isEqualTo(429);
        assertThat(ghost.code()).isEqualTo(real.code());
        assertThat(ghost.headers().firstValue("Retry-After")).isEqualTo(real.headers().firstValue("Retry-After"));
    }

    @Test
    void lockoutCannotBeDodgedByChangingTheCaseOrPaddingTheUsername() {
        String u = alice.username();
        for (String variant : List.of(u, u.toUpperCase(), " " + u, u + " ", u.substring(0, 1).toUpperCase() + u.substring(1))) {
            session().post("/api/auth/login", Map.of("username", variant, "password", "wrong-password-1"));
        }
        Response sixth = session().post("/api/auth/login", Map.of("username", u.toUpperCase(), "password", PASSWORD));
        assertThat(sixth.status()).isEqualTo(429);
    }

    @Test
    void lockoutCannotBeDodgedByFakingTheClientIp() {
        for (int i = 0; i < 5; i++) {
            session().header("X-Forwarded-For", "203.0.113." + i)
                    .post("/api/auth/login", Map.of("username", alice.username(), "password", "wrong-password-1"));
        }
        Response r = session().header("X-Forwarded-For", "198.51.100.7")
                .post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD));
        assertThat(r.status()).isEqualTo(429);
    }

    // ---- authorization matrix -----------------------------------------------------------------

    record Call(String method, String path) {
    }

    private List<Call> memberEndpoints() {
        return List.of(new Call("GET", "/api/auth/me"), new Call("GET", "/api/users"),
                new Call("GET", "/api/users/" + alice.username()), new Call("GET", "/api/users/" + alice.username() + "/stats"),
                new Call("PATCH", "/api/users/me"), new Call("POST", "/api/users/me/password"),
                new Call("GET", "/api/invites"), new Call("POST", "/api/invites"), new Call("DELETE", "/api/invites/1"),
                new Call("GET", "/api/games"), new Call("GET", "/api/games/pending"), new Call("GET", "/api/games/1"),
                new Call("POST", "/api/games"), new Call("POST", "/api/games/1/confirm"),
                new Call("POST", "/api/games/1/dispute"), new Call("POST", "/api/games/1/withdraw"),
                new Call("PATCH", "/api/games/1/moves"), new Call("GET", "/api/games/1/comments"),
                new Call("POST", "/api/games/1/comments"), new Call("PATCH", "/api/games/1/comments/1"),
                new Call("DELETE", "/api/games/1/comments/1"), new Call("GET", "/api/games/1/kudos"),
                new Call("PUT", "/api/games/1/kudos"), new Call("DELETE", "/api/games/1/kudos"),
                new Call("GET", "/api/games/1/context"), new Call("PUT", "/api/games/1/comments/1/like"),
                new Call("DELETE", "/api/games/1/comments/1/like"),
                new Call("GET", "/api/awards"), new Call("GET", "/api/awards/race"),
                new Call("GET", "/api/leaderboard"), new Call("GET", "/api/tournaments"),
                new Call("POST", "/api/tournaments"), new Call("GET", "/api/tournaments/matches/mine"),
                new Call("GET", "/api/tournaments/1"), new Call("PUT", "/api/tournaments/1/participants"),
                new Call("POST", "/api/tournaments/1/join"), new Call("POST", "/api/tournaments/1/leave"),
                new Call("POST", "/api/tournaments/1/start"), new Call("POST", "/api/tournaments/1/cancel"));
    }

    private static List<Call> adminEndpoints() {
        return List.of(new Call("GET", "/api/admin/games/disputed"), new Call("POST", "/api/admin/games/1/resolve"),
                new Call("GET", "/api/admin/users"), new Call("PATCH", "/api/admin/users/1"),
                new Call("POST", "/api/admin/users/1/password-reset"),
                new Call("POST", "/api/admin/ratings/recompute"));
    }

    private static Response call(ApiSession s, Call c) {
        return switch (c.method()) {
            case "GET" -> s.get(c.path());
            case "DELETE" -> s.delete(c.path());
            default -> s.raw(c.method(), c.path(), "application/json", "{}");
        };
    }

    @Test
    void everyEndpointNeedsALogin() {
        ApiSession anonymous = session();
        for (Call c : Stream.concat(memberEndpoints().stream(), adminEndpoints().stream()).toList()) {
            assertThat(call(anonymous, c).status()).as("%s %s without login", c.method(), c.path()).isEqualTo(401);
        }
    }

    @Test
    void membersGet403FromEveryAdminEndpoint() {
        for (Call c : adminEndpoints()) {
            Response r = call(alice.api(), c);
            assertThat(r.status()).as("%s %s as member", c.method(), c.path()).isEqualTo(403);
        }
    }

    @Test
    void authErrorsAreProblemJsonWithACode() {
        Response anonymous = session().get("/api/games");
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(anonymous.code()).isEqualTo("auth.not_logged_in");
        Response member = alice.api().get("/api/admin/users");
        assertThat(member.status()).isEqualTo(403);
        assertThat(member.code()).isEqualTo("forbidden");
        Response csrf = alice.api().postWithoutCsrf("/api/invites", null);
        assertThat(csrf.status()).isEqualTo(403);
        assertThat(csrf.code()).isEqualTo("csrf");
    }

    // ---- over-posting ---------------------------------------------------------------------------

    @Test
    void registrationIgnoresPrivilegedFields() {
        String code = admin.api().post("/api/invites").json().path("code").asString();
        Map<String, Object> body = new java.util.HashMap<>(Map.of("username", "sneaky", "realName", "Sneaky",
                "password", PASSWORD, "inviteCode", code));
        body.putAll(Map.of("role", "ADMIN", "enabled", false, "rating", 2800, "id", 1));
        JsonNode me = session().post("/api/auth/register", body).json();
        assertThat(me.path("role").asString()).isEqualTo("USER");
        assertThat(me.path("rating").asInt()).isEqualTo(1500);
        assertThat(me.path("id").asLong()).isNotEqualTo(1L);
    }

    @Test
    void profileUpdatesOnlyChangeTheName() {
        Response r = alice.api().patch("/api/users/me", Map.of("realName", "Alice Ny", "role", "ADMIN", "rating", 2800,
                "username", "renamed", "enabled", false));
        assertThat(r.status()).isEqualTo(200);
        JsonNode me = alice.api().get("/api/auth/me").json();
        assertThat(me.path("realName").asString()).isEqualTo("Alice Ny");
        assertThat(me.path("role").asString()).isEqualTo("USER");
        assertThat(me.path("username").asString()).isEqualTo(alice.username());
        assertThat(me.path("rating").asInt()).isEqualTo(1500);
    }

    @Test
    void aLoggedGameIgnoresStatusAndReporterFields() {
        Map<String, Object> g = game(alice, bob, "WHITE_WIN");
        g.put("status", "CONFIRMED");
        g.put("reportedBy", bob.id());
        g.put("confirmedBy", bob.id());
        JsonNode created = alice.api().post("/api/games", g).json();
        assertThat(created.path("status").asString()).isEqualTo("PENDING");
        assertThat(created.path("reportedBy").path("id").asLong()).isEqualTo(alice.id());
        assertThat(rating(alice)).isEqualTo(1500);
    }

    // ---- request handling: every error is a clean 4xx with a code ------------------------------

    @Test
    void badRequestsGetACleanErrorWithACode() {
        record Case(String name, Response r, int status) {
        }
        List<Case> cases = List.of(
                new Case("id that isn't a number", alice.api().get("/api/games/abc"), 400),
                new Case("id that overflows a long", alice.api().get("/api/games/99999999999999999999999"), 400),
                new Case("page that overflows an int", alice.api().get("/api/games?page=99999999999"), 400),
                new Case("wrong HTTP method", alice.api().delete("/api/games"), 405),
                new Case("wrong content type", alice.api().raw("POST", "/api/games", "text/plain", "hello"), 415),
                new Case("missing body", alice.api().raw("POST", "/api/games", "application/json", ""), 400),
                new Case("JSON array instead of object", alice.api().raw("POST", "/api/games", "application/json", "[]"), 400),
                new Case("unknown API path", alice.api().get("/api/nothing-here"), 404),
                new Case("unknown enum", alice.api().post("/api/tournaments", Map.of("name", "x", "format", "SWISS",
                        "participantIds", List.of())), 400),
                new Case("playedAt not a date", alice.api().post("/api/games", Map.of("whiteId", alice.id(), "blackId", bob.id(),
                        "result", "DRAW", "playedAt", "yesterday")), 400));
        for (Case c : cases) {
            assertThat(c.r().status()).as("%s: %s", c.name(), c.r()).isEqualTo(c.status());
            assertThat(c.r().code()).as("%s has a code: %s", c.name(), c.r()).isNotBlank();
            assertThat(c.r().body()).as("%s leaks internals", c.name())
                    .doesNotContain("Exception").doesNotContain("org.springframework").doesNotContain("at no.trafonsjakk");
        }
    }

    @Test
    void textWithNulBytesIsRejectedNotA500() {
        Response name = alice.api().patch("/api/users/me", Map.of("realName", "Ali\u0000ce"));
        assertThat(name.status()).as("%s", name).isEqualTo(400);
        Map<String, Object> g = game(alice, bob, "DRAW");
        g.put("notes", "bad\u0000byte");
        Response notes = alice.api().post("/api/games", g);
        assertThat(notes.status()).as("%s", notes).isEqualTo(400);
        assertThat(notes.code()).isEqualTo("validation");
        assertThat(notes.json().path("errorCodes").path("notes").asString()).isEqualTo("PlainText");

        Response login = session().post("/api/auth/login", Map.of("username", "ali\u0000ce", "password", "whatever-123"));
        assertThat(login.status()).as("login: %s", login).isIn(400, 401);
        Response filter = alice.api().get("/api/games?player=a%00b");
        assertThat(filter.status()).as("query: %s", filter).isIn(400, 404);
        Response profile = alice.api().get("/api/users/a%00b");
        assertThat(profile.status()).as("path: %s", profile).isIn(400, 404);
        Response tournament = alice.api().post("/api/tournaments", Map.of("name", "Cup\u0000", "format", "ROUND_ROBIN",
                "participantIds", List.of()));
        assertThat(tournament.status()).as("tournament: %s", tournament).isEqualTo(400);
        // Ordinary line breaks and tabs in notes are fine
        Map<String, Object> ok = game(alice, bob, "DRAW");
        ok.put("notes", "Line one\nLine two\twith a tab");
        assertThat(alice.api().post("/api/games", ok).status()).isEqualTo(201);
    }

    @Test
    void injectionLookingInputIsJustData() {
        Response r = alice.api().get("/api/games?player=" + java.net.URLEncoder.encode("' or 1=1 --", java.nio.charset.StandardCharsets.UTF_8));
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.code()).isEqualTo("player.not_found");

        String html = "<img src=x onerror=alert(1)>";
        assertThat(alice.api().patch("/api/users/me", Map.of("realName", html)).status()).isEqualTo(200);
        Response me = alice.api().get("/api/users/" + alice.username());
        assertThat(me.json().path("realName").asString()).as("stored verbatim, escaped by React").isEqualTo(html);
        assertThat(me.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(me.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    }

    // ---- infrastructure ---------------------------------------------------------------------------

    @Test
    void onlyTheHealthEndpointIsExposed() {
        Response health = session().get("/actuator/health");
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.body()).doesNotContain("components").doesNotContain("diskSpace").doesNotContain("db");
        for (String p : List.of("/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump",
                "/actuator/loggers", "/actuator", "/", "/index.html", "/error")) {
            Response r = admin.api().get(p);
            assertThat(r.status()).as(p).isNotEqualTo(200);
            assertThat(r.body()).as(p).doesNotContain("test-only-jwt-secret").doesNotContain("password");
        }
    }

    @Test
    void otherOriginsGetNoCorsPermission() {
        Response preflight = session().header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST").raw("OPTIONS", "/api/games", null, null);
        assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        Response read = alice.api().header("Origin", "https://evil.example").get("/api/games");
        assertThat(read.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        assertThat(read.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
        alice.api().header("Origin", null);
    }

    @Test
    void personalResponsesAreNotCacheableAndErrorsCarrySecurityHeaders() {
        Response me = alice.api().get("/api/auth/me");
        assertThat(me.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        for (Response r : List.of(session().get("/api/games"), alice.api().get("/api/nothing-here"),
                alice.api().get("/api/admin/users"))) {
            assertThat(r.headers().firstValue("X-Content-Type-Options")).as("%s", r.status()).hasValue("nosniff");
            assertThat(r.headers().firstValue("X-Frame-Options")).as("%s", r.status()).hasValue("DENY");
            assertThat(r.headers().firstValue("Content-Security-Policy")).as("%s", r.status()).isPresent();
        }
    }
}
