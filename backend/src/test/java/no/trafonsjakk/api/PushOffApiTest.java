package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/** Without VAPID keys (the default) push is simply off. */
class PushOffApiTest extends IntegrationTest {

    @Test
    void withoutKeysPushIsOffAndSaysSo() {
        Player admin = registerAdmin();
        assertThat(admin.api().get("/api/push/key").json().path("publicKey").isNull()).isTrue();
        assertThat(admin.api().post("/api/push/subscriptions", Map.of("endpoint", "https://fcm.googleapis.com/fcm/send/x",
                "keys", Map.of("p256dh", "x", "auth", "y"))).code()).isEqualTo("push.not_configured");
        assertThat(admin.api().post("/api/push/test").code()).isEqualTo("push.not_configured");
    }
}
