package no.trafonsjakk.notify;

import no.trafonsjakk.notify.ChatEvents.GameLogged;
import no.trafonsjakk.notify.ChatEvents.LiveGameStarted;

/**
 * The text of the chat messages, in Norwegian (the group's language). Google Chat formatting: {@code *bold*},
 * {@code _italic_}, {@code <url|text>} for links; {@code < > &} in names are escaped so a name can't become a link.
 */
final class ChatMessages {

    private ChatMessages() {
    }

    static String liveStarted(LiveGameStarted e, String site) {
        return "♟️ " + bold(e.white()) + " (hvit) mot " + bold(e.black()) + " (svart) har startet et parti."
                + link(site, "/live/" + e.liveId(), "Følg direkte");
    }

    static String gameLogged(GameLogged e, String site) {
        String what = switch (e.result()) {
            case WHITE_WIN -> bold(e.white()) + " slo " + bold(e.black()) + " med hvit (1–0)";
            case BLACK_WIN -> bold(e.black()) + " slo " + bold(e.white()) + " med svart (0–1)";
            case DRAW -> bold(e.white()) + " og " + bold(e.black()) + " spilte remis (½–½)";
        };
        String where = e.tournament() == null ? "" : " i " + italic(e.tournament());
        return "🏁 " + what + where + "." + link(site, "/games/" + e.gameId(), "Se partiet");
    }

    static String test() {
        return "✅ Trafonsjakk er koblet til denne chatten. Her kommer beskjed når et parti starter direkte eller blir registrert.";
    }

    private static String bold(String s) {
        return "*" + escape(s) + "*";
    }

    private static String italic(String s) {
        return "_" + escape(s) + "_";
    }

    /** No link without a site address (APP_PUBLIC_URL): the message still says what happened. */
    private static String link(String site, String path, String text) {
        return site == null || site.isBlank() ? "" : " <" + site.replaceAll("/+$", "") + path + "|" + text + ">";
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
