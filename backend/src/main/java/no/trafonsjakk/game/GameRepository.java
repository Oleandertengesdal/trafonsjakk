package no.trafonsjakk.game;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRepository extends JpaRepository<Game, Long> {

    @Query(value = "select g from Game g where g.status in :statuses order by g.playedAt desc, g.id desc",
            countQuery = "select count(g) from Game g where g.status in :statuses")
    Page<Game> findByStatuses(@Param("statuses") Collection<GameStatus> statuses, Pageable pageable);

    @Query(value = "select g from Game g where g.status in :statuses "
            + "and (g.white.id = :playerId or g.black.id = :playerId) order by g.playedAt desc, g.id desc",
            countQuery = "select count(g) from Game g where g.status in :statuses "
                    + "and (g.white.id = :playerId or g.black.id = :playerId)")
    Page<Game> findByPlayerAndStatuses(@Param("playerId") Long playerId,
                                       @Param("statuses") Collection<GameStatus> statuses, Pageable pageable);

    /** Pending games where the user is a player and someone else reported it. */
    @Query("select g from Game g where g.status = no.trafonsjakk.game.GameStatus.PENDING "
            + "and (g.white.id = :userId or g.black.id = :userId) and g.reportedBy.id <> :userId "
            + "order by g.createdAt asc")
    List<Game> findAwaitingConfirmationBy(@Param("userId") Long userId);

    List<Game> findByStatusOrderByCreatedAtDesc(GameStatus status);

    @Query("select g from Game g where g.status = no.trafonsjakk.game.GameStatus.PENDING and g.createdAt < :cutoff")
    List<Game> findOverduePending(@Param("cutoff") Instant cutoff);

    @Query("select count(g) > 0 from Game g where g.status = no.trafonsjakk.game.GameStatus.PENDING "
            + "and g.createdAt < :cutoff")
    boolean existsOverduePending(@Param("cutoff") Instant cutoff);

    @Query("select g from Game g where g.status = no.trafonsjakk.game.GameStatus.CONFIRMED "
            + "and (g.white.id = :playerId or g.black.id = :playerId) order by g.playedAt asc, g.id asc")
    List<Game> findConfirmedForPlayer(@Param("playerId") Long playerId);

    @Query("select g from Game g where g.status = no.trafonsjakk.game.GameStatus.CONFIRMED")
    List<Game> findAllConfirmed();

    @Query("select g from Game g where g.match.id = :matchId order by g.playedAt asc, g.id asc")
    List<Game> findByMatchId(@Param("matchId") Long matchId);

    @Query("select g from Game g where g.match.id in :matchIds and g.status <> no.trafonsjakk.game.GameStatus.VOIDED "
            + "order by g.playedAt asc, g.id asc")
    List<Game> findActiveByMatchIds(@Param("matchIds") Collection<Long> matchIds);

    @Query("select count(g) > 0 from Game g where g.match.id = :matchId and g.status in "
            + "(no.trafonsjakk.game.GameStatus.PENDING, no.trafonsjakk.game.GameStatus.DISPUTED)")
    boolean existsOpenForMatch(@Param("matchId") Long matchId);

    @Query("select count(g) > 0 from Game g where g.match.id = :matchId "
            + "and g.status <> no.trafonsjakk.game.GameStatus.VOIDED")
    boolean existsActiveForMatch(@Param("matchId") Long matchId);
}
