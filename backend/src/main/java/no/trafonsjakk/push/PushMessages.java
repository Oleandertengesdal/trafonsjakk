package no.trafonsjakk.push;

import no.trafonsjakk.push.PushService.Message;

/** The text of push notifications, in Norwegian (the group's language). Plain text: no markup. */
final class PushMessages {

    private PushMessages() {
    }

    /** To a player who has to confirm a game someone else logged. */
    static Message confirm(PushEvents.ConfirmationNeeded e) {
        String what = switch (e.result()) {
            case WHITE_WIN -> e.white() + " vant med hvit (1–0)";
            case BLACK_WIN -> e.black() + " vant med svart (0–1)";
            case DRAW -> "Remis (½–½)";
        };
        return new Message(e.reporter() + " registrerte et parti mot deg", what + ". Trykk for å bekrefte eller bestride.",
                "/games/" + e.gameId(), "confirm-" + e.gameId());
    }

    static Message test() {
        return new Message("Varsler er på", "Her kommer beskjed når noen registrerer et parti mot deg.", "/settings", "test");
    }
}
