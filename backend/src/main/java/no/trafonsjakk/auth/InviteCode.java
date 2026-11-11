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

/** Single-use registration code, stored as a SHA-256 hash of its normalized form. */
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by")
    private User usedBy;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected InviteCode() {
    }

    public InviteCode(String codeHash, User createdBy, Instant createdAt, Instant expiresAt) {
        this.codeHash = codeHash;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Status status(Instant now) {
        if (revokedAt != null) {
            return Status.REVOKED;
        }
        if (usedAt != null) {
            return Status.USED;
        }
        if (expiresAt.isBefore(now)) {
            return Status.EXPIRED;
        }
        return Status.ACTIVE;
    }

    public void markUsed(User user, Instant at) {
        this.usedBy = user;
        this.usedAt = at;
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

    public User getUsedBy() {
        return usedBy;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
