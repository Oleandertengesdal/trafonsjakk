package no.trafonsjakk.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import no.trafonsjakk.user.User;

@Entity
@Table(name = "tournament_participants")
public class TournamentParticipant {

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "tournament_id")
        private Long tournamentId;

        @Column(name = "user_id")
        private Long userId;

        protected Key() {
        }

        public Key(Long tournamentId, Long userId) {
            this.tournamentId = tournamentId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(tournamentId, k.tournamentId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tournamentId, userId);
        }
    }

    @EmbeddedId
    private Key id;

    @MapsId("tournamentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column
    private Integer seed;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected TournamentParticipant() {
    }

    public TournamentParticipant(Tournament tournament, User user) {
        this.id = new Key(tournament.getId(), user.getId());
        this.tournament = tournament;
        this.user = user;
        this.joinedAt = Instant.now();
    }

    public Tournament getTournament() {
        return tournament;
    }

    public User getUser() {
        return user;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }
}
