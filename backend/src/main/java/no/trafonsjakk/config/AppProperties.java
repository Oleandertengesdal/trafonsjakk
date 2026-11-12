package no.trafonsjakk.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code app.*} configuration. Secrets arrive through environment variables. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cookies cookies,
        String bootstrapInviteCode,
        Games games,
        Invites invites,
        Security security,
        Awards awards) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Cookies(boolean secure) {
    }

    public record Games(Duration autoConfirmAfter) {
    }

    /** Monthly awards: months in {@code zone}, decided {@code decideAfter} after they end, {@code minGames} to qualify. */
    public record Awards(String zone, Duration decideAfter, int minGames) {
    }

    public record Invites(Duration ttl, int maxActivePerUser) {
    }

    public record Security(int maxFailedLoginsPerUser, int maxFailedLoginsPerIp, Duration lockoutDuration,
                           int maxRegistrationsPerIpPerHour) {
    }
}
