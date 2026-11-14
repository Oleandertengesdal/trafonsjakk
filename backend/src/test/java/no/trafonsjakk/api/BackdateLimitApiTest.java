package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** With the production limit: members log recent games, an admin can enter old ones. */
@TestPropertySource(properties = "app.games.max-backdate=30d")
class BackdateLimitApiTest extends IntegrationTest {

    @Test
    void membersCanDateAGameAMonthBackAndAdminsFurther() {
        Player admin = registerAdmin();
        Player alice = registerMember(admin, "alice");
        Player bob = registerMember(admin, "bob");
        Instant recent = Instant.now().minus(29, ChronoUnit.DAYS);
        Instant old = Instant.now().minus(31, ChronoUnit.DAYS);

        assertThat(alice.api().post("/api/games", game(alice.id(), bob.id(), "DRAW", recent)).status()).isEqualTo(201);
        Response tooOld = alice.api().post("/api/games", game(alice.id(), bob.id(), "DRAW", old));
        assertThat(tooOld.status()).isEqualTo(400);
        assertThat(tooOld.code()).isEqualTo("game.too_old");
        assertThat(admin.api().post("/api/games", game(alice.id(), bob.id(), "DRAW", old)).status()).isEqualTo(201);
    }
}
