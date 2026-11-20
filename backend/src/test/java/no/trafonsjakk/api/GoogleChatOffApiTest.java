package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/** Without a webhook link (the default) chat is simply off. */
class GoogleChatOffApiTest extends IntegrationTest {

    @Test
    void withoutAWebhookChatIsOffAndSaysSo() {
        Player admin = registerAdmin();
        assertThat(admin.api().get("/api/admin/chat").json().path("configured").asBoolean()).isFalse();
        Response test = admin.api().post("/api/admin/chat/test");
        assertThat(test.status()).isEqualTo(409);
        assertThat(test.code()).isEqualTo("chat.not_configured");
    }
}
