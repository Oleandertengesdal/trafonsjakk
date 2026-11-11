package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import no.trafonsjakk.game.AutoConfirmService;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class TournamentApiTest extends IntegrationTest {

    @Autowired
    AutoConfirmService autoConfirm;

    Player admin;
    Player alice;
    Player bob;
    Player carol;
    Player dave;
    Player erin;
    final Map<Long, Player> byId = new HashMap<>();

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
        carol = registerMember(admin, "carol");
        dave = registerMember(admin, "dave");
        erin = registerMember(admin, "erin");
        for (Player p : List.of(admin, alice, bob, carol, dave, erin)) {
            byId.put(p.id(), p);
        }
    }

    // ---- round-robin ---------------------------------------------------------------------------

    @Test
    void roundRobinRunsFromRegistrationToAChampionAndReopensWhenAResultIsVoided() {
        JsonNode t = create(admin, "ROUND_ROBIN", false, alice, bob, carol);
        long id = t.path("summary").path("id").asLong();
        assertThat(t.path("summary").path("status").asString()).isEqualTo("REGISTRATION");
        assertThat(t.path("canManage").asBoolean()).isTrue();

        // Registration: join, leave, double join
        assertThat(detail(dave, id).path("canJoin").asBoolean()).isTrue();
        JsonNode joined = dave.api().post("/api/tournaments/" + id + "/join").json();
        assertThat(joined.path("joined").asBoolean()).isTrue();
        assertThat(joined.path("summary").path("participantCount").asInt()).isEqualTo(4);
        assertThat(dave.api().post("/api/tournaments/" + id + "/join").code()).isEqualTo("tournament.already_joined");
        assertThat(dave.api().post("/api/tournaments/" + id + "/leave").json().path("joined").asBoolean()).isFalse();
        assertThat(dave.api().post("/api/tournaments/" + id + "/leave").code()).isEqualTo("tournament.not_joined");

        Response notMine = alice.api().post("/api/tournaments/" + id + "/start");
        assertThat(notMine.status()).isEqualTo(403);
        assertThat(notMine.code()).isEqualTo("tournament.not_manager");

        JsonNode started = admin.api().post("/api/tournaments/" + id + "/start").json();
        assertThat(started.path("summary").path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(started.path("totalRounds").asInt()).as("3 players: 3 rounds, one bye each").isEqualTo(3);
        assertThat(started.path("totalMatches").asInt()).isEqualTo(3);
        assertThat(started.path("currentRound").asInt()).isEqualTo(1);
        
        for (JsonNode p : started.path("participants")) {
            assertThat(p.path("seed").isNull()).as("everyone is seeded").isFalse();
        }
        assertThat(dave.api().post("/api/tournaments/" + id + "/join").code()).isEqualTo("tournament.wrong_status");

        JsonNode mine = alice.api().get("/api/tournaments/matches/mine").json();
        assertThat(mine).hasSize(2);
        assertThat(mine.get(0).path("format").asString()).isEqualTo("ROUND_ROBIN");
        assertThat(mine.get(0).path("totalRounds").asInt()).isEqualTo(3);

        // Alice wins her games, bob-carol is drawn
        long lastGame = 0;
        for (JsonNode m : matches(started)) {
            boolean aliceWhite = m.path("white").path("id").asLong() == alice.id();
            boolean aliceBlack = m.path("black").path("id").asLong() == alice.id();
            String result = aliceWhite ? "WHITE_WIN" : aliceBlack ? "BLACK_WIN" : "DRAW";
            lastGame = playMatch(m, result);
        }

        JsonNode done = detail(alice, id);
        assertThat(done.path("summary").path("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.path("summary").path("winner").path("id").asLong()).isEqualTo(alice.id());
        assertThat(done.path("completedMatches").asInt()).isEqualTo(3);
        assertThat(done.path("currentRound").isNull()).isTrue();
        JsonNode table = done.path("standings");
        assertThat(table.get(0).path("player").path("id").asLong()).isEqualTo(alice.id());
        assertThat(table.get(0).path("points").asDouble()).isEqualTo(2.0);
        assertThat(table.get(1).path("points").asDouble()).isEqualTo(0.5);
        // Bob and Carol are level on everything (points, SB, their own game), so the
        // final tie-break is the name; ranks stay distinct.
        assertThat(table.findValues("rank")).extracting(JsonNode::asInt).containsExactly(1, 2, 3);
        assertThat(table.get(1).path("player").path("id").asLong()).isEqualTo(bob.id());
        assertThat(table.get(2).path("player").path("id").asLong()).isEqualTo(carol.id());
        assertThat(done.path("canManage").asBoolean()).as("finished tournaments can't be managed").isFalse();
        assertThat(admin.api().post("/api/tournaments/" + id + "/cancel").code()).isEqualTo("tournament.finished");

        // Void the last game: the tournament is live again and the match can be replayed
        assertThat(admin.api().post("/api/admin/games/" + lastGame + "/resolve", Map.of("action", "VOID")).status()).isEqualTo(200);
        JsonNode reopened = detail(alice, id);
        assertThat(reopened.path("summary").path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(reopened.path("summary").path("winner").isNull()).isTrue();
        assertThat(reopened.path("summary").path("completedAt").isNull()).isTrue();
        assertThat(reopened.path("completedMatches").asInt()).isEqualTo(2);
        assertThat(matches(reopened).stream().filter(m -> m.path("status").asString().equals("READY")).count()).isEqualTo(1);
    }

    @Test
    void roundRobinResultIsOrientedToThePairingWhenColoursWereSwappedAtTheBoard() {
        JsonNode t = start(create(admin, "ROUND_ROBIN", false, alice, bob, carol));
        JsonNode m = matches(t).getFirst();
        Player white = byId.get(m.path("white").path("id").asLong());
        Player black = byId.get(m.path("black").path("id").asLong());

        // They sat down the other way round, and the pairing's black player (playing white) won
        Map<String, Object> g = game(black.id(), white.id(), "WHITE_WIN", Instant.now().minusSeconds(60));
        g.put("matchId", m.path("id").asLong());
        long gameId = black.api().post("/api/games", g).json().path("id").asLong();
        assertThat(white.api().post("/api/games/" + gameId + "/confirm").status()).isEqualTo(200);

        JsonNode after = detail(alice, t.path("summary").path("id").asLong());
        JsonNode played = matches(after).stream().filter(x -> x.path("id").asLong() == m.path("id").asLong()).findFirst().orElseThrow();
        assertThat(played.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(played.path("result").asString()).as("relative to the pairing's colours").isEqualTo("BLACK_WIN");
        assertThat(played.path("winner").path("id").asLong()).isEqualTo(black.id());
        JsonNode winnerRow = standingOf(after, black.id());
        assertThat(winnerRow.path("points").asDouble()).isEqualTo(1.0);
        assertThat(standingOf(after, white.id()).path("points").asDouble()).isEqualTo(0.0);
    }

    @Test
    void doubleRoundRobinPlaysEveryPairingTwiceWithColoursReversed() {
        JsonNode t = start(create(admin, "ROUND_ROBIN", true, alice, bob, carol, dave));
        assertThat(t.path("summary").path("doubleRoundRobin").asBoolean()).isTrue();
        assertThat(t.path("totalRounds").asInt()).isEqualTo(6);
        assertThat(t.path("totalMatches").asInt()).isEqualTo(12);
        Map<String, Integer> pairs = new HashMap<>();
        for (JsonNode m : matches(t)) {
            pairs.merge(m.path("white").path("id").asLong() + ">" + m.path("black").path("id").asLong(), 1, Integer::sum);
        }
        assertThat(pairs).hasSize(12).allSatisfy((k, v) -> assertThat(v).isEqualTo(1));
    }

    // ---- validation ----------------------------------------------------------------------------

    @Test
    void tournamentsAreValidatedOnCreateAndStart() {
        JsonNode two = create(alice, "ROUND_ROBIN", false, alice, bob);
        Response tooFew = alice.api().post("/api/tournaments/" + two.path("summary").path("id").asLong() + "/start");
        assertThat(tooFew.status()).isEqualTo(409);
        assertThat(tooFew.code()).isEqualTo("tournament.min_players");
        assertThat(tooFew.json().path("params").path("min").asInt()).isEqualTo(3);

        JsonNode solo = create(alice, "SINGLE_ELIMINATION", false, alice);
        Response alone = alice.api().post("/api/tournaments/" + solo.path("summary").path("id").asLong() + "/start");
        assertThat(alone.json().path("params").path("min").asInt()).isEqualTo(2);

        assertThat(alice.api().post("/api/tournaments", body("Dup", "ROUND_ROBIN", false, List.of(alice.id(), alice.id()))).code())
                .isEqualTo("tournament.duplicate_player");
        assertThat(alice.api().post("/api/tournaments", body("Ghost", "ROUND_ROBIN", false, List.of(alice.id(), 98765L))).code())
                .isEqualTo("player.unknown_or_disabled");
        assertThat(alice.api().post("/api/tournaments", body("   ", "ROUND_ROBIN", false, List.of())).code())
                .isEqualTo("validation");
        List<Long> tooMany = LongStream.rangeClosed(1, 33).boxed().toList();
        Response big = alice.api().post("/api/tournaments", body("Huge", "ROUND_ROBIN", false, tooMany));
        assertThat(big.code()).isEqualTo("validation");
        assertThat(big.json().path("errorCodes").path("participantIds").asString()).isEqualTo("Size");

        // A knockout can't be "double"
        JsonNode ko = create(alice, "SINGLE_ELIMINATION", true, alice, bob);
        assertThat(ko.path("summary").path("doubleRoundRobin").asBoolean()).isFalse();

        assertThat(alice.api().get("/api/tournaments/987654").code()).isEqualTo("tournament.not_found");

        // doubleRoundRobin is optional
        Response minimal = alice.api().post("/api/tournaments", Map.of("name", "Quick", "format", "ROUND_ROBIN",
                "participantIds", List.of(alice.id(), bob.id(), carol.id())));
        assertThat(minimal.status()).as("%s", minimal).isEqualTo(201);
        assertThat(minimal.json().path("summary").path("doubleRoundRobin").asBoolean()).isFalse();
    }

    @Test
    void onlyTheOrganiserOrAnAdminCanEditTheField() {
        long id = create(alice, "ROUND_ROBIN", false, alice, bob).path("summary").path("id").asLong();
        Map<String, Object> field = Map.of("participantIds", List.of(alice.id(), bob.id(), carol.id()));
        assertThat(bob.api().put("/api/tournaments/" + id + "/participants", field).code()).isEqualTo("tournament.not_manager");
        assertThat(alice.api().put("/api/tournaments/" + id + "/participants", field).json().path("participants")).hasSize(3);
        assertThat(admin.api().put("/api/tournaments/" + id + "/participants",
                Map.of("participantIds", List.of(alice.id(), bob.id(), dave.id()))).json().path("participants")).hasSize(3);

        alice.api().post("/api/tournaments/" + id + "/start");
        Response late = alice.api().put("/api/tournaments/" + id + "/participants", field);
        assertThat(late.code()).isEqualTo("tournament.wrong_status");
        assertThat(late.json().path("params").path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(alice.api().post("/api/tournaments/" + id + "/leave").code()).isEqualTo("tournament.wrong_status");
    }

    @Test
    void tournamentGamesMustMatchThePairingAndOneResultAtATime() {
        JsonNode t = start(create(admin, "ROUND_ROBIN", false, alice, bob, carol));
        long id = t.path("summary").path("id").asLong();
        JsonNode m = matches(t).getFirst();
        long matchId = m.path("id").asLong();
        Player white = byId.get(m.path("white").path("id").asLong());
        Player black = byId.get(m.path("black").path("id").asLong());
        Player third = List.of(alice, bob, carol).stream().filter(p -> p != white && p != black).findFirst().orElseThrow();

        Map<String, Object> wrong = game(white.id(), third.id(), "DRAW", Instant.now().minusSeconds(60));
        wrong.put("matchId", matchId);
        assertThat(white.api().post("/api/games", wrong).code()).isEqualTo("match.wrong_players");

        Map<String, Object> ghost = game(white.id(), black.id(), "DRAW", Instant.now().minusSeconds(60));
        ghost.put("matchId", 999999);
        assertThat(white.api().post("/api/games", ghost).code()).isEqualTo("match.not_found");

        long pending = logMatchGame(m, "DRAW");
        assertThat(detail(alice, id).path("rounds").get(0).path("matches").findValues("openGameId").stream()
                .anyMatch(v -> v.asLong() == pending)).isTrue();
        Map<String, Object> second = game(white.id(), black.id(), "WHITE_WIN", Instant.now().minusSeconds(30));
        second.put("matchId", matchId);
        assertThat(black.api().post("/api/games", second).code()).isEqualTo("match.result_pending");
        assertThat(white.api().get("/api/tournaments/matches/mine").json().findValues("matchId").stream()
                .noneMatch(v -> v.asLong() == matchId)).as("a match with a pending result isn't offered again").isTrue();

        // Disputed and voided by an admin: the match can be logged again
        black.api().post("/api/games/" + pending + "/dispute", Map.of("reason", "We never played this"));
        assertThat(black.api().post("/api/games", second).code()).isEqualTo("match.result_pending");
        admin.api().post("/api/admin/games/" + pending + "/resolve", Map.of("action", "VOID"));
        assertThat(black.api().post("/api/games", second).status()).isEqualTo(201);
    }

    @Test
    void cancellingStopsNewGamesButPendingOnesCanStillBeConfirmed() {
        JsonNode t = start(create(admin, "ROUND_ROBIN", false, alice, bob, carol));
        long id = t.path("summary").path("id").asLong();
        JsonNode m = matches(t).getFirst();
        long pending = logMatchGame(m, "WHITE_WIN");

        assertThat(alice.api().post("/api/tournaments/" + id + "/cancel").code()).isEqualTo("tournament.not_manager");
        JsonNode cancelled = admin.api().post("/api/tournaments/" + id + "/cancel").json();
        assertThat(cancelled.path("summary").path("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("canManage").asBoolean()).isFalse();
        assertThat(admin.api().post("/api/tournaments/" + id + "/cancel").code()).isEqualTo("tournament.finished");

        JsonNode other = matches(t).get(1);
        Map<String, Object> g = game(other.path("white").path("id").asLong(), other.path("black").path("id").asLong(), "DRAW",
                Instant.now().minusSeconds(60));
        g.put("matchId", other.path("id").asLong());
        assertThat(byId.get(other.path("white").path("id").asLong()).api().post("/api/games", g).code())
                .isEqualTo("match.tournament_not_running");
        assertThat(alice.api().get("/api/tournaments/matches/mine").json()).isEmpty();

        Player black = byId.get(m.path("black").path("id").asLong());
        Response confirmed = black.api().post("/api/games/" + pending + "/confirm");
        assertThat(confirmed.status()).isEqualTo(200);
        assertThat(detail(alice, id).path("summary").path("status").asString()).isEqualTo("CANCELLED");
    }

    // ---- knockout ------------------------------------------------------------------------------

    @Test
    void knockoutWithByesDrawsAndCorrections() {
        JsonNode t = start(create(admin, "SINGLE_ELIMINATION", false, alice, bob, carol, dave, erin));
        long id = t.path("summary").path("id").asLong();
        assertThat(t.path("totalRounds").asInt()).isEqualTo(3);
        assertThat(t.path("totalMatches").asInt()).as("byes aren't matches").isEqualTo(4);
        assertThat(t.path("rounds").get(2).path("name").asString()).isEqualTo("Final");

        List<JsonNode> r1 = round(t, 1);
        assertThat(r1).hasSize(4);
        assertThat(r1.stream().filter(status("BYE")).count()).isEqualTo(3);
        List<JsonNode> r2 = round(t, 2);
        assertThat(r2.stream().filter(status("READY")).count()).as("two byes meet straight away").isEqualTo(1);
        assertThat(r2.stream().filter(status("WAITING")).count()).isEqualTo(1);

        // The final can't be played yet
        JsonNode fin = round(t, 3).getFirst();
        assertThat(fin.path("status").asString()).isEqualTo("WAITING");

        // Round 1: a draw must be replayed
        JsonNode opener = r1.stream().filter(status("READY")).findFirst().orElseThrow();
        playMatch(opener, "DRAW");
        JsonNode afterDraw = detail(alice, id);
        JsonNode replay = find(afterDraw, opener.path("id").asLong());
        assertThat(replay.path("status").asString()).isEqualTo("READY");
        assertThat(replay.path("needsReplay").asBoolean()).isTrue();
        assertThat(replay.path("games")).hasSize(1);
        Player openerWhite = byId.get(opener.path("white").path("id").asLong());
        JsonNode offered = openerWhite.api().get("/api/tournaments/matches/mine").json();
        assertThat(offered).hasSize(1);
        assertThat(offered.get(0).path("needsReplay").asBoolean()).isTrue();
        assertThat(offered.get(0).path("format").asString()).isEqualTo("SINGLE_ELIMINATION");

        playMatch(replay, "BLACK_WIN");
        JsonNode afterR1 = detail(alice, id);
        assertThat(find(afterR1, opener.path("id").asLong()).path("winner").path("id").asLong())
                .isEqualTo(opener.path("black").path("id").asLong());
        assertThat(round(afterR1, 2).stream().allMatch(status("READY"))).as("winner advanced").isTrue();
        assertThat(round(afterR1, 2).stream().anyMatch(m -> involves(m, opener.path("black").path("id").asLong()))).isTrue();

        // Semifinals
        long semiGame = 0;
        for (JsonNode semi : round(afterR1, 2)) {
            semiGame = playMatch(semi, "WHITE_WIN");
        }
        JsonNode beforeFinal = detail(alice, id);
        JsonNode finalMatch = round(beforeFinal, 3).getFirst();
        assertThat(finalMatch.path("status").asString()).isEqualTo("READY");
        long finalGame = playMatch(finalMatch, "WHITE_WIN");

        JsonNode done = detail(alice, id);
        assertThat(done.path("summary").path("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.path("summary").path("winner").path("id").asLong()).isEqualTo(finalMatch.path("white").path("id").asLong());
        assertThat(done.path("completedMatches").asInt()).isEqualTo(4);

        // A semifinal can't be voided while the final stands; nothing changes
        Response blocked = admin.api().post("/api/admin/games/" + semiGame + "/resolve", Map.of("action", "VOID"));
        assertThat(blocked.status()).isEqualTo(409);
        assertThat(blocked.code()).isEqualTo("match.next_round_started");
        assertThat(alice.api().get("/api/games/" + semiGame).json().path("status").asString()).isEqualTo("CONFIRMED");
        assertThat(detail(alice, id).path("summary").path("status").asString()).isEqualTo("COMPLETED");

        // Void the final: the tournament reopens and the final is playable again
        admin.api().post("/api/admin/games/" + finalGame + "/resolve", Map.of("action", "VOID"));
        JsonNode reopened = detail(alice, id);
        assertThat(reopened.path("summary").path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(reopened.path("summary").path("winner").isNull()).isTrue();
        assertThat(round(reopened, 3).getFirst().path("status").asString()).isEqualTo("READY");

        // Now the semifinal can be corrected: the other player goes through instead
        JsonNode semi = find(reopened, alice.api().get("/api/games/" + semiGame).json().path("tournament").path("matchId").asLong());
        Response corrected = admin.api().post("/api/admin/games/" + semiGame + "/resolve",
                Map.of("action", "CONFIRM", "result", "BLACK_WIN"));
        assertThat(corrected.status()).isEqualTo(200);
        JsonNode fixed = detail(alice, id);
        long newFinalist = semi.path("black").path("id").asLong();
        assertThat(find(fixed, semi.path("id").asLong()).path("winner").path("id").asLong()).isEqualTo(newFinalist);
        JsonNode newFinal = round(fixed, 3).getFirst();
        assertThat(involves(newFinal, newFinalist)).isTrue();
        assertThat(involves(newFinal, semi.path("white").path("id").asLong())).isFalse();
        assertThat(newFinal.path("status").asString()).isEqualTo("READY");
    }

    @Test
    void replaysAfterADrawAreOfferedWithColoursReversed() {
        JsonNode t = start(create(alice, "SINGLE_ELIMINATION", false, alice, bob));
        JsonNode fin = matches(t).getFirst();
        long pairedWhite = fin.path("white").path("id").asLong();
        long pairedBlack = fin.path("black").path("id").asLong();
        JsonNode first = alice.api().get("/api/tournaments/matches/mine").json().get(0);
        assertThat(first.path("white").path("id").asLong()).as("first game: the pairing's colours").isEqualTo(pairedWhite);

        playMatch(fin, "DRAW");
        JsonNode second = alice.api().get("/api/tournaments/matches/mine").json().get(0);
        assertThat(second.path("needsReplay").asBoolean()).isTrue();
        assertThat(second.path("white").path("id").asLong()).as("replay: colours reversed").isEqualTo(pairedBlack);
        assertThat(second.path("black").path("id").asLong()).isEqualTo(pairedWhite);

        // They play the replay as offered and draw again: back to the original colours
        Player white2 = byId.get(pairedBlack);
        Map<String, Object> g = game(pairedBlack, pairedWhite, "DRAW", Instant.now().minusSeconds(30));
        g.put("matchId", fin.path("id").asLong());
        long id = white2.api().post("/api/games", g).json().path("id").asLong();
        assertThat(byId.get(pairedWhite).api().post("/api/games/" + id + "/confirm").status()).isEqualTo(200);
        JsonNode third = alice.api().get("/api/tournaments/matches/mine").json().get(0);
        assertThat(third.path("white").path("id").asLong()).isEqualTo(pairedWhite);
    }

    @Test
    void twoPlayerKnockoutIsJustAFinal() {
        JsonNode t = start(create(alice, "SINGLE_ELIMINATION", false, alice, bob));
        assertThat(t.path("totalRounds").asInt()).isEqualTo(1);
        JsonNode fin = matches(t).getFirst();
        assertThat(t.path("rounds").get(0).path("name").asString()).isEqualTo("Final");
        playMatch(fin, "DRAW");
        assertThat(detail(alice, t.path("summary").path("id").asLong()).path("summary").path("status").asString())
                .as("a drawn final isn't decided").isEqualTo("IN_PROGRESS");
        playMatch(find(detail(alice, t.path("summary").path("id").asLong()), fin.path("id").asLong()), "WHITE_WIN");
        JsonNode done = detail(alice, t.path("summary").path("id").asLong());
        assertThat(done.path("summary").path("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.path("summary").path("winner").path("id").asLong()).isEqualTo(fin.path("white").path("id").asLong());
    }

    @Test
    void autoConfirmedTournamentGamesAdvanceTheBracket() {
        JsonNode t = start(create(admin, "SINGLE_ELIMINATION", false, alice, bob, carol));
        long id = t.path("summary").path("id").asLong();
        JsonNode opener = round(t, 1).stream().filter(status("READY")).findFirst().orElseThrow();
        long g = logMatchGame(opener, "WHITE_WIN");
        jdbc.update("update games set created_at = ? where id = ?", Timestamp.from(Instant.now().minus(49, ChronoUnit.HOURS)), g);

        autoConfirm.scheduled();

        JsonNode after = detail(alice, id);
        assertThat(find(after, opener.path("id").asLong()).path("status").asString()).isEqualTo("COMPLETED");
        assertThat(round(after, 2).getFirst().path("status").asString()).isEqualTo("READY");
    }

    @Test
    void tournamentAndCasualGamesAreAutoConfirmedInTheSameSweep() {
        JsonNode t = start(create(admin, "ROUND_ROBIN", false, alice, bob, carol));
        JsonNode m = matches(t).getFirst();
        long tournamentGame = logMatchGame(m, "DRAW");
        long casual = alice.api().post("/api/games", game(alice, dave, "WHITE_WIN")).json().path("id").asLong();
        Timestamp old = Timestamp.from(Instant.now().minus(3, ChronoUnit.DAYS));
        jdbc.update("update games set created_at = ? where id in (?, ?)", old, tournamentGame, casual);

        autoConfirm.scheduled();

        assertThat(alice.api().get("/api/games/" + casual).json().path("status").asString()).isEqualTo("CONFIRMED");
        assertThat(alice.api().get("/api/games/" + tournamentGame).json().path("status").asString()).isEqualTo("CONFIRMED");
    }

    @Test
    void tournamentListShowsNewestFirstWithCounts() {
        create(alice, "ROUND_ROBIN", false, alice, bob, carol);
        create(bob, "SINGLE_ELIMINATION", false, bob, carol);
        JsonNode list = carol.api().get("/api/tournaments").json();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("format").asString()).isEqualTo("SINGLE_ELIMINATION");
        assertThat(list.get(0).path("participantCount").asInt()).isEqualTo(2);
        assertThat(list.get(1).path("participantCount").asInt()).isEqualTo(3);
        assertThat(list.get(1).path("createdBy").path("id").asLong()).isEqualTo(alice.id());
    }

    @Test
    void seedingDoesNotDependOnTheServerLocale() {
        // Equal ratings are seeded by name. Under a Turkish default locale "I".toLowerCase() is a
        // dotless "ı", which would sort "Ib" after "jo".
        Player ib = register(admin.api().post("/api/invites").json().path("code").asString(), uniqueName("Ib"));
        Player jo = register(admin.api().post("/api/invites").json().path("code").asString(), uniqueName("jo"));
        byId.put(ib.id(), ib);
        byId.put(jo.id(), jo);
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            JsonNode t = start(create(admin, "SINGLE_ELIMINATION", false, jo, ib));
            assertThat(t.path("participants").get(0).path("player").path("id").asLong()).isEqualTo(ib.id());
            assertThat(t.path("participants").get(0).path("seed").asInt()).isEqualTo(1);
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Map<String, Object> body(String name, String format, boolean dbl, List<Long> ids) {
        Map<String, Object> b = new HashMap<>();
        b.put("name", name);
        b.put("format", format);
        b.put("doubleRoundRobin", dbl);
        b.put("participantIds", ids);
        return b;
    }

    private JsonNode create(Player by, String format, boolean dbl, Player... ps) {
        List<Long> ids = new ArrayList<>();
        for (Player p : ps) {
            ids.add(p.id());
        }
        Response r = by.api().post("/api/tournaments", body("Cup " + format, format, dbl, ids));
        assertThat(r.status()).as("create: %s", r.body()).isEqualTo(201);
        return r.json();
    }

    private JsonNode start(JsonNode created) {
        long id = created.path("summary").path("id").asLong();
        Player creator = byId.get(created.path("summary").path("createdBy").path("id").asLong());
        Response r = creator.api().post("/api/tournaments/" + id + "/start");
        assertThat(r.status()).as("start: %s", r.body()).isEqualTo(200);
        return r.json();
    }

    private JsonNode detail(Player p, long id) {
        return p.api().get("/api/tournaments/" + id).json();
    }

    private static List<JsonNode> matches(JsonNode detail) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode r : detail.path("rounds")) {
            r.path("matches").forEach(out::add);
        }
        return out;
    }

    private static List<JsonNode> round(JsonNode detail, int number) {
        return matches(detail).stream().filter(m -> m.path("round").asInt() == number).toList();
    }

    private static JsonNode find(JsonNode detail, long matchId) {
        return matches(detail).stream().filter(m -> m.path("id").asLong() == matchId).findFirst().orElseThrow();
    }

    private static JsonNode standingOf(JsonNode detail, long playerId) {
        for (JsonNode s : detail.path("standings")) {
            if (s.path("player").path("id").asLong() == playerId) {
                return s;
            }
        }
        throw new AssertionError("no standing for " + playerId);
    }

    private static Predicate<JsonNode> status(String s) {
        return m -> m.path("status").asString().equals(s);
    }

    private static boolean involves(JsonNode m, long playerId) {
        return m.path("white").path("id").asLong() == playerId || m.path("black").path("id").asLong() == playerId;
    }

    /** White logs the game for the match (pending). Returns the game id. */
    private long logMatchGame(JsonNode match, String result) {
        Player white = byId.get(match.path("white").path("id").asLong());
        Map<String, Object> g = game(white.id(), match.path("black").path("id").asLong(), result, Instant.now().minusSeconds(60));
        g.put("matchId", match.path("id").asLong());
        Response r = white.api().post("/api/games", g);
        assertThat(r.status()).as("log match game: %s", r.body()).isEqualTo(201);
        return r.json().path("id").asLong();
    }

    /** White logs, black confirms. Returns the game id. */
    private long playMatch(JsonNode match, String result) {
        long id = logMatchGame(match, result);
        Player black = byId.get(match.path("black").path("id").asLong());
        Response r = black.api().post("/api/games/" + id + "/confirm");
        assertThat(r.status()).as("confirm match game: %s", r.body()).isEqualTo(200);
        return id;
    }
}
