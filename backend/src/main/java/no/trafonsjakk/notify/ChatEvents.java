package no.trafonsjakk.notify;

import java.time.Instant;
import no.trafonsjakk.game.GameResult;

/** What the rest of the app tells the chat about. Published inside a transaction, sent once it has committed. */
public final class ChatEvents {

    private ChatEvents() {
    }

    /** A live game has started. Names are the players' real names. */
    public record LiveGameStarted(long liveId, String white, String black) {
    }

    /** A game has been logged (by hand, or by finishing a live game). {@code tournament} is null outside tournaments. */
    public record GameLogged(long gameId, String white, String black, GameResult result, Instant playedAt, String tournament) {
    }
}
