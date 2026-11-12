package no.trafonsjakk.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.ClientAddress;
import no.trafonsjakk.config.AppProperties;
import org.springframework.stereotype.Service;

/**
 * Brute-force protection: per-username lockout, failed-login and registration limits per visitor address and
 * per edge address (see {@link ClientAddress} for why both).
 * In-memory (single instance). Counters expire on their own.
 */
@Service
public class LoginAttemptService {

    private final AppProperties.Security cfg;
    private final Cache<String, AtomicInteger> failures;
    private final Cache<String, AtomicInteger> registrations;

    public LoginAttemptService(AppProperties props) {
        this.cfg = props.security();
        this.failures = Caffeine.newBuilder()
                .expireAfterWrite(cfg.lockoutDuration())
                .maximumSize(50_000)
                .build();
        this.registrations = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(50_000)
                .build();
    }

    public void assertLoginAllowed(String username, ClientAddress from) {
        if (count(failures, userKey(username)) >= cfg.maxFailedLoginsPerUser()
                || count(failures, ipKey(from.client())) >= cfg.maxFailedLoginsPerIp()
                || count(failures, edgeKey(from.edge())) >= cfg.maxFailedLoginsPerEdge()) {
            throw ApiException.tooManyRequests("auth.locked",
                    "Too many failed attempts. Try again in " + cfg.lockoutDuration().toMinutes() + " minutes.",
                    cfg.lockoutDuration()).with("minutes", cfg.lockoutDuration().toMinutes());
        }
    }

    public void recordFailure(String username, ClientAddress from) {
        increment(failures, userKey(username));
        increment(failures, ipKey(from.client()));
        increment(failures, edgeKey(from.edge()));
    }

    public void recordSuccess(String username) {
        failures.invalidate(userKey(username));
    }

    public void assertRegistrationAllowed(ClientAddress from) {
        boolean overClient = increment(registrations, ipKey(from.client())) > cfg.maxRegistrationsPerIpPerHour();
        boolean overEdge = increment(registrations, edgeKey(from.edge())) > cfg.maxRegistrationsPerEdgePerHour();
        if (overClient || overEdge) {
            throw ApiException.tooManyRequests("auth.too_many_signups", "Too many sign-up attempts. Try again later.", Duration.ofHours(1));
        }
    }

    private static int count(Cache<String, AtomicInteger> cache, String key) {
        AtomicInteger c = cache.getIfPresent(key);
        return c == null ? 0 : c.get();
    }

    private static int increment(Cache<String, AtomicInteger> cache, String key) {
        AtomicInteger c = cache.asMap().computeIfAbsent(key, k -> new AtomicInteger());
        int value = c.incrementAndGet();
        cache.put(key, c); // refresh expiry on each failure
        return value;
    }

    private static String userKey(String username) {
        return "u:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private static String edgeKey(String ip) {
        return "edge:" + ip;
    }
}
