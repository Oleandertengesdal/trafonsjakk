package no.trafonsjakk.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import no.trafonsjakk.user.User;

/**
 * Registration code, stored as a SHA-256 hash of its normalized form. Usually for one person; an admin
 * can make one for several ({@code maxUses}). Who used it is in {@link InviteRedemption}.
 */
@Entity
@Table(name = "invite_codes")
public class InviteCode {

    public enum Status { ACTIVE, USED, EXPIRED, REVOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "max_uses", nullable = false)
    private int maxUses = 1;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected InviteCode() {
    }

    public InviteCode(String codeHash, User createdBy, Instant createdAt, Instant expiresAt, int maxUses) {
        this.codeHash = codeHash;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
    }

    public Status status(Instant now) {
        if (revokedAt != null) {
            return Status.REVOKED;
        }
        if (useCount >= maxUses) {
            return Status.USED;
        }
        if (expiresAt.isBefore(now)) {
            return Status.EXPIRED;
        }
        return Status.ACTIVE;
    }

    /** Counts one sign-up. Call only on an ACTIVE code that is row-locked. */
    public void recordUse() {
        if (useCount >= maxUses) {
            throw new IllegalStateException("Invite code is used up");
        }
        useCount++;
    }

    public void revoke(Instant at) {
        this.revokedAt = at;
    }

    public Long getId() {
        return id;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public int getUseCount() {
        return useCount;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
