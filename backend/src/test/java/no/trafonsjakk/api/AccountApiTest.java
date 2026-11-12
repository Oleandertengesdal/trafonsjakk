package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Downloading your own data and deleting your account. */
class AccountApiTest extends IntegrationTest {

    Player admin;
    Player alice;
    Player bob;
    long gameId;

    @BeforeEach
    void someHistory() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
        Map<String, Object> g = game(alice, bob, "WHITE_WIN");
        g.put("moves", "1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7#");
        g.put("notes", "Scholar's mate, sadly");
        gameId = playConfirmed(alice, bob, g).path("id").asLong();
    }

    private long comment(Player by, String body, Long parentId) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("body", body);
        m.put("parentId", parentId);
        Response r = by.api().post("/api/games/" + gameId + "/comments", m);
        assertThat(r.status()).as("%s", r).isEqualTo(201);
        return r.json().path("id").asLong();
    }

    @Test
    void theExportHasEverythingAboutMeAndNothingAboutOthers() {
        long mine = comment(alice, "Good game!", null);
        comment(bob, "Thanks, rematch?", mine);
        bob.api().put("/api/games/" + gameId + "/comments/" + mine + "/like", null);
        admin.api().put("/api/games/" + gameId + "/kudos", null);

        Response r = alice.api().get("/api/users/me/export");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Disposition").orElse("")).contains("attachment");
        assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode data = r.json();
        assertThat(data.path("account").path("username").asString()).isEqualTo(alice.username());
        assertThat(data.path("account").path("realName").asString()).isEqualTo("Real " + alice.username());
        assertThat(data.path("account").has("passwordHash")).isFalse();
        assertThat(data.path("games").get(0).path("moves").asString()).endsWith("Qxf7#");
        assertThat(data.path("games").get(0).path("playedAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        assertThat(data.path("ratingHistory").size()).isEqualTo(1);
        assertThat(data.path("comments").findValuesAsString("body")).containsExactly("Good game!");
        assertThat(data.path("kudosGiven").size()).isZero();
        assertThat(r.body()).doesNotContain("password", "Thanks, rematch?");
    }

    @Test
    void deletingNeedsThePasswordAndIsLimitedLikeLoggingIn() {
        for (int i = 0; i < 5; i++) {
            Response wrong = alice.api().post("/api/users/me/delete", Map.of("password", "not-my-password"));
            assertThat(wrong.status()).isEqualTo(400);
            assertThat(wrong.code()).isEqualTo("auth.wrong_current_password");
        }
        Response locked = alice.api().post("/api/users/me/delete", Map.of("password", PASSWORD));
        assertThat(locked.status()).isEqualTo(429);
        assertThat(alice.api().get("/api/auth/me").status()).isEqualTo(200);
    }

    @Test
    void theOnlyAdminCannotLeaveTheGroupWithoutOne() {
        Response r = admin.api().post("/api/users/me/delete", Map.of("password", PASSWORD));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("account.last_admin");
    }

    @Test
    void deletingRemovesThePersonButKeepsTheGames() {
        long aliceThread = comment(alice, "Good game!", null);
        long bobReply = comment(bob, "Thanks, rematch?", aliceThread);
        long bobThread = comment(bob, "Nice trap", null);
        comment(alice, "I know, right", bobThread);
        long aliceAlone = comment(alice, "Nobody answers this", null);
        alice.api().put("/api/games/" + gameId + "/comments/" + bobReply + "/like", null);
        long otherGame = playConfirmed(admin, bob, game(admin, bob, "DRAW")).path("id").asLong();
        assertThat(alice.api().put("/api/games/" + otherGame + "/kudos", null).status()).isEqualTo(200);
        long live = alice.api().post("/api/live", Map.of("whiteId", alice.id(), "blackId", bob.id())).json().path("id").asLong();
        int ratingBefore = rating(bob);

        Response deleted = alice.api().post("/api/users/me/delete", Map.of("password", PASSWORD));
        assertThat(deleted.status()).as("%s", deleted).isEqualTo(204);
        assertThat(deleted.setCookie("tf_at").orElse("")).contains("Max-Age=0");

        // Logged out everywhere, and the old name and password are gone
        assertThat(alice.api().get("/api/auth/me").status()).isEqualTo(401);
        assertThat(session().post("/api/auth/login", Map.of("username", alice.username(), "password", PASSWORD)).status()).isEqualTo(401);
        assertThat(bob.api().get("/api/users/" + alice.username()).status()).isEqualTo(404);

        // The game stays, under the new name, and Bob's rating doesn't move
        JsonNode game = bob.api().get("/api/games/" + gameId).json();
        assertThat(game.path("white").path("realName").asString()).isEqualTo("Slettet spiller");
        assertThat(game.path("white").path("username").asString()).isEqualTo("deleted_" + alice.id());
        assertThat(game.path("moves").asString()).endsWith("Qxf7#");
        assertThat(rating(bob)).isEqualTo(ratingBefore);
        assertThat(bob.api().get("/api/leaderboard").body()).doesNotContain("Slettet spiller", alice.username());
        assertThat(bob.api().get("/api/users").body()).doesNotContain("deleted_");

        // Her comments go; her thread with Bob's reply keeps its place without text
        JsonNode comments = bob.api().get("/api/games/" + gameId + "/comments").json();
        assertThat(comments.findValuesAsString("body")).containsExactlyInAnyOrder("", "Thanks, rematch?", "Nice trap");
        assertThat(comments.findValues("id").stream().map(JsonNode::asLong)).doesNotContain(aliceAlone);
        assertThat(jdbc.queryForObject("select count(*) from game_comments where author_id = ? and body <> ''", Long.class, alice.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from comment_likes where user_id = ?", Long.class, alice.id())).isZero();
        assertThat(bob.api().get("/api/games/" + otherGame + "/kudos").json().path("count").asInt()).isZero();
        assertThat(bob.api().get("/api/live/" + live).json().path("status").asString()).isEqualTo("ABANDONED");

        // An admin can't switch it back on, and nobody can take the placeholder name
        Response revive = admin.api().patch("/api/admin/users/" + alice.id(), Map.of("enabled", true));
        assertThat(revive.status()).isEqualTo(409);
        assertThat(revive.code()).isEqualTo("admin.user_deleted");
        String code = admin.api().post("/api/invites").json().path("code").asString();
        Response squat = session().post("/api/auth/register", Map.of("username", "Deleted_999", "realName", "Squatter",
                "password", PASSWORD, "inviteCode", code));
        assertThat(squat.code()).isEqualTo("auth.username_taken");
        assertThat(admin.api().get("/api/admin/users").body()).contains("\"deleted\":true");
    }
}
