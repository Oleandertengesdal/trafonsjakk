package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every text limit, on both sides of the boundary. Catches a request limit that is looser than its
 * database column (which would turn into a confusing 409 instead of a field error).
 */
class InputLimitsApiTest extends IntegrationTest {

    Player admin;
    Player alice;
    Player bob;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
    }

    private Response logWith(String field, Object value) {
        Map<String, Object> g = game(alice, bob, "DRAW");
        g.put(field, value);
        return alice.api().post("/api/games", g);
    }

    private static void assertFieldError(Response r, String field, String constraint) {
        assertThat(r.status()).as("%s: %s", field, r).isEqualTo(400);
        assertThat(r.code()).isEqualTo("validation");
        assertThat(r.json().path("errorCodes").path(field).asString()).isEqualTo(constraint);
    }

    @Test
    void gameTextFieldsAcceptTheirMaximumAndRejectOneMore() {
        assertThat(logWith("opening", "o".repeat(80)).status()).isEqualTo(201);
        assertFieldError(logWith("opening", "o".repeat(81)), "opening", "Size");
        assertThat(logWith("notes", "n".repeat(1000)).status()).isEqualTo(201);
        assertFieldError(logWith("notes", "n".repeat(1001)), "notes", "Size");
        assertThat(logWith("moves", "1. e4 ".repeat(3333) + "e5").status()).isEqualTo(201); // 20000 chars
        assertFieldError(logWith("moves", "m".repeat(20001)), "moves", "Size");
        // Norwegian letters count as one character each, like the database counts them
        assertThat(logWith("notes", "æøå".repeat(333) + "Æ").status()).isEqualTo(201);
    }

    @Test
    void ecoCodes() {
        assertThat(logWith("eco", "c50").json().path("eco").asString()).isEqualTo("C50");
        assertThat(logWith("eco", "E99").status()).isEqualTo(201);
        assertThat(logWith("eco", "").status()).as("empty means none").isEqualTo(201);
        for (String bad : List.of("F00", "C5", "C500", "C5a", " C50", "Ç50")) {
            assertFieldError(logWith("eco", bad), "eco", "Pattern");
        }
    }

    @Test
    void playedAtBoundaries() {
        Instant now = Instant.now();
        assertThat(alice.api().post("/api/games", game(alice.id(), bob.id(), "DRAW", now.plus(9, ChronoUnit.MINUTES))).status())
                .as("clocks differ a little: up to 10 minutes ahead is fine (the form allows the same)").isEqualTo(201);
        assertThat(alice.api().post("/api/games", game(alice.id(), bob.id(), "DRAW", now.plus(11, ChronoUnit.MINUTES))).code())
                .isEqualTo("game.in_future");
        assertThat(alice.api().post("/api/games", game(alice.id(), bob.id(), "DRAW", Instant.parse("2000-01-01T00:00:00Z"))).status())
                .isEqualTo(201);
        assertThat(alice.api().post("/api/games", game(alice.id(), bob.id(), "DRAW", Instant.parse("1999-12-31T23:59:59Z"))).code())
                .isEqualTo("game.too_old");
        // With a timezone offset, as some clients send it
        Map<String, Object> offset = game(alice, bob, "DRAW");
        offset.put("playedAt", "2026-01-15T19:30:00+01:00");
        assertThat(alice.api().post("/api/games", offset).json().path("playedAt").asString()).isEqualTo("2026-01-15T18:30:00Z");
    }

    @Test
    void disputeReasonLength() {
        long id = alice.api().post("/api/games", game(alice, bob, "DRAW")).json().path("id").asLong();
        assertFieldError(bob.api().post("/api/games/" + id + "/dispute", Map.of("reason", "four")), "reason", "Size");
        assertFieldError(bob.api().post("/api/games/" + id + "/dispute", Map.of("reason", "r".repeat(501))), "reason", "Size");
        assertFieldError(bob.api().post("/api/games/" + id + "/dispute", Map.of()), "reason", "NotNull");
        assertThat(bob.api().post("/api/games/" + id + "/dispute", Map.of("reason", "r".repeat(500))).status()).isEqualTo(200);
    }

    @Test
    void nameLimits() {
        assertThat(alice.api().patch("/api/users/me", Map.of("realName", "R".repeat(60))).status()).isEqualTo(200);
        assertFieldError(alice.api().patch("/api/users/me", Map.of("realName", "R".repeat(61))), "realName", "Size");
        assertFieldError(alice.api().patch("/api/users/me", Map.of("realName", "   ")), "realName", "NotBlank");
        assertThat(alice.api().patch("/api/users/me", Map.of("realName", "  Kari Nordmann  ")).json().path("realName").asString())
                .isEqualTo("Kari Nordmann");
        assertThat(alice.api().patch("/api/users/me", Map.of("realName", "Åse Ødegård-Æsøy")).status()).isEqualTo(200);

        Map<String, Object> t = new java.util.HashMap<>(Map.of("format", "ROUND_ROBIN", "participantIds", List.of()));
        t.put("name", "T".repeat(80));
        assertThat(alice.api().post("/api/tournaments", t).status()).isEqualTo(201);
        t.put("name", "T".repeat(81));
        assertFieldError(alice.api().post("/api/tournaments", t), "name", "Size");
        t.put("name", "  Vårcupen  ");
        assertThat(alice.api().post("/api/tournaments", t).json().path("summary").path("name").asString()).isEqualTo("Vårcupen");
    }

    @Test
    void usernameAndPasswordRules() {
        record Case(String username, String password, int status, String code) {
        }
        String a = "abcdefghij";
        List<Case> cases = List.of(
                new Case("abc", PASSWORD, 201, null),
                new Case("u".repeat(20), PASSWORD, 201, null),
                new Case("ab", PASSWORD, 400, "validation"),
                new Case("u".repeat(21), PASSWORD, 400, "validation"),
                new Case("kari nordmann", PASSWORD, 400, "validation"),
                new Case("kåre", PASSWORD, 400, "validation"),
                new Case("drop_table", PASSWORD, 201, null),
                new Case("tenchars", a, 201, null),
                new Case("ninechars", a.substring(1), 400, "validation"),
                new Case("max_bytes", "æ".repeat(36), 201, null),          // 72 bytes
                new Case("too_many_bytes", "æ".repeat(37), 400, "auth.password_too_long")); // 74 bytes, 37 chars
        for (Case c : cases) {
            String code = admin.api().post("/api/invites").json().path("code").asString();
            Response r = session().post("/api/auth/register", Map.of("username", c.username(), "realName", "X",
                    "password", c.password(), "inviteCode", code));
            assertThat(r.status()).as("%s / %s: %s", c.username(), c.password().length(), r).isEqualTo(c.status());
            if (c.code() != null) {
                assertThat(r.code()).as(c.username()).isEqualTo(c.code());
            }
        }
        // The 72-byte password really works (not truncated)
        Response login = session().post("/api/auth/login", Map.of("username", "max_bytes", "password", "æ".repeat(36)));
        assertThat(login.status()).isEqualTo(200);
        Response almost = session().post("/api/auth/login", Map.of("username", "max_bytes", "password", "æ".repeat(35) + "a"));
        assertThat(almost.status()).isEqualTo(401);
    }

    @Test
    void passwordChangeRules() {
        assertThat(alice.api().post("/api/users/me/password", Map.of("currentPassword", "wrong-one-123", "newPassword", "another-pass-1")).code())
                .isEqualTo("auth.wrong_current_password");
        assertFieldError(alice.api().post("/api/users/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "short")),
                "newPassword", "Size");
        assertThat(alice.api().post("/api/users/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "ø".repeat(37))).code())
                .isEqualTo("auth.password_too_long");
        assertThat(alice.api().post("/api/users/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "new-passphrase-1")).status())
                .isEqualTo(204);
        assertThat(session().post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD)).status()).isEqualTo(401);
        assertThat(session().post("/api/auth/login", Map.of("username", alice.username(), "password", "new-passphrase-1")).status()).isEqualTo(200);
    }
}
