package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import no.trafonsjakk.support.ApiSession;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class AuthApiTest extends IntegrationTest {

    private static Map<String, Object> registration(String username, String password, String code) {
        return Map.of("username", username, "realName", "Kari Nordmann", "password", password, "inviteCode", code);
    }

    @Test
    void bootstrapCodeCreatesTheFirstAdminOnlyOnce() {
        ApiSession first = session();
        Response r = first.post("/api/auth/register", registration("first_admin", PASSWORD, BOOTSTRAP_CODE));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.json().path("role").asString()).isEqualTo("ADMIN");

        Response again = session().post("/api/auth/register", registration("sneaky", PASSWORD, BOOTSTRAP_CODE));
        assertThat(again.status()).isEqualTo(400);
        assertThat(again.code()).isEqualTo("invite.invalid");
    }

    @Test
    void authCookiesAreHttpOnlyAndScoped() {
        Response r = session().post("/api/auth/register", registration("cookie_check", PASSWORD, BOOTSTRAP_CODE));
        String access = r.setCookie("tf_at").orElseThrow();
        String refresh = r.setCookie("tf_rt").orElseThrow();
        assertThat(access).contains("HttpOnly").contains("Path=/api").contains("SameSite=Lax");
        assertThat(refresh).contains("HttpOnly").contains("Path=/api/auth").contains("SameSite=Strict");
    }

    @Test
    void invitesAreSingleUseAndCaseInsensitive() {
        Player admin = registerAdmin();
        String code = admin.api().post("/api/invites").json().path("code").asString();
        String sloppy = code.toLowerCase().replace("-", " ");
        assertThat(session().post("/api/auth/register", registration("uses_code", PASSWORD, sloppy)).status()).isEqualTo(201);
        Response reuse = session().post("/api/auth/register", registration("reuses_code", PASSWORD, code));
        assertThat(reuse.status()).isEqualTo(400);
        assertThat(reuse.code()).isEqualTo("invite.invalid");
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        Player admin = registerAdmin();
        String code = admin.api().post("/api/invites").json().path("code").asString();
        Response r = session().post("/api/auth/register", registration(admin.username().toUpperCase(), PASSWORD, code));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("auth.username_taken");
    }

    @Test
    void validationErrorsCarryFieldCodes() {
        Response r = session().post("/api/auth/register", registration("x", "short", BOOTSTRAP_CODE));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("validation");
        assertThat(r.json().path("errorCodes").path("username").asString()).isEqualTo("Pattern");
        assertThat(r.json().path("errorCodes").path("password").asString()).isEqualTo("Size");
    }

    @Test
    void passwordsOver72BytesAreRejectedNotTruncated() {
        String fortyNorwegianLetters = "ø".repeat(40); // 40 chars, 80 bytes in UTF-8
        Response r = session().post("/api/auth/register", registration("long_pw", fortyNorwegianLetters, BOOTSTRAP_CODE));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("auth.password_too_long");
    }

    @Test
    void malformedJsonIsA400NotA500() {
        Response r = session().post("/api/auth/login", "{not json");
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).as("translatable in the UI").isEqualTo("request.malformed");
    }

    @Test
    void wrongPasswordAndUnknownUserLookTheSame() {
        Player admin = registerAdmin();
        Response wrong = session().post("/api/auth/login", Map.of("username", admin.username(), "password", "nope-nope-nope"));
        Response unknown = session().post("/api/auth/login", Map.of("username", "nobody_here", "password", "nope-nope-nope"));
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrong.code()).isEqualTo("auth.bad_credentials").isEqualTo(unknown.code());
        assertThat(wrong.json().path("detail")).isEqualTo(unknown.json().path("detail"));
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheRightPassword() {
        Player admin = registerAdmin();
        ApiSession attacker = session();
        for (int i = 0; i < 5; i++) {
            assertThat(attacker.post("/api/auth/login", Map.of("username", admin.username(), "password", "guess" + i)).status())
                    .isEqualTo(401);
        }
        Response locked = session().post("/api/auth/login", Map.of("username", admin.username(), "password", PASSWORD));
        assertThat(locked.status()).isEqualTo(429);
        assertThat(locked.code()).isEqualTo("auth.locked");
        assertThat(locked.json().path("params").path("minutes").asInt()).isEqualTo(15);
        assertThat(locked.headers().firstValue("Retry-After")).isPresent();
    }

    @Test
    void loginIsCaseInsensitiveOnUsername() {
        Player admin = registerAdmin();
        Response r = session().post("/api/auth/login", Map.of("username", admin.username().toUpperCase(), "password", PASSWORD));
        assertThat(r.status()).isEqualTo(200);
    }

    @Test
    void apiRequiresLogin() {
        assertThat(session().get("/api/auth/me").status()).isEqualTo(401);
        assertThat(session().get("/api/games").status()).isEqualTo(401);
        assertThat(session().get("/actuator/health").status()).isEqualTo(200);
    }

    @Test
    void stateChangingRequestsNeedTheCsrfHeaderEvenWhenLoggedIn() {
        // Regression: the access token travels in a cookie, so CSRF must not be skipped for "bearer" requests.
        Player admin = registerAdmin();
        Response missing = admin.api().postWithoutCsrf("/api/invites", null);
        assertThat(missing.status()).as("no CSRF header: %s", missing).isEqualTo(403);

        // The victim's browser sends its real cookies, but the attacker can only guess the header value.
        Response forgedResponse = admin.api().postWithCsrfHeader("/api/invites", "attacker-guess");
        assertThat(forgedResponse.status()).as("forged CSRF token: %s", forgedResponse).isEqualTo(403);

        assertThat(admin.api().post("/api/invites").status()).isEqualTo(201);
        assertThat(admin.api().get("/api/invites").status()).as("reads never need the header").isEqualTo(200);
    }

    @Test
    void securityHeadersAreSet() {
        Response r = session().get("/api/auth/csrf");
        assertThat(r.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(r.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(r.headers().firstValue("Content-Security-Policy").orElse("")).contains("default-src 'none'");
    }

    @Test
    void refreshRotatesTheTokenAndTheOldOneStopsWorking() {
        Player admin = registerAdmin();
        String oldRefresh = admin.api().cookie("tf_rt").orElseThrow();
        assertThat(admin.api().post("/api/auth/refresh").status()).isEqualTo(204);
        String newRefresh = admin.api().cookie("tf_rt").orElseThrow();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        ApiSession thief = session();
        thief.setCookie("tf_rt", oldRefresh, "/api/auth");
        assertThat(thief.post("/api/auth/refresh").status()).isEqualTo(401);
        // Within the grace period the family survives, so the real user keeps working
        assertThat(admin.api().post("/api/auth/refresh").status()).isEqualTo(204);
    }

    @Test
    void aRefreshThatLosesTheRaceWithAnotherTabDoesNotClearTheNewCookies() {
        Player admin = registerAdmin();
        String oldRefresh = admin.api().cookie("tf_rt").orElseThrow();
        assertThat(admin.api().post("/api/auth/refresh").status()).isEqualTo(204);

        // The other tab sent the same token a moment later
        ApiSession otherTab = session();
        otherTab.setCookie("tf_rt", oldRefresh, "/api/auth");
        Response late = otherTab.post("/api/auth/refresh");
        assertThat(late.status()).isEqualTo(401);
        assertThat(late.code()).isEqualTo("auth.refresh_raced");
        assertThat(late.setCookie("tf_rt")).as("the cookies the first tab just got stay").isEmpty();
        assertThat(late.setCookie("tf_at")).isEmpty();
        assertThat(admin.api().get("/api/auth/me").status()).isEqualTo(200);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        Player admin = registerAdmin();
        String refresh = admin.api().cookie("tf_rt").orElseThrow();
        assertThat(admin.api().post("/api/auth/logout").status()).isEqualTo(204);
        ApiSession replay = session();
        replay.setCookie("tf_rt", refresh, "/api/auth");
        assertThat(replay.post("/api/auth/refresh").status()).isEqualTo(401);
    }

    @Test
    void disabledUsersCannotLogInOrRefresh() {
        Player admin = registerAdmin();
        Player bob = registerMember(admin, "bob");
        assertThat(admin.api().patch("/api/admin/users/" + bob.id(), Map.of("enabled", false)).status()).isEqualTo(200);

        assertThat(bob.api().post("/api/auth/refresh").status()).isEqualTo(401);
        Response login = session().post("/api/auth/login", Map.of("username", bob.username(), "password", PASSWORD));
        assertThat(login.status()).isEqualTo(403);
        assertThat(login.code()).isEqualTo("auth.account_disabled");
    }

    @Test
    void changingPasswordSignsOutOtherSessions() {
        Player admin = registerAdmin();
        ApiSession laptop = session();
        assertThat(laptop.post("/api/auth/login", Map.of("username", admin.username(), "password", PASSWORD)).status()).isEqualTo(200);

        Response wrongCurrent = admin.api().post("/api/users/me/password", Map.of("currentPassword", "wrong-one-x", "newPassword", "another-good-pass"));
        assertThat(wrongCurrent.code()).isEqualTo("auth.wrong_current_password");

        assertThat(admin.api().post("/api/users/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "another-good-pass")).status())
                .isEqualTo(204);
        assertThat(laptop.post("/api/auth/refresh").status()).as("other device signed out").isEqualTo(401);
        assertThat(admin.api().get("/api/auth/me").status()).as("this device keeps working").isEqualTo(200);
        assertThat(session().post("/api/auth/login", Map.of("username", admin.username(), "password", "another-good-pass")).status())
                .isEqualTo(200);
    }

    @Test
    void membersCannotUseAdminEndpoints() {
        Player admin = registerAdmin();
        Player bob = registerMember(admin, "bob");
        assertThat(bob.api().get("/api/admin/users").status()).isEqualTo(403);
        assertThat(bob.api().post("/api/admin/ratings/recompute").status()).isEqualTo(403);
        Response self = admin.api().patch("/api/admin/users/" + admin.id(), Map.of("role", "USER"));
        assertThat(self.status()).isEqualTo(400);
        assertThat(self.code()).isEqualTo("admin.cannot_change_self");
    }
}
