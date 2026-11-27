package no.trafonsjakk.push;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends push notifications for events, once the change has been saved. */
@Component
public class PushNotifications {

    private final PushService push;

    public PushNotifications(PushService push) {
        this.push = push;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void confirmationNeeded(PushEvents.ConfirmationNeeded e) {
        push.notify(e.recipients(), PushMessages.confirm(e));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void challengeReceived(PushEvents.ChallengeReceived e) {
        push.notify(java.util.List.of(e.recipient()), PushMessages.challenge(e));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void yourMove(PushEvents.YourMove e) {
        push.notify(java.util.List.of(e.recipient()), PushMessages.yourMove(e));
    }
}
