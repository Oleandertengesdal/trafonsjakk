package no.trafonsjakk.live;

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

/** A game being played right now. See V5 migration. */
@Entity
@Table(name = "live_games")
public class LiveGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "white_id", nullable = false)
    private User white;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "black_id", nullable = false)
    private User black;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "started_by", nullable = false)
    private User startedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LiveStatus status = LiveStatus.LIVE;

    @Column(columnDefinition = "text")
    private String moves;

    /** Counts saves, so a phone with an old copy can't overwrite newer moves. Rows are locked when changed. */
    @Column(nullable = false)
    private long version;

    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LiveGame() {
    }

    public LiveGame(User white, User black, User startedBy, Instant now) {
        this.white = white;
        this.black = black;
        this.startedBy = startedBy;
        this.startedAt = now;
        this.updatedAt = now;
    }

    public boolean hasPlayer(long userId) {
        return white.getId() == userId || black.getId() == userId;
    }

    public void saveMoves(String moves, Instant now) {
        this.moves = moves;
        this.version++;
        this.updatedAt = now;
    }

    public void finish(long gameId, Instant now) {
        this.status = LiveStatus.FINISHED;
        this.gameId = gameId;
        this.version++;
        this.updatedAt = now;
    }

    public void abandon(Instant now) {
        this.status = LiveStatus.ABANDONED;
        this.version++;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public User getWhite() { return white; }
    public User getBlack() { return black; }
    public User getStartedBy() { return startedBy; }
    public LiveStatus getStatus() { return status; }
    public String getMoves() { return moves; }
    public long getVersion() { return version; }
    public Long getGameId() { return gameId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
