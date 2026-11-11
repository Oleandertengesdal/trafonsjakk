package no.trafonsjakk.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.Tokens;
import no.trafonsjakk.config.AppProperties;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues short-lived access JWTs and rotating refresh tokens.
 * Reusing a rotated refresh token revokes the whole family (likely theft).
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    /** Two tabs refreshing at the same moment shouldn't be treated as theft. */
    private static final Duration REUSE_GRACE = Duration.ofSeconds(20);

    public record IssuedTokens(String accessToken, String refreshToken, User user) {
    }

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final AppProperties props;

    public TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens, UserRepository users,
                        AppProperties props) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.props = props;
    }

    @Transactional
    public IssuedTokens issue(User user) {
        return issue(user, UUID.randomUUID());
    }

    private IssuedTokens issue(User user, UUID familyId) {
        Instant now = Instant.now();
        String refresh = Tokens.randomToken();
        refreshTokens.save(new RefreshToken(user.getId(), Tokens.sha256(refresh), familyId, now,
                now.plus(props.jwt().refreshTokenTtl())));
        return new IssuedTokens(accessToken(user, now, familyId), refresh, user);
    }

    /**
     * The access token carries its login session ("sid" = refresh-token family). The request filter
     * only accepts it while that session is live, so logout, a password change, an admin disabling
     * the account or detected refresh-token theft end it immediately, not after 15 minutes.
     */
    private String accessToken(User user, Instant now, UUID familyId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().accessTokenTtl()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim("sid", familyId.toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Rotates a refresh token. noRollbackFor so that a theft-triggered family revoke is committed. */
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedTokens rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("auth.session_expired", "Session expired");
        }
        Instant now = Instant.now();
        RefreshToken token = refreshTokens.findByTokenHash(Tokens.sha256(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("auth.session_expired", "Session expired"));

        if (token.getRevokedAt() != null) {
            if (token.getRevokedAt().isBefore(now.minus(REUSE_GRACE))) {
                log.warn("Refresh token reuse detected for user {}; revoking token family", token.getUserId());
                refreshTokens.revokeFamily(token.getFamilyId(), now);
            }
            throw ApiException.unauthorized("auth.session_expired", "Session expired");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw ApiException.unauthorized("auth.session_expired", "Session expired");
        }
        User user = users.findById(token.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> ApiException.unauthorized("auth.session_expired", "Session expired"));

        token.revoke(now);
        return issue(user, token.getFamilyId());
    }

    /** Logout: ends this login session (and with it the session's access token). */
    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(Tokens.sha256(rawRefreshToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void revokeAllForUser(long userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    /** Housekeeping: drop tokens that expired more than a day ago. */
    @Scheduled(cron = "0 17 4 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = refreshTokens.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }
}
