package no.trafonsjakk.tournament;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import no.trafonsjakk.user.User;

@Entity
@Table(name = "tournaments")
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TournamentFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TournamentStatus status = TournamentStatus.REGISTRATION;

    @Column(name = "double_round_robin", nullable = false)
    private boolean doubleRoundRobin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected Tournament() {
    }

    public Tournament(String name, TournamentFormat format, boolean doubleRoundRobin, User createdBy) {
        this.name = name;
        this.format = format;
        this.doubleRoundRobin = doubleRoundRobin;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void start(Instant at) {
        status = TournamentStatus.IN_PROGRESS;
        startedAt = at;
    }

    public void complete(User champion, Instant at) {
        status = TournamentStatus.COMPLETED;
        winner = champion;
        completedAt = at;
    }

    /** Undo completion after an admin voids or corrects a deciding game. */
    public void reopen() {
        status = TournamentStatus.IN_PROGRESS;
        winner = null;
        completedAt = null;
    }

    public void cancel(Instant at) {
        status = TournamentStatus.CANCELLED;
        completedAt = at;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public TournamentFormat getFormat() {
        return format;
    }

    public TournamentStatus getStatus() {
        return status;
    }

    public boolean isDoubleRoundRobin() {
        return doubleRoundRobin;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public User getWinner() {
        return winner;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
