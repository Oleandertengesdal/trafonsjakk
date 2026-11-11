package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class AdminApiTest extends IntegrationTest {

    Player admin;
    Player alice;
    Player bob;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
    }

    @Test
    void adminListsEveryAccountIncludingDisabledOnes() {
        disable(bob);
        JsonNode users = admin.api().get("/api/admin/users").json();
        assertThat(users).hasSize(3);
        assertThat(alice.api().get("/api/users").json().findValuesAsString("username"))
                .as("the player picker only offers active players")
                .contains(alice.username()).doesNotContain(bob.username());
        assertThat(admin.api().patch("/api/admin/users/777777", Map.of("enabled", false)).code()).isEqualTo("user.not_found");
    }

    @Test
    void disablingAPlayerEndsTheirOpenSessionImmediately() {
        assertThat(bob.api().get("/api/auth/me").status()).isEqualTo(200);
        disable(bob);
        // Their access token is still unexpired, but the account is off
        assertThat(bob.api().get("/api/auth/me").status()).isEqualTo(401);
        assertThat(bob.api().post("/api/invites").status()).as("no new invite codes from a removed member").isEqualTo(401);
        assertThat(bob.api().post("/api/auth/refresh").status()).isEqualTo(401);
    }

    @Test
    void reenabledPlayersCanLogInAgain() {
        disable(bob);
        assertThat(admin.api().patch("/api/admin/users/" + bob.id(), Map.of("enabled", true)).json().path("enabled").asBoolean())
                .isTrue();
        Response login = session().post("/api/auth/login", Map.of("username", bob.username(), "password", PASSWORD));
        assertThat(login.status()).isEqualTo(200);
    }

    @Test
    void demotedAdminLosesAdminRightsImmediately() {
        assertThat(admin.api().patch("/api/admin/users/" + alice.id(), Map.of("role", "ADMIN")).status()).isEqualTo(200);
        // Alice's current token says MEMBER; after a refresh she is an admin
        assertThat(alice.api().post("/api/auth/refresh").status()).isEqualTo(204);
        assertThat(alice.api().get("/api/admin/users").status()).isEqualTo(200);

        assertThat(admin.api().patch("/api/admin/users/" + alice.id(), Map.of("role", "USER")).status()).isEqualTo(200);
        Response afterDemotion = alice.api().get("/api/admin/users");
        assertThat(afterDemotion.status()).as("the old ADMIN token must not keep working").isIn(401, 403);
        // A refresh gives her a normal member session
        assertThat(alice.api().post("/api/auth/refresh").status()).isEqualTo(204);
        assertThat(alice.api().get("/api/auth/me").json().path("role").asString()).isEqualTo("USER");
        assertThat(alice.api().get("/api/admin/users").status()).isEqualTo(403);
    }

    @Test
    void disablingAPlayerRevokesTheInviteCodesTheyHandedOut() {
        String code = bob.api().post("/api/invites").json().path("code").asString();
        disable(bob);
        Response r = session().post("/api/auth/register", Map.of("username", uniqueName("friend"), "realName", "Friend",
                "password", PASSWORD, "inviteCode", code));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("invite.invalid");
    }

    @Test
    void disabledPlayersKeepTheirHistoryButCannotBePairedAgain() {
        playConfirmed(alice, bob, game(alice, bob, "WHITE_WIN"));
        disable(bob);
        assertThat(alice.api().get("/api/games").json().path("totalElements").asInt()).isEqualTo(1);
        assertThat(alice.api().post("/api/games", game(alice, bob, "DRAW")).code()).isEqualTo("player.unknown_or_disabled");
        Response cup = alice.api().post("/api/tournaments", Map.of("name", "Cup", "format", "ROUND_ROBIN",
                "participantIds", java.util.List.of(alice.id(), bob.id(), admin.id())));
        assertThat(cup.code()).as("%s", cup).isEqualTo("player.unknown_or_disabled");
    }

    @Test
    void anAdminCanStillRecordTournamentResultsForAPlayerWhoWasDisabledMidway() {
        Player carol = registerMember(admin, "carol");
        long tid = admin.api().post("/api/tournaments", Map.of("name", "Cup", "format", "SINGLE_ELIMINATION",
                "participantIds", java.util.List.of(alice.id(), bob.id()))).json().path("summary").path("id").asLong();
        JsonNode fin = admin.api().post("/api/tournaments/" + tid + "/start").json().path("rounds").get(0).path("matches").get(0);
        disable(bob);

        Map<String, Object> g = game(fin.path("white").path("id").asLong(), fin.path("black").path("id").asLong(), "WHITE_WIN",
                java.time.Instant.now().minusSeconds(60));
        g.put("matchId", fin.path("id").asLong());
        assertThat(alice.api().post("/api/games", g).code()).as("members still can't").isEqualTo("player.unknown_or_disabled");
        Response logged = admin.api().post("/api/games", g);
        assertThat(logged.status()).as("%s", logged).isEqualTo(201);
        admin.api().post("/api/admin/games/" + logged.json().path("id").asLong() + "/resolve", Map.of("action", "CONFIRM"));
        assertThat(admin.api().get("/api/tournaments/" + tid).json().path("summary").path("status").asString()).isEqualTo("COMPLETED");

        // Outside tournaments a disabled player can't be given new games, even by an admin
        assertThat(admin.api().post("/api/games", game(carol, bob, "DRAW")).code()).isEqualTo("player.unknown_or_disabled");
    }

    @Test
    void recomputeIsAdminOnlyAndReportsWhatItDid() {
        playConfirmed(alice, bob, game(alice, bob, "WHITE_WIN"));
        assertThat(alice.api().post("/api/admin/ratings/recompute").status()).isEqualTo(403);
        JsonNode r = admin.api().post("/api/admin/ratings/recompute").json();
        assertThat(r.path("games").asInt()).isEqualTo(1);
    }

    private void disable(Player p) {
        Response r = admin.api().patch("/api/admin/users/" + p.id(), Map.of("enabled", false));
        assertThat(r.status()).as("disable: %s", r.body()).isEqualTo(200);
        assertThat(r.json().path("enabled").asBoolean()).isFalse();
    }
}
