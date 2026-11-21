package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import no.trafonsjakk.push.PushTestKeys;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Push notifications, with a stand-in push service on this machine that records what it's sent. */
class PushApiTest extends IntegrationTest {

    private static final String[] VAPID = PushTestKeys.vapidPair();
    private static final BlockingQueue<Received> RECEIVED = new LinkedBlockingQueue<>();
    private static final HttpServer SERVICE = start();
    /** Paths that answer 410 Gone (the browser has unsubscribed). */
    private static final String GONE = "/gone/";

    record Received(String path, Map<String, String> headers, byte[] body) {
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                Map<String, String> h = new java.util.HashMap<>();
                exchange.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.getFirst()));
                RECEIVED.add(new Received(exchange.getRequestURI().getPath(), h, exchange.getRequestBody().readAllBytes()));
                exchange.sendResponseHeaders(exchange.getRequestURI().getPath().startsWith(GONE) ? 410 : 201, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void push(DynamicPropertyRegistry registry) {
        registry.add("app.push.vapid-public-key", () -> VAPID[0]);
        registry.add("app.push.vapid-private-key", () -> VAPID[1]);
        registry.add("app.push.subject", () -> "mailto:admin@example.org");
        registry.add("app.push.allow-local-endpoints", () -> "true");
    }

    @AfterAll
    static void stop() {
        SERVICE.stop(0);
    }

    Player admin;
    Player alice;
    Player bob;

    @BeforeEach
    void players() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
        RECEIVED.clear();
    }

    private static String endpoint(String path) {
        return "http://127.0.0.1:" + SERVICE.getAddress().getPort() + path;
    }

    private static Map<String, Object> subscription(String endpoint, PushTestKeys keys) {
        return Map.of("endpoint", endpoint, "keys", Map.of("p256dh", keys.p256dh(), "auth", keys.authKey()));
    }

    private static Received next() throws InterruptedException {
        Received r = RECEIVED.poll(10, TimeUnit.SECONDS);
        assertThat(r).as("a push message").isNotNull();
        return r;
    }

    @Test
    void theOpponentIsAskedToConfirmOnTheirPhone() throws Exception {
        assertThat(bob.api().get("/api/push/key").json().path("publicKey").asString()).isEqualTo(VAPID[0]);
        PushTestKeys phone = new PushTestKeys();
        assertThat(bob.api().post("/api/push/subscriptions", subscription(endpoint("/bob-phone"), phone)).status()).isEqualTo(204);
        assertThat(bob.api().post("/api/push/status", Map.of("endpoint", endpoint("/bob-phone"))).json().path("subscribed").asBoolean()).isTrue();
        assertThat(alice.api().post("/api/push/status", Map.of("endpoint", endpoint("/bob-phone"))).json().path("subscribed").asBoolean()).isFalse();

        long id = alice.api().post("/api/games", game(alice, bob, "WHITE_WIN")).json().path("id").asLong();
        Received r = next();
        assertThat(r.path()).isEqualTo("/bob-phone");
        assertThat(r.headers().get("content-encoding")).isEqualTo("aes128gcm");
        assertThat(r.headers().get("ttl")).isEqualTo("86400");
        String claims = PushTestKeys.verifyVapid(r.headers().get("authorization"), VAPID[0]);
        assertThat(claims).contains("\"aud\":\"http://127.0.0.1:" + SERVICE.getAddress().getPort() + "\"")
                .contains("\"sub\":\"mailto:admin@example.org\"");

        JsonNode message = JsonMapper.builder().build().readTree(phone.decrypt(r.body()));
        assertThat(message.path("title").asString()).isEqualTo("Real " + alice.username() + " registrerte et parti mot deg");
        assertThat(message.path("body").asString()).startsWith("Real " + alice.username() + " vant med hvit (1–0).");
        assertThat(message.path("url").asString()).isEqualTo("/games/" + id);
        assertThat(message.path("tag").asString()).isEqualTo("confirm-" + id);
        assertThat(RECEIVED.poll(500, TimeUnit.MILLISECONDS)).as("nothing to the one who logged it").isNull();
    }

    @Test
    void anAdminLoggingAGameAsksBothPlayers() throws Exception {
        bob.api().post("/api/push/subscriptions", subscription(endpoint("/bob"), new PushTestKeys()));
        alice.api().post("/api/push/subscriptions", subscription(endpoint("/alice"), new PushTestKeys()));
        admin.api().post("/api/games", game(alice, bob, "DRAW"));
        assertThat(java.util.Set.of(next().path(), next().path())).containsExactlyInAnyOrder("/alice", "/bob");
    }

    @Test
    void aBrowserThatIsGoneIsForgotten() throws Exception {
        bob.api().post("/api/push/subscriptions", subscription(endpoint(GONE + "bob"), new PushTestKeys()));
        alice.api().post("/api/games", game(alice, bob, "DRAW"));
        assertThat(next().path()).isEqualTo(GONE + "bob");
        Thread.sleep(300);
        assertThat(bob.api().post("/api/push/status", Map.of("endpoint", endpoint(GONE + "bob"))).json().path("subscribed").asBoolean()).isFalse();
    }

    @Test
    void turningOffAndTestNotifications() throws Exception {
        PushTestKeys laptop = new PushTestKeys();
        Response none = bob.api().post("/api/push/test");
        assertThat(none.status()).isEqualTo(409);
        assertThat(none.code()).isEqualTo("push.not_subscribed");

        bob.api().post("/api/push/subscriptions", subscription(endpoint("/laptop"), laptop));
        Response test = bob.api().post("/api/push/test");
        assertThat(test.json().path("sent").asInt()).isEqualTo(1);
        assertThat(JsonMapper.builder().build().readTree(laptop.decrypt(next().body())).path("title").asString()).isEqualTo("Varsler er på");

        // Someone else can't turn off Bob's browser
        assertThat(alice.api().post("/api/push/subscriptions/remove", Map.of("endpoint", endpoint("/laptop"))).status()).isEqualTo(204);
        assertThat(bob.api().post("/api/push/status", Map.of("endpoint", endpoint("/laptop"))).json().path("subscribed").asBoolean()).isTrue();
        assertThat(bob.api().post("/api/push/subscriptions/remove", Map.of("endpoint", endpoint("/laptop"))).status()).isEqualTo(204);
        alice.api().post("/api/games", game(alice, bob, "DRAW"));
        assertThat(RECEIVED.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void onlyRealPushServicesAndRealKeys() {
        PushTestKeys k = new PushTestKeys();
        for (String bad : new String[] {"http://169.254.169.254/latest", "https://example.org/push", "https://fcm.googleapis.com.evil.example/x",
                "https://fcm.googleapis.com:8443/x", "ftp://fcm.googleapis.com/x", "not a url"}) {
            Response r = bob.api().post("/api/push/subscriptions", subscription(bad, k));
            assertThat(r.code()).as(bad).isEqualTo("push.bad_endpoint");
        }
        assertThat(bob.api().post("/api/push/subscriptions", Map.of("endpoint", "https://fcm.googleapis.com/fcm/send/abc",
                "keys", Map.of("p256dh", "AAAA", "auth", k.authKey()))).code()).isEqualTo("push.bad_keys");
        assertThat(bob.api().post("/api/push/subscriptions", Map.of("endpoint", "https://fcm.googleapis.com/fcm/send/abc")).code())
                .isEqualTo("push.bad_keys");
        assertThat(bob.api().post("/api/push/subscriptions", subscription("https://fcm.googleapis.com/fcm/send/abc", k)).status()).isEqualTo(204);
        assertThat(bob.api().post("/api/push/subscriptions", subscription("https://web.push.apple.com/QGx", k)).status()).isEqualTo(204);
        assertThat(bob.api().post("/api/push/subscriptions", subscription("https://updates.push.services.mozilla.com/wpush/v2/x", k)).status()).isEqualTo(204);
    }

    @Test
    void aMemberKeepsAtMostTenBrowsers() throws Exception {
        for (int i = 0; i < 12; i++) {
            bob.api().post("/api/push/subscriptions", subscription(endpoint("/b" + i), new PushTestKeys()));
        }
        assertThat(bob.api().post("/api/push/status", Map.of("endpoint", endpoint("/b0"))).json().path("subscribed").asBoolean()).isFalse();
        assertThat(bob.api().post("/api/push/status", Map.of("endpoint", endpoint("/b11"))).json().path("subscribed").asBoolean()).isTrue();
        assertThat(bob.api().post("/api/push/test").json().path("sent").asInt()).isEqualTo(10);
    }

    @Test
    void deletingTheAccountForgetsItsBrowsers() throws Exception {
        bob.api().post("/api/push/subscriptions", subscription(endpoint("/bob"), new PushTestKeys()));
        assertThat(bob.api().post("/api/users/me/delete", Map.of("password", PASSWORD)).status()).isEqualTo(204);
        Integer left = jdbc.queryForObject("select count(*) from push_subscriptions", Integer.class);
        assertThat(left).isZero();
    }
}
