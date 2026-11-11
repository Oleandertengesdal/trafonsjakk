package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import no.trafonsjakk.rating.Glicko2;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** "Before the game" statistics on the game page: expected score, upset, head-to-head. */
class GameContextApiTest extends IntegrationTest {

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

    private long played(Player white, Player black, String result, Instant at) {
        long id = admin.api().post("/api/games", game(white.id(), black.id(), result, at)).json().path("id").asLong();
        admin.api().post("/api/admin/games/" + id + "/resolve", Map.of("action", "CONFIRM"));
        return id;
    }

    private JsonNode context(Player viewer, long gameId) {
        Response r = viewer.api().get("/api/games/" + gameId + "/context");
        assertThat(r.status()).as("%s", r).isEqualTo(200);
        return r.json();
    }

    @Test
    void aFirstGameBetweenNewPlayersIsEvenAndTheirFirstMeeting() {
        long id = played(alice, bob, "WHITE_WIN", Instant.now().minus(1, ChronoUnit.HOURS));
        JsonNode c = context(carol, id);
        assertThat(c.path("expected").path("white").asDouble()).isCloseTo(0.5, within(1e-9));
        assertThat(c.path("expected").path("black").asDouble()).isCloseTo(0.5, within(1e-9));
        assertThat(c.path("upset").asBoolean()).isFalse();
        assertThat(c.path("meeting").asInt()).isEqualTo(1);
        assertThat(c.path("headToHead").path("games").asInt()).isEqualTo(1);
        assertThat(c.path("recent")).isEmpty();
    }

    @Test
    void headToHeadCountsEveryConfirmedGameBetweenThemFromThisGamesSides() {
        Instant t = Instant.now().minus(10, ChronoUnit.DAYS);
        long first = played(alice, bob, "WHITE_WIN", t);                    // alice wins
        played(bob, alice, "WHITE_WIN", t.plus(1, ChronoUnit.DAYS));        // bob wins
        played(alice, bob, "DRAW", t.plus(2, ChronoUnit.DAYS));
        played(alice, carol, "WHITE_WIN", t.plus(3, ChronoUnit.DAYS));      // someone else: not counted
        long pending = alice.api().post("/api/games", game(alice.id(), bob.id(), "WHITE_WIN", t.plus(4, ChronoUnit.DAYS)))
                .json().path("id").asLong();                                 // not confirmed: not counted
        long voided = played(bob, alice, "BLACK_WIN", t.plus(5, ChronoUnit.DAYS));
        admin.api().post("/api/admin/games/" + voided + "/resolve", Map.of("action", "VOID"));
        long latest = played(bob, alice, "BLACK_WIN", t.plus(6, ChronoUnit.DAYS)); // alice wins as black

        JsonNode c = context(alice, latest);
        JsonNode h = c.path("headToHead");
        // From this game's sides: white = bob, black = alice
        assertThat(h.path("games").asInt()).isEqualTo(4);
        assertThat(h.path("whiteWins").asInt()).as("bob's wins").isEqualTo(1);
        assertThat(h.path("blackWins").asInt()).as("alice's wins").isEqualTo(2);
        assertThat(h.path("draws").asInt()).isEqualTo(1);
        assertThat(h.path("firstPlayedAt").asString()).as("the first game's time, as stored")
                .isEqualTo(alice.api().get("/api/games/" + first).json().path("playedAt").asString());
        assertThat(c.path("meeting").asInt()).isEqualTo(4);

        JsonNode recent = c.path("recent");
        assertThat(recent).hasSize(3);
        assertThat(recent.findValues("id")).extracting(JsonNode::asLong).doesNotContain(latest, pending, voided);
        assertThat(recent.get(2).path("id").asLong()).as("newest first").isEqualTo(first);
        assertThat(recent.get(0).path("result").asString()).isEqualTo("DRAW");
        assertThat(recent.get(0).path("whiteId").asLong()).isEqualTo(alice.id());

        // The first game is their first meeting
        assertThat(context(alice, first).path("meeting").asInt()).isEqualTo(1);
    }

    @Test
    void expectedScoreUsesTheRatingsFromJustBeforeTheGameAndSpotsUpsets() {
        Instant t = Instant.now().minus(20, ChronoUnit.DAYS);
        for (int i = 0; i < 8; i++) {
            played(bob, carol, "WHITE_WIN", t.plus(i, ChronoUnit.HOURS)); // bob climbs
        }
        long upset = played(alice, bob, "WHITE_WIN", Instant.now().minus(1, ChronoUnit.HOURS));

        double bobBefore = jdbc.queryForObject("select rating_before from rating_history where game_id = ? and user_id = ?",
                Double.class, upset, bob.id());
        double bobRdBefore = jdbc.queryForObject("select rd_before from rating_history where game_id = ? and user_id = ?",
                Double.class, upset, bob.id());
        double aliceRdBefore = jdbc.queryForObject("select rd_before from rating_history where game_id = ? and user_id = ?",
                Double.class, upset, alice.id());
        double expectedWhite = Glicko2.expectedScore(new Glicko2.Rating(1500, aliceRdBefore, 0.06),
                new Glicko2.Rating(bobBefore, bobRdBefore, 0.06));

        JsonNode c = context(carol, upset);
        assertThat(c.path("expected").path("white").asDouble()).isCloseTo(expectedWhite, within(1e-6));
        assertThat(c.path("expected").path("white").asDouble() + c.path("expected").path("black").asDouble()).isCloseTo(1.0, within(1e-9));
        assertThat(c.path("expected").path("white").asDouble()).isLessThan(0.35);
        assertThat(c.path("upset").asBoolean()).as("the underdog won").isTrue();

        // The favourite winning is no upset
        long expected = played(bob, carol, "WHITE_WIN", Instant.now().minus(30, ChronoUnit.MINUTES));
        assertThat(context(alice, expected).path("upset").asBoolean()).isFalse();
    }

    @Test
    void aPendingGameUsesCurrentRatingsAndHasNoMeetingNumberYet() {
        played(alice, bob, "WHITE_WIN", Instant.now().minus(2, ChronoUnit.DAYS));
        long pending = alice.api().post("/api/games", game(alice, bob, "DRAW")).json().path("id").asLong();
        JsonNode c = context(bob, pending);
        assertThat(c.path("expected").path("white").asDouble()).isGreaterThan(0.5);
        assertThat(c.path("meeting").isNull()).isTrue();
        assertThat(c.path("upset").asBoolean()).isFalse();
        assertThat(c.path("headToHead").path("games").asInt()).isEqualTo(1);
    }

    @Test
    void unknownGamesAndAnonymousVisitors() {
        assertThat(alice.api().get("/api/games/999999/context").code()).isEqualTo("game.not_found");
        long id = played(alice, bob, "DRAW", Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(session().get("/api/games/" + id + "/context").status()).isEqualTo(401);
    }
}
