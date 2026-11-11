package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.game.AutoConfirmService;
import no.trafonsjakk.rating.Glicko2;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class GameApiTest extends IntegrationTest {

    @Autowired
    AutoConfirmService autoConfirm;

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

    @Test
    void loggedGameCountsOnlyAfterTheOpponentConfirms() {
        Response created = alice.api().post("/api/games", game(alice, bob, "WHITE_WIN"));
        assertThat(created.status()).isEqualTo(201);
        JsonNode g = created.json();
        long id = g.path("id").asLong();
        assertThat(g.path("status").asString()).isEqualTo("PENDING");
        assertThat(g.path("canWithdraw").asBoolean()).isTrue();
        assertThat(g.path("canConfirm").asBoolean()).isFalse();
        assertThat(g.path("autoConfirmAt").isNull()).isFalse();
        assertThat(rating(alice)).as("pending games don't count").isEqualTo(1500);

        Response selfConfirm = alice.api().post("/api/games/" + id + "/confirm");
        assertThat(selfConfirm.status()).isEqualTo(403);
        assertThat(selfConfirm.code()).isEqualTo("game.not_opponent");
        assertThat(carol.api().post("/api/games/" + id + "/confirm").status()).isEqualTo(403);

        JsonNode pending = bob.api().get("/api/games/pending").json();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).path("canConfirm").asBoolean()).isTrue();
        assertThat(alice.api().get("/api/games/pending").json()).as("reporter has nothing to confirm").isEmpty();

        Response confirmed = bob.api().post("/api/games/" + id + "/confirm");
        assertThat(confirmed.status()).isEqualTo(200);
        assertThat(confirmed.json().path("status").asString()).isEqualTo("CONFIRMED");
        JsonNode change = confirmed.json().path("ratingChange");
        assertThat(change.path("white").path("before").asInt()).isEqualTo(1500);
        assertThat(change.path("white").path("after").asInt()).isGreaterThan(1500);
        assertThat(change.path("black").path("after").asInt()).isLessThan(1500);
        assertThat(rating(alice)).isEqualTo(change.path("white").path("after").asInt());

        Response twice = bob.api().post("/api/games/" + id + "/confirm");
        assertThat(twice.status()).isEqualTo(409);
        assertThat(twice.code()).isEqualTo("game.wrong_status");
        assertThat(twice.json().path("params").path("status").asString()).isEqualTo("CONFIRMED");
    }

    @Test
    void onlyPlayersOrAdminsCanLogAGame() {
        Response outsider = carol.api().post("/api/games", game(alice, bob, "DRAW"));
        assertThat(outsider.status()).isEqualTo(403);
        assertThat(outsider.code()).isEqualTo("game.not_a_player");

        Response byAdmin = admin.api().post("/api/games", game(alice, bob, "DRAW"));
        assertThat(byAdmin.status()).isEqualTo(201);
        long id = byAdmin.json().path("id").asLong();
        // Admin logged it for others: either player may confirm
        assertThat(alice.api().get("/api/games/pending").json()).hasSize(1);
        assertThat(bob.api().get("/api/games/pending").json()).hasSize(1);
        assertThat(alice.api().post("/api/games/" + id + "/confirm").status()).isEqualTo(200);
    }

    @Test
    void invalidGamesAreRejectedWithCodes() {
        Response same = alice.api().post("/api/games", game(alice, alice, "WHITE_WIN"));
        assertThat(same.code()).isEqualTo("game.same_player");

        Response future = alice.api().post("/api/games", game(alice.id(), bob.id(), "WHITE_WIN", Instant.now().plus(1, ChronoUnit.DAYS)));
        assertThat(future.status()).isEqualTo(400);
        assertThat(future.code()).isEqualTo("game.in_future");

        Response ancient = alice.api().post("/api/games", game(alice.id(), bob.id(), "WHITE_WIN", Instant.parse("1999-01-01T00:00:00Z")));
        assertThat(ancient.code()).isEqualTo("game.too_old");

        Response ghost = alice.api().post("/api/games", game(alice.id(), 9999, "WHITE_WIN", Instant.now()));
        assertThat(ghost.status()).isEqualTo(400);
        assertThat(ghost.code()).isEqualTo("player.unknown_or_disabled");

        Map<String, Object> badEco = game(alice, bob, "WHITE_WIN");
        badEco.put("eco", "Z99");
        Response eco = alice.api().post("/api/games", badEco);
        assertThat(eco.code()).isEqualTo("validation");
        assertThat(eco.json().path("errorCodes").path("eco").asString()).isEqualTo("Pattern");

        Response badResult = alice.api().post("/api/games", Map.of("whiteId", alice.id(), "blackId", bob.id(), "result", "WIN",
                "playedAt", Instant.now().toString()));
        assertThat(badResult.status()).as("unknown enum value: %s", badResult).isEqualTo(400);
    }

    @Test
    void ecoCodeIsStoredUppercaseAndTextIsTrimmed() {
        Map<String, Object> g = game(alice, bob, "WHITE_WIN");
        g.put("eco", "c50");
        g.put("opening", "  Italian Game  ");
        g.put("notes", "   ");
        JsonNode created = alice.api().post("/api/games", g).json();
        assertThat(created.path("eco").asString()).isEqualTo("C50");
        assertThat(created.path("opening").asString()).isEqualTo("Italian Game");
        assertThat(created.path("notes").isNull()).as("blank notes become null").isTrue();
    }

    @Test
    void disputedResultCanBeCorrectedByAnAdmin() {
        long id = alice.api().post("/api/games", game(alice, bob, "WHITE_WIN")).json().path("id").asLong();
        Response tooShort = bob.api().post("/api/games/" + id + "/dispute", Map.of("reason", "no"));
        assertThat(tooShort.status()).isEqualTo(400);
        assertThat(bob.api().post("/api/games/" + id + "/dispute", Map.of("reason", "I won that game")).json().path("status").asString())
                .isEqualTo("DISPUTED");
        assertThat(admin.api().get("/api/admin/games/disputed").json()).hasSize(1);

        Response resolved = admin.api().post("/api/admin/games/" + id + "/resolve", Map.of("action", "CONFIRM", "result", "BLACK_WIN"));
        assertThat(resolved.status()).isEqualTo(200);
        assertThat(resolved.json().path("result").asString()).isEqualTo("BLACK_WIN");
        assertThat(rating(bob)).isGreaterThan(1500);
        assertThat(rating(alice)).isLessThan(1500);
        assertThat(admin.api().get("/api/admin/games/disputed").json()).isEmpty();
    }

    @Test
    void voidingAConfirmedGameRestoresRatings() {
        long id = playConfirmed(alice, bob, game(alice, bob, "WHITE_WIN")).path("id").asLong();
        assertThat(rating(alice)).isGreaterThan(1500);

        assertThat(admin.api().post("/api/admin/games/" + id + "/resolve", Map.of("action", "VOID")).status()).isEqualTo(200);
        assertThat(rating(alice)).isEqualTo(1500);
        assertThat(rating(bob)).isEqualTo(1500);
        assertThat(jdbc.queryForObject("select count(*) from rating_history", Integer.class)).isZero();
        assertThat(alice.api().get("/api/users/" + alice.username()).json().path("ratedGames").asInt()).isZero();

        Response again = admin.api().post("/api/admin/games/" + id + "/resolve", Map.of("action", "CONFIRM"));
        assertThat(again.code()).isEqualTo("game.voided");
    }

    @Test
    void reporterCanWithdrawAPendingGame() {
        long id = alice.api().post("/api/games", game(alice, bob, "WHITE_WIN")).json().path("id").asLong();
        assertThat(bob.api().post("/api/games/" + id + "/withdraw").code()).isEqualTo("game.not_reporter");
        assertThat(alice.api().post("/api/games/" + id + "/withdraw").json().path("status").asString()).isEqualTo("VOIDED");
        assertThat(bob.api().get("/api/games/pending").json()).isEmpty();
        assertThat(bob.api().post("/api/games/" + id + "/confirm").code()).isEqualTo("game.wrong_status");
        // Voided games are hidden from the default list
        assertThat(alice.api().get("/api/games").json().path("totalElements").asInt()).isZero();
        assertThat(alice.api().get("/api/games?status=VOIDED").json().path("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void backdatedGamesAreReplayedInTheOrderTheyWerePlayed() {
        Instant now = Instant.now();
        // Played later, confirmed first
        JsonNode later = playConfirmed(alice, bob, game(alice.id(), bob.id(), "WHITE_WIN", now.minus(1, ChronoUnit.DAYS)));
        // Played earlier, confirmed afterwards (a forgotten result)
        JsonNode earlier = playConfirmed(bob, alice, game(bob.id(), alice.id(), "WHITE_WIN", now.minus(3, ChronoUnit.DAYS)));

        // Expected: exactly the Glicko-2 result of replaying in played order (with 2 days of inactivity growth)
        Glicko2 g = new Glicko2();
        Glicko2.Rating a = Glicko2.Rating.initial();
        Glicko2.Rating b = Glicko2.Rating.initial();
        Glicko2.Rating a1 = g.update(a, b, 0); // earlier: bob (white) beat alice
        Glicko2.Rating b1 = g.update(b, a, 1);
        double periods = 2.0 / Glicko2.RATING_PERIOD_DAYS;
        Glicko2.Rating a1d = g.decay(a1, periods);
        Glicko2.Rating b1d = g.decay(b1, periods);
        Glicko2.Rating a2 = g.update(a1d, b1d, 1); // later: alice (white) beat bob
        Glicko2.Rating b2 = g.update(b1d, a1d, 0);

        assertThat(rating(alice)).isCloseTo((int) Math.round(a2.rating()), org.assertj.core.data.Offset.offset(1));
        assertThat(rating(bob)).isCloseTo((int) Math.round(b2.rating()), org.assertj.core.data.Offset.offset(1));

        JsonNode history = alice.api().get("/api/users/" + alice.username() + "/stats").json().path("ratingHistory");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).path("gameId").asLong()).isEqualTo(earlier.path("id").asLong());
        assertThat(history.get(1).path("gameId").asLong()).isEqualTo(later.path("id").asLong());

        // A full recompute changes nothing (deterministic)
        int before = rating(alice);
        assertThat(admin.api().post("/api/admin/ratings/recompute").json().path("games").asInt()).isEqualTo(2);
        assertThat(rating(alice)).isEqualTo(before);
    }

    @Test
    void pendingResultsAreAutoConfirmedAfter48Hours() {
        long id = alice.api().post("/api/games", game(alice, bob, "WHITE_WIN")).json().path("id").asLong();
        long fresh = alice.api().post("/api/games", game(alice, carol, "WHITE_WIN")).json().path("id").asLong();
        jdbc.update("update games set created_at = ? where id = ?", Timestamp.from(Instant.now().minus(49, ChronoUnit.HOURS)), id);

        autoConfirm.scheduled();

        JsonNode g = alice.api().get("/api/games/" + id).json();
        assertThat(g.path("status").asString()).isEqualTo("CONFIRMED");
        assertThat(g.path("confirmedBy").isNull()).as("auto-confirmed has no confirmer").isTrue();
        assertThat(rating(alice)).isGreaterThan(1500);
        assertThat(alice.api().get("/api/games/" + fresh).json().path("status").asString()).isEqualTo("PENDING");
    }

    @Test
    void disputedGamesAreNeverAutoConfirmed() {
        long id = alice.api().post("/api/games", game(alice, bob, "WHITE_WIN")).json().path("id").asLong();
        bob.api().post("/api/games/" + id + "/dispute", Map.of("reason", "That is not what happened"));
        jdbc.update("update games set created_at = ? where id = ?", Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)), id);
        autoConfirm.scheduled();
        assertThat(alice.api().get("/api/games/" + id).json().path("status").asString()).isEqualTo("DISPUTED");
    }

    @Test
    void listingFiltersByPlayerAndPagesNewestFirst() {
        List<Long> ids = new ArrayList<>();
        Instant t = Instant.now().minus(10, ChronoUnit.DAYS);
        for (int i = 0; i < 5; i++) {
            ids.add(playConfirmed(alice, bob, game(alice.id(), bob.id(), "DRAW", t.plus(i, ChronoUnit.HOURS))).path("id").asLong());
        }
        playConfirmed(carol, bob, game(carol, bob, "WHITE_WIN"));

        JsonNode page0 = alice.api().get("/api/games?player=" + alice.username() + "&size=2").json();
        assertThat(page0.path("totalElements").asInt()).isEqualTo(5);
        assertThat(page0.path("totalPages").asInt()).isEqualTo(3);
        assertThat(page0.path("content").get(0).path("id").asLong()).as("newest first").isEqualTo(ids.get(4));
        JsonNode page2 = alice.api().get("/api/games?player=" + alice.username() + "&size=2&page=2").json();
        assertThat(page2.path("content")).hasSize(1);
        assertThat(alice.api().get("/api/games?size=1000").json().path("size").asInt()).as("page size is capped").isEqualTo(100);
        assertThat(alice.api().get("/api/games?player=nobody_like_this").status()).isEqualTo(404);
        assertThat(alice.api().get("/api/games?page=-5").status()).isEqualTo(200);
    }

    @Test
    void statsAndLeaderboardAgreeWithTheGames() {
        playConfirmed(alice, bob, game(alice, bob, "WHITE_WIN"));
        playConfirmed(bob, alice, game(bob, alice, "DRAW"));
        playConfirmed(alice, carol, game(alice, carol, "BLACK_WIN"));

        JsonNode stats = alice.api().get("/api/users/" + alice.username() + "/stats").json();
        assertThat(stats.path("totals").path("games").asInt()).isEqualTo(3);
        assertThat(stats.path("totals").path("wins").asInt()).isEqualTo(1);
        assertThat(stats.path("totals").path("draws").asInt()).isEqualTo(1);
        assertThat(stats.path("totals").path("losses").asInt()).isEqualTo(1);
        assertThat(stats.path("headToHead")).hasSize(2);
        assertThat(stats.path("ratingHistory")).hasSize(3);

        JsonNode board = alice.api().get("/api/leaderboard").json();
        JsonNode row = null;
        for (JsonNode r : board) if (r.path("player").path("id").asLong() == alice.id()) row = r;
        assertThat(row).isNotNull();
        assertThat(row.path("games").asInt()).isEqualTo(3);
        assertThat(row.path("wins").asInt() + row.path("draws").asInt() + row.path("losses").asInt()).isEqualTo(3);
        assertThat(alice.api().get("/api/users/nobody_here/stats").code()).isEqualTo("player.not_found");
    }
}
