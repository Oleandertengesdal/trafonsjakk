package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import no.trafonsjakk.game.AutoConfirmService;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** A result against someone whose account is disabled waits for an admin instead of confirming itself. */
class HeldGamesApiTest extends IntegrationTest {

    @Autowired
    AutoConfirmService autoConfirm;

    @Test
    void aResultAgainstADisabledPlayerIsHeldForAnAdmin() {
        Player admin = registerAdmin();
        Player alice = registerMember(admin, "alice");
        Player bob = registerMember(admin, "bob");
        long id = alice.api().post("/api/games", game(alice, bob, "WHITE_WIN")).json().path("id").asLong();
        assertThat(admin.api().patch("/api/admin/users/" + bob.id(), Map.of("enabled", false)).status()).isEqualTo(200);
        jdbc.update("update games set created_at = ? where id = ?", Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)), id);

        autoConfirm.scheduled();

        assertThat(alice.api().get("/api/games/" + id).json().path("status").asString()).isEqualTo("PENDING");
        assertThat(rating(alice)).isEqualTo(1500);

        assertThat(alice.api().get("/api/admin/games/held").status()).isEqualTo(403);
        JsonNode held = admin.api().get("/api/admin/games/held").json();
        assertThat(held.size()).isEqualTo(1);
        assertThat(held.get(0).path("id").asLong()).isEqualTo(id);

        assertThat(admin.api().post("/api/admin/games/" + id + "/resolve", Map.of("action", "CONFIRM")).status()).isEqualTo(200);
        assertThat(rating(alice)).isGreaterThan(1500);
        assertThat(admin.api().get("/api/admin/games/held").json().size()).isZero();
    }

    @Test
    void aResultBetweenActivePlayersStillConfirmsItself() {
        Player admin = registerAdmin();
        Player alice = registerMember(admin, "alice");
        Player bob = registerMember(admin, "bob");
        long id = alice.api().post("/api/games", game(alice, bob, "WHITE_WIN")).json().path("id").asLong();
        jdbc.update("update games set created_at = ? where id = ?", Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)), id);

        autoConfirm.scheduled();

        assertThat(alice.api().get("/api/games/" + id).json().path("status").asString()).isEqualTo("CONFIRMED");
        assertThat(admin.api().get("/api/admin/games/held").json().size()).isZero();
    }
}
