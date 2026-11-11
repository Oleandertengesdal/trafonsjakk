package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import no.trafonsjakk.support.ApiSession;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** An admin makes a one-time link; the person who forgot their password picks a new one. */
class PasswordResetApiTest extends IntegrationTest {

    static final String NEW_PASSWORD = "a-fresh-passphrase-9";

    Player admin;
    Player alice;
    Player bob;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
    }

    private String resetToken(Player p) {
        Response r = admin.api().post("/api/admin/users/" + p.id() + "/password-reset");
        assertThat(r.status()).as("%s", r).isEqualTo(201);
        assertThat(Instant.parse(r.json().path("expiresAt").asString()))
                .isBetween(Instant.now().plus(23, ChronoUnit.HOURS), Instant.now().plus(25, ChronoUnit.HOURS));
        return r.json().path("token").asString();
    }

    private Response check(String token) {
        return session().post("/api/auth/password-reset/check", Map.of("token", token));
    }

    private Response reset(ApiSession s, String token, String password) {
        return s.post("/api/auth/password-reset", Map.of("token", token, "newPassword", password));
    }

    @Test
    void theLinkLetsThePersonChooseANewPasswordAndLogsThemIn() {
        String token = resetToken(alice);
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);

        JsonNode who = check(token).json();
        assertThat(who.path("username").asString()).isEqualTo(alice.username());
        assertThat(who.path("realName").isMissingNode()).isFalse();

        ApiSession browser = session();
        Response done = reset(browser, token, NEW_PASSWORD);
        assertThat(done.status()).as("%s", done).isEqualTo(200);
        assertThat(done.json().path("username").asString()).isEqualTo(alice.username());
        assertThat(browser.get("/api/auth/me").status()).as("logged in straight away").isEqualTo(200);

        assertThat(session().post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD)).status()).isEqualTo(401);
        assertThat(session().post("/api/auth/login", Map.of("username", alice.username(), "password", NEW_PASSWORD)).status()).isEqualTo(200);
    }

    @Test
    void aLinkWorksOnlyOnce() {
        String token = resetToken(alice);
        assertThat(reset(session(), token, NEW_PASSWORD).status()).isEqualTo(200);
        Response again = reset(session(), token, "yet-another-pass-1");
        assertThat(again.status()).isEqualTo(400);
        assertThat(again.code()).isEqualTo("reset.invalid");
        assertThat(check(token).code()).isEqualTo("reset.invalid");
    }

    @Test
    void aLinkExpiresAfter24Hours() {
        String token = resetToken(alice);
        jdbc.update("update password_reset_tokens set expires_at = ?", Timestamp.from(Instant.now().minusSeconds(1)));
        assertThat(check(token).code()).isEqualTo("reset.invalid");
        assertThat(reset(session(), token, NEW_PASSWORD).code()).isEqualTo("reset.invalid");
    }

    @Test
    void aNewLinkReplacesTheOldOne() {
        String first = resetToken(alice);
        String second = resetToken(alice);
        assertThat(check(first).code()).isEqualTo("reset.invalid");
        assertThat(check(second).status()).isEqualTo(200);
    }

    @Test
    void resettingEndsEverySessionOfThatPersonAndClearsALockout() {
        // Alice is logged in somewhere (maybe someone else is), and locked out after guesses
        ApiSession oldDevice = alice.api();
        for (int i = 0; i < 5; i++) {
            session().post("/api/auth/login", Map.of("username", alice.username(), "password", "wrong-password-1"));
        }
        assertThat(session().post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD)).status()).isEqualTo(429);

        assertThat(reset(session(), resetToken(alice), NEW_PASSWORD).status()).isEqualTo(200);
        assertThat(oldDevice.get("/api/auth/me").status()).isEqualTo(401);
        assertThat(oldDevice.post("/api/auth/refresh").status()).isEqualTo(401);
        assertThat(session().post("/api/auth/login", Map.of("username", alice.username(), "password", NEW_PASSWORD)).status())
                .as("lockout cleared").isEqualTo(200);
        assertThat(bob.api().get("/api/auth/me").status()).as("other people stay logged in").isEqualTo(200);
    }

    @Test
    void theNewPasswordFollowsTheUsualRules() {
        String token = resetToken(alice);
        Response tooShort = reset(session(), token, "short");
        assertThat(tooShort.code()).isEqualTo("validation");
        assertThat(tooShort.json().path("errorCodes").path("newPassword").asString()).isEqualTo("Size");
        assertThat(reset(session(), token, "ø".repeat(37)).code()).isEqualTo("auth.password_too_long");
        assertThat(check(token).status()).as("a rejected attempt doesn't use the link up").isEqualTo(200);
    }

    @Test
    void onlyAdminsCanMakeLinksAndOnlyForOthersWhoAreActive() {
        Response member = alice.api().post("/api/admin/users/" + bob.id() + "/password-reset");
        assertThat(member.status()).isEqualTo(403);
        assertThat(session().post("/api/admin/users/" + bob.id() + "/password-reset").status()).isEqualTo(401);

        Response self = admin.api().post("/api/admin/users/" + admin.id() + "/password-reset");
        assertThat(self.status()).isEqualTo(400);
        assertThat(self.code()).isEqualTo("admin.cannot_change_self");

        assertThat(admin.api().post("/api/admin/users/987654/password-reset").code()).isEqualTo("user.not_found");

        admin.api().patch("/api/admin/users/" + bob.id(), Map.of("enabled", false));
        Response disabled = admin.api().post("/api/admin/users/" + bob.id() + "/password-reset");
        assertThat(disabled.status()).isEqualTo(409);
        assertThat(disabled.code()).isEqualTo("user.disabled");
    }

    @Test
    void disablingSomeoneCancelsTheirOpenResetLink() {
        String token = resetToken(bob);
        admin.api().patch("/api/admin/users/" + bob.id(), Map.of("enabled", false));
        assertThat(check(token).code()).isEqualTo("reset.invalid");
        assertThat(reset(session(), token, NEW_PASSWORD).code()).isEqualTo("reset.invalid");
    }

    @Test
    void tokensAreStoredOnlyAsHashesAndGuessesFail() {
        String token = resetToken(alice);
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens where token_hash = ?", Integer.class, token)).isZero();
        assertThat(check("not-a-real-token").code()).isEqualTo("reset.invalid");
        assertThat(check("").status()).isEqualTo(400);
        assertThat(reset(session(), token.substring(1), NEW_PASSWORD).code()).isEqualTo("reset.invalid");
    }

    @Test
    void theResetFormIsProtectedAgainstCrossSiteRequests() {
        String token = resetToken(alice);
        assertThat(session().postWithoutCsrf("/api/auth/password-reset", Map.of("token", token, "newPassword", NEW_PASSWORD)).status())
                .isEqualTo(403);
        assertThat(check(token).status()).as("the link was not used").isEqualTo(200);
    }
}
