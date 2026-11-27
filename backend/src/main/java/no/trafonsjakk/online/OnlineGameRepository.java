package no.trafonsjakk.online;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OnlineGameRepository extends JpaRepository<OnlineGame, Long> {

    @Query("select g from OnlineGame g join fetch g.white join fetch g.black where g.id = :id")
    Optional<OnlineGame> findWithPlayers(@Param("id") Long id);

    /** Locks the row: moves, resignations and the clock running out are handled one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from OnlineGame g join fetch g.white join fetch g.black where g.id = :id")
    Optional<OnlineGame> findByIdForUpdate(@Param("id") Long id);

    /** Every game being played. A friend group has a handful at a time. */
    @Query("select g from OnlineGame g join fetch g.white join fetch g.black "
            + "where g.status = no.trafonsjakk.online.OnlineGame.Status.ACTIVE order by g.startedAt desc, g.id desc")
    List<OnlineGame> findActive();

    @Query("select g.id from OnlineGame g where g.status = no.trafonsjakk.online.OnlineGame.Status.ACTIVE "
            + "and (g.white.id in :ids or g.black.id in :ids)")
    List<Long> findActiveIdsFor(@Param("ids") Collection<Long> ids);

    @Query("select count(g) > 0 from OnlineGame g where g.status = no.trafonsjakk.online.OnlineGame.Status.ACTIVE "
            + "and g.kind = no.trafonsjakk.online.TimeControl.Kind.LIVE and (g.white.id in :ids or g.black.id in :ids)")
    boolean anyLiveActiveFor(@Param("ids") Collection<Long> ids);

    /** A player's games that have ended (with or without a result), newest first. */
    @Query("select g from OnlineGame g join fetch g.white join fetch g.black "
            + "where g.status <> no.trafonsjakk.online.OnlineGame.Status.ACTIVE and (g.white.id = :userId or g.black.id = :userId) "
            + "order by g.endedAt desc, g.id desc")
    List<OnlineGame> findEndedFor(@Param("userId") Long userId, Pageable page);
}
