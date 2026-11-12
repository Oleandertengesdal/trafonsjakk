package no.trafonsjakk.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import no.trafonsjakk.rating.Glicko2;

/**
 * A member of the group.
 * <p>
 * The rating columns are a cache owned by {@code RatingService}: JPA never writes them
 * ({@code insertable = false, updatable = false}); they are rebuilt with JDBC from confirmed games.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(name = "real_name", nullable = false, length = 60)
    private String realName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(insertable = false, updatable = false)
    private double rating = Glicko2.DEFAULT_RATING;

    @Column(name = "rating_deviation", insertable = false, updatable = false)
    private double ratingDeviation = Glicko2.DEFAULT_RD;

    @Column(insertable = false, updatable = false)
    private double volatility = Glicko2.DEFAULT_VOLATILITY;

    @Column(name = "rated_games", insertable = false, updatable = false)
    private int ratedGames;

    @Column(name = "peak_rating", insertable = false, updatable = false)
    private double peakRating = Glicko2.DEFAULT_RATING;

    @Column(name = "last_game_at", insertable = false, updatable = false)
    private Instant lastGameAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String username, String realName, String passwordHash, Role role) {
        this.username = username;
        this.realName = realName;
        this.passwordHash = passwordHash;
        this.role = role;
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

    public boolean isProvisional() {
        return ratingDeviation > Glicko2.PROVISIONAL_RD;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Set when the person deleted their account; such an account can never be switched back on. */
    public Instant getDeletedAt() {
        return deletedAt;
    }

    /** Removes everything that identifies the person. Their games stay, under {@code realName}. */
    public void anonymise(String username, String realName, String unusablePasswordHash, Instant at) {
        this.username = username;
        this.realName = realName;
        this.passwordHash = unusablePasswordHash;
        this.role = Role.USER;
        this.enabled = false;
        this.deletedAt = at;
    }

    public double getRating() {
        return rating;
    }

    public double getRatingDeviation() {
        return ratingDeviation;
    }

    public double getVolatility() {
        return volatility;
    }

    public int getRatedGames() {
        return ratedGames;
    }

    public double getPeakRating() {
        return peakRating;
    }

    public Instant getLastGameAt() {
        return lastGameAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
