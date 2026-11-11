package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Ratings, leaderboard and statistics as the app shows them. */
class RatingStatsApiTest extends IntegrationTest {

    Player admin;
    Player alice;
    Player bob;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
    }

    /** Admin logs and confirms a game between two players, at a given time. */
    private long played(long white, long black, String result, Instant at) {
        long id = admin.api().post("/api/games", game(white, black, result, at)).json().path("id").asLong();
        assertThat(admin.api().post("/api/admin/games/" + id + "/resolve", Map.of("action", "CONFIRM")).status()).isEqualTo(200);
        return id;
    }

    private JsonNode row(long playerId) {
        for (JsonNode r : alice.api().get("/api/leaderboard").json()) {
            if (r.path("player").path("id").asLong() == playerId) {
                return r;
            }
        }
        return null;
    }

    @Test
    void aNewPlayersStatsAreValidJsonWithZeros() {
        Response r = alice.api().get("/api/users/" + alice.username() + "/stats");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).doesNotContain("NaN").doesNotContain("Infinity");
        assertThat(r.json()).as("parsable JSON").isNotNull();
        assertThat(r.json().path("totals").path("games").asInt()).isZero();
        assertThat(r.json().path("totals").path("winRate").asDouble()).isZero();
        assertThat(r.json().path("white").path("scorePct").asDouble()).isZero();
        assertThat(r.json().path("ratingHistory")).isEmpty();
        // Only draws, only as black: still no division by zero anywhere
        played(alice.id(), bob.id(), "DRAW", Instant.now().minus(1, ChronoUnit.HOURS));
        Response bobStats = bob.api().get("/api/users/" + bob.username() + "/stats");
        assertThat(bobStats.body()).doesNotContain("NaN").doesNotContain("Infinity");
        assertThat(bobStats.json().path("totals").path("scorePct").asDouble()).isEqualTo(0.5);
    }

    @Test
    void provisionalPlayersAreListedButNotRanked() {
        List<Long> regulars = bulkUsers(3, "regular");
        Instant t = Instant.now().minus(20, ChronoUnit.DAYS);
        // Three evenly matched regulars play a lot, so their rating deviation drops below the provisional
        // threshold. (Lopsided results would not: beating much weaker players says little about you.)
        // The first regular wins slightly more often, so ends up on top.
        String[] cycle = {"WHITE_WIN", "BLACK_WIN", "DRAW", "WHITE_WIN"};
        int k = 0;
        for (int i = 0; i < 12; i++) {
            for (int a = 0; a < 3; a++) {
                for (int b = a + 1; b < 3; b++) {
                    played(regulars.get(a), regulars.get(b), cycle[(i + a + b) % cycle.length], t.plus(k++, ChronoUnit.MINUTES));
                }
            }
        }
        played(alice.id(), bob.id(), "WHITE_WIN", Instant.now().minus(1, ChronoUnit.HOURS));

        JsonNode board = alice.api().get("/api/leaderboard").json();
        int ranked = 0;
        boolean seenProvisional = false;
        double previous = Double.MAX_VALUE;
        for (JsonNode r : board) {
            boolean provisional = r.path("player").path("provisional").asBoolean();
            if (provisional) {
                seenProvisional = true;
                assertThat(r.path("rank").isNull()).isTrue();
            } else {
                assertThat(seenProvisional).as("ranked players come first").isFalse();
                assertThat(r.path("rank").asInt()).isEqualTo(++ranked);
                assertThat(r.path("player").path("rating").asDouble()).isLessThanOrEqualTo(previous);
                previous = r.path("player").path("rating").asDouble();
            }
            assertThat(r.path("wins").asInt() + r.path("draws").asInt() + r.path("losses").asInt()).isEqualTo(r.path("games").asInt());
        }
        assertThat(ranked).isEqualTo(3);
        // The profile shows the same rank as the leaderboard
        for (Long id : regulars) {
            String name = jdbc.queryForObject("select username from users where id = ?", String.class, id);
            assertThat(alice.api().get("/api/users/" + name).json().path("rank").asInt()).isEqualTo(row(id).path("rank").asInt());
        }
        assertThat(alice.api().get("/api/users/" + alice.username()).json().path("rank").isNull()).isTrue();
    }

    @Test
    void disabledPlayersLeaveTheLeaderboardButTheirGamesStay() {
        played(alice.id(), bob.id(), "WHITE_WIN", Instant.now().minus(2, ChronoUnit.HOURS));
        admin.api().patch("/api/admin/users/" + bob.id(), Map.of("enabled", false));
        assertThat(row(bob.id())).isNull();
        assertThat(row(alice.id()).path("games").asInt()).isEqualTo(1);
        assertThat(alice.api().get("/api/users/" + alice.username() + "/stats").json().path("totals").path("wins").asInt()).isEqualTo(1);
    }

    @Test
    void onlyConfirmedGamesCount() {
        played(alice.id(), bob.id(), "WHITE_WIN", Instant.now().minus(3, ChronoUnit.HOURS));
        alice.api().post("/api/games", game(alice, bob, "WHITE_WIN")); // pending
        long disputed = alice.api().post("/api/games", game(alice, bob, "WHITE_WIN")).json().path("id").asLong();
        bob.api().post("/api/games/" + disputed + "/dispute", Map.of("reason", "Never happened"));
        long voided = played(alice.id(), bob.id(), "WHITE_WIN", Instant.now().minus(2, ChronoUnit.HOURS));
        admin.api().post("/api/admin/games/" + voided + "/resolve", Map.of("action", "VOID"));

        assertThat(row(alice.id()).path("games").asInt()).isEqualTo(1);
        JsonNode stats = alice.api().get("/api/users/" + alice.username() + "/stats").json();
        assertThat(stats.path("totals").path("games").asInt()).isEqualTo(1);
        assertThat(stats.path("ratingHistory")).hasSize(1);
        assertThat(alice.api().get("/api/users/" + alice.username()).json().path("ratedGames").asInt()).isEqualTo(1);
    }

    @Test
    void peakRatingCountsOnlyEstablishedRatingsAndFollowsCorrections() {
        long win = played(alice.id(), bob.id(), "WHITE_WIN", Instant.now().minus(30, ChronoUnit.DAYS));
        assertThat(rating(alice)).isGreaterThan(1500);
        assertThat(alice.api().get("/api/users/" + alice.username()).json().path("peakRating").asInt())
                .as("a provisional high doesn't count as a peak").isEqualTo(1500);

        // Enough even games to become established
        Instant t = Instant.now().minus(29, ChronoUnit.DAYS);
        for (int i = 0; i < 30; i++) {
            played(alice.id(), bob.id(), i % 3 == 0 ? "DRAW" : i % 2 == 0 ? "WHITE_WIN" : "BLACK_WIN", t.plus(i, ChronoUnit.HOURS));
        }
        JsonNode p = alice.api().get("/api/users/" + alice.username()).json();
        assertThat(p.path("provisional").asBoolean()).isFalse();
        int peak = p.path("peakRating").asInt();
        assertThat(peak).isGreaterThanOrEqualTo(p.path("rating").asInt());
        // Win until a new peak is set; that last win is the peak game
        long peakWin = 0;
        int newPeak = peak;
        for (int i = 0; i < 10 && newPeak == peak; i++) {
            peakWin = played(alice.id(), bob.id(), "WHITE_WIN", Instant.now().minus(3, ChronoUnit.HOURS).plus(i, ChronoUnit.MINUTES));
            newPeak = alice.api().get("/api/users/" + alice.username()).json().path("peakRating").asInt();
        }
        assertThat(newPeak).isGreaterThan(peak);
        assertThat(newPeak).isEqualTo(rating(alice));
        played(alice.id(), bob.id(), "BLACK_WIN", Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(alice.api().get("/api/users/" + alice.username()).json().path("peakRating").asInt())
                .as("peak survives a later loss").isEqualTo(newPeak);

        admin.api().post("/api/admin/games/" + peakWin + "/resolve", Map.of("action", "VOID"));
        assertThat(alice.api().get("/api/users/" + alice.username()).json().path("peakRating").asInt())
                .as("a voided win no longer counts").isLessThan(newPeak);
        admin.api().post("/api/admin/games/" + win + "/resolve", Map.of("action", "VOID"));
        assertThat(jdbc.queryForObject("select count(*) from rating_history where user_id = ?", Integer.class, alice.id()))
                .isEqualTo(alice.api().get("/api/users/" + alice.username()).json().path("ratedGames").asInt());
    }

    @Test
    void ratingsBecomeLessCertainAfterABreak() {
        Instant longAgo = Instant.now().minus(200, ChronoUnit.DAYS);
        for (int i = 0; i < 6; i++) {
            played(alice.id(), bob.id(), i % 2 == 0 ? "WHITE_WIN" : "BLACK_WIN", longAgo.plus(i, ChronoUnit.HOURS));
        }
        Map<String, Object> last = jdbc.queryForMap("select rd_after, volatility_after, played_at from rating_history "
                + "where user_id = ? order by played_at desc limit 1", alice.id());
        long recent = played(alice.id(), bob.id(), "DRAW", Instant.now().minus(1, ChronoUnit.HOURS));
        Map<String, Object> next = jdbc.queryForMap("select rd_before, played_at from rating_history where user_id = ? and game_id = ?",
                alice.id(), recent);

        double rdAfter = ((Number) last.get("rd_after")).doubleValue();
        double sigma = ((Number) last.get("volatility_after")).doubleValue();
        double days = java.time.Duration.between(((java.sql.Timestamp) last.get("played_at")).toInstant(),
                ((java.sql.Timestamp) next.get("played_at")).toInstant()).toMinutes() / 1440.0;
        // Glicko-2: φ* = sqrt(φ² + σ²·t), t in 7-day rating periods, RD = 173.7178·φ
        double phi = rdAfter / 173.7178;
        double expected = Math.sqrt(phi * phi + sigma * sigma * days / 7.0) * 173.7178;
        double rdBefore = ((Number) next.get("rd_before")).doubleValue();
        assertThat(rdBefore).as("a long break widens the rating deviation").isGreaterThan(rdAfter);
        assertThat(rdBefore).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void ratingDeviationNeverExceedsTheStartingValue() {
        played(alice.id(), bob.id(), "DRAW", Instant.parse("2001-01-01T00:00:00Z"));
        long id = played(alice.id(), bob.id(), "DRAW", Instant.now().minus(1, ChronoUnit.HOURS));
        double rdBefore = jdbc.queryForObject("select rd_before from rating_history where user_id = ? and game_id = ?",
                Double.class, alice.id(), id);
        assertThat(rdBefore).as("25 years away caps at 350, the starting RD").isEqualTo(350.0);
    }

    @Test
    void leaderboardTrendAndLastPlayed() {
        Instant t = Instant.now().minus(10, ChronoUnit.DAYS);
        long lastId = 0;
        for (int i = 0; i < 7; i++) {
            lastId = played(alice.id(), bob.id(), "WHITE_WIN", t.plus(i, ChronoUnit.DAYS));
        }
        JsonNode r = row(alice.id());
        double from = jdbc.queryForObject("select rating_before from rating_history where user_id = ? order by played_at limit 1 offset 2",
                Double.class, alice.id());
        double to = jdbc.queryForObject("select rating_after from rating_history where user_id = ? order by played_at desc limit 1",
                Double.class, alice.id());
        assertThat(r.path("trend").asInt()).as("change over the last five games").isEqualTo((int) Math.round(to - from));
        assertThat(r.path("lastPlayedAt").asString()).as("the newest game's time, not when it was logged")
                .isEqualTo(alice.api().get("/api/games/" + lastId).json().path("playedAt").asString());
        assertThat(row(bob.id()).path("trend").asInt()).isNegative();
    }

    @Test
    void headToHeadAndColoursAreConsistent() {
        Player carol = registerMember(admin, "carol");
        Instant t = Instant.now().minus(5, ChronoUnit.DAYS);
        played(alice.id(), bob.id(), "WHITE_WIN", t);
        played(bob.id(), alice.id(), "WHITE_WIN", t.plusSeconds(60));
        played(alice.id(), carol.id(), "DRAW", t.plusSeconds(120));
        played(carol.id(), alice.id(), "WHITE_WIN", t.plusSeconds(180));
        JsonNode s = alice.api().get("/api/users/" + alice.username() + "/stats").json();
        assertThat(s.path("white").path("games").asInt() + s.path("black").path("games").asInt()).isEqualTo(4);
        assertThat(s.path("white").path("wins").asInt()).isEqualTo(1);
        assertThat(s.path("black").path("losses").asInt()).isEqualTo(2);
        int h2hGames = 0;
        for (JsonNode h : s.path("headToHead")) {
            h2hGames += h.path("games").asInt();
            assertThat(h.path("scorePct").asDouble()).isBetween(0.0, 1.0);
        }
        assertThat(h2hGames).isEqualTo(4);
        assertThat(s.path("nemesis").path("opponent").path("id").asLong()).isIn(bob.id(), carol.id());
        assertThat(s.path("streaks").path("current").path("type").asString()).isEqualTo("LOSS");
    }
}
