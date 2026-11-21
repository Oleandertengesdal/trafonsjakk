package no.trafonsjakk.push;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.config.AppProperties;
import no.trafonsjakk.push.PushSubscriptionRepository.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Push notifications to the members' phones and computers (Web Push with VAPID). Off unless the server has a
 * VAPID key pair. Browsers subscribe through the settings page; messages are encrypted for each browser and
 * sent from a background thread after the change that caused them has been saved, so a slow push service
 * never slows anything down. A browser the push service says is gone is forgotten.
 * <p>
 * The server only ever sends to the big push services' own addresses ({@link #PUSH_HOSTS}), so a member
 * can't make it send requests anywhere else.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    /** Per member: a phone, a tablet, a couple of computers… the oldest go beyond this. */
    static final int MAX_PER_USER = 10;
    /** A confirmation request is still worth delivering a day later (a phone that was off). */
    private static final int TTL_SECONDS = 24 * 3600;
    private static final Set<String> PUSH_HOSTS = Set.of("fcm.googleapis.com", "android.googleapis.com",
            "updates.push.services.mozilla.com", "web.push.apple.com");
    private static final Set<String> PUSH_HOST_SUFFIXES = Set.of(".push.services.mozilla.com", ".push.apple.com", ".notify.windows.com");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** What a notification says, where tapping it goes, and a tag (a newer one with the same tag replaces it). */
    public record Message(String title, String body, String url, String tag) {
    }

    private final PushSubscriptionRepository subscriptions;
    private final ECPrivateKey privateKey;
    private final byte[] publicPoint;
    private final String subject;
    private final boolean allowLocal;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ThreadPoolExecutor sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(200), r -> {
        Thread t = new Thread(r, "web-push");
        t.setDaemon(true);
        return t;
    });

    public PushService(PushSubscriptionRepository subscriptions, AppProperties props) {
        this.subscriptions = subscriptions;
        AppProperties.Push cfg = props.push();
        String pub = cfg == null ? null : cfg.vapidPublicKey();
        String priv = cfg == null ? null : cfg.vapidPrivateKey();
        String subj = cfg == null || cfg.subject() == null || cfg.subject().isBlank() ? props.publicUrl() : cfg.subject();
        this.allowLocal = cfg != null && cfg.allowLocalEndpoints();
        ECPrivateKey key = null;
        byte[] point = null;
        if (pub != null && !pub.isBlank() && priv != null && !priv.isBlank()) {
            try {
                point = WebPush.decode(pub);
                ECPublicKey publicKey = WebPush.publicKey(point);
                key = WebPush.privateKey(WebPush.decode(priv));
                if (!WebPush.matches(key, publicKey)) {
                    throw new GeneralSecurityException("the keys aren't a pair");
                }
                if (subj == null || !(subj.startsWith("mailto:") || subj.startsWith("https://"))) {
                    throw new GeneralSecurityException("PUSH_SUBJECT (or APP_PUBLIC_URL) must be a mailto: or https:// address");
                }
                log.info("Push notifications are on");
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                log.error("Push notifications are off: the VAPID keys can't be used ({})", e.getMessage());
                key = null;
                point = null;
            }
        }
        this.privateKey = key;
        this.publicPoint = point;
        this.subject = subj;
    }

    public boolean configured() {
        return privateKey != null;
    }

    /** The public key browsers subscribe with; null when push is off. */
    public String publicKey() {
        return configured() ? WebPush.encode(publicPoint) : null;
    }

    public void subscribe(CurrentUser actor, String endpoint, String p256dh, String auth) {
        requireConfigured();
        if (endpoint == null || endpoint.length() > 1000 || !allowedEndpoint(endpoint)) {
            throw ApiException.badRequest("push.bad_endpoint", "That isn't a push service this site sends to");
        }
        try {
            WebPush.publicKey(WebPush.decode(p256dh));
            if (WebPush.decode(auth).length != 16) {
                throw new IllegalArgumentException();
            }
        } catch (GeneralSecurityException | IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("push.bad_keys", "The browser's push keys aren't valid");
        }
        subscriptions.save(actor.id(), endpoint, p256dh.trim(), auth.trim(), Instant.now());
        subscriptions.trim(actor.id(), MAX_PER_USER);
    }

    public void unsubscribe(CurrentUser actor, String endpoint) {
        if (endpoint != null) {
            subscriptions.delete(actor.id(), endpoint);
        }
    }

    public boolean subscribed(CurrentUser actor, String endpoint) {
        return endpoint != null && subscriptions.has(actor.id(), endpoint);
    }

    /** Sends to every browser of these members, in the background. */
    public void notify(Collection<Long> userIds, Message message) {
        if (!configured() || userIds.isEmpty()) {
            return;
        }
        try {
            sender.execute(() -> {
                for (Subscription s : subscriptions.forUsers(userIds)) {
                    send(s, message);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Push: too many notifications waiting, one was dropped");
        }
    }

    /** The settings page's test: sends to the member's own browsers and says how many accepted it. */
    public int sendTest(CurrentUser actor, Message message) {
        requireConfigured();
        List<Subscription> mine = subscriptions.forUsers(List.of(actor.id()));
        if (mine.isEmpty()) {
            throw ApiException.conflict("push.not_subscribed", "Turn on notifications on this device first");
        }
        CompletableFuture<Integer> done = new CompletableFuture<>();
        try {
            sender.execute(() -> done.complete((int) mine.stream().filter(s -> send(s, message)).count()));
            return done.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return 0;
        }
    }

    private boolean send(Subscription s, Message message) {
        URI endpoint = URI.create(s.endpoint());
        String host = endpoint.getHost();
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("title", message.title());
            body.put("body", message.body());
            body.put("url", message.url());
            body.put("tag", message.tag());
            byte[] encrypted = WebPush.encrypt(JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8),
                    WebPush.decode(s.p256dh()), WebPush.decode(s.auth()));
            String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("TTL", String.valueOf(TTL_SECONDS))
                    .header("Urgency", "normal")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", WebPush.vapidHeader(audience, subject, privateKey, publicPoint, Instant.now()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(encrypted))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status / 100 == 2) {
                return true;
            }
            if (status == 404 || status == 410) {
                // The browser unsubscribed or the subscription expired
                subscriptions.forget(s.id());
            } else {
                log.warn("Push: {} answered {}", host, status);
            }
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            log.warn("Push: couldn't send to {} ({})", host, e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    boolean allowedEndpoint(String raw) {
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null) {
            return false;
        }
        if (allowLocal && "http".equals(uri.getScheme()) && ("127.0.0.1".equals(host) || "localhost".equals(host))) {
            return true;
        }
        if (!"https".equals(uri.getScheme()) || uri.getPort() != -1) {
            return false;
        }
        String h = host.toLowerCase(java.util.Locale.ROOT);
        return PUSH_HOSTS.contains(h) || PUSH_HOST_SUFFIXES.stream().anyMatch(h::endsWith);
    }

    private void requireConfigured() {
        if (!configured()) {
            throw ApiException.conflict("push.not_configured", "Push notifications aren't set up on the server");
        }
    }

    @PreDestroy
    void stop() {
        sender.shutdownNow();
    }
}
