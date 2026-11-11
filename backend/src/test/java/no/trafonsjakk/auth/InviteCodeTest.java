package no.trafonsjakk.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import no.trafonsjakk.auth.InviteCode.Status;
import org.junit.jupiter.api.Test;

class InviteCodeTest {

    private final Instant now = Instant.parse("2026-11-11T12:00:00Z");

    private InviteCode code(Duration validFor) {
        return new InviteCode("hash", null, now.minus(Duration.ofDays(1)), now.plus(validFor));
    }

    @Test
    void activeUntilItExpires() {
        assertThat(code(Duration.ofHours(1)).status(now)).isEqualTo(Status.ACTIVE);
        assertThat(code(Duration.ofHours(-1)).status(now)).isEqualTo(Status.EXPIRED);
    }

    @Test
    void usedAndRevokedTakePrecedenceOverExpiry() {
        InviteCode used = code(Duration.ofHours(-1));
        used.markUsed(null, now.minus(Duration.ofHours(2)));
        assertThat(used.status(now)).isEqualTo(Status.USED);

        InviteCode revoked = code(Duration.ofHours(1));
        revoked.markUsed(null, now);
        revoked.revoke(now);
        assertThat(revoked.status(now)).isEqualTo(Status.REVOKED);
    }
}
