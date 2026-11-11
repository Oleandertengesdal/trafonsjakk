package no.trafonsjakk.auth;

import java.time.Duration;
import java.time.Instant;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.common.Tokens;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Forgot password" without email: an admin makes a one-time link (valid 24 h) and sends it to the
 * person, who picks a new password. The admin never sees or sets the password.
 */
@Service
public class PasswordResetService {

    static final Duration VALID_FOR = Duration.ofHours(24);

    public record CreatedReset(String token, Instant expiresAt) {
    }

    public record ResetTarget(String username, String realName, Instant expiresAt) {
    }

    private final PasswordResetTokenRepository resets;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final LoginAttemptService attempts;

    public PasswordResetService(PasswordResetTokenRepository resets, UserRepository users, PasswordEncoder passwordEncoder,
                                TokenService tokens, LoginAttemptService attempts) {
        this.resets = resets;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.attempts = attempts;
    }

    /** Admin only (enforced by the URL). Replaces any earlier open link for the same person. */
    @Transactional
    public CreatedReset create(long userId, CurrentUser admin) {
        if (userId == admin.id()) {
            throw ApiException.badRequest("admin.cannot_change_self", "Change your own password in Settings");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("user.not_found", "User not found"));
        if (!user.isEnabled()) {
            throw ApiException.conflict("user.disabled", "This account is disabled. Enable it first.");
        }
        Instant now = Instant.now();
        resets.revokeOpenForUser(user.getId(), now);
        String token = Tokens.randomToken();
        PasswordResetToken saved = resets.save(new PasswordResetToken(user, Tokens.sha256(token), admin.id(), now, now.plus(VALID_FOR)));
        return new CreatedReset(token, saved.getExpiresAt());
    }

    /** For the reset page: whose password this link is for. */
    @Transactional(readOnly = true)
    public ResetTarget check(String token) {
        PasswordResetToken t = resets.findByTokenHash(Tokens.sha256(token))
                .filter(r -> r.isUsable(Instant.now()))
                .orElseThrow(PasswordResetService::invalid);
        return new ResetTarget(t.getUser().getUsername(), t.getUser().getRealName(), t.getExpiresAt());
    }

    /**
     * Sets the new password, uses up the link, ends every session of that person (someone else may have
     * been logged in as them), clears a login lockout, and logs this browser in.
     */
    @Transactional
    public TokenService.IssuedTokens reset(String token, String newPassword) {
        PasswordResetToken t = resets.findByTokenHashForUpdate(Tokens.sha256(token))
                .filter(r -> r.isUsable(Instant.now()))
                .orElseThrow(PasswordResetService::invalid);
        AuthService.assertPasswordFitsBcrypt(newPassword);
        User user = t.getUser();
        Instant now = Instant.now();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        t.markUsed(now);
        resets.revokeOpenForUser(user.getId(), now);
        tokens.revokeAllForUser(user.getId());
        attempts.recordSuccess(user.getUsername());
        return tokens.issue(user);
    }

    private static ApiException invalid() {
        return ApiException.badRequest("reset.invalid", "This link is invalid, used or expired. Ask an admin for a new one.");
    }
}
