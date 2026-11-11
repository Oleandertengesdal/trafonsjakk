package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Per-IP limits, with small numbers so they're quick to hit. Each test uses its own client IP. */
@TestPropertySource(properties = {
        "app.security.max-failed-logins-per-ip=4",
        "app.security.max-registrations-per-ip-per-hour=3"})
class RateLimitApiTest extends IntegrationTest {

    @Test
    void oneIpCannotGuessPasswordsAcrossManyAccounts() {
        // Players are inserted directly: registering them would use up the low sign-up limit
        Player alice = login(bulkUsers(1, "alice").getFirst());
        String ip = "192.0.2.10";
        for (int i = 0; i < 4; i++) {
            session().header("X-Forwarded-For", ip)
                    .post("/api/auth/login", Map.of("username", "victim_" + i, "password", "wrong-password-1"));
        }
        Response r = session().header("X-Forwarded-For", ip)
                .post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD));
        assertThat(r.status()).isEqualTo(429);
        assertThat(r.code()).isEqualTo("auth.locked");
        assertThat(r.headers().firstValue("Retry-After")).isPresent();

        // Someone else is unaffected
        Response other = session().header("X-Forwarded-For", "192.0.2.11")
                .post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD));
        assertThat(other.status()).isEqualTo(200);
    }

    @Test
    void signUpAttemptsAreLimitedPerIpEvenWhenTheyFail() {
        String ip = "192.0.2.20";
        for (int i = 0; i < 3; i++) {
            Response r = session().header("X-Forwarded-For", ip).post("/api/auth/register", Map.of("username", "guess_" + i,
                    "realName", "Guess", "password", PASSWORD, "inviteCode", "AAAAA-BBBB" + i));
            assertThat(r.code()).isEqualTo("invite.invalid");
        }
        Response blocked = session().header("X-Forwarded-For", ip).post("/api/auth/register", Map.of("username", "guess_x",
                "realName", "Guess", "password", PASSWORD, "inviteCode", "AAAAA-BBBBB"));
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.code()).isEqualTo("auth.too_many_signups");
    }

    @Test
    void aSuccessfulLoginClearsTheUsersFailureCount() {
        Player alice = login(bulkUsers(1, "alice").getFirst());
        for (int round = 0; round < 3; round++) {
            String ip = "192.0.2." + (30 + round);
            for (int i = 0; i < 3; i++) {
                session().header("X-Forwarded-For", ip)
                        .post("/api/auth/login", Map.of("username", alice.username(), "password", "wrong-password-1"));
            }
            Response ok = session().header("X-Forwarded-For", "192.0.2.99")
                    .post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD));
            assertThat(ok.status()).as("round %d", round).isEqualTo(200);
        }
    }
}
