package no.trafonsjakk.push;

import java.util.HashMap;
import java.util.Map;
import no.trafonsjakk.common.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Turning push notifications on and off for one browser, and a test notification. */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    public record Keys(String p256dh, String auth) {
    }

    /** As the browser's {@code PushSubscription.toJSON()} gives it. */
    public record SubscriptionRequest(String endpoint, Keys keys) {
    }

    public record EndpointRequest(String endpoint) {
    }

    /** {@code publicKey} is null when push is off on the server. */
    @GetMapping("/key")
    Map<String, Object> key() {
        Map<String, Object> out = new HashMap<>();
        out.put("publicKey", push.publicKey());
        return out;
    }

    /** Whether this browser (its endpoint) gets this member's notifications. */
    @PostMapping("/status")
    Map<String, Object> status(@RequestBody EndpointRequest req, @AuthenticationPrincipal Jwt jwt) {
        return Map.of("subscribed", push.subscribed(CurrentUser.of(jwt), req.endpoint()));
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void subscribe(@RequestBody SubscriptionRequest req, @AuthenticationPrincipal Jwt jwt) {
        Keys keys = req.keys() == null ? new Keys(null, null) : req.keys();
        push.subscribe(CurrentUser.of(jwt), req.endpoint(), keys.p256dh(), keys.auth());
    }

    @PostMapping("/subscriptions/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribe(@RequestBody EndpointRequest req, @AuthenticationPrincipal Jwt jwt) {
        push.unsubscribe(CurrentUser.of(jwt), req.endpoint());
    }

    @PostMapping("/test")
    Map<String, Object> test(@AuthenticationPrincipal Jwt jwt) {
        int sent = push.sendTest(CurrentUser.of(jwt), PushMessages.test());
        return Map.of("sent", sent);
    }
}
