package no.trafonsjakk.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    /** Both optional: one person, the default lifetime (a week). Several people is admin-only. */
    public record CreateInviteRequest(@Min(1) @Max(500) Integer maxUses, @Min(1) Integer validHours) {
    }

    public record CreatedInvite(long id, String code, Instant expiresAt, int maxUses) {
    }

    public record InviteUse(PlayerRef player, Instant usedAt) {
    }

    public record InviteView(long id, PlayerRef createdBy, Instant createdAt, Instant expiresAt, int maxUses,
                             int useCount, List<InviteUse> uses, InviteCode.Status status) {
    }

    /** The longest an admin's code can stay open (90 days). Members get at most the default lifetime. */
    static final int ADMIN_MAX_HOURS = 90 * 24;

    private final InviteCodeRepository invites;
    private final InviteRedemptionRepository redemptions;
    private final UserRepository users;
    private final AppProperties props;

    public InviteService(InviteCodeRepository invites, InviteRedemptionRepository redemptions, UserRepository users,
                         AppProperties props) {
        this.invites = invites;
        this.redemptions = redemptions;
        this.users = users;
        this.props = props;
    }

    @Transactional
    public CreatedInvite create(CurrentUser actor, CreateInviteRequest req) {
        Instant now = Instant.now();
        int maxUses = req == null || req.maxUses() == null ? 1 : req.maxUses();
        if (maxUses > 1 && !actor.admin()) {
            throw ApiException.forbidden("invite.multi_use_admin_only", "Only admins can make a code for several people");
        }
        int defaultHours = (int) props.invites().ttl().toHours();
        int maxHours = actor.admin() ? ADMIN_MAX_HOURS : defaultHours;
        int hours = req == null || req.validHours() == null ? defaultHours : req.validHours();
        if (hours > maxHours) {
            throw ApiException.badRequest("invite.too_long", "An invite code can be open for at most " + maxHours + " hours")
                    .with("maxHours", maxHours);
        }
        if (!actor.admin() && invites.countActiveByCreator(actor.id(), now) >= props.invites().maxActivePerUser()) {
            throw ApiException.conflict("invite.limit", "You already have " + props.invites().maxActivePerUser()
                    + " active invite codes. Revoke one or wait for them to be used.")
                    .with("max", props.invites().maxActivePerUser());
        }
        User creator = users.findById(actor.id()).orElseThrow(() -> ApiException.unauthorized("auth.not_logged_in", "Not logged in"));
        String code = Tokens.inviteCode();
        InviteCode invite = invites.save(new InviteCode(Tokens.sha256(Tokens.normalizeInviteCode(code)), creator, now,
                now.plus(Duration.ofHours(hours)), maxUses));
        return new CreatedInvite(invite.getId(), code, invite.getExpiresAt(), invite.getMaxUses());
    }

    @Transactional(readOnly = true)
    public List<InviteView> list(CurrentUser actor) {
        Instant now = Instant.now();
        List<InviteCode> list = actor.admin() ? invites.findAllWithUsers() : invites.findByCreator(actor.id());
        Map<Long, List<InviteUse>> uses = list.isEmpty() ? Map.of()
                : redemptions.findByInviteIds(list.stream().map(InviteCode::getId).toList()).stream()
                        .collect(Collectors.groupingBy(r -> r.getInvite().getId(),
                                Collectors.mapping(r -> new InviteUse(PlayerRef.of(r.getUser()), r.getUsedAt()), Collectors.toList())));
        return list.stream()
                .map(i -> new InviteView(i.getId(), PlayerRef.of(i.getCreatedBy()), i.getCreatedAt(), i.getExpiresAt(),
                        i.getMaxUses(), i.getUseCount(), uses.getOrDefault(i.getId(), List.of()), i.status(now)))
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

    /** Records that {@code user} signed up with {@code invite} (locked by {@link #redeem}, same transaction). */
    @Transactional
    public void recordUse(InviteCode invite, User user) {
        invite.recordUse();
        redemptions.save(new InviteRedemption(invite, user, Instant.now()));
    }

    /**
     * Checks a code during registration and locks it (same transaction as creating the user), so people
     * signing up at the same moment are counted one after the other and never exceed {@code maxUses}.
     */
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
