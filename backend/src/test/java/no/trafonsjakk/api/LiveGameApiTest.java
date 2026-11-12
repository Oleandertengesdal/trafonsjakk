package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class LiveGameApiTest extends IntegrationTest {

    Player admin;
    Player alice;
    Player bob;
    Player carol;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
        carol = registerMember(admin, "carol");
    }

    private JsonNode start(Player by, Player white, Player black) {
        Response r = by.api().post("/api/live", Map.of("whiteId", white.id(), "blackId", black.id()));
        assertThat(r.status()).as("start: %s", r).isEqualTo(201);
        return r.json();
    }

    private static Map<String, Object> moves(String moves, long version) {
        Map<String, Object> m = new HashMap<>();
        m.put("moves", moves);
        m.put("version", version);
        return m;
    }

    @Test
    void aPlayerRecordsMovesAsTheyGoAndEveryoneCanFollow() {
        JsonNode live = start(alice, alice, bob);
        long id = live.path("id").asLong();
        assertThat(live.path("status").asString()).isEqualTo("LIVE");
        assertThat(live.path("canRecord").asBoolean()).isTrue();
        assertThat(live.path("moves").isNull()).isTrue();

        Response first = alice.api().put("/api/live/" + id + "/moves", moves("1. e4", 0));
        assertThat(first.status()).isEqualTo(200);
        Response second = alice.api().put("/api/live/" + id + "/moves", moves("1. e4 e5", first.json().path("version").asLong()));
        assertThat(second.json().path("version").asLong()).isEqualTo(2);

        // Carol isn't playing: she can watch but not record
        JsonNode watching = carol.api().get("/api/live/" + id).json();
        assertThat(watching.path("moves").asString()).isEqualTo("1. e4 e5");
        assertThat(watching.path("canRecord").asBoolean()).isFalse();
        assertThat(carol.api().get("/api/live").json().findValuesAsString("moves")).containsExactly("1. e4 e5");
        Response meddling = carol.api().put("/api/live/" + id + "/moves", moves("1. e4 e5 2. Qh5", 2));
        assertThat(meddling.status()).isEqualTo(403);
        assertThat(meddling.code()).isEqualTo("live.not_a_player");
    }

    @Test
    void anOldCopyCannotOverwriteNewerMoves() {
        long id = start(alice, alice, bob).path("id").asLong();
        assertThat(alice.api().put("/api/live/" + id + "/moves", moves("1. d4", 0)).status()).isEqualTo(200);
        // Bob's phone still has version 0
        Response stale = bob.api().put("/api/live/" + id + "/moves", moves("1. c4", 0));
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.code()).isEqualTo("live.out_of_date");
        assertThat(bob.api().get("/api/live/" + id).json().path("moves").asString()).isEqualTo("1. d4");
    }

    @Test
    void finishingLogsAnOrdinaryGameForTheOpponentToConfirm() {
        long id = start(bob, alice, bob).path("id").asLong();
        bob.api().put("/api/live/" + id + "/moves", moves("1. f3 e5 2. g4 Qh4#", 0));
        Response finished = bob.api().post("/api/live/" + id + "/finish", Map.of("result", "BLACK_WIN"));
        assertThat(finished.status()).as("%s", finished).isEqualTo(201);
        long gameId = finished.json().path("id").asLong();
        assertThat(finished.json().path("status").asString()).isEqualTo("PENDING");
        assertThat(finished.json().path("moves").asString()).isEqualTo("1. f3 e5 2. g4 Qh4#");

        JsonNode after = alice.api().get("/api/live/" + id).json();
        assertThat(after.path("status").asString()).isEqualTo("FINISHED");
        assertThat(after.path("gameId").asLong()).isEqualTo(gameId);
        assertThat(alice.api().get("/api/live").json().size()).isZero();
        assertThat(alice.api().post("/api/games/" + gameId + "/confirm").status()).isEqualTo(200);

        Response again = bob.api().post("/api/live/" + id + "/finish", Map.of("result", "DRAW"));
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("live.not_running");
    }

    @Test
    void movesAreOptional() {
        long id = start(alice, alice, bob).path("id").asLong();
        Response finished = alice.api().post("/api/live/" + id + "/finish", Map.of("result", "DRAW"));
        assertThat(finished.status()).isEqualTo(201);
        assertThat(finished.json().path("moves").isNull()).isTrue();
    }

    @Test
    void onlyPlayersStartAndNobodyPlaysTwoAtOnce() {
        Response outsider = carol.api().post("/api/live", Map.of("whiteId", alice.id(), "blackId", bob.id()));
        assertThat(outsider.status()).isEqualTo(403);
        assertThat(outsider.code()).isEqualTo("live.not_a_player");
        Response self = alice.api().post("/api/live", Map.of("whiteId", alice.id(), "blackId", alice.id()));
        assertThat(self.code()).isEqualTo("game.same_player");

        start(alice, alice, bob);
        Response busy = carol.api().post("/api/live", Map.of("whiteId", carol.id(), "blackId", bob.id()));
        assertThat(busy.status()).isEqualTo(409);
        assertThat(busy.code()).isEqualTo("live.already_playing");
        // An admin can start one for others (e.g. from the club's tablet)
        assertThat(admin.api().post("/api/live", Map.of("whiteId", carol.id(), "blackId", admin.id())).status()).isEqualTo(201);
    }

    @Test
    void abandonedAndForgottenGamesDropOffTheList() {
        long id = start(alice, alice, bob).path("id").asLong();
        assertThat(carol.api().post("/api/live/" + id + "/abandon").status()).isEqualTo(403);
        assertThat(bob.api().post("/api/live/" + id + "/abandon").status()).isEqualTo(204);
        assertThat(alice.api().get("/api/live/" + id).json().path("status").asString()).isEqualTo("ABANDONED");

        long forgotten = start(alice, alice, bob).path("id").asLong();
        jdbc.update("update live_games set updated_at = now() - interval '7 hours' where id = ?", forgotten);
        assertThat(carol.api().get("/api/live").json().size()).isZero();
        Response late = alice.api().put("/api/live/" + forgotten + "/moves", moves("1. e4", 0));
        assertThat(late.code()).isEqualTo("live.not_running");
        // The players are free to start a new one
        start(bob, alice, bob);
    }

    @Test
    void movesHaveTheSameLimitsAsAGame() {
        long id = start(alice, alice, bob).path("id").asLong();
        Response tooLong = alice.api().put("/api/live/" + id + "/moves", moves("e4 ".repeat(7000), 0));
        assertThat(tooLong.status()).isEqualTo(400);
        Response noVersion = alice.api().put("/api/live/" + id + "/moves", Map.of("moves", "1. e4"));
        assertThat(noVersion.status()).isEqualTo(400);
        assertThat(alice.api().get("/api/live/99999").code()).isEqualTo("live.not_found");
        assertThat(List.of(alice.api().get("/api/live/" + id).json().path("white").path("id").asLong())).containsExactly(alice.id());
    }
}
