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
import no.trafonsjakk.chess.ChessGame;
import no.trafonsjakk.chess.Color;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.user.User;

/**
 * A game played on the website, with the server as arbiter: it checks every move and keeps the clock.
 * See V10 migration and {@link OnlineGameService}.
 */
@Entity
@Table(name = "online_games")
public class OnlineGame {

    public enum Status { ACTIVE, FINISHED, ABORTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "white_id", nullable = false)
    private User white;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "black_id", nullable = false)
    private User black;

    @Column(name = "challenge_id")
    private Long challengeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

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
    @Column(nullable = false, length = 8)
    private Status status = Status.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private GameResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 25)
    private EndReason endReason;

    @Column(columnDefinition = "text")
    private String moves;

    @Column(nullable = false)
    private int plies;

    @Column(name = "last_move", length = 5)
    private String lastMove;

    @Column(name = "white_ms", nullable = false)
    private long whiteMs;

    @Column(name = "black_ms", nullable = false)
    private long blackMs;

    @Column(name = "turn_started_at", nullable = false)
    private Instant turnStartedAt;

    @Column(name = "draw_offer_by")
    private Long drawOfferBy;

    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** Counts changes; the board sends the ply it saw, so a stale or repeated move is refused. */
    @Column(nullable = false)
    private long version;

    protected OnlineGame() {
    }

    public OnlineGame(User white, User black, User createdBy, Long challengeId, boolean rated, TimeControl tc, Instant now) {
        this.white = white;
        this.black = black;
        this.createdBy = createdBy;
        this.challengeId = challengeId;
        this.rated = rated;
        this.kind = tc.kind();
        this.initialSeconds = tc.initialSeconds();
        this.incrementSeconds = tc.incrementSeconds();
        this.daysPerMove = tc.daysPerMove();
        this.startedAt = now;
        setClock(Clock.start(tc, now));
    }

    // ---- derived ------------------------------------------------------------------------------------------

    public TimeControl timeControl() {
        return new TimeControl(kind, initialSeconds, incrementSeconds, daysPerMove);
    }

    public Clock clock() {
        return new Clock(timeControl(), whiteMs, blackMs, plies % 2 == 0 ? Color.WHITE : Color.BLACK, plies,
                turnStartedAt, status == Status.ACTIVE);
    }

    /** Replays the score sheet: the server's own record, so it is always legal. */
    public ChessGame board() {
        return ChessGame.fromMoveText(moves);
    }

    public Color colourOf(long userId) {
        if (white.getId() == userId) {
            return Color.WHITE;
        }
        return black.getId() == userId ? Color.BLACK : null;
    }

    public User player(Color c) {
        return c == Color.WHITE ? white : black;
    }

    public boolean hasPlayer(long userId) {
        return colourOf(userId) != null;
    }

    public boolean active() {
        return status == Status.ACTIVE;
    }

    // ---- changes ------------------------------------------------------------------------------------------

    private void setClock(Clock c) {
        this.whiteMs = c.whiteMs();
        this.blackMs = c.blackMs();
        this.turnStartedAt = c.turnStartedAt();
    }

    /** A move has been checked and played on {@code board}; store it and run the clock on. */
    public void moved(ChessGame board, Clock after) {
        this.moves = board.moveText();
        this.plies = board.sans().size();
        this.lastMove = board.lastUci();
        this.drawOfferBy = null; // moving instead of accepting declines an offer (and withdraws your own)
        setClock(after);
        this.version++;
    }

    public void finish(Ending ending, Instant now) {
        setClock(clock().stop(now));
        this.status = Status.FINISHED;
        this.result = ending.result();
        this.endReason = ending.reason();
        this.drawOfferBy = null;
        this.endedAt = now;
        this.version++;
    }

    public void abort(Instant now) {
        setClock(clock().stop(now));
        this.status = Status.ABORTED;
        this.drawOfferBy = null;
        this.endedAt = now;
        this.version++;
    }

    public void offerDraw(long userId) {
        this.drawOfferBy = userId;
        this.version++;
    }

    public void clearDrawOffer() {
        this.drawOfferBy = null;
        this.version++;
    }

    public void becameGame(long gameId) {
        this.gameId = gameId;
    }

    public Long getId() { return id; }
    public User getWhite() { return white; }
    public User getBlack() { return black; }
    public User getCreatedBy() { return createdBy; }
    public Long getChallengeId() { return challengeId; }
    public boolean isRated() { return rated; }
    public Status getStatus() { return status; }
    public GameResult getResult() { return result; }
    public EndReason getEndReason() { return endReason; }
    public String getMoves() { return moves; }
    public int getPlies() { return plies; }
    public String getLastMove() { return lastMove; }
    public Long getDrawOfferBy() { return drawOfferBy; }
    public Long getGameId() { return gameId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public long getVersion() { return version; }
}
