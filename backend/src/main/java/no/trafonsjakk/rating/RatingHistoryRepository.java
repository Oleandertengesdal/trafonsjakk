package no.trafonsjakk.rating;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {

    List<RatingHistory> findByGameIdIn(Collection<Long> gameIds);

    /** A player's history in one rating pool ("OTB" or "ONLINE"), oldest first. */
    @Query("select h from RatingHistory h where h.userId = :userId and h.pool = :pool order by h.playedAt asc, h.gameId asc")
    List<RatingHistory> findForUser(@Param("userId") Long userId, @Param("pool") String pool);
}
