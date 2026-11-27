package no.trafonsjakk.online;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import no.trafonsjakk.user.User;

/** An invitation to play: to one member, or open to anyone. See V10 migration. */
@Entity
@Table(name = "online_challenges")
public class OnlineChallenge {

    public enum Status { OPEN, ACCEPTED, DECLINED, CANCELLED }

    /** The colour the creator wants. */
    public enum Colour { WHITE, BLACK, RANDOM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opponent_id")
    private User opponent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Colour colour;

    @Column(nullable = false)
    private boolean rated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private TimeControl.Kind kind;

    @Column(name = "initial_seconds", nullable = false)
    private int initialSeconds;

    @Column(name = "increment_seconds", nullable = false)
    private int incrementSeconds;

    @Column(name = "days_per_move", nullable = false)
    private int daysPerMove;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private Status status = Status.OPEN;

    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected OnlineChallenge() {
    }

    public OnlineChallenge(User createdBy, User opponent, Colour colour, boolean rated, TimeControl tc, Instant now,
                           Instant expiresAt) {
        this.createdBy = createdBy;
        this.opponent = opponent;
        this.colour = colour;
        this.rated = rated;
        this.kind = tc.kind();
        this.initialSeconds = tc.initialSeconds();
        this.incrementSeconds = tc.incrementSeconds();
        this.daysPerMove = tc.daysPerMove();
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public TimeControl timeControl() {
        return new TimeControl(kind, initialSeconds, incrementSeconds, daysPerMove);
    }

    public boolean openAt(Instant now) {
        return status == Status.OPEN && now.isBefore(expiresAt);
    }

    public void accept(long gameId) {
        this.status = Status.ACCEPTED;
        this.gameId = gameId;
    }

    public void decline() {
        this.status = Status.DECLINED;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    public Long getId() { return id; }
    public User getCreatedBy() { return createdBy; }
    public User getOpponent() { return opponent; }
    public Colour getColour() { return colour; }
    public boolean isRated() { return rated; }
    public Status getStatus() { return status; }
    public Long getGameId() { return gameId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
