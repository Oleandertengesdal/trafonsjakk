package no.trafonsjakk.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebPushTest {

    @Test
    void aBrowserCanDecryptWhatWeSend() throws GeneralSecurityException {
        PushTestKeys browser = new PushTestKeys();
        byte[] body = WebPush.encrypt("{\"title\":\"Hei ♟️\"}".getBytes(StandardCharsets.UTF_8),
                WebPush.decode(browser.p256dh()), browser.auth);
        assertThat(browser.decrypt(body)).isEqualTo("{\"title\":\"Hei ♟️\"}");
        // A different browser can't
        assertThatThrownBy(() -> new PushTestKeys().decrypt(body)).isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void theVapidTokenIsSignedWithOurKey() throws GeneralSecurityException {
        String[] pair = PushTestKeys.vapidPair();
        String header = WebPush.vapidHeader("https://fcm.googleapis.com", "mailto:admin@example.org",
                WebPush.privateKey(WebPush.decode(pair[1])), WebPush.decode(pair[0]), Instant.ofEpochSecond(1_000_000));
        String claims = PushTestKeys.verifyVapid(header, pair[0]);
        assertThat(claims).isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1043200,\"sub\":\"mailto:admin@example.org\"}");
        assertThatThrownBy(() -> PushTestKeys.verifyVapid(header, PushTestKeys.vapidPair()[0])).isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void keysMustBeAPair() throws GeneralSecurityException {
        String[] a = PushTestKeys.vapidPair();
        String[] b = PushTestKeys.vapidPair();
        assertThat(WebPush.matches(WebPush.privateKey(WebPush.decode(a[1])), WebPush.publicKey(WebPush.decode(a[0])))).isTrue();
        assertThat(WebPush.matches(WebPush.privateKey(WebPush.decode(a[1])), WebPush.publicKey(WebPush.decode(b[0])))).isFalse();
        assertThatThrownBy(() -> WebPush.publicKey(new byte[10])).isInstanceOf(GeneralSecurityException.class);
    }
}
