package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import no.trafonsjakk.support.Race;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Playing online: challenges, moves checked by the server, the clock, every way a game ends, and ratings. */
class OnlineGameApiTest extends IntegrationTest {

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

    // ---- helpers ----------------------------------------------------------------------------------------

    static Map<String, Object> live(int initialSeconds, int incrementSeconds) {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", "LIVE");
        m.put("initialSeconds", initialSeconds);
        m.put("incrementSeconds", incrementSeconds);
        m.put("colour", "WHITE");
        m.put("rated", true);
        return m;
    }

    static Map<String, Object> daily(int days) {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", "DAILY");
        m.put("daysPerMove", days);
        m.put("colour", "WHITE");
        m.put("rated", true);
        return m;
    }

    static Map<String, Object> with(Map<String, Object> m, String key, Object value) {
        Map<String, Object> copy = new HashMap<>(m);
        copy.put(key, value);
        return copy;
    }

    JsonNode challenge(Player by, Player opponent, Map<String, Object> settings) {
        Map<String, Object> body = new HashMap<>(settings);
        if (opponent != null) {
            body.put("opponentId", opponent.id());
        }
        Response r = by.api().post("/api/online/challenges", body);
        assertThat(r.status()).as("challenge: %s", r).isEqualTo(201);
        return r.json();
    }

    JsonNode accept(Player by, long challengeId) {
        Response r = by.api().post("/api/online/challenges/" + challengeId + "/accept");
        assertThat(r.status()).as("accept: %s", r).isEqualTo(201);
        return r.json();
    }

    /** Alice (White) against Bob (Black). */
    long startGame(Map<String, Object> settings) {
        return accept(bob, challenge(alice, bob, settings).path("id").asLong()).path("id").asLong();
    }

    JsonNode game(Player who, long id) {
        Response r = who.api().get("/api/online/games/" + id);
        assertThat(r.status()).as("get: %s", r).isEqualTo(200);
        return r.json();
    }

    Response tryMove(Player who, long id, String uci) {
        int ply = game(who, id).path("plies").asInt();
        return who.api().post("/api/online/games/" + id + "/moves", Map.of("uci", uci, "ply", ply));
    }

    JsonNode move(Player who, long id, String uci) {
        Response r = tryMove(who, id, uci);
        assertThat(r.status()).as("move %s: %s", uci, r).isEqualTo(200);
        return r.json();
    }

    /** Plays the moves alternately, White first. */
    JsonNode moves(long id, String... uci) {
        JsonNode last = null;
        for (int i = 0; i < uci.length; i++) {
            last = move(i % 2 == 0 ? alice : bob, id, uci[i]);
        }
        return last;
    }

    /** Moves the clock back: as if the side to move had been thinking {@code seconds} longer. */
    void think(long id, long seconds) {
        jdbc.update("update online_games set turn_started_at = turn_started_at - make_interval(secs => ?) where id = ?",
                (double) seconds, id);
    }

    double onlineRating(Player p) {
        return jdbc.queryForObject("select online_rating from users where id = ?", Double.class, p.id());
    }

    double otbRating(Player p) {
        return jdbc.queryForObject("select rating from users where id = ?", Double.class, p.id());
    }

    // ---- challenges ---------------------------------------------------------------------------------------

    @Test
    void aChallengedFriendAcceptsAndTheGameStarts() {
        JsonNode c = challenge(alice, bob, live(600, 5));
        assertThat(c.path("timeControl").path("label").asString()).isEqualTo("10+5");
        assertThat(c.path("opponent").path("id").asLong()).isEqualTo(bob.id());

        // Bob sees it waiting for him; Carol doesn't
        assertThat(bob.api().get("/api/online").json().path("challengesToMe").valueStream().map(n -> n.path("id").asString()).toList())
                .containsExactly(c.path("id").asString());
        assertThat(carol.api().get("/api/online").json().path("challengesToMe").isEmpty()).isTrue();

        // Only Bob may take it
        Response stolen = carol.api().post("/api/online/challenges/" + c.path("id").asLong() + "/accept");
        assertThat(stolen.status()).isEqualTo(403);
        assertThat(stolen.code()).isEqualTo("online.not_for_you");

        JsonNode g = accept(bob, c.path("id").asLong());
        assertThat(g.path("white").path("id").asLong()).isEqualTo(alice.id());
        assertThat(g.path("black").path("id").asLong()).isEqualTo(bob.id());
        assertThat(g.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(g.path("rated").asBoolean()).isTrue();
        assertThat(g.path("clock").path("whiteMs").asLong()).isEqualTo(600_000);
        assertThat(g.path("myColour").asString()).isEqualTo("BLACK");

        // The challenge is used up
        Response again = bob.api().post("/api/online/challenges/" + c.path("id").asLong() + "/accept");
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("online.challenge_closed");
    }

    @Test
    void anOpenChallengeCanBeTakenByAnyoneButTheCreator() {
        JsonNode c = challenge(alice, null, with(live(300, 3), "colour", "BLACK"));
        assertThat(carol.api().get("/api/online").json().path("openChallenges").valueStream().map(n -> n.path("id").asString()).toList())
                .containsExactly(c.path("id").asString());

        Response own = alice.api().post("/api/online/challenges/" + c.path("id").asLong() + "/accept");
        assertThat(own.status()).isEqualTo(400);
        assertThat(own.code()).isEqualTo("online.own_challenge");

        JsonNode g = accept(carol, c.path("id").asLong());
        assertThat(g.path("white").path("id").asLong()).isEqualTo(carol.id());
        assertThat(g.path("black").path("id").asLong()).isEqualTo(alice.id());
    }

    @Test
    void randomColoursGiveEachSideWhiteSometimes() {
        boolean aliceWhite = false;
        boolean aliceBlack = false;
        for (int i = 0; i < 30 && !(aliceWhite && aliceBlack); i++) {
            JsonNode g = accept(bob, challenge(alice, bob, with(daily(3), "colour", "RANDOM")).path("id").asLong());
            boolean white = g.path("white").path("id").asLong() == alice.id();
            aliceWhite |= white;
            aliceBlack |= !white;
        }
        assertThat(aliceWhite).isTrue();
        assertThat(aliceBlack).isTrue();
    }

    @Test
    void challengesCanBeDeclinedOrWithdrawn() {
        long declined = challenge(alice, bob, live(600, 5)).path("id").asLong();
        assertThat(carol.api().post("/api/online/challenges/" + declined + "/decline").status()).isEqualTo(403);
        assertThat(bob.api().post("/api/online/challenges/" + declined + "/decline").status()).isEqualTo(204);
        assertThat(bob.api().post("/api/online/challenges/" + declined + "/accept").code()).isEqualTo("online.challenge_closed");

        long withdrawn = challenge(alice, bob, live(600, 5)).path("id").asLong();
        assertThat(bob.api().post("/api/online/challenges/" + withdrawn + "/cancel").status()).isEqualTo(403);
        assertThat(alice.api().post("/api/online/challenges/" + withdrawn + "/cancel").status()).isEqualTo(204);
        assertThat(bob.api().get("/api/online").json().path("challengesToMe").isEmpty()).isTrue();
    }

    @Test
    void oldChallengesExpire() {
        long id = challenge(alice, bob, live(600, 5)).path("id").asLong();
        jdbc.update("update online_challenges set expires_at = now() - interval '1 second' where id = ?", id);
        assertThat(bob.api().get("/api/online").json().path("challengesToMe").isEmpty()).isTrue();
        assertThat(bob.api().post("/api/online/challenges/" + id + "/accept").code()).isEqualTo("online.challenge_closed");
    }

    @Test
    void refusesBadChallenges() {
        Response self = alice.api().post("/api/online/challenges", with(live(600, 5), "opponentId", alice.id()));
        assertThat(self.status()).isEqualTo(400);
        assertThat(self.code()).isEqualTo("game.same_player");

        Response unknown = alice.api().post("/api/online/challenges", with(live(600, 5), "opponentId", 99_999));
        assertThat(unknown.status()).isEqualTo(400);
        assertThat(unknown.code()).isEqualTo("player.unknown_or_disabled");

        Response tooFast = alice.api().post("/api/online/challenges", live(10, 0));
        assertThat(tooFast.status()).isEqualTo(400);
        assertThat(tooFast.code()).isEqualTo("online.bad_time_control");

        Response noDays = alice.api().post("/api/online/challenges", daily(30));
        assertThat(noDays.code()).isEqualTo("online.bad_time_control");

        Response noKind = alice.api().post("/api/online/challenges", with(live(600, 5), "kind", null));
        assertThat(noKind.status()).isEqualTo(400);
    }

    @Test
    void nobodyCanPlayTwoClockGamesAtOnce() {
        startGame(live(600, 5));
        long second = challenge(carol, bob, live(300, 0)).path("id").asLong();
        Response busy = bob.api().post("/api/online/challenges/" + second + "/accept");
        assertThat(busy.status()).isEqualTo(409);
        assertThat(busy.code()).isEqualTo("online.already_playing");
        // Daily games don't need you at the board, so you can have several
        accept(bob, challenge(carol, bob, daily(3)).path("id").asLong());
    }

    @Test
    void aChallengeCanOnlyBeAcceptedOnce() {
        long id = challenge(alice, null, live(600, 5)).path("id").asLong();
        List<Response> both = Race.run(
                () -> bob.api().post("/api/online/challenges/" + id + "/accept"),
                () -> carol.api().post("/api/online/challenges/" + id + "/accept"));
        assertThat(both).extracting(Response::status).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("select count(*) from online_games", Integer.class)).isEqualTo(1);
    }

    // ---- moves --------------------------------------------------------------------------------------------

    @Test
    void theServerChecksEveryMoveAndKeepsTheScoreSheet() {
        long id = startGame(live(600, 5));
        JsonNode after = moves(id, "e2e4", "e7e5", "g1f3");
        assertThat(after.path("moves").asString()).isEqualTo("1. e4 e5 2. Nf3");
        assertThat(after.path("plies").asInt()).isEqualTo(3);
        assertThat(after.path("turn").asString()).isEqualTo("BLACK");
        assertThat(after.path("lastMove").asString()).isEqualTo("g1f3");
        assertThat(after.path("fen").asString()).startsWith("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq");

        Response illegal = tryMove(bob, id, "e5e4");
        assertThat(illegal.status()).isEqualTo(400);
        assertThat(illegal.code()).isEqualTo("online.illegal_move");

        Response notYours = tryMove(alice, id, "d2d4");
        assertThat(notYours.status()).isEqualTo(409);
        assertThat(notYours.code()).isEqualTo("online.not_your_turn");

        Response spectator = tryMove(carol, id, "b8c6");
        assertThat(spectator.status()).isEqualTo(403);
        assertThat(spectator.code()).isEqualTo("online.not_a_player");

        // Carol can watch, though
        JsonNode watching = game(carol, id);
        assertThat(watching.path("moves").asString()).isEqualTo("1. e4 e5 2. Nf3");
        assertThat(watching.path("myColour").isNull()).isTrue();
    }

    @Test
    void aMoveSentTwiceIsOnlyPlayedOnce() {
        long id = startGame(live(600, 5));
        Map<String, Object> e4 = Map.of("uci", "e2e4", "ply", 0);
        assertThat(alice.api().post("/api/online/games/" + id + "/moves", e4).status()).isEqualTo(200);
        // A double tap, or a retry after a dropped connection: the board has moved on
        Response again = alice.api().post("/api/online/games/" + id + "/moves", e4);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("online.out_of_date");
        assertThat(game(alice, id).path("plies").asInt()).isEqualTo(1);
    }

    @Test
    void twoMovesAtTheSameInstantCannotBothCount() {
        long id = startGame(live(600, 5));
        List<Response> both = Race.run(
                () -> alice.api().post("/api/online/games/" + id + "/moves", Map.of("uci", "e2e4", "ply", 0)),
                () -> alice.api().post("/api/online/games/" + id + "/moves", Map.of("uci", "d2d4", "ply", 0)));
        assertThat(both).extracting(Response::status).containsExactlyInAnyOrder(200, 409);
        assertThat(game(alice, id).path("plies").asInt()).isEqualTo(1);
    }

    // ---- how games end ------------------------------------------------------------------------------------

    @Test
    void checkmateEndsTheGameAndCountsForTheOnlineRatingOnly() {
        double aliceOtb = otbRating(alice);
        long id = startGame(live(600, 5));
        JsonNode end = moves(id, "f2f3", "e7e5", "g2g4", "d8h4");
        assertThat(end.path("status").asString()).isEqualTo("FINISHED");
        assertThat(end.path("result").asString()).isEqualTo("BLACK_WIN");
        assertThat(end.path("endReason").asString()).isEqualTo("CHECKMATE");
        assertThat(end.path("clock").path("running").asBoolean()).isFalse();

        // It's now an ordinary, already confirmed game, marked as online
        long gameId = end.path("gameId").asLong();
        JsonNode logged = alice.api().get("/api/games/" + gameId).json();
        assertThat(logged.path("status").asString()).isEqualTo("CONFIRMED");
        assertThat(logged.path("mode").asString()).isEqualTo("ONLINE");
        assertThat(logged.path("timeControl").asString()).isEqualTo("10+5");
        assertThat(logged.path("moves").asString()).isEqualTo("1. f3 e5 2. g4 Qh4#");
        assertThat(logged.path("canConfirm").asBoolean()).isFalse();
        assertThat(logged.path("ratingChange").path("black").path("after").asInt()).isGreaterThan(1500);

        // Over-the-board ratings didn't move; online ones did
        assertThat(otbRating(alice)).isEqualTo(aliceOtb);
        assertThat(onlineRating(bob)).isGreaterThan(1500);
        assertThat(onlineRating(alice)).isLessThan(1500);
        assertThat(jdbc.queryForObject("select pool from rating_history where game_id = ? limit 1", String.class, gameId))
                .isEqualTo("ONLINE");

        // Moves can't be edited afterwards: the server saw them played
        Response edit = alice.api().patch("/api/games/" + gameId + "/moves", Map.of("moves", "1. e4"));
        assertThat(edit.status()).isEqualTo(409);
        assertThat(edit.code()).isEqualTo("game.online_moves");

        // Nor can it be played on
        assertThat(tryMove(alice, id, "a2a3").code()).isEqualTo("online.game_over");
    }

    @Test
    void theOnlineLeaderboardIsSeparate() {
        long id = startGame(live(600, 5));
        moves(id, "f2f3", "e7e5", "g2g4", "d8h4");
        JsonNode otb = alice.api().get("/api/leaderboard").json();
        JsonNode online = alice.api().get("/api/leaderboard?pool=ONLINE").json();
        for (JsonNode row : otb) {
            assertThat(row.path("games").asInt()).as("OTB table counts OTB games").isZero();
        }
        JsonNode bobRow = null;
        for (JsonNode row : online) {
            if (row.path("player").path("id").asLong() == bob.id()) {
                bobRow = row;
            }
        }
        assertThat(bobRow).isNotNull();
        assertThat(bobRow.path("wins").asInt()).isEqualTo(1);
        assertThat(bobRow.path("player").path("rating").asInt()).isGreaterThan(1500);
        assertThat(alice.api().get("/api/leaderboard?pool=NONSENSE").status()).isEqualTo(400);
    }

    @Test
    void theOnlineRatingStartsFromTheOverTheBoardOne() {
        // Alice beats Carol twice over the board first
        playConfirmed(alice, carol, game(alice, carol, "WHITE_WIN"));
        playConfirmed(alice, carol, game(carol, alice, "BLACK_WIN"));
        double aliceOtb = otbRating(alice);
        assertThat(aliceOtb).isGreaterThan(1600);

        long id = startGame(live(600, 5));
        JsonNode end = moves(id, "f2f3", "e7e5", "g2g4", "d8h4");
        double before = jdbc.queryForObject("select rating_before from rating_history where game_id = ? and user_id = ?",
                Double.class, end.path("gameId").asLong(), alice.id());
        assertThat(before).isCloseTo(aliceOtb, org.assertj.core.data.Offset.offset(0.001));
        // Bob never played over the board: he starts at 1500 like everyone
        double bobBefore = jdbc.queryForObject("select rating_before from rating_history where game_id = ? and user_id = ?",
                Double.class, end.path("gameId").asLong(), bob.id());
        assertThat(bobBefore).isEqualTo(1500.0);
    }

    @Test
    void resigning() {
        long id = startGame(live(600, 5));
        moves(id, "e2e4", "e7e5");
        assertThat(carol.api().post("/api/online/games/" + id + "/resign").status()).isEqualTo(403);
        Response r = bob.api().post("/api/online/games/" + id + "/resign");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json().path("result").asString()).isEqualTo("WHITE_WIN");
        assertThat(r.json().path("endReason").asString()).isEqualTo("RESIGNATION");
        assertThat(bob.api().post("/api/online/games/" + id + "/resign").code()).isEqualTo("online.game_over");
    }

    @Test
    void aDrawByAgreement() {
        long id = startGame(live(600, 5));
        moves(id, "e2e4", "e7e5");
        JsonNode offered = alice.api().post("/api/online/games/" + id + "/offer-draw").json();
        assertThat(offered.path("drawOfferBy").asLong()).isEqualTo(alice.id());
        // You can't accept your own offer
        assertThat(alice.api().post("/api/online/games/" + id + "/accept-draw").code()).isEqualTo("online.no_draw_offer");
        JsonNode drawn = bob.api().post("/api/online/games/" + id + "/accept-draw").json();
        assertThat(drawn.path("result").asString()).isEqualTo("DRAW");
        assertThat(drawn.path("endReason").asString()).isEqualTo("AGREEMENT");
    }

    @Test
    void aDrawOfferLapsesWhenItIsDeclinedOrTheOpponentMoves() {
        long id = startGame(live(600, 5));
        moves(id, "e2e4");
        alice.api().post("/api/online/games/" + id + "/offer-draw");
        assertThat(bob.api().post("/api/online/games/" + id + "/decline-draw").json().path("drawOfferBy").isNull()).isTrue();

        alice.api().post("/api/online/games/" + id + "/offer-draw");
        JsonNode moved = move(bob, id, "e7e5");
        assertThat(moved.path("drawOfferBy").isNull()).isTrue();
        assertThat(bob.api().post("/api/online/games/" + id + "/accept-draw").code()).isEqualTo("online.no_draw_offer");
    }

    @Test
    void runningOutOfTimeLoses() {
        long id = startGame(live(180, 2));
        moves(id, "e2e4", "e7e5");
        think(id, 200); // White is far past three minutes
        JsonNode g = game(carol, id);
        assertThat(g.path("status").asString()).isEqualTo("FINISHED");
        assertThat(g.path("result").asString()).isEqualTo("BLACK_WIN");
        assertThat(g.path("endReason").asString()).isEqualTo("TIMEOUT");
        assertThat(g.path("clock").path("whiteMs").asLong()).isZero();
        assertThat(g.path("gameId").isNull()).isFalse();
    }

    @Test
    void aMoveAfterTheFlagFellLosesInsteadOfCounting() {
        long id = startGame(live(180, 2));
        moves(id, "e2e4", "e7e5");
        think(id, 200);
        Response late = alice.api().post("/api/online/games/" + id + "/moves", Map.of("uci", "g1f3", "ply", 2));
        assertThat(late.status()).isEqualTo(409);
        assertThat(late.code()).isEqualTo("online.game_over");
        JsonNode g = game(alice, id);
        assertThat(g.path("plies").asInt()).isEqualTo(2);
        assertThat(g.path("endReason").asString()).isEqualTo("TIMEOUT");
    }

    @Test
    void aGameNobodyStartsIsCancelledWithoutAResult() {
        long id = startGame(live(600, 5));
        jdbc.update("update online_games set turn_started_at = now() - interval '2 minutes' where id = ?", id);
        JsonNode g = game(carol, id);
        assertThat(g.path("status").asString()).isEqualTo("ABORTED");
        assertThat(g.path("gameId").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from games", Integer.class)).isZero();
        assertThat(onlineRating(alice)).isEqualTo(1500.0);
    }

    @Test
    void eitherPlayerCanCancelBeforeBothHaveMoved() {
        long id = startGame(live(600, 5));
        moves(id, "e2e4");
        JsonNode g = bob.api().post("/api/online/games/" + id + "/abort").json();
        assertThat(g.path("status").asString()).isEqualTo("ABORTED");

        long later = startGame(live(600, 5));
        moves(later, "e2e4", "e7e5");
        Response tooLate = alice.api().post("/api/online/games/" + later + "/abort");
        assertThat(tooLate.status()).isEqualTo(409);
        assertThat(tooLate.code()).isEqualTo("online.cannot_abort");
    }

    @Test
    void stalemateAndRepetitionAreDrawsDetectedByTheServer() {
        long id = startGame(live(600, 5));
        JsonNode end = moves(id, "g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8");
        assertThat(end.path("result").asString()).isEqualTo("DRAW");
        assertThat(end.path("endReason").asString()).isEqualTo("REPETITION");
    }

    @Test
    void casualGamesDoNotCountAnywhere() {
        long id = startGame(with(live(600, 5), "rated", false));
        JsonNode end = moves(id, "f2f3", "e7e5", "g2g4", "d8h4");
        assertThat(end.path("status").asString()).isEqualTo("FINISHED");
        assertThat(end.path("gameId").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from games", Integer.class)).isZero();
        assertThat(onlineRating(bob)).isEqualTo(1500.0);
    }

    @Test
    void rematchSwapsColours() {
        long id = startGame(live(300, 3));
        moves(id, "f2f3", "e7e5", "g2g4", "d8h4");
        Response r = alice.api().post("/api/online/games/" + id + "/rematch");
        assertThat(r.status()).isEqualTo(201);
        JsonNode rematch = accept(bob, r.json().path("id").asLong());
        assertThat(rematch.path("white").path("id").asLong()).isEqualTo(bob.id());
        assertThat(rematch.path("timeControl").path("label").asString()).isEqualTo("5+3");
        assertThat(carol.api().post("/api/online/games/" + id + "/rematch").status()).isEqualTo(403);
    }

    // ---- daily games ------------------------------------------------------------------------------------

    @Test
    void aDailyGameGivesEachMoveDays() {
        long id = startGame(daily(2));
        JsonNode g = moves(id, "e2e4", "e7e5");
        Instant deadline = Instant.parse(g.path("clock").path("deadline").asString());
        assertThat(deadline).isBetween(Instant.now().plus(47, ChronoUnit.HOURS), Instant.now().plus(49, ChronoUnit.HOURS));
        assertThat(alice.api().get("/api/online").json().path("myTurn").valueStream().map(n -> n.path("id").asString()).toList()).containsExactly(String.valueOf(id));
        assertThat(bob.api().get("/api/online").json().path("myTurn").isEmpty()).isTrue();

        think(id, 3 * 24 * 3600);
        JsonNode lost = game(bob, id);
        assertThat(lost.path("result").asString()).isEqualTo("BLACK_WIN");
        assertThat(lost.path("endReason").asString()).isEqualTo("TIMEOUT");
    }

    @Test
    void finishedDailyGamesEndEvenIfNobodyLooks() {
        long id = startGame(daily(1));
        moves(id, "e2e4", "e7e5");
        think(id, 2 * 24 * 3600);
        // The lobby sweeps games whose time is up
        alice.api().get("/api/online");
        assertThat(jdbc.queryForObject("select status from online_games where id = ?", String.class, id)).isEqualTo("FINISHED");
    }

    // ---- who sees what ------------------------------------------------------------------------------------

    @Test
    void theLobbyListsGamesBeingPlayed() {
        long id = startGame(live(600, 5));
        JsonNode lobby = carol.api().get("/api/online").json();
        assertThat(lobby.path("playing").valueStream().map(n -> n.path("id").asString()).toList()).contains(String.valueOf(id));
        assertThat(alice.api().get("/api/online").json().path("myGames").valueStream().map(n -> n.path("id").asString()).toList()).containsExactly(String.valueOf(id));
    }

    @Test
    void profilesShowTheOnlineRating() {
        long id = startGame(live(600, 5));
        moves(id, "f2f3", "e7e5", "g2g4", "d8h4");
        JsonNode profile = alice.api().get("/api/users/" + bob.username()).json();
        assertThat(profile.path("rating").asInt()).isEqualTo(1500);
        assertThat(profile.path("online").path("rating").asInt()).isGreaterThan(1500);
        assertThat(profile.path("online").path("games").asInt()).isEqualTo(1);
        JsonNode stats = alice.api().get("/api/users/" + bob.username() + "/stats").json();
        assertThat(stats.path("ratingHistory").isEmpty()).as("the OTB graph has no online games").isTrue();
        assertThat(stats.path("onlineRatingHistory")).hasSize(1);
    }
}
