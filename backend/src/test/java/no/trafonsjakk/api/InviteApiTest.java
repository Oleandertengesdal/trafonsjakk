package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class InviteApiTest extends IntegrationTest {

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
    void codeIsShownOnceAndNeverListed() {
        Response created = alice.api().post("/api/invites");
        assertThat(created.status()).isEqualTo(201);
        String code = created.json().path("code").asString();
        assertThat(code).matches("[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}");
        assertThat(created.json().path("expiresAt").asString()).isNotBlank();

        JsonNode list = alice.api().get("/api/invites").json();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).has("code")).as("only a hash is stored").isFalse();
        assertThat(list.get(0).path("status").asString()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from invite_codes where code_hash like ?", Integer.class, "%" + code + "%"))
                .isZero();
    }

    @Test
    void membersHaveAtMostFiveActiveCodesAdminsHaveNoLimit() {
        long first = 0;
        for (int i = 0; i < 5; i++) {
            Response r = alice.api().post("/api/invites");
            assertThat(r.status()).isEqualTo(201);
            if (i == 0) {
                first = r.json().path("id").asLong();
            }
        }
        Response sixth = alice.api().post("/api/invites");
        assertThat(sixth.status()).isEqualTo(409);
        assertThat(sixth.code()).isEqualTo("invite.limit");
        assertThat(sixth.json().path("params").path("max").asInt()).isEqualTo(5);

        // Revoking or expiry frees a slot
        assertThat(alice.api().delete("/api/invites/" + first).status()).isEqualTo(204);
        assertThat(alice.api().post("/api/invites").status()).isEqualTo(201);
        jdbc.update("update invite_codes set expires_at = ? where id = (select max(id) from invite_codes where created_by = ?)",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), alice.id());
        assertThat(alice.api().post("/api/invites").status()).isEqualTo(201);

        for (int i = 0; i < 7; i++) {
            assertThat(admin.api().post("/api/invites").status()).isEqualTo(201);
        }
    }

    @Test
    void membersSeeTheirOwnCodesAdminsSeeAll() {
        alice.api().post("/api/invites");
        bob.api().post("/api/invites");
        assertThat(alice.api().get("/api/invites").json()).hasSize(1);
        JsonNode all = admin.api().get("/api/invites").json();
        // two used by alice and bob themselves, plus the two new ones
        assertThat(all).hasSize(4);
        long used = 0;
        for (JsonNode i : all) {
            if (i.path("status").asString().equals("USED")) {
                used++;
                assertThat(i.path("usedBy").path("username").asString()).isIn(alice.username(), bob.username());
                assertThat(i.path("usedAt").isNull()).isFalse();
            }
        }
        assertThat(used).isEqualTo(2);
    }

    @Test
    void onlyTheOwnerOrAnAdminCanRevokeAndOnlyActiveCodes() {
        Response created = alice.api().post("/api/invites");
        long id = created.json().path("id").asLong();
        String code = created.json().path("code").asString();

        Response notMine = bob.api().delete("/api/invites/" + id);
        assertThat(notMine.status()).isEqualTo(403);
        assertThat(notMine.code()).isEqualTo("invite.not_owner");
        assertThat(admin.api().delete("/api/invites/" + id).status()).isEqualTo(204);
        assertThat(alice.api().delete("/api/invites/" + id).code()).isEqualTo("invite.inactive");
        assertThat(alice.api().delete("/api/invites/424242").code()).isEqualTo("invite.not_found");

        Response revoked = session().post("/api/auth/register", Map.of("username", uniqueName("eve"), "realName", "Eve",
                "password", PASSWORD, "inviteCode", code));
        assertThat(revoked.status()).isEqualTo(400);
        assertThat(revoked.code()).isEqualTo("invite.invalid");
        assertThat(alice.api().get("/api/invites").json().get(0).path("status").asString()).isEqualTo("REVOKED");
    }

    @Test
    void expiredCodesCannotBeUsed() {
        Response created = alice.api().post("/api/invites");
        jdbc.update("update invite_codes set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.SECONDS)), created.json().path("id").asLong());
        Response r = session().post("/api/auth/register", Map.of("username", uniqueName("late"), "realName", "Late",
                "password", PASSWORD, "inviteCode", created.json().path("code").asString()));
        assertThat(r.code()).isEqualTo("invite.invalid");
        assertThat(alice.api().get("/api/invites").json().get(0).path("status").asString()).isEqualTo("EXPIRED");
        assertThat(alice.api().delete("/api/invites/" + created.json().path("id").asLong()).code()).isEqualTo("invite.inactive");
    }

    @Test
    void theBootstrapCodeStopsWorkingOnceSomeoneHasRegistered() {
        Response r = session().post("/api/auth/register", Map.of("username", uniqueName("sneaky"), "realName", "Sneaky",
                "password", PASSWORD, "inviteCode", BOOTSTRAP_CODE));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("invite.invalid");
    }
}
