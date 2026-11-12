package no.trafonsjakk.live;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveGameRepository extends JpaRepository<LiveGame, Long> {

    @Query("select g from LiveGame g join fetch g.white join fetch g.black where g.status = 'LIVE' order by g.startedAt desc")
    List<LiveGame> findRunning();

    @Query("select g from LiveGame g join fetch g.white join fetch g.black where g.id = :id")
    Optional<LiveGame> findWithPlayers(@Param("id") Long id);

    /** Locks the row: two phones saving at once are handled one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from LiveGame g join fetch g.white join fetch g.black where g.id = :id")
    Optional<LiveGame> findByIdForUpdate(@Param("id") Long id);

    @Query("select count(g) > 0 from LiveGame g where g.status = 'LIVE' and (g.white.id in :ids or g.black.id in :ids)")
    boolean anyRunningFor(@Param("ids") List<Long> ids);

    /** Games nobody has touched for a while were left behind (a dead phone, a forgotten tab). */
    @Modifying
    @Query("update LiveGame g set g.status = 'ABANDONED', g.version = g.version + 1 where g.status = 'LIVE' and g.updatedAt < :cutoff")
    int abandonIdleSince(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("update LiveGame g set g.status = 'ABANDONED', g.version = g.version + 1, g.updatedAt = :now "
            + "where g.status = 'LIVE' and (g.white.id = :userId or g.black.id = :userId)")
    int abandonAllFor(@Param("userId") Long userId, @Param("now") Instant now);
}
