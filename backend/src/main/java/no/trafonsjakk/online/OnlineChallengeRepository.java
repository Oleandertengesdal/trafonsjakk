package no.trafonsjakk.online;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OnlineChallengeRepository extends JpaRepository<OnlineChallenge, Long> {

    /** Locks the row, so two people accepting at once are handled one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OnlineChallenge c join fetch c.createdBy left join fetch c.opponent where c.id = :id")
    Optional<OnlineChallenge> findByIdForUpdate(@Param("id") Long id);

    /** Challenges that can still be accepted, newest first. */
    @Query("select c from OnlineChallenge c join fetch c.createdBy left join fetch c.opponent "
            + "where c.status = no.trafonsjakk.online.OnlineChallenge.Status.OPEN and c.expiresAt > :now "
            + "order by c.createdAt desc, c.id desc")
    List<OnlineChallenge> findOpen(@Param("now") Instant now);

    @Query("select count(c) from OnlineChallenge c where c.createdBy.id = :userId "
            + "and c.status = no.trafonsjakk.online.OnlineChallenge.Status.OPEN and c.expiresAt > :now")
    long countOpenBy(@Param("userId") Long userId, @Param("now") Instant now);
}
