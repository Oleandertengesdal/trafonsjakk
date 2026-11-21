package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.analysis.GameAccuracy;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Engine analysis saved on the server: shared, validated, forgotten when the moves change, and the statistics on it. */
class AnalysisApiTest extends IntegrationTest {

    /** 24 half-moves: 12 each. Legality isn't the server's business; it only counts them. */
    static final String MOVES = "1. Nf3 Nf6 2. Ng1 Ng8 3. Nf3 Nf6 4. Ng1 Ng8 5. Nc3 Nc6 6. Nb1 Nb8 "
            + "7. Nf3 Nf6 8. Ng1 Ng8 9. Nc3 Nc6 10. Nb1 Nb8 11. e4 e5 12. d4 d5";
    static final int PLIES = 24;

    Player admin;
    Player alice;
    Player bob;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
    }

    private long gameWithMoves(Player white, Player black, String moves, boolean confirmed, Instant playedAt) {
        Map<String, Object> g = game(white.id(), black.id(), "WHITE_WIN", playedAt);
        g.put("moves", moves);
        if (confirmed) {
            return playConfirmed(white, black, g).path("id").asLong();
        }
        Response r = white.api().post("/api/games", g);
        assertThat(r.status()).isEqualTo(201);
        return r.json().path("id").asLong();
    }

    private long gameWithMoves() {
        return gameWithMoves(alice, bob, MOVES, true, Instant.now().minus(1, ChronoUnit.HOURS));
    }

    /** White slowly getting better, with a slip by black at {@code slip}. */
    static List<Map<String, Object>> evals(int plies, int slip) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i <= plies; i++) {
            Map<String, Object> e = new HashMap<>();
            e.put("score", Map.of("cp", 20 + 10 * i + (i >= slip ? 300 : 0)));
            e.put("best", i % 2 == 0 ? "e2e4" : null);
            out.add(e);
        }
        return out;
    }

    static GameAccuracy.Result expected(int plies, int slip) {
        List<GameAccuracy.Score> scores = new ArrayList<>();
        for (int i = 0; i <= plies; i++) {
            scores.add(GameAccuracy.Score.cp(20 + 10 * i + (i >= slip ? 300 : 0)));
        }
        return GameAccuracy.compute(scores);
    }

    static Map<String, Object> body(List<Map<String, Object>> evals) {
        return Map.of("depth", 14, "evals", evals);
    }

    @Test
    void theFirstAnalysisIsSavedAndEveryoneGetsIt() {
        long id = gameWithMoves();
        Response none = bob.api().get("/api/games/" + id + "/analysis");
        assertThat(none.status()).isEqualTo(404);
        assertThat(none.code()).isEqualTo("analysis.not_found");

        Response saved = alice.api().post("/api/games/" + id + "/analysis", body(evals(PLIES, 9)));
        assertThat(saved.status()).as("%s", saved).isEqualTo(200);
        GameAccuracy.Result want = expected(PLIES, 9);
        assertThat(saved.json().path("white").path("moves").asInt()).isEqualTo(12);
        assertThat(saved.json().path("white").path("accuracy").asDouble()).isCloseTo(want.white().accuracy(), within(1e-9));
        assertThat(saved.json().path("black").path("accuracy").asDouble()).isCloseTo(want.black().accuracy(), within(1e-9));
        assertThat(saved.json().path("black").path("accuracy").asDouble()).isLessThan(saved.json().path("white").path("accuracy").asDouble());

        // Someone else analysing at the same time doesn't replace it
        Response second = bob.api().post("/api/games/" + id + "/analysis", body(evals(PLIES, 99)));
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.json().path("black").path("accuracy").asDouble()).isCloseTo(want.black().accuracy(), within(1e-9));

        JsonNode got = bob.api().get("/api/games/" + id + "/analysis").json();
        assertThat(got.path("depth").asInt()).isEqualTo(14);
        assertThat(got.path("evals")).hasSize(PLIES + 1);
        // The same shape the website uses: only the fields that apply
        JsonNode first = got.path("evals").get(0);
        assertThat(first.path("score").has("mate")).isFalse();
        assertThat(first.path("score").path("cp").asInt()).isEqualTo(20);
        assertThat(first.path("best").asString()).isEqualTo("e2e4");
        assertThat(got.path("evals").get(1).has("best")).isTrue();
        assertThat(got.path("evals").get(1).path("best").isNull()).isTrue();
    }

    @Test
    void matesAreKeptAsMates() {
        long id = gameWithMoves();
        List<Map<String, Object>> evals = evals(PLIES, 99);
        evals.get(PLIES).put("score", Map.of("mate", 0, "by", "w"));
        evals.get(PLIES).put("best", null);
        evals.get(PLIES - 1).put("score", Map.of("mate", 1, "by", "w"));
        assertThat(alice.api().post("/api/games/" + id + "/analysis", body(evals)).status()).isEqualTo(200);
        JsonNode last = alice.api().get("/api/games/" + id + "/analysis").json().path("evals").get(PLIES).path("score");
        assertThat(last.path("mate").asInt()).isZero();
        assertThat(last.path("by").asString()).isEqualTo("w");
        assertThat(last.has("cp")).isFalse();
    }

    @Test
    void refusesAnythingThatIsntAnAnalysisOfThisGame() {
        long id = gameWithMoves();
        String url = "/api/games/" + id + "/analysis";
        assertThat(alice.api().post(url, body(evals(PLIES - 1, 99))).code()).isEqualTo("analysis.wrong_length");
        assertThat(alice.api().post(url, body(evals(PLIES + 1, 99))).code()).isEqualTo("analysis.wrong_length");
        assertThat(alice.api().post(url, Map.of("evals", evals(PLIES, 99))).code()).isEqualTo("analysis.invalid");
        assertThat(alice.api().post(url, Map.of("depth", 0, "evals", evals(PLIES, 99))).code()).isEqualTo("analysis.invalid");

        List<Map<String, Object>> both = evals(PLIES, 99);
        both.get(3).put("score", Map.of("cp", 10, "mate", 2, "by", "w"));
        assertThat(alice.api().post(url, body(both)).code()).isEqualTo("analysis.invalid");
        List<Map<String, Object>> badSide = evals(PLIES, 99);
        badSide.get(3).put("score", Map.of("mate", 2, "by", "x"));
        assertThat(alice.api().post(url, body(badSide)).code()).isEqualTo("analysis.invalid");
        List<Map<String, Object>> huge = evals(PLIES, 99);
        huge.get(3).put("score", Map.of("cp", 1_000_000));
        assertThat(alice.api().post(url, body(huge)).code()).isEqualTo("analysis.invalid");
        List<Map<String, Object>> badMove = evals(PLIES, 99);
        badMove.get(3).put("best", "<script>");
        Response r = alice.api().post(url, body(badMove));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("analysis.invalid");
        List<Map<String, Object>> noScore = evals(PLIES, 99);
        noScore.get(3).remove("score");
        assertThat(alice.api().post(url, body(noScore)).code()).isEqualTo("analysis.invalid");
        assertThat(alice.api().get(url).status()).as("nothing was saved").isEqualTo(404);

        long noMoves = playConfirmed(alice, bob, game(alice, bob, "DRAW")).path("id").asLong();
        Response none = alice.api().post("/api/games/" + noMoves + "/analysis", body(evals(0, 99)));
        assertThat(none.status()).isEqualTo(409);
        assertThat(none.code()).isEqualTo("analysis.no_moves");
        assertThat(alice.api().post("/api/games/999999/analysis", body(evals(PLIES, 99))).code()).isEqualTo("game.not_found");
    }

    @Test
    void changingTheMovesForgetsTheAnalysis() {
        long id = gameWithMoves();
        assertThat(alice.api().post("/api/games/" + id + "/analysis", body(evals(PLIES, 9))).status()).isEqualTo(200);
        // Saving the same moves again keeps it
        assertThat(alice.api().patch("/api/games/" + id + "/moves", Map.of("moves", MOVES)).status()).isEqualTo(200);
        assertThat(alice.api().get("/api/games/" + id + "/analysis").status()).isEqualTo(200);

        assertThat(alice.api().patch("/api/games/" + id + "/moves", Map.of("moves", MOVES + " 13. Nf3")).status()).isEqualTo(200);
        assertThat(alice.api().get("/api/games/" + id + "/analysis").status()).isEqualTo(404);
        assertThat(alice.api().post("/api/games/" + id + "/analysis", body(evals(PLIES + 1, 9))).status()).isEqualTo(200);
    }

    @Test
    void anAdminCanForgetAnAnalysis() {
        long id = gameWithMoves();
        alice.api().post("/api/games/" + id + "/analysis", body(evals(PLIES, 9)));
        assertThat(alice.api().delete("/api/admin/games/" + id + "/analysis").status()).isEqualTo(403);
        assertThat(admin.api().delete("/api/admin/games/" + id + "/analysis").status()).isEqualTo(204);
        assertThat(alice.api().get("/api/games/" + id + "/analysis").status()).isEqualTo(404);
    }

    @Test
    void profilesShowAverageAccuracyAndTheBestGame() {
        JsonNode empty = alice.api().get("/api/users/" + alice.username() + "/accuracy").json();
        assertThat(empty.path("games").asInt()).isZero();
        assertThat(empty.path("average").isNull()).isTrue();

        long older = gameWithMoves(alice, bob, MOVES, true, Instant.now().minus(3, ChronoUnit.DAYS));
        long newer = gameWithMoves(bob, alice, MOVES, true, Instant.now().minus(2, ChronoUnit.DAYS));
        long pending = gameWithMoves(alice, bob, MOVES, false, Instant.now().minus(1, ChronoUnit.DAYS));
        long shortGame = gameWithMoves(alice, bob, "1. e4 e5 2. Nf3 Nc6", true, Instant.now().minus(1, ChronoUnit.DAYS));
        alice.api().post("/api/games/" + older + "/analysis", body(evals(PLIES, 99)));
        alice.api().post("/api/games/" + newer + "/analysis", body(evals(PLIES, 9)));
        alice.api().post("/api/games/" + pending + "/analysis", body(evals(PLIES, 9)));
        alice.api().post("/api/games/" + shortGame + "/analysis", body(evals(4, 99)));

        // Alice was white in the older game (no slip), black in the newer one (the slip was hers)
        double clean = expected(PLIES, 99).white().accuracy();
        double slipped = expected(PLIES, 9).black().accuracy();
        JsonNode a = bob.api().get("/api/users/" + alice.username() + "/accuracy").json();
        assertThat(a.path("games").asInt()).as("confirmed games with 10+ of her moves").isEqualTo(2);
        assertThat(a.path("average").asDouble()).isCloseTo((clean + slipped) / 2, within(1e-9));
        assertThat(a.path("best").path("gameId").asLong()).isEqualTo(older);
        assertThat(a.path("best").path("colour").asString()).isEqualTo("WHITE");
        assertThat(a.path("best").path("opponent").path("username").asString()).isEqualTo(bob.username());
        assertThat(a.path("recent")).hasSize(2);
        assertThat(a.path("recent").get(0).path("gameId").asLong()).as("oldest first").isEqualTo(older);
        assertThat(bob.api().get("/api/users/nobody_here/accuracy").code()).isEqualTo("player.not_found");
    }

    @Test
    void calibrationWaitsForEnoughGames() {
        long id = gameWithMoves();
        alice.api().post("/api/games/" + id + "/analysis", body(evals(PLIES, 9)));
        JsonNode c = alice.api().get("/api/analysis/calibration").json();
        assertThat(c.path("samples").asInt()).isEqualTo(2);
        assertThat(c.path("minSamples").asInt()).isEqualTo(30);
        assertThat(c.path("slope").isNull()).isTrue();
    }

    @Test
    void theMonthsMostAccuratePlayerIsInTheRace() {
        for (int i = 0; i < 3; i++) {
            long id = gameWithMoves(alice, bob, MOVES, true, Instant.now().minus(1, ChronoUnit.MINUTES));
            alice.api().post("/api/games/" + id + "/analysis", body(evals(PLIES, 9)));
        }
        JsonNode race = alice.api().get("/api/awards/race").json().path("mostAccurate");
        assertThat(race.path("standings")).hasSize(2);
        JsonNode first = race.path("standings").get(0);
        assertThat(first.path("player").path("username").asString()).isEqualTo(alice.username());
        assertThat(first.path("games").asInt()).isEqualTo(3);
        assertThat(first.path("qualified").asBoolean()).isTrue();
        assertThat(first.path("accuracy").asDouble()).isCloseTo(expected(PLIES, 9).white().accuracy(), within(1e-9));
        assertThat(race.path("winner").isNull()).as("not decided while the month is running").isTrue();
    }

    @Test
    void aDecidedMonthStoresTheMostAccuratePlayer() {
        Instant twoMonthsAgo = Instant.now().minus(62, ChronoUnit.DAYS);
        for (int i = 0; i < 3; i++) {
            long id = gameWithMoves(alice, bob, MOVES, true, twoMonthsAgo.plus(i, ChronoUnit.HOURS));
            alice.api().post("/api/games/" + id + "/analysis", body(evals(PLIES, 9)));
        }
        JsonNode won = bob.api().get("/api/awards?player=" + alice.username()).json();
        JsonNode accurate = null;
        for (JsonNode a : won) {
            if (a.path("kind").asString().equals("MOST_ACCURATE")) {
                accurate = a;
            }
        }
        assertThat(accurate).as("%s", won).isNotNull();
        assertThat(accurate.path("games").asInt()).isEqualTo(3);
        assertThat(accurate.path("accuracy").asDouble()).isCloseTo(expected(PLIES, 9).white().accuracy(), within(1e-9));
        // Year in review still counts only its two awards
        assertThat(bob.api().get("/api/year/" + twoMonthsAgo.atZone(java.time.ZoneId.of("Europe/Oslo")).getYear()).status()).isEqualTo(200);
    }
}
