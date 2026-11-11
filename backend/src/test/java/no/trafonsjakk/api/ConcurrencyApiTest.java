package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

/**
 * Two clicks, two tabs, two people at once. Each race runs several rounds; the loser of a race must get
 * a clear, translatable error and the data must stay consistent.
 */
class ConcurrencyApiTest extends IntegrationTest {

    static final int ROUNDS = 6;

    Player admin;
    Player alice;
    Player bob;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
    }

    @Test
    void confirmingTheSameGameTwiceAtOnceCountsItOnce() {
        Player bobTab2 = login(bob.id());
        for (int round = 0; round < ROUNDS; round++) {
            long id = alice.api().post("/api/games", game(alice, bob, "WHITE_WIN")).json().path("id").asLong();
            List<Response> rs = Race.run(() -> bob.api().post("/api/games/" + id + "/confirm"),
                    () -> bobTab2.api().post("/api/games/" + id + "/confirm"));
            assertThat(Race.count(rs, 200)).as("round %d: %s", round, rs).isEqualTo(1);
            Response loser = rs.stream().filter(r -> r.status() != 200).findFirst().orElseThrow();
            assertThat(loser.status()).isEqualTo(409);
            assertThat(loser.code()).as("%s", loser).isIn("game.wrong_status", "concurrent_change");
        }
        assertThat(jdbc.queryForObject("select count(*) from rating_history", Integer.class)).isEqualTo(2 * ROUNDS);
        assertThat(alice.api().get("/api/users/" + alice.username()).json().path("ratedGames").asInt()).isEqualTo(ROUNDS);
    }

    @Test
    void confirmAndWithdrawAtOnceLeaveTheGameInOneState() {
        for (int round = 0; round < ROUNDS; round++) {
            long id = alice.api().post("/api/games", game(alice, bob, "WHITE_WIN")).json().path("id").asLong();
            List<Response> rs = Race.run(() -> bob.api().post("/api/games/" + id + "/confirm"),
                    () -> alice.api().post("/api/games/" + id + "/withdraw"));
            assertThat(Race.count(rs, 200)).as("round %d: %s", round, rs).isEqualTo(1);
            String status = alice.api().get("/api/games/" + id).json().path("status").asString();
            boolean confirmedWon = rs.get(0).status() == 200;
            assertThat(status).isEqualTo(confirmedWon ? "CONFIRMED" : "VOIDED");
        }
        int confirmed = jdbc.queryForObject("select count(*) from games where status = 'CONFIRMED'", Integer.class);
        assertThat(jdbc.queryForObject("select count(*) from rating_history", Integer.class)).isEqualTo(2 * confirmed);
    }

    @Test
    void oneInviteCodeCanOnlyBeUsedOnceEvenAtTheSameMoment() {
        for (int round = 0; round < ROUNDS; round++) {
            String code = admin.api().post("/api/invites").json().path("code").asString();
            String a = uniqueName("twin_a");
            String b = uniqueName("twin_b");
            List<Response> rs = Race.run(
                    () -> session().post("/api/auth/register", Map.of("username", a, "realName", "A", "password", PASSWORD, "inviteCode", code)),
                    () -> session().post("/api/auth/register", Map.of("username", b, "realName", "B", "password", PASSWORD, "inviteCode", code)));
            assertThat(Race.count(rs, 201)).as("round %d: %s", round, rs).isEqualTo(1);
            Response loser = rs.stream().filter(r -> r.status() != 201).findFirst().orElseThrow();
            assertThat(loser.code()).as("%s", loser).isEqualTo("invite.invalid");
        }
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(3 + ROUNDS);
    }

    @Test
    void twoPeopleTakingTheSameUsernameAtOnceGetAClearAnswer() {
        for (int round = 0; round < ROUNDS; round++) {
            String c1 = admin.api().post("/api/invites").json().path("code").asString();
            String c2 = admin.api().post("/api/invites").json().path("code").asString();
            String name = uniqueName("same");
            List<Response> rs = Race.run(
                    () -> session().post("/api/auth/register", Map.of("username", name, "realName", "A", "password", PASSWORD, "inviteCode", c1)),
                    () -> session().post("/api/auth/register", Map.of("username", name.toUpperCase(), "realName", "B", "password", PASSWORD, "inviteCode", c2)));
            assertThat(Race.count(rs, 201)).as("round %d: %s", round, rs).isEqualTo(1);
            Response loser = rs.stream().filter(r -> r.status() != 201).findFirst().orElseThrow();
            assertThat(loser.status()).isEqualTo(409);
            assertThat(loser.code()).as("%s", loser).isEqualTo("auth.username_taken");
        }
        // The loser's invite code was not used up
        assertThat(jdbc.queryForObject("select count(*) from invite_codes where use_count < max_uses and revoked_at is null", Integer.class))
                .isGreaterThanOrEqualTo(ROUNDS);
    }

    @Test
    void bothPlayersLoggingTheSameTournamentGameAtOnceGetOneResult() {
        Player carol = registerMember(admin, "carol");
        List<Long> ids = List.of(alice.id(), bob.id(), carol.id());
        JsonNode t = admin.api().post("/api/tournaments", Map.of("name", "Race cup", "format", "ROUND_ROBIN",
                "participantIds", ids)).json();
        long tid = t.path("summary").path("id").asLong();
        JsonNode started = admin.api().post("/api/tournaments/" + tid + "/start").json();
        Map<Long, Player> byId = Map.of(alice.id(), alice, bob.id(), bob, carol.id(), carol);
        for (JsonNode r : started.path("rounds")) {
            for (JsonNode m : r.path("matches")) {
                long w = m.path("white").path("id").asLong();
                long b = m.path("black").path("id").asLong();
                Map<String, Object> g = game(w, b, "DRAW", Instant.now().minusSeconds(60));
                g.put("matchId", m.path("id").asLong());
                List<Response> rs = Race.run(() -> byId.get(w).api().post("/api/games", g), () -> byId.get(b).api().post("/api/games", g));
                assertThat(Race.count(rs, 201)).as("%s", rs).isEqualTo(1);
                Response loser = rs.stream().filter(x -> x.status() != 201).findFirst().orElseThrow();
                assertThat(loser.code()).as("%s", loser).isEqualTo("match.result_pending");
            }
        }
    }

    @Test
    void aFullTournamentCannotBeOverfilledByPeopleJoiningAtOnce() {
        for (int round = 0; round < ROUNDS; round++) {
            List<Long> field = new ArrayList<>(bulkUsers(31, "field"));
            JsonNode t = admin.api().post("/api/tournaments", Map.of("name", "Big", "format", "ROUND_ROBIN",
                    "participantIds", field)).json();
            long tid = t.path("summary").path("id").asLong();
            assertThat(t.path("summary").path("participantCount").asInt()).isEqualTo(31);
            Player x = login(bulkUsers(1, "late").getFirst());
            Player y = login(bulkUsers(1, "late").getFirst());
            List<Response> rs = Race.run(() -> x.api().post("/api/tournaments/" + tid + "/join"),
                    () -> y.api().post("/api/tournaments/" + tid + "/join"));
            int count = admin.api().get("/api/tournaments/" + tid).json().path("summary").path("participantCount").asInt();
            assertThat(count).as("round %d: %s", round, rs).isEqualTo(32);
            assertThat(Race.count(rs, 200)).isEqualTo(1);
            Response loser = rs.stream().filter(r -> r.status() != 200).findFirst().orElseThrow();
            assertThat(loser.code()).as("%s", loser).isEqualTo("tournament.full");
        }
    }

    @Test
    void startingATournamentTwiceAtOnceCreatesOneSetOfPairings() {
        for (int round = 0; round < ROUNDS; round++) {
            JsonNode t = admin.api().post("/api/tournaments", Map.of("name", "Twice", "format", "ROUND_ROBIN",
                    "participantIds", bulkUsers(4, "s"))).json();
            long tid = t.path("summary").path("id").asLong();
            Player admin2 = round == 0 ? login(admin.id()) : admin;
            List<Response> rs = Race.run(() -> admin.api().post("/api/tournaments/" + tid + "/start"),
                    () -> admin2.api().post("/api/tournaments/" + tid + "/start"));
            assertThat(Race.count(rs, 200)).as("round %d: %s", round, rs).isEqualTo(1);
            Response loser = rs.stream().filter(r -> r.status() != 200).findFirst().orElseThrow();
            assertThat(loser.status()).isEqualTo(409);
            assertThat(jdbc.queryForObject("select count(*) from tournament_matches where tournament_id = ?", Integer.class, tid))
                    .isEqualTo(6);
        }
    }

    @Test
    void ratingsStayConsistentWhenManyResultsAreConfirmedAtOnce() {
        List<Player> ps = new ArrayList<>();
        for (Long id : bulkUsers(6, "p")) {
            ps.add(login(id));
        }
        List<Long> games = new ArrayList<>();
        for (int i = 0; i < ps.size(); i++) {
            Player w = ps.get(i);
            Player b = ps.get((i + 1) % ps.size());
            games.add(w.api().post("/api/games", game(w, b, i % 3 == 0 ? "DRAW" : "WHITE_WIN")).json().path("id").asLong());
        }
        @SuppressWarnings("unchecked")
        Supplier<Response>[] confirms = new Supplier[games.size()];
        for (int i = 0; i < games.size(); i++) {
            Player b = ps.get((i + 1) % ps.size());
            long id = games.get(i);
            confirms[i] = () -> b.api().post("/api/games/" + id + "/confirm");
        }
        assertThat(Race.count(Race.run(confirms), 200)).isEqualTo(games.size());

        List<Integer> before = new ArrayList<>();
        for (Player p : ps) {
            before.add(rating(p));
        }
        String historyBefore = jdbc.queryForList("select game_id, user_id, round(rating_after::numeric, 6) r from rating_history order by game_id, user_id").toString();
        admin.api().post("/api/admin/ratings/recompute");
        List<Integer> after = new ArrayList<>();
        for (Player p : ps) {
            after.add(rating(p));
        }
        assertThat(after).as("the live updates under load equal a clean replay").isEqualTo(before);
        assertThat(jdbc.queryForList("select game_id, user_id, round(rating_after::numeric, 6) r from rating_history order by game_id, user_id").toString())
                .isEqualTo(historyBefore);
        assertThat(jdbc.queryForObject("select count(*) from rating_history", Integer.class)).isEqualTo(2 * games.size());
    }

    /** Logs every READY match of the tournament (white reports), returning game id -> confirmer. */
    private Map<Long, Player> logReadyMatches(long tid, Map<Long, Player> byId, String result) {
        Map<Long, Player> pending = new java.util.LinkedHashMap<>();
        for (JsonNode r : admin.api().get("/api/tournaments/" + tid).json().path("rounds")) {
            for (JsonNode m : r.path("matches")) {
                if (!m.path("status").asString().equals("READY") || !m.path("openGameId").isNull()) {
                    continue;
                }
                long w = m.path("white").path("id").asLong();
                long b = m.path("black").path("id").asLong();
                Map<String, Object> g = game(w, b, result, Instant.now().minusSeconds(60));
                g.put("matchId", m.path("id").asLong());
                Response created = byId.get(w).api().post("/api/games", g);
                assertThat(created.status()).as("%s", created).isEqualTo(201);
                pending.put(created.json().path("id").asLong(), byId.get(b));
            }
        }
        return pending;
    }

    @SuppressWarnings("unchecked")
    private static List<Response> confirmAllAtOnce(Map<Long, Player> pending) {
        List<Supplier<Response>> calls = new ArrayList<>();
        pending.forEach((gameId, confirmer) -> calls.add(() -> confirmer.api().post("/api/games/" + gameId + "/confirm")));
        return Race.run(calls.toArray(new Supplier[0]));
    }

    @Test
    void bothSemifinalsConfirmedAtOnceFillTheFinal() {
        for (int round = 0; round < ROUNDS; round++) {
            Map<Long, Player> byId = new java.util.HashMap<>();
            List<Long> ids = bulkUsers(4, "ko");
            for (Long id : ids) {
                byId.put(id, login(id));
            }
            long tid = admin.api().post("/api/tournaments", Map.of("name", "KO", "format", "SINGLE_ELIMINATION",
                    "participantIds", ids)).json().path("summary").path("id").asLong();
            admin.api().post("/api/tournaments/" + tid + "/start");
            List<Response> rs = confirmAllAtOnce(logReadyMatches(tid, byId, "WHITE_WIN"));
            assertThat(Race.count(rs, 200)).as("round %d: %s", round, rs).isEqualTo(2);
            JsonNode fin = admin.api().get("/api/tournaments/" + tid).json().path("rounds").get(1).path("matches").get(0);
            assertThat(fin.path("status").asString()).as("round %d final: %s", round, fin).isEqualTo("READY");
            assertThat(fin.path("white").isNull() || fin.path("black").isNull()).isFalse();
        }
    }

    @Test
    void theLastRoundRobinGamesConfirmedAtOnceStillFinishTheTournament() {
        for (int round = 0; round < ROUNDS; round++) {
            Map<Long, Player> byId = new java.util.HashMap<>();
            List<Long> ids = bulkUsers(4, "rr");
            for (Long id : ids) {
                byId.put(id, login(id));
            }
            long tid = admin.api().post("/api/tournaments", Map.of("name", "RR", "format", "ROUND_ROBIN",
                    "participantIds", ids)).json().path("summary").path("id").asLong();
            admin.api().post("/api/tournaments/" + tid + "/start");
            // All six games are pending, then confirmed in one burst
            List<Response> rs = confirmAllAtOnce(logReadyMatches(tid, byId, "DRAW"));
            assertThat(Race.count(rs, 200)).as("round %d: %s", round, rs).isEqualTo(6);
            JsonNode t = admin.api().get("/api/tournaments/" + tid).json();
            assertThat(t.path("completedMatches").asInt()).isEqualTo(6);
            assertThat(t.path("summary").path("status").asString()).as("round %d", round).isEqualTo("COMPLETED");
            assertThat(t.path("summary").path("winner").isNull()).isFalse();
        }
    }

    @Test
    void aCodeForThreePeopleLetsExactlyThreeInEvenWhenSixTryAtOnce() {
        String code = admin.api().post("/api/invites", Map.of("maxUses", 3)).json().path("code").asString();
        @SuppressWarnings("unchecked")
        Supplier<Response>[] tries = new Supplier[6];
        for (int i = 0; i < tries.length; i++) {
            String name = uniqueName("rush");
            tries[i] = () -> session().post("/api/auth/register", Map.of("username", name, "realName", "Rush",
                    "password", PASSWORD, "inviteCode", code));
        }
        List<Response> rs = Race.run(tries);
        assertThat(Race.count(rs, 201)).as("%s", rs).isEqualTo(3);
        assertThat(rs.stream().filter(r -> r.status() != 201)).allSatisfy(r -> assertThat(r.code()).isEqualTo("invite.invalid"));
        assertThat(jdbc.queryForObject("select use_count from invite_codes where max_uses = 3", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from invite_redemptions r join invite_codes i on i.id = r.invite_id "
                + "where i.max_uses = 3", Integer.class)).isEqualTo(3);
    }
}
