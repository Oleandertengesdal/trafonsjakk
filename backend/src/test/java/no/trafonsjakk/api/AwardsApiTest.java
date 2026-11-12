package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import no.trafonsjakk.support.Race;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** "Månedens spiller": monthly awards for beating your rating's expectations and for playing the most. */
class AwardsApiTest extends IntegrationTest {

    static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    Player admin;
    Player alice;
    Player bob;
    Player carol;
    Player dave;

    /** Two months ago: long since decided. */
    YearMonth m2;
    YearMonth m3;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
        carol = registerMember(admin, "carol");
        dave = registerMember(admin, "dave");
        YearMonth now = YearMonth.now(OSLO);
        m2 = now.minusMonths(2);
        m3 = now.minusMonths(3);
    }

    private static Instant at(YearMonth m, int day, int hour, int minute) {
        return m.atDay(day).atTime(hour, minute).atZone(OSLO).toInstant();
    }

    private long played(Player white, Player black, String result, Instant playedAt) {
        Response created = admin.api().post("/api/games", game(white.id(), black.id(), result, playedAt));
        assertThat(created.status()).as("%s", created).isEqualTo(201);
        long id = created.json().path("id").asLong();
        Response ok = admin.api().post("/api/admin/games/" + id + "/resolve", Map.of("action", "CONFIRM"));
        assertThat(ok.status()).as("%s", ok).isEqualTo(200);
        return id;
    }

    private JsonNode race(Player viewer, YearMonth month) {
        Response r = viewer.api().get("/api/awards/race" + (month == null ? "" : "?month=" + month));
        assertThat(r.status()).as("%s", r).isEqualTo(200);
        return r.json();
    }

    private JsonNode awards(Player viewer, String query) {
        Response r = viewer.api().get("/api/awards" + query);
        assertThat(r.status()).as("%s", r).isEqualTo(200);
        return r.json();
    }

    private static JsonNode standing(JsonNode list, Player p) {
        for (JsonNode s : list) {
            if (s.path("player").path("id").asLong() == p.id()) {
                return s;
            }
        }
        throw new AssertionError(p.username() + " not in " + list);
    }

    @Test
    void theUnderdogWhoBeatsExpectationsWinsNotTheHighestRated() {
        // Month 3: alice gets strong, bob weak
        for (int d = 1; d <= 4; d++) {
            played(alice, bob, "WHITE_WIN", at(m3, d, 18, 0));
        }
        played(alice, carol, "WHITE_WIN", at(m3, 10, 18, 0));
        // Month 2: bob beats alice three times; carol and dave draw; dave plays the most
        played(bob, alice, "WHITE_WIN", at(m2, 3, 18, 0));
        played(alice, bob, "BLACK_WIN", at(m2, 5, 18, 0));
        played(bob, alice, "WHITE_WIN", at(m2, 7, 18, 0));
        played(carol, dave, "DRAW", at(m2, 8, 18, 0));
        played(dave, carol, "DRAW", at(m2, 9, 18, 0));
        played(carol, dave, "DRAW", at(m2, 10, 18, 0));
        played(dave, admin, "WHITE_WIN", at(m2, 11, 18, 0));

        JsonNode r = race(carol, m2);
        assertThat(r.path("month").asString()).isEqualTo(m2.toString());
        assertThat(r.path("status").asString()).isEqualTo("DECIDED");
        assertThat(r.path("minGames").asInt()).isEqualTo(3);

        JsonNode pom = r.path("playerOfMonth");
        assertThat(pom.path("winner").path("player").path("id").asLong()).isEqualTo(bob.id());
        JsonNode list = pom.path("standings");
        assertThat(list.get(0).path("player").path("id").asLong()).isEqualTo(bob.id());
        JsonNode b = standing(list, bob);
        assertThat(b.path("games").asInt()).isEqualTo(3);
        assertThat(b.path("wins").asInt()).isEqualTo(3);
        assertThat(b.path("points").asDouble()).isEqualTo(3.0);
        assertThat(b.path("overExpected").asDouble()).as("three upsets").isGreaterThan(1.2);
        assertThat(b.path("ratingChange").asInt()).isPositive();
        assertThat(b.path("qualified").asBoolean()).isTrue();
        assertThat(standing(list, alice).path("overExpected").asDouble()).isNegative();

        double sum = 0;
        for (JsonNode s : list) {
            assertThat(s.path("overExpected").asDouble())
                    .isCloseTo(s.path("points").asDouble() - s.path("expected").asDouble(), within(1e-6));
            sum += s.path("overExpected").asDouble();
        }
        assertThat(sum).as("everyone's points over expectation add up to zero").isCloseTo(0, within(1e-6));
        assertThat(standing(list, admin).path("qualified").asBoolean()).as("one game").isFalse();

        JsonNode active = r.path("mostActive");
        assertThat(active.path("winner").path("player").path("id").asLong()).isEqualTo(dave.id());
        assertThat(active.path("winner").path("games").asInt()).isEqualTo(4);
        assertThat(active.path("standings").get(0).path("player").path("id").asLong()).isEqualTo(dave.id());
    }

    @Test
    void monthsFollowNorwegianTime() {
        YearMonth m1 = m2.plusMonths(1);
        Instant lastMinute = at(m2, m2.lengthOfMonth(), 23, 30);
        Instant firstMinute = at(m1, 1, 0, 30); // still the previous day in UTC
        played(alice, bob, "WHITE_WIN", lastMinute);
        played(alice, bob, "WHITE_WIN", firstMinute);

        assertThat(standing(race(alice, m2).path("playerOfMonth").path("standings"), alice).path("games").asInt()).isEqualTo(1);
        assertThat(standing(race(alice, m1).path("playerOfMonth").path("standings"), alice).path("games").asInt()).isEqualTo(1);
    }

    @Test
    void onlyConfirmedGamesCount() {
        played(alice, bob, "WHITE_WIN", at(m2, 2, 18, 0));
        long voided = played(alice, bob, "WHITE_WIN", at(m2, 3, 18, 0));
        admin.api().post("/api/admin/games/" + voided + "/resolve", Map.of("action", "VOID"));
        Response pending = alice.api().post("/api/games", game(alice.id(), bob.id(), "WHITE_WIN", at(m2, 4, 18, 0)));
        assertThat(pending.status()).isEqualTo(201);

        JsonNode list = race(carol, m2).path("playerOfMonth").path("standings");
        assertThat(standing(list, alice).path("games").asInt()).isEqualTo(1);
        assertThat(list).hasSize(2);
    }

    @Test
    void aDecidedMonthIsFrozenAndShowsOnTheWinnersProfile() {
        for (int d = 1; d <= 3; d++) {
            played(alice, bob, "WHITE_WIN", at(m2, d, 18, 0));
        }
        JsonNode all = awards(carol, "");
        assertThat(all).hasSize(2);
        for (JsonNode a : all) {
            assertThat(a.path("month").asString()).isEqualTo(m2.toString());
            assertThat(a.path("player").path("id").asLong()).isEqualTo(alice.id());
            assertThat(a.path("games").asInt()).isEqualTo(3);
            assertThat(a.path("decidedAt").asString()).isNotBlank();
        }
        assertThat(all).extracting(a -> a.path("kind").asString()).containsExactlyInAnyOrder("PLAYER_OF_MONTH", "MOST_ACTIVE");

        // A late game for that month changes the live standings, not the award
        for (int d = 10; d <= 14; d++) {
            played(bob, alice, "WHITE_WIN", at(m2, d, 18, 0));
        }
        JsonNode r = race(carol, m2);
        assertThat(r.path("playerOfMonth").path("standings").get(0).path("player").path("id").asLong()).isEqualTo(bob.id());
        assertThat(r.path("playerOfMonth").path("winner").path("player").path("id").asLong()).isEqualTo(alice.id());
        assertThat(awards(carol, "")).hasSize(2).allMatch(a -> a.path("player").path("id").asLong() == alice.id());

        assertThat(awards(bob, "?player=" + alice.username())).hasSize(2);
        assertThat(awards(alice, "?player=" + bob.username())).isEmpty();
        Response unknown = alice.api().get("/api/awards?player=nobody-here");
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.json().path("code").asString()).isEqualTo("player.not_found");
    }

    @Test
    void awardsAreListedNewestFirstAcrossMonths() {
        for (int d = 1; d <= 3; d++) {
            played(bob, carol, "WHITE_WIN", at(m3, d, 18, 0));
            played(alice, dave, "WHITE_WIN", at(m2, d, 18, 0));
        }
        JsonNode all = awards(alice, "");
        assertThat(all).hasSize(4);
        assertThat(all.get(0).path("month").asString()).isEqualTo(m2.toString());
        assertThat(all.get(3).path("month").asString()).isEqualTo(m3.toString());
    }

    @Test
    void nobodyWinsWhenNobodyPlayedEnough() {
        played(alice, bob, "WHITE_WIN", at(m2, 2, 18, 0));
        played(alice, bob, "WHITE_WIN", at(m2, 3, 18, 0));
        JsonNode r = race(alice, m2);
        assertThat(r.path("playerOfMonth").path("winner").isNull()).isTrue();
        assertThat(r.path("mostActive").path("winner").isNull()).isTrue();
        assertThat(r.path("playerOfMonth").path("standings")).hasSize(2).allMatch(s -> !s.path("qualified").asBoolean());
        assertThat(awards(alice, "")).isEmpty();
    }

    @Test
    void theCurrentMonthIsLiveUntilItEnds() {
        Instant justNow = Instant.now().minusSeconds(60);
        YearMonth current = YearMonth.now(OSLO);
        boolean sameMonth = YearMonth.from(justNow.atZone(OSLO)).equals(current);
        if (sameMonth) {
            played(alice, bob, "DRAW", justNow);
        }
        JsonNode r = race(bob, null);
        assertThat(r.path("month").asString()).isEqualTo(current.toString());
        assertThat(r.path("status").asString()).isEqualTo("LIVE");
        Instant ends = current.plusMonths(1).atDay(1).atStartOfDay(OSLO).toInstant();
        assertThat(Instant.parse(r.path("endsAt").asString())).isEqualTo(ends);
        assertThat(Instant.parse(r.path("decidesAt").asString())).isEqualTo(ends.plus(Duration.ofDays(3)));
        assertThat(r.path("playerOfMonth").path("winner").isNull()).isTrue();
        if (sameMonth) {
            assertThat(r.path("playerOfMonth").path("standings")).hasSize(2);
        }
        assertThat(awards(bob, "")).as("nothing decided yet").isEmpty();
    }

    @Test
    void rejectsMonthsThatAreMalformedOrInTheFuture() {
        for (String bad : List.of("2026-13", "abc", "2026-1", "%27--", YearMonth.now(OSLO).plusMonths(1).toString(), "1999-12")) {
            Response r = alice.api().get("/api/awards/race?month=" + bad);
            assertThat(r.status()).as(bad).isEqualTo(400);
            assertThat(r.json().path("code").asString()).as(bad).isEqualTo("award.bad_month");
        }
    }

    @Test
    void simultaneousReadsDecideEachMonthOnce() {
        for (int d = 1; d <= 3; d++) {
            played(alice, bob, "WHITE_WIN", at(m2, d, 18, 0));
        }
        List<Supplier<Response>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Player p = i % 2 == 0 ? alice : bob;
            calls.add(i % 3 == 0 ? () -> p.api().get("/api/awards/race?month=" + m2) : () -> p.api().get("/api/awards"));
        }
        @SuppressWarnings("unchecked")
        List<Response> rs = Race.run(calls.toArray(new Supplier[0]));
        assertThat(Race.count(rs, 200)).isEqualTo(8);
        for (Response r : rs) {
            JsonNode body = r.json();
            if (body.isArray()) {
                assertThat(body).as("every reader sees the decided month").hasSize(2);
            }
        }
        Integer rows = jdbc.queryForObject("select count(*) from monthly_awards where month = ?", Integer.class, m2.atDay(1));
        assertThat(rows).isEqualTo(2);
    }

    @Test
    void needsYouToBeLoggedIn() {
        assertThat(session().get("/api/awards").status()).isEqualTo(401);
        assertThat(session().get("/api/awards/race").status()).isEqualTo(401);
    }
}
