package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import no.trafonsjakk.support.ApiSession;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Per-address limits, with small numbers so they're quick to hit. Each test uses its own addresses.
 * {@code True-Client-IP} alone is someone talking to Render directly; {@link #viaRender} adds a forwarding chain.
 */
@TestPropertySource(properties = {
        "app.security.max-failed-logins-per-ip=4",
        "app.security.max-registrations-per-ip-per-hour=3",
        "app.security.max-failed-logins-per-edge=6",
        "app.security.max-registrations-per-edge-per-hour=5"})
class RateLimitApiTest extends IntegrationTest {

    @Test
    void oneIpCannotGuessPasswordsAcrossManyAccounts() {
        // Players are inserted directly: registering them would use up the low sign-up limit
        Player alice = login(bulkUsers(1, "alice").getFirst());
        String ip = "192.0.2.10";
        for (int i = 0; i < 4; i++) {
            session().header("True-Client-IP", ip)
                    .post("/api/auth/login", Map.of("username", "victim_" + i, "password", "wrong-password-1"));
        }
        Response r = session().header("True-Client-IP", ip)
                .post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD));
        assertThat(r.status()).isEqualTo(429);
        assertThat(r.code()).isEqualTo("auth.locked");
        assertThat(r.headers().firstValue("Retry-After")).isPresent();

        // Someone else is unaffected
        Response other = session().header("True-Client-IP", "192.0.2.11")
                .post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD));
        assertThat(other.status()).isEqualTo(200);
    }

    @Test
    void signUpAttemptsAreLimitedPerIpEvenWhenTheyFail() {
        String ip = "192.0.2.20";
        for (int i = 0; i < 3; i++) {
            Response r = session().header("True-Client-IP", ip).post("/api/auth/register", Map.of("username", "guess_" + i,
                    "realName", "Guess", "password", PASSWORD, "inviteCode", "AAAAA-BBBB" + i));
            assertThat(r.code()).isEqualTo("invite.invalid");
        }
        Response blocked = session().header("True-Client-IP", ip).post("/api/auth/register", Map.of("username", "guess_x",
                "realName", "Guess", "password", PASSWORD, "inviteCode", "AAAAA-BBBBB"));
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.code()).isEqualTo("auth.too_many_signups");
    }

    /** A request as Render's edge passes it on: True-Client-IP is the connecting address, which can't be faked. */
    private ApiSession viaRender(String forwardedFor, String edge) {
        return session().header("True-Client-IP", edge).header("X-Forwarded-For", forwardedFor + ", " + edge + ", 172.71.0.1, 10.0.0.1");
    }

    @Test
    void fakingTheForwardedForHeaderAtRenderDoesNotGetAroundTheLimits() {
        Player alice = login(bulkUsers(1, "alice").getFirst());
        String attacker = "203.0.113.50";
        for (int i = 0; i < 6; i++) {
            // A new fake address every time, and a new account, so neither of those limits is reached
            Response r = viaRender("198.51.100." + i, attacker)
                    .post("/api/auth/login", Map.of("username", "victim_" + i, "password", "wrong-password-1"));
            assertThat(r.status()).as("attempt %d", i).isEqualTo(401);
        }
        Response r = viaRender("198.51.100.200", attacker).post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD));
        assertThat(r.status()).isEqualTo(429);
        assertThat(r.code()).isEqualTo("auth.locked");

        for (int i = 0; i < 5; i++) {
            viaRender("198.51.101." + i, attacker).post("/api/auth/register", Map.of("username", "guess_" + i,
                    "realName", "Guess", "password", PASSWORD, "inviteCode", "AAAAA-BBBB" + i));
        }
        Response signup = viaRender("198.51.101.200", attacker).post("/api/auth/register", Map.of("username", "guess_x",
                "realName", "Guess", "password", PASSWORD, "inviteCode", "AAAAA-BBBBB"));
        assertThat(signup.status()).isEqualTo(429);
        assertThat(signup.code()).isEqualTo("auth.too_many_signups");
    }

    @Test
    void throughVercelEachVisitorHasTheirOwnLimit() {
        Player alice = login(bulkUsers(1, "alice").getFirst());
        String vercel = "76.76.21.21";
        for (int i = 0; i < 4; i++) {
            viaRender("198.51.100.7", vercel).post("/api/auth/login", Map.of("username", "victim_" + i, "password", "wrong-password-1"));
        }
        assertThat(viaRender("198.51.100.7", vercel).post("/api/auth/login",
                Map.of("username", alice.username(), "password", PASSWORD)).status()).as("the guesser").isEqualTo(429);
        assertThat(viaRender("198.51.100.8", vercel).post("/api/auth/login",
                Map.of("username", alice.username(), "password", PASSWORD)).status()).as("someone else via Vercel").isEqualTo(200);
    }

    @Test
    void aSuccessfulLoginClearsTheUsersFailureCount() {
        Player alice = login(bulkUsers(1, "alice").getFirst());
        for (int round = 0; round < 3; round++) {
            String ip = "192.0.2." + (30 + round);
            for (int i = 0; i < 3; i++) {
                session().header("True-Client-IP", ip)
                        .post("/api/auth/login", Map.of("username", alice.username(), "password", "wrong-password-1"));
            }
            Response ok = session().header("True-Client-IP", "192.0.2.99")
                    .post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD));
            assertThat(ok.status()).as("round %d", round).isEqualTo(200);
        }
    }
}
