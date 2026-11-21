package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class RivalryApiTest extends IntegrationTest {

    @Test
    void twoPlayersRecordAgainstEachOther() {
        Player admin = registerAdmin();
        Player alice = registerMember(admin, "alice");
        Player bob = registerMember(admin, "bob");
        Player carol = registerMember(admin, "carol");
        Instant t = Instant.now().minus(10, ChronoUnit.DAYS);
        playConfirmed(alice, bob, game(alice.id(), bob.id(), "WHITE_WIN", t));
        playConfirmed(bob, alice, game(bob.id(), alice.id(), "BLACK_WIN", t.plus(1, ChronoUnit.DAYS)));
        playConfirmed(alice, bob, game(alice.id(), bob.id(), "DRAW", t.plus(2, ChronoUnit.DAYS)));
        playConfirmed(alice, carol, game(alice.id(), carol.id(), "WHITE_WIN", t.plus(3, ChronoUnit.DAYS))); // someone else
        alice.api().post("/api/games", game(alice, bob, "BLACK_WIN")); // pending: not counted

        JsonNode r = carol.api().get("/api/rivalry/" + alice.username() + "/" + bob.username()).json();
        assertThat(r.path("a").path("username").asString()).isEqualTo(alice.username());
        assertThat(r.path("overall").path("games").asInt()).isEqualTo(3);
        assertThat(r.path("overall").path("aWins").asInt()).isEqualTo(2);
        assertThat(r.path("overall").path("draws").asInt()).isEqualTo(1);
        assertThat(r.path("overall").path("aPoints").asDouble()).isEqualTo(2.5);
        assertThat(r.path("aAsBlack").path("aWins").asInt()).isEqualTo(1);
        assertThat(r.path("longestStreak").path("side").asString()).isEqualTo("A");
        assertThat(r.path("longestStreak").path("length").asInt()).isEqualTo(2);
        assertThat(r.path("currentStreak").path("side").isNull()).isTrue();
        assertThat(r.path("games")).hasSize(3);
        assertThat(r.path("games").get(0).path("result").asString()).as("newest first").isEqualTo("DRAW");

        // The other way round: the same games from Bob's side
        JsonNode flipped = carol.api().get("/api/rivalry/" + bob.username() + "/" + alice.username()).json();
        assertThat(flipped.path("overall").path("bWins").asInt()).isEqualTo(2);
        assertThat(flipped.path("longestStreak").path("side").asString()).isEqualTo("B");

        assertThat(carol.api().get("/api/rivalry/" + alice.username() + "/" + alice.username()).code()).isEqualTo("rivalry.same_player");
        assertThat(carol.api().get("/api/rivalry/" + alice.username() + "/nobody_here").code()).isEqualTo("player.not_found");
    }
}
