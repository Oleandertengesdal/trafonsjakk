package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
                assertThat(i.path("uses")).hasSize(1);
                assertThat(i.path("uses").get(0).path("player").path("username").asString()).isIn(alice.username(), bob.username());
                assertThat(i.path("uses").get(0).path("usedAt").isNull()).isFalse();
                assertThat(i.path("useCount").asInt()).isEqualTo(1);
                assertThat(i.path("maxUses").asInt()).isEqualTo(1);
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

    // ---- codes for several people (admin only) ------------------------------------------------

    private Response signUp(String code, String name) {
        return session().post("/api/auth/register", Map.of("username", uniqueName(name), "realName", name,
                "password", PASSWORD, "inviteCode", code));
    }

    @Test
    void anAdminCodeCanSignUpSeveralPeopleUpToItsLimit() {
        Response created = admin.api().post("/api/invites", Map.of("maxUses", 3, "validHours", 48));
        assertThat(created.status()).as("%s", created).isEqualTo(201);
        assertThat(created.json().path("maxUses").asInt()).isEqualTo(3);
        Instant expires = Instant.parse(created.json().path("expiresAt").asString());
        assertThat(expires).isBetween(Instant.now().plus(47, ChronoUnit.HOURS), Instant.now().plus(49, ChronoUnit.HOURS));
        String code = created.json().path("code").asString();

        for (String n : List.of("one", "two", "three")) {
            assertThat(signUp(code, n).status()).isEqualTo(201);
        }
        Response fourth = signUp(code, "four");
        assertThat(fourth.code()).isEqualTo("invite.invalid");

        JsonNode view = admin.api().get("/api/invites").json();
        JsonNode invite = null;
        for (JsonNode i : view) {
            if (i.path("id").asLong() == created.json().path("id").asLong()) {
                invite = i;
            }
        }
        assertThat(invite).isNotNull();
        assertThat(invite.path("status").asString()).isEqualTo("USED");
        assertThat(invite.path("useCount").asInt()).isEqualTo(3);
        assertThat(invite.path("uses")).hasSize(3);
        assertThat(invite.path("uses").get(0).path("player").path("realName").asString()).isEqualTo("one");
    }

    @Test
    void aPartlyUsedCodeStaysActiveAndCanBeRevoked() {
        JsonNode created = admin.api().post("/api/invites", Map.of("maxUses", 5)).json();
        String code = created.path("code").asString();
        signUp(code, "early");
        JsonNode listed = admin.api().get("/api/invites").json().get(0);
        assertThat(listed.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(listed.path("useCount").asInt()).isEqualTo(1);
        assertThat(Instant.parse(listed.path("expiresAt").asString()))
                .as("default lifetime is a week").isAfter(Instant.now().plus(6, ChronoUnit.DAYS));

        assertThat(admin.api().delete("/api/invites/" + created.path("id").asLong()).status()).isEqualTo(204);
        assertThat(signUp(code, "late").code()).isEqualTo("invite.invalid");
        assertThat(admin.api().get("/api/invites").json().get(0).path("uses")).as("who already joined stays listed").hasSize(1);
    }

    @Test
    void anExpiredMultiUseCodeStopsWorking() {
        JsonNode created = admin.api().post("/api/invites", Map.of("maxUses", 10, "validHours", 1)).json();
        jdbc.update("update invite_codes set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), created.path("id").asLong());
        assertThat(signUp(created.path("code").asString(), "late").code()).isEqualTo("invite.invalid");
        assertThat(admin.api().get("/api/invites").json().get(0).path("status").asString()).isEqualTo("EXPIRED");
    }

    @Test
    void membersCanOnlyMakeSinglePersonCodesForAtMostAWeek() {
        Response multi = alice.api().post("/api/invites", Map.of("maxUses", 2));
        assertThat(multi.status()).isEqualTo(403);
        assertThat(multi.code()).isEqualTo("invite.multi_use_admin_only");
        assertThat(alice.api().post("/api/invites", Map.of("maxUses", 1)).status()).isEqualTo(201);

        Response long1 = alice.api().post("/api/invites", Map.of("validHours", 169));
        assertThat(long1.status()).isEqualTo(400);
        assertThat(long1.code()).isEqualTo("invite.too_long");
        assertThat(long1.json().path("params").path("maxHours").asInt()).isEqualTo(168);
        JsonNode shortOne = alice.api().post("/api/invites", Map.of("validHours", 2)).json();
        assertThat(Instant.parse(shortOne.path("expiresAt").asString())).isBefore(Instant.now().plus(3, ChronoUnit.HOURS));
        // No body at all still works (the old app sends none)
        assertThat(alice.api().post("/api/invites").status()).isEqualTo(201);
    }

    @Test
    void adminLimitsAreValidated() {
        Response zero = admin.api().post("/api/invites", Map.of("maxUses", 0));
        assertThat(zero.code()).isEqualTo("validation");
        assertThat(zero.json().path("errorCodes").path("maxUses").asString()).isEqualTo("Min");
        assertThat(admin.api().post("/api/invites", Map.of("maxUses", 501)).json().path("errorCodes").path("maxUses").asString())
                .isEqualTo("Max");
        assertThat(admin.api().post("/api/invites", Map.of("validHours", 0)).json().path("errorCodes").path("validHours").asString())
                .isEqualTo("Min");
        Response tooLong = admin.api().post("/api/invites", Map.of("validHours", 2161));
        assertThat(tooLong.code()).isEqualTo("invite.too_long");
        assertThat(tooLong.json().path("params").path("maxHours").asInt()).isEqualTo(2160);
        assertThat(admin.api().post("/api/invites", Map.of("maxUses", 500, "validHours", 2160)).status()).isEqualTo(201);
    }

    @Test
    void aMultiUseCodeDoesNotCountAgainstTheMemberLimitOnceFull() {
        // Members' limit counts active codes; a used-up code frees the slot
        for (int i = 0; i < 5; i++) {
            String code = alice.api().post("/api/invites").json().path("code").asString();
            if (i == 0) {
                assertThat(signUp(code, "friend").status()).isEqualTo(201);
            }
        }
        assertThat(alice.api().post("/api/invites").status()).isEqualTo(201);
        assertThat(alice.api().post("/api/invites").code()).isEqualTo("invite.limit");
    }
}
