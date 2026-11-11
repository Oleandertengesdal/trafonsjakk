package no.trafonsjakk.rating;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {

    List<RatingHistory> findByGameIdIn(Collection<Long> gameIds);

    @Query("select h from RatingHistory h where h.userId = :userId order by h.playedAt asc, h.gameId asc")
    List<RatingHistory> findForUser(@Param("userId") Long userId);

    @Query("select h from RatingHistory h order by h.userId asc, h.playedAt asc, h.gameId asc")
    List<RatingHistory> findAllOrdered();
}
