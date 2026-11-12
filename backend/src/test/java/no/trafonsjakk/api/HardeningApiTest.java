package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Findings from the November 2026 security review. */
class HardeningApiTest extends IntegrationTest {

    Player admin;
    Player alice;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
    }

    @Test
    void hugeRequestBodiesAreRefusedBeforeTheyAreRead() {
        // Anyone can reach the login endpoint; a body this size must not be parsed into memory
        String huge = "{\"username\":\"" + "a".repeat(300_000) + "\",\"password\":\"x\"}";
        Response r = session().raw("POST", "/api/auth/login", "application/json", huge);
        assertThat(r.status()).isEqualTo(413);
        assertThat(r.code()).isEqualTo("request.too_large");

        Response loggedIn = alice.api().raw("POST", "/api/games/1/comments", "application/json",
                "{\"body\":\"" + "b".repeat(300_000) + "\"}");
        assertThat(loggedIn.status()).isEqualTo(413);
    }

    @Test
    void theLargestRealRequestStillFits() {
        // 20 000 characters of moves (the maximum), all two-byte characters: about 40 KB
        Map<String, Object> body = new java.util.HashMap<>(game(alice.id(), admin.id(), "DRAW", java.time.Instant.now().minusSeconds(60)));
        body.put("moves", "ø".repeat(20_000));
        Response r = alice.api().post("/api/games", body);
        assertThat(r.status()).as("%s", r).isNotEqualTo(413);
    }

    @Test
    void guessingTheCurrentPasswordIsLimitedLikeLoggingIn() {
        for (int i = 0; i < 5; i++) {
            Response r = alice.api().post("/api/users/me/password", Map.of("currentPassword", "guess-number-" + i, "newPassword", "new-passphrase-1"));
            assertThat(r.code()).isEqualTo("auth.wrong_current_password");
        }
        Response locked = alice.api().post("/api/users/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "new-passphrase-1"));
        assertThat(locked.status()).isEqualTo(429);
        assertThat(locked.code()).isEqualTo("auth.locked");
        assertThat(session().post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD)).status())
                .as("logins for that account wait too").isEqualTo(429);
    }
}
