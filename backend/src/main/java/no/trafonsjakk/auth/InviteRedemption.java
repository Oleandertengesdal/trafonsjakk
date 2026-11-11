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

/** Someone signed up with an invite code. */
@Entity
@Table(name = "invite_redemptions")
public class InviteRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invite_id")
    private InviteCode invite;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    protected InviteRedemption() {
    }

    public InviteRedemption(InviteCode invite, User user, Instant usedAt) {
        this.invite = invite;
        this.user = user;
        this.usedAt = usedAt;
    }

    public InviteCode getInvite() {
        return invite;
    }

    public User getUser() {
        return user;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
