package no.trafonsjakk.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.notify.ChatEvents.GameLogged;
import no.trafonsjakk.notify.ChatEvents.LiveGameStarted;
import org.junit.jupiter.api.Test;

class GoogleChatClientTest {

    private static final String LINK = "https://chat.googleapis.com/v1/spaces/AAAA/messages?key=k-1&token=t-2";

    @Test
    void acceptsOnlyAGoogleChatWebhookLink() {
        assertThat(GoogleChatClient.webhookUri(LINK)).hasValueSatisfying(u -> assertThat(u.toString()).isEqualTo(LINK));
        assertThat(GoogleChatClient.webhookUri("  " + LINK + "\n")).isPresent();
        assertThat(GoogleChatClient.webhookUri("http://127.0.0.1:8099/v1/spaces/T/messages?key=k&token=t")).isPresent();

        assertThat(GoogleChatClient.webhookUri(null)).isEmpty();
        assertThat(GoogleChatClient.webhookUri("")).isEmpty();
        assertThat(GoogleChatClient.webhookUri(LINK.replace("https", "http"))).isEmpty();
        assertThat(GoogleChatClient.webhookUri(LINK.replace("chat.googleapis.com", "chat.googleapis.com.evil.example"))).isEmpty();
        assertThat(GoogleChatClient.webhookUri(LINK.replace("chat.googleapis.com", "chat.googleapis.com:444"))).isEmpty();
        assertThat(GoogleChatClient.webhookUri(LINK.replace("https://", "https://me@"))).isEmpty();
        assertThat(GoogleChatClient.webhookUri(LINK.replace("/v1/spaces/", "/v2/other/"))).isEmpty();
        assertThat(GoogleChatClient.webhookUri(LINK.replace("&token=t-2", ""))).isEmpty();
        assertThat(GoogleChatClient.webhookUri("not a link at all")).isEmpty();
    }

    @Test
    void choosesTheThreadOptionPerMessageNotFromTheLink() {
        assertThat(GoogleChatClient.webhookUri(LINK + "&messageReplyOption=REPLY_MESSAGE_OR_FAIL"))
                .hasValueSatisfying(u -> assertThat(u.toString()).isEqualTo(LINK));
    }

    @Test
    void readsGooglesExplanationFromAnErrorAnswer() {
        assertThat(GoogleChatClient.googleMessage("{\"error\":{\"code\":403,\"message\":\"Webhooks are  disabled.\"}}"))
                .isEqualTo(": Webhooks are disabled.");
        assertThat(GoogleChatClient.googleMessage("<html>nope</html>")).isEmpty();
        assertThat(GoogleChatClient.googleMessage("")).isEmpty();
        assertThat(GoogleChatClient.googleMessage(null)).isEmpty();
    }

    @Test
    void messagesNameThePlayersAndLinkToTheSite() {
        assertThat(ChatMessages.liveStarted(new LiveGameStarted(7, "Ola", "Kari"), "https://example.org/"))
                .isEqualTo("♟️ *Ola* (hvit) mot *Kari* (svart) har startet et parti. <https://example.org/live/7|Følg direkte>");

        Instant now = Instant.now();
        assertThat(ChatMessages.gameLogged(new GameLogged(3, "Ola", "Kari", GameResult.BLACK_WIN, now, null), "https://example.org"))
                .isEqualTo("🏁 *Kari* slo *Ola* med svart (0–1). <https://example.org/games/3|Se partiet>");
        assertThat(ChatMessages.gameLogged(new GameLogged(3, "Ola", "Kari", GameResult.DRAW, now, "Vårcupen"), ""))
                .isEqualTo("🏁 *Ola* og *Kari* spilte remis (½–½) i _Vårcupen_.");
    }

    @Test
    void aNameCannotBecomeALink() {
        assertThat(ChatMessages.liveStarted(new LiveGameStarted(1, "<https://x.example|Ola>", "A & B"), null))
                .isEqualTo("♟️ *&lt;https://x.example|Ola&gt;* (hvit) mot *A &amp; B* (svart) har startet et parti.");
    }
}
