package no.trafonsjakk.social;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameCommentRepository extends JpaRepository<GameComment, Long> {

    @Query("select c from GameComment c join fetch c.author where c.gameId = :gameId order by c.createdAt, c.id")
    List<GameComment> findByGame(@Param("gameId") Long gameId);

    /** [gameId, count] for each game that has comments. */
    @Query("select c.gameId, count(c) from GameComment c where c.gameId in :ids group by c.gameId")
    List<Object[]> countByGames(@Param("ids") Collection<Long> ids);
}
