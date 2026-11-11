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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.user.User;

/** One pairing in a tournament. A knockout match may need several games (draws are replayed). */
@Entity
@Table(name = "tournament_matches")
public class TournamentMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "board_number", nullable = false)
    private int boardNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_id")
    private User white;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "black_id")
    private User black;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MatchStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private GameResult result;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Version
    private long version;

    protected TournamentMatch() {
    }

    public TournamentMatch(Tournament tournament, int roundNumber, int boardNumber, User white, User black,
                           MatchStatus status) {
        this.tournament = tournament;
        this.roundNumber = roundNumber;
        this.boardNumber = boardNumber;
        this.white = white;
        this.black = black;
        this.status = status;
    }

    public boolean involves(long userId) {
        return (white != null && white.getId() == userId) || (black != null && black.getId() == userId);
    }

    public Long getId() {
        return id;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public int getBoardNumber() {
        return boardNumber;
    }

    public User getWhite() {
        return white;
    }

    public void setWhite(User white) {
        this.white = white;
    }

    public User getBlack() {
        return black;
    }

    public void setBlack(User black) {
        this.black = black;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public GameResult getResult() {
        return result;
    }

    public void setResult(GameResult result) {
        this.result = result;
    }

    public User getWinner() {
        return winner;
    }

    public void setWinner(User winner) {
        this.winner = winner;
    }
}
