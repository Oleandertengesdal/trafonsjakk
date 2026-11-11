package no.trafonsjakk.game;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import no.trafonsjakk.tournament.TournamentMatch;
import no.trafonsjakk.user.User;

@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "white_id", nullable = false)
    private User white;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "black_id", nullable = false)
    private User black;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GameResult result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GameStatus status = GameStatus.PENDING;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by", nullable = false)
    private User reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "dispute_reason", length = 500)
    private String disputeReason;

    @Column(length = 80)
    private String opening;

    @Column(length = 3)
    private String eco;

    @Column(length = 1000)
    private String notes;

    @Column(columnDefinition = "text")
    private String moves;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_match_id")
    private TournamentMatch match;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Game() {
    }

    public Game(User white, User black, GameResult result, Instant playedAt, User reportedBy) {
        this.white = white;
        this.black = black;
        this.result = result;
        this.playedAt = playedAt;
        this.reportedBy = reportedBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean involves(long userId) {
        return white.getId() == userId || black.getId() == userId;
    }

    public boolean reportedByAPlayer() {
        return involves(reportedBy.getId());
    }

    /** Who may confirm or dispute: the opponent of a player-reporter, or either player if an admin reported it. */
    public boolean canBeConfirmedBy(long userId) {
        if (!involves(userId)) {
            return false;
        }
        return !reportedByAPlayer() || reportedBy.getId() != userId;
    }

    public void confirm(User by, Instant at) {
        this.status = GameStatus.CONFIRMED;
        this.confirmedBy = by;
        this.confirmedAt = at;
    }

    public void dispute(String reason) {
        this.status = GameStatus.DISPUTED;
        this.disputeReason = reason;
    }

    public void voidGame() {
        this.status = GameStatus.VOIDED;
    }

    public Long getId() {
        return id;
    }

    public User getWhite() {
        return white;
    }

    public User getBlack() {
        return black;
    }

    public GameResult getResult() {
        return result;
    }

    public void setResult(GameResult result) {
        this.result = result;
    }

    public GameStatus getStatus() {
        return status;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }

    public User getReportedBy() {
        return reportedBy;
    }

    public User getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getDisputeReason() {
        return disputeReason;
    }

    public String getOpening() {
        return opening;
    }

    public void setOpening(String opening) {
        this.opening = opening;
    }

    public String getEco() {
        return eco;
    }

    public void setEco(String eco) {
        this.eco = eco;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getMoves() {
        return moves;
    }

    public void setMoves(String moves) {
        this.moves = moves;
    }

    public TournamentMatch getMatch() {
        return match;
    }

    public void setMatch(TournamentMatch match) {
        this.match = match;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The winner's user, or null for a draw. */
    public User winner() {
        return switch (result) {
            case WHITE_WIN -> white;
            case BLACK_WIN -> black;
            case DRAW -> null;
        };
    }
}
