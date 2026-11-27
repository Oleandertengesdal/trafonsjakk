package no.trafonsjakk.online;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import no.trafonsjakk.chess.Color;
import no.trafonsjakk.game.GameDtos.RatingChange;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.user.PlayerRef;

public final class OnlineDtos {

    private OnlineDtos() {
    }

    // ---- requests -------------------------------------------------------------------------------------------

    /**
     * {@code opponentId} null: an open challenge anyone can take. {@code colour}: the colour the creator plays.
     * Live games give {@code initialSeconds} + {@code incrementSeconds}; daily games {@code daysPerMove}.
     */
    public record ChallengeRequest(Long opponentId, @NotNull OnlineChallenge.Colour colour, @NotNull Boolean rated,
                                   @NotNull TimeControl.Kind kind, Integer initialSeconds, Integer incrementSeconds,
                                   Integer daysPerMove) {
        TimeControl timeControl() {
            return kind == TimeControl.Kind.LIVE
                    ? TimeControl.live(orZero(initialSeconds), orZero(incrementSeconds))
                    : TimeControl.daily(orZero(daysPerMove));
        }

        private static int orZero(Integer i) {
            return i == null ? 0 : i;
        }
    }

    /**
     * {@code uci}: "e2e4", "e7e8q". {@code ply}: how many half-moves the board had when the move was made,
     * so a repeated or stale request isn't played twice. {@code thinkMs}: how long the player thought, by
     * the phone's own count (see {@link Clock#LAG_ALLOWANCE}).
     */
    public record MoveRequest(@NotNull @Pattern(regexp = "^[a-h][1-8][a-h][1-8][qrbn]?$") String uci,
                              @NotNull @Min(0) Integer ply, @Min(0) @Max(86_400_000L * 14) Long thinkMs) {
    }

    // ---- responses ------------------------------------------------------------------------------------------

    public record TimeControlDto(TimeControl.Kind kind, int initialSeconds, int incrementSeconds, int daysPerMove,
                                 String label) {
        static TimeControlDto of(TimeControl tc) {
            return new TimeControlDto(tc.kind(), tc.initialSeconds(), tc.incrementSeconds(), tc.daysPerMove(), tc.label());
        }
    }

    /** {@code opponent} null: open to anyone. Players carry their online rating. */
    public record ChallengeDto(long id, PlayerRef createdBy, PlayerRef opponent, OnlineChallenge.Colour colour,
                               boolean rated, TimeControlDto timeControl, Instant createdAt, Instant expiresAt) {
    }

    /**
     * The clock as of {@code serverTime}: the page counts down from there itself. {@code firstMoveBy}:
     * while the game can still be called off, when the next first move has to come (or it's cancelled).
     * {@code deadline}: daily games, when the side to move has to have moved.
     */
    public record ClockDto(long whiteMs, long blackMs, boolean running, Color toMove, Instant serverTime,
                           Instant firstMoveBy, Instant deadline) {
    }

    /**
     * An online game. {@code myColour}: the colour of whoever is asking, or null for a spectator.
     * {@code gameId}: the confirmed game it became (rated games with a result). {@code version} changes
     * whenever anything does, so a page that polls can skip redrawing.
     */
    public record OnlineGameDto(long id, PlayerRef white, PlayerRef black, boolean rated, TimeControlDto timeControl,
                                OnlineGame.Status status, GameResult result, EndReason endReason, String moves,
                                int plies, String lastMove, String fen, Color turn, ClockDto clock, Long drawOfferBy,
                                Color myColour, boolean canAbort, Long gameId, RatingChange ratingChange,
                                Instant startedAt, Instant endedAt, long version) {
    }

    /**
     * Everything for the "Play" page. {@code myTurn}: my games where it's my move (daily games first
     * need this). {@code playing}: every game being played, for watching. {@code recent}: my last games.
     */
    public record Lobby(List<ChallengeDto> challengesToMe, List<ChallengeDto> myChallenges,
                        List<ChallengeDto> openChallenges, List<OnlineGameDto> myGames, List<OnlineGameDto> myTurn,
                        List<OnlineGameDto> playing, List<OnlineGameDto> recent) {
    }
}
