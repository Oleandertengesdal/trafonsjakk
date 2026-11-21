package no.trafonsjakk.push;

import java.util.List;
import no.trafonsjakk.game.GameResult;

/** What the rest of the app tells members' devices about. Published inside a transaction, sent once it has committed. */
public final class PushEvents {

    private PushEvents() {
    }

    /** A game was logged and {@code recipients} (players other than whoever logged it) have to confirm it. */
    public record ConfirmationNeeded(long gameId, List<Long> recipients, String reporter, String white, String black,
                                     GameResult result) {
    }
}
