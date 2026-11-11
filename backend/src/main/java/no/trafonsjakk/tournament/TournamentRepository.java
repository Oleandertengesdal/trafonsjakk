package no.trafonsjakk.tournament;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    List<Tournament> findAllByOrderByCreatedAtDesc();

    /**
     * Locks the tournament row for the rest of the transaction. Every change to a tournament's field,
     * fixtures, bracket or standings takes this lock first, so concurrent requests (two people
     * joining, two results confirmed at once) run one after the other and see each other's changes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tournament t where t.id = :id")
    Optional<Tournament> findByIdForUpdate(@Param("id") Long id);

    @Query("select count(t) from Tournament t where t.winner.id = :userId")
    long countWins(@Param("userId") Long userId);
}
