package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** "Årets sjakk": a year in review per player and for the group. */
class YearReviewApiTest extends IntegrationTest {

    static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    Player admin;
    Player alice;
    Player bob;
    Player carol;
    Player dave;
    int last;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
        carol = registerMember(admin, "carol");
        dave = registerMember(admin, "dave");
        last = Year.now(OSLO).getValue() - 1;
    }

    private Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDate.of(year, month, day).atTime(hour, minute).atZone(OSLO).toInstant();
    }

    private long played(Player white, Player black, String result, Instant when, Map<String, Object> extra) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(game(white.id(), black.id(), result, when));
        body.putAll(extra);
        Response created = admin.api().post("/api/games", body);
        assertThat(created.status()).as("%s", created).isEqualTo(201);
        long id = created.json().path("id").asLong();
        assertThat(admin.api().post("/api/admin/games/" + id + "/resolve", Map.of("action", "CONFIRM")).status()).isEqualTo(200);
        return id;
    }

    private long played(Player white, Player black, String result, Instant when) {
        return played(white, black, result, when, Map.of());
    }

    private JsonNode get(Player viewer, String path) {
        Response r = viewer.api().get(path);
        assertThat(r.status()).as("%s", r).isEqualTo(200);
        return r.json();
    }

    /** A year with a dominant player, an upset, a lively game and an award. */
    private long story() {
        played(alice, bob, "WHITE_WIN", at(last, 3, 3, 18, 0), Map.of("opening", "Italian Game"));
        long lively = played(bob, alice, "BLACK_WIN", at(last, 3, 10, 18, 0),
                Map.of("opening", "italian game", "moves", "1. e4 e5 2. Nf3 Nc6 3. Bc4"));
        played(alice, bob, "WHITE_WIN", at(last, 3, 17, 18, 0));
        played(carol, alice, "WHITE_WIN", at(last, 4, 7, 18, 0));   // carol beats the in-form alice
        played(bob, carol, "DRAW", at(last, 5, 5, 18, 0));
        assertThat(carol.api().put("/api/games/" + lively + "/kudos", Map.of()).status()).isEqualTo(200);
        assertThat(dave.api().post("/api/games/" + lively + "/comments", Map.of("body", "What a finish")).status()).isEqualTo(201);
        return lively;
    }

    @Test
    void aPlayersYear() {
        story();
        JsonNode y = get(bob, "/api/year/" + last + "/players/" + alice.username());
        assertThat(y.path("year").asInt()).isEqualTo(last);
        assertThat(y.path("complete").asBoolean()).isTrue();
        assertThat(y.path("player").path("username").asString()).isEqualTo(alice.username());
        assertThat(y.path("games").asInt()).isEqualTo(4);
        assertThat(y.path("wins").asInt()).isEqualTo(3);
        assertThat(y.path("losses").asInt()).isEqualTo(1);
        assertThat(y.path("ratingStart").asInt()).isEqualTo(1500);
        assertThat(y.path("ratingEnd").asInt()).isEqualTo(rating(alice));
        assertThat(y.path("ratingPeak").asInt()).isGreaterThanOrEqualTo(y.path("ratingEnd").asInt());
        assertThat(y.path("ratingByMonth")).extracting(m -> m.path("month").asString()).containsExactly(last + "-03", last + "-04");
        assertThat(y.path("bestMonth").path("month").asString()).isEqualTo(last + "-03");
        assertThat(y.path("bestMonth").path("wins").asInt()).isEqualTo(3);
        assertThat(y.path("rival").path("opponent").path("id").asLong()).isEqualTo(bob.id());
        assertThat(y.path("rival").path("games").asInt()).isEqualTo(3);
        assertThat(y.path("longestWinStreak").path("length").asInt()).isEqualTo(3);
        assertThat(y.path("favoriteOpening").path("name").asString()).isEqualTo("Italian Game");
        assertThat(y.path("favoriteOpening").path("games").asInt()).isEqualTo(2);
        assertThat(y.path("gamesWithMoves").asInt()).isEqualTo(1);
        assertThat(y.path("mostKudos").path("kudos").asInt()).isEqualTo(1);
        assertThat(y.path("mostKudos").path("comments").asInt()).isEqualTo(1);
        assertThat(y.path("rankByGames").asInt()).isEqualTo(1);
        assertThat(y.path("players").asInt()).isEqualTo(3);
        assertThat(y.path("awards")).anyMatch(a -> a.path("month").asString().equals(last + "-03")
                && a.path("kind").asString().equals("PLAYER_OF_MONTH"));

        JsonNode c = get(alice, "/api/year/" + last + "/players/" + carol.username());
        assertThat(c.path("biggestUpset").path("loser").path("id").asLong()).isEqualTo(alice.id());
        assertThat(c.path("biggestUpset").path("expected").asDouble()).isLessThan(0.5);
        assertThat(c.path("favoriteOpening").isNull()).as("openings are optional").isTrue();
    }

    @Test
    void someoneWhoDidntPlayGetsAnEmptyYear() {
        story();
        JsonNode y = get(alice, "/api/year/" + last + "/players/" + dave.username());
        assertThat(y.path("games").asInt()).isZero();
        assertThat(y.path("rival").isNull()).isTrue();
        assertThat(y.path("ratingStart").isNull()).isTrue();
        assertThat(y.path("players").asInt()).isEqualTo(3);
        assertThat(y.path("awards")).isEmpty();
    }

    @Test
    void theGroupsYear() {
        long lively = story();
        JsonNode y = get(dave, "/api/year/" + last);
        assertThat(y.path("games").asInt()).isEqualTo(5);
        assertThat(y.path("players").asInt()).isEqualTo(3);
        assertThat(y.path("draws").asInt()).isEqualTo(1);
        assertThat(y.path("kudos").asInt()).isEqualTo(1);
        assertThat(y.path("comments").asInt()).isEqualTo(1);
        assertThat(y.path("gamesWithMoves").asInt()).isEqualTo(1);
        assertThat(y.path("busiestMonth").path("month").asString()).isEqualTo(last + "-03");
        assertThat(y.path("gameOfTheYear").path("game").path("id").asLong()).isEqualTo(lively);
        assertThat(y.path("gameOfTheYear").path("game").path("black").path("id").asLong()).isEqualTo(alice.id());
        assertThat(y.path("giantKiller").path("winner").path("id").asLong()).isEqualTo(carol.id());
        assertThat(y.path("giantKiller").path("expected").asDouble()).isLessThan(0.5);
        assertThat(y.path("awardLeaders").get(0).path("player").path("id").asLong()).isEqualTo(alice.id());
        assertThat(y.path("awardLeaders").get(0).path("playerOfMonth").asInt()).isEqualTo(1);
        assertThat(y.path("mostGames").get(0).path("player").path("id").asLong()).isEqualTo(alice.id());
        assertThat(y.path("mostGames").get(0).path("games").asInt()).isEqualTo(4);
        assertThat(y.path("topRivalry").path("a").path("id").asLong()).isEqualTo(alice.id());
        assertThat(y.path("topRivalry").path("aWins").asInt()).isEqualTo(3);
        assertThat(y.path("longestWinStreak").path("player").path("id").asLong()).isEqualTo(alice.id());
    }

    @Test
    void yearsFollowNorwegianTimeAndOnlyConfirmedGamesCount() {
        played(alice, bob, "WHITE_WIN", at(last, 12, 31, 23, 30));
        played(alice, bob, "WHITE_WIN", at(last + 1, 1, 1, 0, 30));      // still Dec 31 in UTC
        long voided = played(alice, bob, "WHITE_WIN", at(last, 6, 1, 18, 0));
        admin.api().post("/api/admin/games/" + voided + "/resolve", Map.of("action", "VOID"));
        alice.api().post("/api/games", game(alice.id(), bob.id(), "WHITE_WIN", at(last, 6, 2, 18, 0)));   // pending

        assertThat(get(bob, "/api/year/" + last + "/players/" + alice.username()).path("games").asInt()).isEqualTo(1);
        assertThat(get(bob, "/api/year/" + (last + 1) + "/players/" + alice.username()).path("games").asInt()).isEqualTo(1);
        assertThat(get(bob, "/api/year/" + last).path("games").asInt()).isEqualTo(1);
        JsonNode current = get(bob, "/api/year/" + (last + 1));
        assertThat(current.path("complete").asBoolean()).as("this year isn't over").isFalse();
    }

    @Test
    void anEmptyYear() {
        JsonNode y = get(alice, "/api/year/" + last);
        assertThat(y.path("games").asInt()).isZero();
        assertThat(y.path("gameOfTheYear").isNull()).isTrue();
        assertThat(y.path("mostGames")).isEmpty();
    }

    @Test
    void rejectsYearsOutOfRangeAndUnknownPlayers() {
        for (String bad : List.of("1999", String.valueOf(last + 2), "-1")) {
            Response r = alice.api().get("/api/year/" + bad);
            assertThat(r.status()).as(bad).isEqualTo(400);
            assertThat(r.json().path("code").asString()).as(bad).isEqualTo("year.bad_year");
        }
        Response notANumber = alice.api().get("/api/year/abc");
        assertThat(notANumber.status()).isEqualTo(400);
        assertThat(notANumber.json().path("code").asString()).isEqualTo("request.malformed");
        Response r = alice.api().get("/api/year/" + last + "/players/nobody-here");
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.json().path("code").asString()).isEqualTo("player.not_found");
        assertThat(alice.api().get("/api/year/" + last + "/players/" + bob.username().toUpperCase()).status())
                .as("usernames are case-insensitive").isEqualTo(200);
    }

    @Test
    void needsYouToBeLoggedIn() {
        assertThat(session().get("/api/year/" + last).status()).isEqualTo(401);
        assertThat(session().get("/api/year/" + last + "/players/" + alice.username()).status()).isEqualTo(401);
    }
}
