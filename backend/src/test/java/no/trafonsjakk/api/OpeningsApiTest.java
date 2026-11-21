package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class OpeningsApiTest extends IntegrationTest {

    @Test
    void confirmedGamesWithTheirFirstMovesOrATypedName() {
        Player admin = registerAdmin();
        Player alice = registerMember(admin, "alice");
        Player bob = registerMember(admin, "bob");
        Player carol = registerMember(admin, "carol");

        StringBuilder longGame = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            longGame.append(i).append(". Nf3 Nf6 ").append(i).append("... ").append(" ");
        }
        Map<String, Object> withMoves = game(alice, bob, "WHITE_WIN");
        withMoves.put("moves", "1. e4 {best by test} c5 2. Nf3 d6 " + longGame);
        long a = playConfirmed(alice, bob, withMoves).path("id").asLong();
        Map<String, Object> named = game(bob, carol, "DRAW");
        named.put("opening", "  London System ");
        long b = playConfirmed(bob, carol, named).path("id").asLong();
        playConfirmed(alice, carol, game(alice, carol, "DRAW")); // neither: left out
        Map<String, Object> pending = game(alice, bob, "DRAW");
        pending.put("moves", "1. d4 d5");
        alice.api().post("/api/games", pending); // not confirmed: left out

        JsonNode all = carol.api().get("/api/openings/games").json();
        assertThat(all).hasSize(2);
        JsonNode first = all.get(0).path("id").asLong() == a ? all.get(0) : all.get(1);
        String[] moves = first.path("moves").asString().split(" ");
        assertThat(moves).hasSize(30);
        assertThat(moves[0]).isEqualTo("e4");
        assertThat(moves[1]).isEqualTo("c5");
        assertThat(first.path("white").path("username").asString()).isEqualTo(alice.username());
        JsonNode second = all.get(0).path("id").asLong() == b ? all.get(0) : all.get(1);
        assertThat(second.path("opening").asString()).isEqualTo("London System");
        assertThat(second.path("moves").isNull()).isTrue();

        assertThat(carol.api().get("/api/openings/games?player=" + alice.username()).json()).hasSize(1);
        assertThat(carol.api().get("/api/openings/games?player=nobody_at_all").code()).isEqualTo("player.not_found");
    }
}
