package no.trafonsjakk.game;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import no.trafonsjakk.common.validation.PlainText;
import no.trafonsjakk.user.PlayerRef;

public final class GameDtos {

    private GameDtos() {
    }

    public record CreateGameRequest(
            @NotNull Long whiteId,
            @NotNull Long blackId,
            @NotNull GameResult result,
            @NotNull Instant playedAt,
            @Size(max = 80) @PlainText String opening,
            @Pattern(regexp = "^([A-Ea-e][0-9]{2})?$", message = "must be an ECO code like C50") String eco,
            @Size(max = 1000) @PlainText String notes,
            @Size(max = 20000) @PlainText String moves,
            Long matchId) {
    }

    public record DisputeRequest(
            @NotNull @Size(min = 5, max = 500, message = "must be 5–500 characters") @PlainText String reason) {
    }

    public enum ResolveAction { CONFIRM, VOID }

    public record ResolveRequest(@NotNull ResolveAction action, GameResult result) {
    }

    public record RatingDelta(int before, int after) {
    }

    public record RatingChange(RatingDelta white, RatingDelta black) {
    }

    public record TournamentRef(long id, String name, long matchId, int round) {
    }

    public record GameDto(
            long id,
            PlayerRef white,
            PlayerRef black,
            GameResult result,
            GameStatus status,
            Instant playedAt,
            Instant createdAt,
            PlayerRef reportedBy,
            PlayerRef confirmedBy,
            Instant confirmedAt,
            Instant autoConfirmAt,
            String opening,
            String eco,
            String notes,
            String moves,
            String disputeReason,
            TournamentRef tournament,
            RatingChange ratingChange,
            boolean canConfirm,
            boolean canDispute,
            boolean canWithdraw,
            boolean canEditMoves,
            int commentCount,
            int kudosCount,
            boolean kudosGivenByMe,
            boolean canGiveKudos) {
    }

    /** Moves in PGN/SAN ("1. e4 e5 2. Nf3 …"), checked for legality in the app. Blank clears them. */
    public record MovesRequest(@Size(max = 20000) @PlainText String moves) {
    }
}
