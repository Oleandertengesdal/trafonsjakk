package no.trafonsjakk.social;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameCommentRepository extends JpaRepository<GameComment, Long> {

    @Query("select c from GameComment c join fetch c.author left join fetch c.replyTo where c.gameId = :gameId "
            + "order by c.createdAt, c.id")
    List<GameComment> findByGame(@Param("gameId") Long gameId);

    /** [gameId, count] of visible (not deleted) comments, replies included. */
    @Query("select c.gameId, count(c) from GameComment c where c.gameId in :ids and c.deletedAt is null group by c.gameId")
    List<Object[]> countByGames(@Param("ids") Collection<Long> ids);

    long countByParentId(Long parentId);
}
