package no.trafonsjakk.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.ClientAddress;
import no.trafonsjakk.common.Tokens;
import no.trafonsjakk.config.AppProperties;
import no.trafonsjakk.user.AccountService;
import no.trafonsjakk.user.Role;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String BAD_CREDENTIALS = "Invalid username or password";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final InviteService invites;
    private final LoginAttemptService attempts;
    private final AppProperties props;
    /** Used to spend the same BCrypt time when the user doesn't exist (no username enumeration). */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                       InviteService invites, LoginAttemptService attempts, AppProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.invites = invites;
        this.attempts = attempts;
        this.props = props;
        this.dummyHash = passwordEncoder.encode(Tokens.randomToken());
    }

    /**
     * Not transactional on purpose: BCrypt takes a second or more on a small server, and a transaction
     * would hold one of the few database connections for all of it. The lookup and the token each
     * use their own short one.
     */
    public TokenService.IssuedTokens login(String username, String password, ClientAddress ip) {
        attempts.assertLoginAllowed(username, ip);
        User user = users.findByUsernameIgnoreCase(username.trim()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean ok = passwordEncoder.matches(password, hash) && user != null;
        if (!ok) {
            attempts.recordFailure(username, ip);
            throw ApiException.unauthorized("auth.bad_credentials", BAD_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            throw ApiException.forbidden("auth.account_disabled", "This account has been disabled. Ask an admin.");
        }
        attempts.recordSuccess(username);
        return tokens.issue(user);
    }

    @Transactional
    public TokenService.IssuedTokens register(AuthDtos.RegisterRequest req, ClientAddress ip) {
        attempts.assertRegistrationAllowed(ip);
        assertPasswordFitsBcrypt(req.password());

        String username = req.username().trim();
        Role role = Role.USER;
        InviteCode invite = null;
        String bootstrap = props.bootstrapInviteCode();
        if (users.count() == 0 && bootstrap != null && !bootstrap.isBlank()
                && Tokens.constantTimeEquals(bootstrap.trim(), req.inviteCode().trim())) {
            role = Role.ADMIN;
            log.info("Bootstrap admin '{}' created. Remove BOOTSTRAP_INVITE_CODE now.", username);
        } else {
            invite = invites.redeem(req.inviteCode());
        }
        // Only after the invite is known to be valid, so strangers can't probe which usernames exist.
        // (Nothing is consumed if this throws: the transaction rolls back.)
        // deleted_<id> is what a deleted account is renamed to (AccountService), so it's never free to take
        if (users.existsByUsernameIgnoreCase(username)
                || username.toLowerCase(Locale.ROOT).startsWith(AccountService.DELETED_PREFIX)) {
            throw ApiException.conflict("auth.username_taken", "That username is taken");
        }

        User user;
        try {
            user = users.saveAndFlush(new User(username, req.realName().trim(), passwordEncoder.encode(req.password()), role));
        } catch (DataIntegrityViolationException e) {
            // Someone took the same name a moment ago (the unique index caught it)
            throw ApiException.conflict("auth.username_taken", "That username is taken");
        }
        if (invite != null) {
            invites.recordUse(invite, user);
        }
        return tokens.issue(user);
    }

    static void assertPasswordFitsBcrypt(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("auth.password_too_long", "Password is too long (max 72 bytes)");
        }
    }
}
