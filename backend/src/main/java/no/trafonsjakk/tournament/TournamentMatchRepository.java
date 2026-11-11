package no.trafonsjakk.tournament;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TournamentMatchRepository extends JpaRepository<TournamentMatch, Long> {

    @Query("select m from TournamentMatch m where m.tournament.id = :tournamentId "
            + "order by m.roundNumber asc, m.boardNumber asc")
    List<TournamentMatch> findByTournamentId(@Param("tournamentId") Long tournamentId);

    @Query("select m from TournamentMatch m where m.tournament.id = :tournamentId "
            + "and m.roundNumber = :round and m.boardNumber = :board")
    Optional<TournamentMatch> findSlot(@Param("tournamentId") Long tournamentId, @Param("round") int round,
                                       @Param("board") int board);

    @Query("select max(m.roundNumber) from TournamentMatch m where m.tournament.id = :tournamentId")
    Integer maxRound(@Param("tournamentId") Long tournamentId);

    @Query("select m from TournamentMatch m join fetch m.tournament t "
            + "where t.status = no.trafonsjakk.tournament.TournamentStatus.IN_PROGRESS "
            + "and m.status = no.trafonsjakk.tournament.MatchStatus.READY "
            + "and (m.white.id = :userId or m.black.id = :userId) "
            + "order by t.id asc, m.roundNumber asc, m.boardNumber asc")
    List<TournamentMatch> findReadyForUser(@Param("userId") Long userId);

    @Query("select m.tournament.id from TournamentMatch m where m.id = :matchId")
    Optional<Long> findTournamentId(@Param("matchId") Long matchId);

    /** Locks one match, so two players logging its result at the same moment are handled in turn. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from TournamentMatch m join fetch m.tournament where m.id = :id")
    Optional<TournamentMatch> findByIdForUpdate(@Param("id") Long id);
}
