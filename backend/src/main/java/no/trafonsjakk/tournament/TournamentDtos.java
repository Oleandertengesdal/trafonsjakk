package no.trafonsjakk.tournament;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import no.trafonsjakk.common.validation.PlainText;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.game.GameStatus;
import no.trafonsjakk.user.PlayerRef;

public final class TournamentDtos {

    private TournamentDtos() {
    }

    public record CreateTournamentRequest(
            @NotBlank @Size(max = 80) @PlainText String name,
            @NotNull TournamentFormat format,
            Boolean doubleRoundRobin,
            @NotNull @Size(max = 32, message = "at most 32 players") List<Long> participantIds,
            /** Swiss only: how many rounds (1–15, and fewer than the players); default from the number of players. */
            @Min(1) @Max(15) Integer rounds) {

        /** Optional in the request; only meaningful for round-robins. */
        public boolean isDouble() {
            return Boolean.TRUE.equals(doubleRoundRobin);
        }
    }

    public record ParticipantsRequest(
            @NotNull @Size(max = 32, message = "at most 32 players") List<Long> participantIds) {
    }

    public record TournamentSummary(long id, String name, TournamentFormat format, TournamentStatus status,
                                    boolean doubleRoundRobin, Integer swissRounds, int participantCount, PlayerRef createdBy,
                                    PlayerRef winner, Instant createdAt, Instant startedAt, Instant completedAt) {
    }

    public record ParticipantDto(PlayerRef player, Integer seed) {
    }

    public record MatchGameDto(long id, long whiteId, long blackId, GameResult result, GameStatus status,
                               Instant playedAt) {
    }

    public record MatchDto(long id, int round, int board, PlayerRef white, PlayerRef black, MatchStatus status,
                           GameResult result, PlayerRef winner, List<MatchGameDto> games, Long openGameId,
                           boolean needsReplay) {
    }

    public record RoundDto(int number, String name, List<MatchDto> matches) {
    }

    /** {@code byes} and {@code buchholz} are for Swiss tournaments (0 and null otherwise). */
    public record StandingDto(int rank, PlayerRef player, int played, int wins, int draws, int losses,
                              double points, double sonnebornBerger, int byes, Double buchholz) {
    }

    public record TournamentDetail(TournamentSummary summary, List<ParticipantDto> participants,
                                   List<RoundDto> rounds, List<StandingDto> standings, Integer currentRound,
                                   int totalRounds, int completedMatches, int totalMatches,
                                   boolean canManage, boolean canJoin, boolean joined) {
    }

    public record MyMatchDto(long matchId, long tournamentId, String tournamentName, TournamentFormat format,
                             int round, int totalRounds, String roundName, PlayerRef white, PlayerRef black,
                             boolean needsReplay) {
    }
}
