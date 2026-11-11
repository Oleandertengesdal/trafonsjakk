package no.trafonsjakk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import no.trafonsjakk.auth.InviteCode.Status;
import org.junit.jupiter.api.Test;

class InviteCodeTest {

    private final Instant now = Instant.parse("2026-11-11T12:00:00Z");

    private InviteCode code(Duration validFor, int maxUses) {
        return new InviteCode("hash", null, now.minus(Duration.ofDays(1)), now.plus(validFor), maxUses);
    }

    @Test
    void activeUntilItExpires() {
        assertThat(code(Duration.ofHours(1), 1).status(now)).isEqualTo(Status.ACTIVE);
        assertThat(code(Duration.ofHours(-1), 1).status(now)).isEqualTo(Status.EXPIRED);
    }

    @Test
    void usedUpAndRevokedTakePrecedenceOverExpiry() {
        InviteCode used = code(Duration.ofHours(-1), 1);
        used.recordUse();
        assertThat(used.status(now)).isEqualTo(Status.USED);

        InviteCode revoked = code(Duration.ofHours(1), 1);
        revoked.recordUse();
        revoked.revoke(now);
        assertThat(revoked.status(now)).isEqualTo(Status.REVOKED);
    }

    @Test
    void aCodeForSeveralPeopleStaysActiveUntilItsLastUse() {
        InviteCode three = code(Duration.ofHours(1), 3);
        three.recordUse();
        three.recordUse();
        assertThat(three.status(now)).isEqualTo(Status.ACTIVE);
        assertThat(three.getUseCount()).isEqualTo(2);
        three.recordUse();
        assertThat(three.status(now)).isEqualTo(Status.USED);
        assertThatThrownBy(three::recordUse).isInstanceOf(IllegalStateException.class);
        assertThat(three.getUseCount()).isEqualTo(3);
    }

    @Test
    void aPartlyUsedCodeExpiresLikeAnyOther() {
        InviteCode five = code(Duration.ofHours(-1), 5);
        five.recordUse();
        assertThat(five.status(now)).isEqualTo(Status.EXPIRED);
    }
}
