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
        Awards awards,
        String publicUrl,
        GoogleChat googleChat,
        Push push) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Cookies(boolean secure) {
    }

    /** {@code maxBackdate}: how far back a member may date a game they log (admins aren't limited). */
    public record Games(Duration autoConfirmAfter, Duration maxBackdate) {
    }

    /** Monthly awards: months in {@code zone}, decided {@code decideAfter} after they end, {@code minGames} to qualify. */
    public record Awards(String zone, Duration decideAfter, int minGames) {
    }

    public record Invites(Duration ttl, int maxActivePerUser) {
    }

    /**
     * Messages to a Google Chat space when a live game starts or a game is logged. {@code webhookUrl} is the
     * space's incoming-webhook link, which carries its own secret token: environment variable only, never
     * logged. Blank turns notifications off. {@code minInterval} keeps within Chat's one message a second.
     */
    public record GoogleChat(String webhookUrl, Duration minInterval) {
    }

    /**
     * Web Push (notifications on members' phones and computers). A VAPID key pair in the usual base64url form
     * (e.g. from {@code npx web-push generate-vapid-keys}); the private key is a secret: environment variable
     * only. {@code subject} is a mailto: or https: contact for the push services (default: the site address).
     * Blank keys turn push off. {@code allowLocalEndpoints} is for tests only.
     */
    public record Push(String vapidPublicKey, String vapidPrivateKey, String subject, boolean allowLocalEndpoints) {
    }

    /**
     * Per-IP limits apply to the visitor's address; the per-edge limits to the address that connected to
     * the hosting edge, which can't be faked (see ClientAddress) and is shared by everyone behind Vercel.
     */
    public record Security(int maxFailedLoginsPerUser, int maxFailedLoginsPerIp, Duration lockoutDuration,
                           int maxRegistrationsPerIpPerHour, int maxFailedLoginsPerEdge, int maxRegistrationsPerEdgePerHour) {
    }
}
