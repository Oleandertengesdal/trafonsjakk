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

/** A one-time link an admin made for someone who forgot their password. Only the SHA-256 hash is stored. */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(User user, String tokenHash, Long createdBy, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** Usable: not used, not replaced or cancelled, not expired, and the account is enabled. */
    public boolean isUsable(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now) && user.isEnabled();
    }

    public void markUsed(Instant at) {
        this.usedAt = at;
    }

    public User getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
