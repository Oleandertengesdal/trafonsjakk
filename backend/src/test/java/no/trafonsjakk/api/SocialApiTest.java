package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import no.trafonsjakk.support.Race;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Comments and kudos on games, and adding the moves after the game was logged. */
class SocialApiTest extends IntegrationTest {

    Player admin;
    Player alice;
    Player bob;
    Player carol;
    long gameId;

    @BeforeEach
    void setUp() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
        carol = registerMember(admin, "carol");
        gameId = playConfirmed(alice, bob, game(alice, bob, "WHITE_WIN")).path("id").asLong();
    }

    private String comments() {
        return "/api/games/" + gameId + "/comments";
    }

    // ---- comments -------------------------------------------------------------------------------

    @Test
    void anyoneCanCommentOnAnyGame() {
        Response c = carol.api().post(comments(), Map.of("body", "  Lovely queen sacrifice!  "));
        assertThat(c.status()).as("%s", c).isEqualTo(201);
        assertThat(c.json().path("body").asString()).isEqualTo("Lovely queen sacrifice!");
        assertThat(c.json().path("author").path("id").asLong()).isEqualTo(carol.id());
        assertThat(c.json().path("canEdit").asBoolean()).isTrue();
        bob.api().post(comments(), Map.of("body", "I was lost after move 12"));

        JsonNode list = alice.api().get(comments()).json();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("body").asString()).as("oldest first").isEqualTo("Lovely queen sacrifice!");
        assertThat(list.get(0).path("canEdit").asBoolean()).isFalse();
        assertThat(list.get(0).path("canDelete").asBoolean()).isFalse();

        JsonNode g = alice.api().get("/api/games/" + gameId).json();
        assertThat(g.path("commentCount").asInt()).isEqualTo(2);
        JsonNode row = alice.api().get("/api/games").json().path("content").get(0);
        assertThat(row.path("commentCount").asInt()).as("counts show in lists too").isEqualTo(2);
    }

    @Test
    void onlyTheAuthorEditsAndTheAuthorOrAnAdminDeletes() {
        long id = carol.api().post(comments(), Map.of("body", "First thought")).json().path("id").asLong();
        String one = comments() + "/" + id;

        Response notMine = bob.api().patch(one, Map.of("body", "Hacked"));
        assertThat(notMine.status()).isEqualTo(403);
        assertThat(notMine.code()).isEqualTo("comment.not_author");
        assertThat(admin.api().patch(one, Map.of("body", "Admin edit")).code()).as("admins delete, they don't rewrite").isEqualTo("comment.not_author");

        JsonNode edited = carol.api().patch(one, Map.of("body", "Second thought")).json();
        assertThat(edited.path("body").asString()).isEqualTo("Second thought");
        assertThat(edited.path("editedAt").isNull()).isFalse();

        assertThat(bob.api().delete(one).code()).isEqualTo("comment.not_author");
        assertThat(admin.api().get(comments()).json().get(0).path("canDelete").asBoolean()).isTrue();
        assertThat(admin.api().delete(one).status()).isEqualTo(204);
        assertThat(alice.api().get(comments()).json()).isEmpty();
        assertThat(carol.api().delete(one).code()).isEqualTo("comment.not_found");
    }

    @Test
    void commentsAreValidated() {
        Response blank = alice.api().post(comments(), Map.of("body", "   "));
        assertThat(blank.code()).isEqualTo("validation");
        assertThat(blank.json().path("errorCodes").path("body").asString()).isEqualTo("NotBlank");
        assertThat(alice.api().post(comments(), Map.of("body", "x".repeat(1001))).json().path("errorCodes").path("body").asString())
                .isEqualTo("Size");
        assertThat(alice.api().post(comments(), Map.of("body", "x".repeat(1000))).status()).isEqualTo(201);
        assertThat(alice.api().post(comments(), Map.of("body", "nul\u0000")).json().path("errorCodes").path("body").asString())
                .isEqualTo("PlainText");
        assertThat(alice.api().post(comments(), Map.of("body", "Line one\nLine two")).status()).isEqualTo(201);
        assertThat(alice.api().post("/api/games/999999/comments", Map.of("body", "hi")).code()).isEqualTo("game.not_found");
        // A comment id from another game isn't reachable through this game
        long other = playConfirmed(carol, bob, game(carol, bob, "DRAW")).path("id").asLong();
        long foreign = carol.api().post("/api/games/" + other + "/comments", Map.of("body", "elsewhere")).json().path("id").asLong();
        assertThat(carol.api().delete(comments() + "/" + foreign).code()).isEqualTo("comment.not_found");
    }

    @Test
    void voidedGamesTakeNoNewComments() {
        alice.api().post(comments(), Map.of("body", "Before"));
        admin.api().post("/api/admin/games/" + gameId + "/resolve", Map.of("action", "VOID"));
        assertThat(bob.api().post(comments(), Map.of("body", "After")).code()).isEqualTo("game.voided");
        assertThat(bob.api().get(comments()).json()).as("old comments stay readable").hasSize(1);
    }

    @Test
    void commentsAreJustText() {
        String html = "<script>alert(1)</script>";
        carol.api().post(comments(), Map.of("body", html));
        Response r = alice.api().get(comments());
        assertThat(r.json().get(0).path("body").asString()).isEqualTo(html);
        assertThat(r.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
    }

    // ---- kudos ----------------------------------------------------------------------------------

    private String kudos() {
        return "/api/games/" + gameId + "/kudos";
    }

    @Test
    void othersCanGiveKudosOnceAndTakeItBack() {
        JsonNode before = carol.api().get("/api/games/" + gameId).json();
        assertThat(before.path("kudosCount").asInt()).isZero();
        assertThat(before.path("canGiveKudos").asBoolean()).isTrue();
        assertThat(alice.api().get("/api/games/" + gameId).json().path("canGiveKudos").asBoolean())
                .as("not on your own game").isFalse();

        JsonNode given = carol.api().put(kudos(), Map.of()).json();
        assertThat(given.path("count").asInt()).isEqualTo(1);
        assertThat(given.path("givenByMe").asBoolean()).isTrue();
        assertThat(given.path("givers").get(0).path("id").asLong()).isEqualTo(carol.id());
        assertThat(carol.api().put(kudos(), Map.of()).json().path("count").asInt()).as("giving twice counts once").isEqualTo(1);
        admin.api().put(kudos(), Map.of());

        JsonNode g = carol.api().get("/api/games/" + gameId).json();
        assertThat(g.path("kudosCount").asInt()).isEqualTo(2);
        assertThat(g.path("kudosGivenByMe").asBoolean()).isTrue();
        assertThat(alice.api().get(kudos()).json().path("givers")).hasSize(2);

        JsonNode taken = carol.api().delete(kudos()).json();
        assertThat(taken.path("count").asInt()).isEqualTo(1);
        assertThat(taken.path("givenByMe").asBoolean()).isFalse();
        assertThat(carol.api().delete(kudos()).json().path("count").asInt()).as("taking back twice is harmless").isEqualTo(1);
    }

    @Test
    void kudosRules() {
        Response own = alice.api().put(kudos(), Map.of());
        assertThat(own.status()).isEqualTo(403);
        assertThat(own.code()).isEqualTo("kudos.own_game");
        long pending = alice.api().post("/api/games", game(alice, bob, "DRAW")).json().path("id").asLong();
        Response early = carol.api().put("/api/games/" + pending + "/kudos", Map.of());
        assertThat(early.status()).isEqualTo(409);
        assertThat(early.code()).isEqualTo("kudos.not_confirmed");
        assertThat(carol.api().get("/api/games/" + pending).json().path("canGiveKudos").asBoolean()).isFalse();
        assertThat(carol.api().put("/api/games/999999/kudos", Map.of()).code()).isEqualTo("game.not_found");
    }

    @Test
    void doubleClickingKudosCountsOnce() {
        Player carolTab2 = login(carol.id());
        List<Response> rs = Race.run(() -> carol.api().put(kudos(), Map.of()), () -> carolTab2.api().put(kudos(), Map.of()),
                () -> carol.api().put(kudos(), Map.of()));
        assertThat(rs).allSatisfy(r -> assertThat(r.status()).as("%s", r).isEqualTo(200));
        assertThat(jdbc.queryForObject("select count(*) from game_kudos", Integer.class)).isEqualTo(1);
    }

    // ---- moves added afterwards ---------------------------------------------------------------

    private static final String MOVES = "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 5. d4 exd4";

    @Test
    void eitherPlayerOrAnAdminCanAddTheMovesLater() {
        assertThat(alice.api().get("/api/games/" + gameId).json().path("canEditMoves").asBoolean()).isTrue();
        assertThat(carol.api().get("/api/games/" + gameId).json().path("canEditMoves").asBoolean()).isFalse();

        Response byBob = bob.api().patch("/api/games/" + gameId + "/moves", Map.of("moves", MOVES));
        assertThat(byBob.status()).as("%s", byBob).isEqualTo(200);
        assertThat(byBob.json().path("moves").asString()).isEqualTo(MOVES);
        int ratingBefore = rating(alice);

        Response outsider = carol.api().patch("/api/games/" + gameId + "/moves", Map.of("moves", "1. d4"));
        assertThat(outsider.status()).isEqualTo(403);
        assertThat(outsider.code()).isEqualTo("game.not_a_player");
        assertThat(admin.api().patch("/api/games/" + gameId + "/moves", Map.of("moves", MOVES + " 6. cxd4")).status()).isEqualTo(200);

        Map<String, Object> clear = new java.util.HashMap<>();
        clear.put("moves", null);
        assertThat(alice.api().patch("/api/games/" + gameId + "/moves", clear).json().path("moves").isNull()).isTrue();
        assertThat(alice.api().patch("/api/games/" + gameId + "/moves", Map.of("moves", "   ")).json().path("moves").isNull()).isTrue();
        assertThat(rating(alice)).as("moves don't touch ratings").isEqualTo(ratingBefore);
        assertThat(alice.api().get("/api/games/" + gameId).json().path("status").asString()).isEqualTo("CONFIRMED");
    }

    @Test
    void movesAreValidatedAndVoidedGamesAreLocked() {
        Response tooLong = alice.api().patch("/api/games/" + gameId + "/moves", Map.of("moves", "e".repeat(20001)));
        assertThat(tooLong.json().path("errorCodes").path("moves").asString()).isEqualTo("Size");
        assertThat(alice.api().patch("/api/games/" + gameId + "/moves", Map.of("moves", "1. e4\u0000")).json()
                .path("errorCodes").path("moves").asString()).isEqualTo("PlainText");
        admin.api().post("/api/admin/games/" + gameId + "/resolve", Map.of("action", "VOID"));
        assertThat(alice.api().patch("/api/games/" + gameId + "/moves", Map.of("moves", MOVES)).code()).isEqualTo("game.voided");
        assertThat(alice.api().get("/api/games/" + gameId).json().path("canEditMoves").asBoolean()).isFalse();
    }
}
