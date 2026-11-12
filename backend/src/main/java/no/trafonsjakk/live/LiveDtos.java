package no.trafonsjakk.live;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.common.validation.PlainText;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.user.PlayerRef;

public final class LiveDtos {

    private LiveDtos() {
    }

    /** {@code canRecord}: whether the person asking may save moves and finish it (a player, or an admin). */
    public record LiveGameDto(long id, PlayerRef white, PlayerRef black, LiveStatus status, String moves, long version,
                              Instant startedAt, Instant updatedAt, Long gameId, boolean canRecord) {
        static LiveGameDto of(LiveGame g, CurrentUser actor) {
            return new LiveGameDto(g.getId(), PlayerRef.of(g.getWhite()), PlayerRef.of(g.getBlack()), g.getStatus(),
                    g.getMoves(), g.getVersion(), g.getStartedAt(), g.getUpdatedAt(), g.getGameId(),
                    actor.admin() || g.hasPlayer(actor.id()));
        }
    }

    public record StartLiveRequest(@NotNull Long whiteId, @NotNull Long blackId) {
    }

    /** {@code version}: the version the phone last saw; the save is refused if someone saved since. */
    public record SaveMovesRequest(@Size(max = 20000) @PlainText String moves, @NotNull Long version) {
    }

    public record FinishLiveRequest(@NotNull GameResult result) {
    }
}
