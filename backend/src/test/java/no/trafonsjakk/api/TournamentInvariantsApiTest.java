package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

/**
 * Plays whole tournaments of many sizes with random results (seeded, so failures reproduce) and checks
 * what must always hold: brackets advance the right players and finish with one champion; round-robin
 * tables add up, everyone meets everyone, colours are balanced and tie-breaks are computed correctly.
 * The admin logs and confirms every game, which is quick and exercises the admin path too.
 */
class TournamentInvariantsApiTest extends IntegrationTest {

    Player admin;
    long minutes;

    @BeforeEach
    void setUp() {
        admin = registerAdmin();
        minutes = 0;
    }

    private long start(String format, boolean dbl, List<Long> ids) {
        Map<String, Object> body = new HashMap<>(Map.of("name", format + " " + ids.size(), "format", format,
                "participantIds", ids));
        body.put("doubleRoundRobin", dbl);
        Response created = admin.api().post("/api/tournaments", body);
        assertThat(created.status()).as("%s", created).isEqualTo(201);
        long id = created.json().path("summary").path("id").asLong();
        Response started = admin.api().post("/api/tournaments/" + id + "/start");
        assertThat(started.status()).as("%s", started).isEqualTo(200);
        return id;
    }

    private JsonNode detail(long id) {
        return admin.api().get("/api/tournaments/" + id).json();
    }

    private static List<JsonNode> matches(JsonNode t) {
        List<JsonNode> out = new ArrayList<>();
        t.path("rounds").forEach(r -> r.path("matches").forEach(out::add));
        return out;
    }

    /** Admin logs a game for the match (as paired) and confirms it. */
    private void play(JsonNode m, String result) {
        Map<String, Object> g = game(m.path("white").path("id").asLong(), m.path("black").path("id").asLong(), result,
                Instant.now().minusSeconds(86_400).plusSeconds(60 * minutes++));
        g.put("matchId", m.path("id").asLong());
        Response created = admin.api().post("/api/games", g);
        assertThat(created.status()).as("log %s", created).isEqualTo(201);
        Response confirmed = admin.api().post("/api/admin/games/" + created.json().path("id").asLong() + "/resolve",
                Map.of("action", "CONFIRM"));
        assertThat(confirmed.status()).as("confirm %s", confirmed).isEqualTo(200);
    }

    @ParameterizedTest(name = "knockout with {0} players")
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8, 9, 12, 13, 16, 17, 24, 32})
    void knockoutsAlwaysProduceOneChampion(int n) {
        Random rnd = new Random(n * 7919L);
        List<Long> ids = bulkUsers(n, "ko");
        long tid = start("SINGLE_ELIMINATION", false, ids);
        JsonNode t = detail(tid);
        int rounds = t.path("totalRounds").asInt();
        int bracket = 1 << rounds;
        assertThat(bracket).as("smallest power of two ≥ n").isGreaterThanOrEqualTo(n);
        assertThat(bracket / 2).isLessThan(n);
        assertThat(t.path("totalMatches").asInt()).as("one match per eliminated player").isEqualTo(n - 1);

        // Byes go to the top seeds, and only in round one
        Map<Long, Integer> seed = new HashMap<>();
        t.path("participants").forEach(p -> seed.put(p.path("player").path("id").asLong(), p.path("seed").asInt()));
        Set<Integer> byeSeeds = new HashSet<>();
        for (JsonNode m : matches(t)) {
            if (m.path("status").asString().equals("BYE")) {
                assertThat(m.path("round").asInt()).isEqualTo(1);
                byeSeeds.add(seed.get(m.path("white").path("id").asLong()));
            }
        }
        Set<Integer> topSeeds = new HashSet<>();
        for (int s = 1; s <= bracket - n; s++) {
            topSeeds.add(s);
        }
        assertThat(byeSeeds).isEqualTo(topSeeds);

        // Play every ready match until done; about one in five games is drawn and replayed
        int safety = 0;
        while (true) {
            List<JsonNode> ready = matches(detail(tid)).stream().filter(m -> m.path("status").asString().equals("READY")).toList();
            if (ready.isEmpty()) {
                break;
            }
            for (JsonNode m : ready) {
                play(m, rnd.nextInt(5) == 0 ? "DRAW" : rnd.nextBoolean() ? "WHITE_WIN" : "BLACK_WIN");
            }
            assertThat(++safety).as("bracket must make progress").isLessThan(200);
        }

        JsonNode done = detail(tid);
        assertThat(done.path("summary").path("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.path("completedMatches").asInt()).isEqualTo(n - 1);
        List<JsonNode> all = matches(done);
        JsonNode fin = all.stream().filter(m -> m.path("round").asInt() == rounds).findFirst().orElseThrow();
        long champion = done.path("summary").path("winner").path("id").asLong();
        assertThat(fin.path("winner").path("id").asLong()).isEqualTo(champion);

        Map<Long, Integer> losses = new HashMap<>();
        Map<Integer, Set<Long>> winnersByRound = new HashMap<>();
        for (JsonNode m : all) {
            String status = m.path("status").asString();
            assertThat(status).as("match %s", m).isIn("COMPLETED", "BYE");
            long winner = m.path("winner").path("id").asLong();
            winnersByRound.computeIfAbsent(m.path("round").asInt(), k -> new HashSet<>()).add(winner);
            if (status.equals("COMPLETED")) {
                long w = m.path("white").path("id").asLong();
                long b = m.path("black").path("id").asLong();
                assertThat(winner).isIn(w, b);
                losses.merge(winner == w ? b : w, 1, Integer::sum);
                // The recorded result matches the winner, relative to the pairing's colours
                assertThat(m.path("result").asString()).isEqualTo(winner == w ? "WHITE_WIN" : "BLACK_WIN");
            }
        }
        assertThat(losses).as("everyone but the champion is knocked out exactly once").hasSize(n - 1)
                .doesNotContainKey(champion).allSatisfy((p, l) -> assertThat(l).isEqualTo(1));
        // Only winners of round r play in round r + 1
        for (JsonNode m : all) {
            int r = m.path("round").asInt();
            if (r > 1) {
                assertThat(winnersByRound.get(r - 1)).contains(m.path("white").path("id").asLong(), m.path("black").path("id").asLong());
            }
        }
    }

    @ParameterizedTest(name = "round-robin with {0} players")
    @ValueSource(ints = {3, 4, 5, 6, 7, 8, 9, -4, -5})
    void roundRobinTablesAlwaysAddUp(int spec) {
        int n = Math.abs(spec);
        boolean dbl = spec < 0;
        Random rnd = new Random(n * 104729L + (dbl ? 1 : 0));
        List<Long> ids = bulkUsers(n, "rr");
        long tid = start("ROUND_ROBIN", dbl, ids);
        JsonNode t = detail(tid);
        int cycles = dbl ? 2 : 1;
        int games = n * (n - 1) / 2 * cycles;
        assertThat(t.path("totalMatches").asInt()).isEqualTo(games);
        assertThat(t.path("totalRounds").asInt()).isEqualTo((n % 2 == 0 ? n - 1 : n) * cycles);

        Map<String, String> results = new HashMap<>();
        for (JsonNode m : matches(t)) {
            int roll = rnd.nextInt(3);
            String result = roll == 0 ? "WHITE_WIN" : roll == 1 ? "BLACK_WIN" : "DRAW";
            play(m, result);
            results.put(m.path("white").path("id").asLong() + ">" + m.path("black").path("id").asLong(), result);
        }
        JsonNode done = detail(tid);
        assertThat(done.path("summary").path("status").asString()).isEqualTo("COMPLETED");

        // Everyone met everyone (twice, with colours reversed, in a double round-robin)
        Map<String, Integer> meetings = new HashMap<>();
        Map<Long, List<Character>> colours = new HashMap<>();
        for (JsonNode r : done.path("rounds")) {
            for (JsonNode m : r.path("matches")) {
                long w = m.path("white").path("id").asLong();
                long b = m.path("black").path("id").asLong();
                meetings.merge(Math.min(w, b) + "-" + Math.max(w, b), 1, Integer::sum);
                colours.computeIfAbsent(w, k -> new ArrayList<>()).add('W');
                colours.computeIfAbsent(b, k -> new ArrayList<>()).add('B');
            }
        }
        assertThat(meetings).hasSize(n * (n - 1) / 2).allSatisfy((k, v) -> assertThat(v).isEqualTo(cycles));
        colours.forEach((p, cs) -> {
            long whites = cs.stream().filter(c -> c == 'W').count();
            assertThat(Math.abs(2 * whites - cs.size())).as("colour balance for %d: %s", p, cs).isLessThanOrEqualTo(dbl ? 0 : 1);
            for (int i = 2; i < cs.size(); i++) {
                assertThat(cs.get(i) == cs.get(i - 1) && cs.get(i) == cs.get(i - 2)).as("three in a row for %d: %s", p, cs).isFalse();
            }
        });

        // The table adds up and Sonneborn–Berger is right
        Map<Long, Double> points = new HashMap<>();
        Map<Long, Map<Long, Double>> scoreVs = new HashMap<>();
        results.forEach((pair, res) -> {
            String[] ab = pair.split(">");
            long w = Long.parseLong(ab[0]);
            long b = Long.parseLong(ab[1]);
            double ws = res.equals("WHITE_WIN") ? 1 : res.equals("DRAW") ? 0.5 : 0;
            points.merge(w, ws, Double::sum);
            points.merge(b, 1 - ws, Double::sum);
            scoreVs.computeIfAbsent(w, k -> new HashMap<>()).merge(b, ws, Double::sum);
            scoreVs.computeIfAbsent(b, k -> new HashMap<>()).merge(w, 1 - ws, Double::sum);
        });
        JsonNode table = done.path("standings");
        assertThat(table).hasSize(n);
        double total = 0;
        double previous = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            JsonNode row = table.get(i);
            long p = row.path("player").path("id").asLong();
            assertThat(row.path("rank").asInt()).isEqualTo(i + 1);
            assertThat(row.path("played").asInt()).isEqualTo((n - 1) * cycles);
            assertThat(row.path("wins").asInt() + row.path("draws").asInt() + row.path("losses").asInt()).isEqualTo(row.path("played").asInt());
            assertThat(row.path("points").asDouble()).isEqualTo(row.path("wins").asInt() + row.path("draws").asInt() / 2.0)
                    .isEqualTo(points.getOrDefault(p, 0.0));
            double sb = 0;
            for (Map.Entry<Long, Double> e : scoreVs.getOrDefault(p, Map.of()).entrySet()) {
                sb += e.getValue() * points.get(e.getKey());
            }
            assertThat(row.path("sonnebornBerger").asDouble()).as("SB of %d", p).isCloseTo(sb, within(1e-9));
            assertThat(row.path("points").asDouble()).as("sorted by points").isLessThanOrEqualTo(previous);
            previous = row.path("points").asDouble();
            total += row.path("points").asDouble();
        }
        assertThat(total).as("one point per game").isEqualTo(games);
        assertThat(done.path("summary").path("winner").path("id").asLong()).isEqualTo(table.get(0).path("player").path("id").asLong());

        // Ratings: every player rated once per game, and a clean replay gives the same numbers
        assertThat(jdbc.queryForObject("select count(*) from rating_history", Integer.class)).isEqualTo(2 * games);
        String before = jdbc.queryForList("select id, round(rating::numeric, 6) r, round(rating_deviation::numeric, 6) d from users order by id").toString();
        admin.api().post("/api/admin/ratings/recompute");
        assertThat(jdbc.queryForList("select id, round(rating::numeric, 6) r, round(rating_deviation::numeric, 6) d from users order by id").toString())
                .isEqualTo(before);
    }
}
