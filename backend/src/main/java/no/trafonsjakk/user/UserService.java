package no.trafonsjakk.user;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import no.trafonsjakk.auth.InviteCodeRepository;
import no.trafonsjakk.auth.LoginAttemptService;
import no.trafonsjakk.auth.PasswordResetTokenRepository;
import no.trafonsjakk.auth.TokenService;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.ClientAddress;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.user.UserDtos.AdminUpdateUserRequest;
import no.trafonsjakk.user.UserDtos.AdminUser;
import no.trafonsjakk.user.UserDtos.Me;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final InviteCodeRepository invites;
    private final PasswordResetTokenRepository resets;
    private final LoginAttemptService attempts;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                       InviteCodeRepository invites, PasswordResetTokenRepository resets,
                       LoginAttemptService attempts) {
        this.resets = resets;
        this.attempts = attempts;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.invites = invites;
    }

    @Transactional(readOnly = true)
    public List<PlayerRef> activePlayers() {
        return users.findAllByOrderByUsernameAsc().stream().filter(User::isEnabled).map(PlayerRef::of).toList();
    }

    @Transactional
    public Me updateRealName(CurrentUser actor, String realName) {
        User u = me(actor);
        u.setRealName(realName.trim());
        return Me.of(u);
    }

    /** Changes the password and signs out every other session. */
    @Transactional
    public TokenService.IssuedTokens changePassword(CurrentUser actor, String current, String next, ClientAddress from) {
        User u = me(actor);
        // Someone holding a stolen session must not be able to guess the password here faster than at the login.
        attempts.assertLoginAllowed(u.getUsername(), from);
        if (!passwordEncoder.matches(current, u.getPasswordHash())) {
            attempts.recordFailure(u.getUsername(), from);
            throw ApiException.badRequest("auth.wrong_current_password", "Your current password is incorrect");
        }
        if (next.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("auth.password_too_long", "Password is too long (max 72 bytes)");
        }
        u.setPasswordHash(passwordEncoder.encode(next));
        tokens.revokeAllForUser(u.getId());
        return tokens.issue(u);
    }

    @Transactional(readOnly = true)
    public List<AdminUser> allForAdmin() {
        return users.findAllByOrderByUsernameAsc().stream().map(AdminUser::of).toList();
    }

    @Transactional
    public AdminUser adminUpdate(long id, AdminUpdateUserRequest req, CurrentUser actor) {
        if (id == actor.id()) {
            throw ApiException.badRequest("admin.cannot_change_self", "You can't change your own role or status");
        }
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("user.not_found", "User not found"));
        if (u.getDeletedAt() != null) {
            throw ApiException.conflict("admin.user_deleted", "This person deleted their account");
        }
        if (req.role() != null) {
            u.setRole(req.role());
        }
        if (req.enabled() != null) {
            u.setEnabled(req.enabled());
            if (!req.enabled()) {
                // Signed out everywhere (the access-token filter checks the account on every request)
                // and their unused invite codes stop working.
                tokens.revokeAllForUser(u.getId());
                invites.revokeActiveByCreator(u.getId(), Instant.now());
                resets.revokeOpenForUser(u.getId(), Instant.now());
            }
        }
        return AdminUser.of(u);
    }

    private User me(CurrentUser actor) {
        return users.findById(actor.id()).orElseThrow(() -> ApiException.unauthorized("auth.not_logged_in", "Not logged in"));
    }
}
