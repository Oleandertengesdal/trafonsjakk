package no.trafonsjakk.auth;

import java.time.Instant;
import java.util.List;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.common.Tokens;
import no.trafonsjakk.config.AppProperties;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteService {

    public record CreatedInvite(long id, String code, Instant expiresAt) {
    }

    public record InviteView(long id, PlayerRef createdBy, Instant createdAt, Instant expiresAt,
                             PlayerRef usedBy, Instant usedAt, InviteCode.Status status) {
    }

    private final InviteCodeRepository invites;
    private final UserRepository users;
    private final AppProperties props;

    public InviteService(InviteCodeRepository invites, UserRepository users, AppProperties props) {
        this.invites = invites;
        this.users = users;
        this.props = props;
    }

    @Transactional
    public CreatedInvite create(CurrentUser actor) {
        Instant now = Instant.now();
        if (!actor.admin() && invites.countActiveByCreator(actor.id(), now) >= props.invites().maxActivePerUser()) {
            throw ApiException.conflict("invite.limit", "You already have " + props.invites().maxActivePerUser()
                    + " active invite codes. Revoke one or wait for them to be used.")
                    .with("max", props.invites().maxActivePerUser());
        }
        User creator = users.findById(actor.id()).orElseThrow(() -> ApiException.unauthorized("auth.not_logged_in", "Not logged in"));
        String code = Tokens.inviteCode();
        InviteCode invite = invites.save(new InviteCode(Tokens.sha256(Tokens.normalizeInviteCode(code)), creator, now,
                now.plus(props.invites().ttl())));
        return new CreatedInvite(invite.getId(), code, invite.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public List<InviteView> list(CurrentUser actor) {
        Instant now = Instant.now();
        List<InviteCode> list = actor.admin() ? invites.findAllWithUsers() : invites.findByCreator(actor.id());
        return list.stream()
                .map(i -> new InviteView(i.getId(), PlayerRef.of(i.getCreatedBy()), i.getCreatedAt(), i.getExpiresAt(),
                        PlayerRef.of(i.getUsedBy()), i.getUsedAt(), i.status(now)))
                .toList();
    }

    @Transactional
    public void revoke(long id, CurrentUser actor) {
        InviteCode invite = invites.findById(id).orElseThrow(() -> ApiException.notFound("invite.not_found", "Invite not found"));
        boolean owner = invite.getCreatedBy() != null && invite.getCreatedBy().getId() == actor.id();
        if (!owner && !actor.admin()) {
            throw ApiException.forbidden("invite.not_owner", "You can only revoke your own invite codes");
        }
        if (invite.status(Instant.now()) != InviteCode.Status.ACTIVE) {
            throw ApiException.conflict("invite.inactive", "This invite code is no longer active");
        }
        invite.revoke(Instant.now());
    }

    /** Validates and consumes a code during registration (same transaction as user creation). */
    @Transactional
    public InviteCode redeem(String rawCode) {
        String normalized = Tokens.normalizeInviteCode(rawCode);
        if (normalized.isEmpty()) {
            throw ApiException.badRequest("invite.invalid", "Invalid or expired invite code");
        }
        InviteCode invite = invites.findByCodeHashForUpdate(Tokens.sha256(normalized))
                .orElseThrow(() -> ApiException.badRequest("invite.invalid", "Invalid or expired invite code"));
        if (invite.status(Instant.now()) != InviteCode.Status.ACTIVE) {
            throw ApiException.badRequest("invite.invalid", "Invalid or expired invite code");
        }
        return invite;
    }
}
