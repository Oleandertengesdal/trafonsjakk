package no.trafonsjakk.tournament;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TournamentParticipantRepository
        extends JpaRepository<TournamentParticipant, TournamentParticipant.Key> {

    @Query("select p from TournamentParticipant p join fetch p.user where p.tournament.id = :tournamentId")
    List<TournamentParticipant> findByTournamentId(@Param("tournamentId") Long tournamentId);

    @Query("select p.tournament.id, count(p) from TournamentParticipant p group by p.tournament.id")
    List<Object[]> countPerTournament();

    @Modifying
    @Query("delete from TournamentParticipant p where p.tournament.id = :tournamentId")
    void deleteByTournamentId(@Param("tournamentId") Long tournamentId);
}
