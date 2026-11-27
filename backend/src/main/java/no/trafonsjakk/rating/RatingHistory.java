package no.trafonsjakk.rating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Read-only view of a rating change. Rows are written in bulk by {@link RatingService}. */
@Entity
@Immutable
@Table(name = "rating_history")
public class RatingHistory {

    @Id
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rating_before", nullable = false)
    private double ratingBefore;

    @Column(name = "rating_after", nullable = false)
    private double ratingAfter;

    @Column(name = "rd_before", nullable = false)
    private double rdBefore;

    @Column(name = "rd_after", nullable = false)
    private double rdAfter;

    @Column(name = "volatility_after", nullable = false)
    private double volatilityAfter;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    /** "OTB" or "ONLINE": which rating this row belongs to (the game's mode). */
    @Column(nullable = false, length = 10)
    private String pool;

    protected RatingHistory() {
    }

    public Long getId() {
        return id;
    }

    public Long getGameId() {
        return gameId;
    }

    public Long getUserId() {
        return userId;
    }

    public double getRatingBefore() {
        return ratingBefore;
    }

    public double getRatingAfter() {
        return ratingAfter;
    }

    public double getRdBefore() {
        return rdBefore;
    }

    public double getRdAfter() {
        return rdAfter;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }

    public boolean online() {
        return "ONLINE".equals(pool);
    }
}
