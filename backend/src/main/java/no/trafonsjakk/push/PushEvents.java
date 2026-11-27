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

    /** {@code recipient} was challenged to an online game ({@code timeControl}: "10+5", "3d"). */
    public record ChallengeReceived(long challengeId, long recipient, String challenger, String timeControl, boolean rated) {
    }

    /** It's {@code recipient}'s turn in a daily game (live games are played with the page open, so no message). */
    public record YourMove(long onlineGameId, long recipient, String opponent, String lastMove) {
    }
}
