package no.trafonsjakk.notify;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import no.trafonsjakk.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Posts messages to one Google Chat space through its incoming webhook.
 * <p>
 * Messages go out one at a time on a background thread, at most one per {@code minInterval} (Chat allows
 * about one a second per space), so a request never waits for Google. A message that fails because Google
 * is busy or unreachable is tried again a couple of times; one that still fails is dropped and logged.
 * Chat is a nice-to-have: nothing in the app depends on it.
 * <p>
 * The webhook link contains the space's secret token, so it is never logged or returned by the API.
 */
@Component
public class GoogleChatClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleChatClient.class);
    private static final int ATTEMPTS = 3;
    /** Waiting messages beyond this are dropped (an admin entering a season's games at once, say). */
    private static final int QUEUE = 100;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final URI webhook;
    private final Duration minInterval;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ThreadPoolExecutor sender;
    private long lastSentNanos;

    public GoogleChatClient(AppProperties props) {
        AppProperties.GoogleChat cfg = props.googleChat();
        String raw = cfg == null ? null : cfg.webhookUrl();
        this.webhook = webhookUri(raw).orElse(null);
        this.minInterval = cfg == null || cfg.minInterval() == null ? Duration.ofMillis(1100) : cfg.minInterval();
        if (raw != null && !raw.isBlank() && webhook == null) {
            log.error("GOOGLE_CHAT_WEBHOOK_URL isn't a Google Chat webhook link (https://chat.googleapis.com/v1/spaces/...); "
                    + "chat messages are off");
        } else if (webhook != null) {
            log.info("Google Chat messages are on");
        }
        this.sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE), r -> {
            Thread t = new Thread(r, "google-chat");
            t.setDaemon(true);
            return t;
        });
    }

    public boolean configured() {
        return webhook != null;
    }

    /**
     * Queues a message. Messages with the same {@code threadKey} land in one thread in the space (a live game
     * and its result, say). Completes with whether Chat accepted it; never fails.
     */
    public CompletableFuture<Boolean> post(String text, String threadKey) {
        if (webhook == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        try {
            sender.execute(() -> done.complete(send(text, threadKey)));
        } catch (RejectedExecutionException e) {
            log.warn("Google Chat: too many messages waiting, one was dropped");
            done.complete(false);
        }
        return done;
    }

    private boolean send(String text, String threadKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        if (threadKey != null) {
            body.put("thread", Map.of("threadKey", threadKey));
        }
        HttpRequest request = HttpRequest.newBuilder(webhook)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            String failure;
            try {
                pace();
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status / 100 == 2) {
                    return true;
                }
                failure = "answered " + status;
                // Only "busy" and Google's own errors are worth another try; anything else won't change
                if (status != 429 && status / 100 != 5) {
                    log.warn("Google Chat {}; message dropped (is the webhook link still valid?)", failure);
                    return false;
                }
            } catch (IOException e) {
                failure = "unreachable (" + e.getClass().getSimpleName() + ")";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (attempt == ATTEMPTS) {
                log.warn("Google Chat {}; message dropped after {} attempts", failure, ATTEMPTS);
                return false;
            }
            if (!sleep(Duration.ofSeconds(2L << (attempt - 1)))) {
                return false;
            }
        }
        return false;
    }

    /** At most one message per {@code minInterval}. Only the sender thread calls this. */
    private void pace() throws InterruptedException {
        long wait = lastSentNanos + minInterval.toNanos() - System.nanoTime();
        if (lastSentNanos != 0 && wait > 0) {
            TimeUnit.NANOSECONDS.sleep(wait);
        }
        lastSentNanos = System.nanoTime();
    }

    private static boolean sleep(Duration d) {
        try {
            Thread.sleep(d);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @PreDestroy
    void stop() {
        sender.shutdownNow();
    }

    /**
     * The webhook link, if it is one: https on chat.googleapis.com under /v1/spaces/, with its key and token.
     * Plain http to this machine is allowed for tests. Replies go into the message's thread when it has a key.
     */
    static Optional<URI> webhookUri(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String host = uri.getHost();
        String path = uri.getRawPath();
        String query = uri.getRawQuery();
        boolean google = "https".equals(uri.getScheme()) && "chat.googleapis.com".equals(host) && uri.getPort() == -1;
        boolean local = "http".equals(uri.getScheme()) && ("localhost".equals(host) || "127.0.0.1".equals(host));
        if (!(google || local) || path == null || !path.startsWith("/v1/spaces/") || query == null
                || !query.contains("key=") || !query.contains("token=") || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null) {
            return Optional.empty();
        }
        if (!query.contains("messageReplyOption=")) {
            query += "&messageReplyOption=REPLY_MESSAGE_FALLBACK_TO_NEW_THREAD";
        }
        return Optional.of(URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + path + "?" + query));
    }
}
