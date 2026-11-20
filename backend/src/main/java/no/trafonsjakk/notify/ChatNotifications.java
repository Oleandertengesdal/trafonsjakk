package no.trafonsjakk.notify;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import no.trafonsjakk.config.AppProperties;
import no.trafonsjakk.live.LiveGameRepository;
import no.trafonsjakk.notify.ChatEvents.GameLogged;
import no.trafonsjakk.notify.ChatEvents.LiveGameStarted;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the Google Chat space when a live game starts and when a game is logged, once the change has been
 * saved (nothing is announced for a request that fails). A finished live game's result lands in the same
 * thread as its start.
 */
@Component
public class ChatNotifications {

    /** Games dated further back than this are being entered afterwards (old results), not news. */
    static final Duration NEWS = Duration.ofDays(3);

    private final GoogleChatClient chat;
    private final LiveGameRepository live;
    private final String site;

    public ChatNotifications(GoogleChatClient chat, LiveGameRepository live, AppProperties props) {
        this.chat = chat;
        this.live = live;
        this.site = props.publicUrl();
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void liveStarted(LiveGameStarted e) {
        if (chat.configured()) {
            chat.post(ChatMessages.liveStarted(e, site), "live-" + e.liveId());
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void gameLogged(GameLogged e) {
        if (!chat.configured() || e.playedAt().isBefore(Instant.now().minus(NEWS))) {
            return;
        }
        String thread = live.findIdByGameId(e.gameId()).map(id -> "live-" + id).orElse("game-" + e.gameId());
        chat.post(ChatMessages.gameLogged(e, site), thread);
    }

    /** For the admin page's "send a test message": waits for Chat's answer. */
    public boolean sendTest() {
        try {
            return chat.post(ChatMessages.test(), null).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }
}
