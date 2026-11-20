package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Google Chat, with a stand-in for Google's webhook on this machine that records what it's sent. */
class GoogleChatApiTest extends IntegrationTest {

    private static final BlockingQueue<Received> RECEIVED = new LinkedBlockingQueue<>();
    /** What the stand-in answers: 200, or an error like Google's. */
    private static volatile int answer = 200;
    private static final HttpServer CHAT = startChat();

    record Received(String query, JsonNode body) {
    }

    private static HttpServer startChat() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/spaces/TEST/messages", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                RECEIVED.add(new Received(exchange.getRequestURI().getRawQuery(), JsonMapper.builder().build().readTree(body)));
                if (answer == 200) {
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    byte[] error = ("{\"error\":{\"code\":" + answer + ",\"message\":\"Webhooks are disabled.\",\"status\":\"PERMISSION_DENIED\"}}")
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(answer, error.length);
                    exchange.getResponseBody().write(error);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void chat(DynamicPropertyRegistry registry) {
        registry.add("app.google-chat.webhook-url",
                () -> "http://127.0.0.1:" + CHAT.getAddress().getPort() + "/v1/spaces/TEST/messages?key=k&token=t");
        registry.add("app.google-chat.min-interval", () -> "0ms");
        registry.add("app.public-url", () -> "https://sjakk.example");
    }

    @AfterAll
    static void stopChat() {
        CHAT.stop(0);
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
        answer = 200;
    }

    private static Received next() throws InterruptedException {
        Received r = RECEIVED.poll(10, TimeUnit.SECONDS);
        assertThat(r).as("a chat message").isNotNull();
        return r;
    }

    @Test
    void aLiveGameAndItsResultAreAnnouncedInOneThread() throws InterruptedException {
        Response started = alice.api().post("/api/live", Map.of("whiteId", alice.id(), "blackId", bob.id()));
        long liveId = started.json().path("id").asLong();
        Received start = next();
        assertThat(start.query()).contains("token=t").contains("messageReplyOption=REPLY_MESSAGE_FALLBACK_TO_NEW_THREAD");
        assertThat(start.body().path("text").asString())
                .isEqualTo("♟️ *Real " + alice.username() + "* (hvit) mot *Real " + bob.username()
                        + "* (svart) har startet et parti. <https://sjakk.example/live/" + liveId + "|Følg direkte>");
        assertThat(start.body().path("thread").path("threadKey").asString()).isEqualTo("live-" + liveId);

        Response finished = alice.api().post("/api/live/" + liveId + "/finish", Map.of("result", "WHITE_WIN"));
        long gameId = finished.json().path("id").asLong();
        Received result = next();
        assertThat(result.body().path("text").asString())
                .startsWith("🏁 *Real " + alice.username() + "* slo *Real " + bob.username() + "* med hvit (1–0).")
                .endsWith("<https://sjakk.example/games/" + gameId + "|Se partiet>");
        assertThat(result.body().path("thread").path("threadKey").asString()).isEqualTo("live-" + liveId);
    }

    @Test
    void aLoggedGameIsAnnouncedButAnOldOneEnteredAfterwardsIsNot() throws InterruptedException {
        Instant longAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        assertThat(admin.api().post("/api/games", game(alice.id(), bob.id(), "DRAW", longAgo)).status()).isEqualTo(201);
        Response logged = bob.api().post("/api/games", game(alice, bob, "DRAW"));
        long id = logged.json().path("id").asLong();
        Received r = next();
        assertThat(r.body().path("text").asString()).contains("spilte remis (½–½)");
        assertThat(r.body().path("thread").path("threadKey").asString()).isEqualTo("game-" + id);
        assertThat(RECEIVED.poll(500, TimeUnit.MILLISECONDS)).as("nothing about the old game").isNull();
    }

    @Test
    void nothingIsAnnouncedForARequestThatFails() throws InterruptedException {
        assertThat(alice.api().post("/api/games", game(alice, alice, "DRAW")).status()).isEqualTo(400);
        assertThat(RECEIVED.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void anAdminCanSeeThatChatIsOnAndSendATestMessage() throws InterruptedException {
        assertThat(admin.api().get("/api/admin/chat").json().path("configured").asBoolean()).isTrue();
        assertThat(admin.api().post("/api/admin/chat/test").status()).isEqualTo(204);
        Received test = next();
        assertThat(test.body().path("text").asString()).contains("koblet til");
        assertThat(test.body().has("thread")).isFalse();
        assertThat(test.query()).isEqualTo("key=k&token=t");
        assertThat(alice.api().post("/api/admin/chat/test").status()).isEqualTo(403);
    }

    @Test
    void whenGoogleRefusesTheAdminSeesWhy() {
        answer = 403;
        Response test = admin.api().post("/api/admin/chat/test");
        assertThat(test.status()).isEqualTo(502);
        assertThat(test.code()).isEqualTo("chat.failed");
        assertThat(test.json().path("params").path("reason").asString())
                .isEqualTo("Google answered 403: Webhooks are disabled.");
        assertThat(test.json().toString()).doesNotContain("token=");
    }
}
