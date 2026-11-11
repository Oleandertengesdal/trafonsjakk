package no.trafonsjakk.support;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One "browser": keeps cookies between requests and sends the CSRF header on state-changing calls,
 * exactly like the frontend's fetch client does.
 */
public final class ApiSession {

    public record Response(int status, HttpHeaders headers, JsonNode json, String body) {
        public String code() {
            return json == null ? null : json.path("code").asString(null);
        }

        public List<String> setCookies() {
            return headers.allValues("set-cookie");
        }

        public Optional<String> setCookie(String name) {
            return setCookies().stream().filter(c -> c.startsWith(name + "=")).findFirst();
        }

        @Override
        public String toString() {
            return status + " " + body;
        }
    }

    private static final JsonMapper JSON = JsonMapper.shared();

    private final String base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient client;

    public ApiSession(int port) {
        this.base = "http://localhost:" + port;
        this.client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
    }

    public Response get(String path) {
        return send("GET", path, null, true);
    }

    public Response post(String path) {
        return send("POST", path, null, true);
    }

    public Response post(String path, Object body) {
        return send("POST", path, body, true);
    }

    public Response put(String path, Object body) {
        return send("PUT", path, body, true);
    }

    public Response patch(String path, Object body) {
        return send("PATCH", path, body, true);
    }

    public Response delete(String path) {
        return send("DELETE", path, null, true);
    }

    /** A state-changing call without the X-XSRF-TOKEN header (what a cross-site attacker would send). */
    public Response postWithoutCsrf(String path, Object body) {
        return send("POST", path, body, false);
    }

    /** A POST with this session's cookies but an arbitrary X-XSRF-TOKEN value (a guessed token). */
    public Response postWithCsrfHeader(String path, String headerValue) {
        csrfOverride = headerValue;
        try {
            return send("POST", path, null, true);
        } finally {
            csrfOverride = null;
        }
    }

    private String csrfOverride;
    private final Map<String, String> extraHeaders = new LinkedHashMap<>();

    /** Sends this header on every following request (e.g. Origin or X-Forwarded-For). */
    public ApiSession header(String name, String value) {
        if (value == null) {
            extraHeaders.remove(name);
        } else {
            extraHeaders.put(name, value);
        }
        return this;
    }

    /** Any method, any body, any content type (the CSRF header is still sent for writes). */
    public Response raw(String method, String path, String contentType, String body) {
        rawContentType = contentType;
        try {
            return send(method, path, body, true);
        } finally {
            rawContentType = null;
        }
    }

    private String rawContentType;

    public Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals(name)).map(HttpCookie::getValue)
                .findFirst();
    }

    /** Puts a cookie back (e.g. replaying an old refresh token). */
    public void setCookie(String name, String value, String path) {
        HttpCookie c = new HttpCookie(name, value);
        c.setPath(path);
        c.setVersion(0);
        cookies.getCookieStore().getCookies().stream().filter(x -> x.getName().equals(name))
                .forEach(x -> cookies.getCookieStore().remove(URI.create(base), x));
        cookies.getCookieStore().add(URI.create(base + path), c);
    }

    private Response send(String method, String path, Object body, boolean withCsrf) {
        try {
            boolean unsafe = !method.equals("GET") && !method.equals("OPTIONS") && !method.equals("HEAD");
            if (unsafe && withCsrf && cookie("XSRF-TOKEN").isEmpty()) {
                send("GET", "/api/auth/csrf", null, false);
            }
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json");
            extraHeaders.forEach(b::header);
            if (unsafe && withCsrf) {
                b.header("X-XSRF-TOKEN", csrfOverride != null ? csrfOverride : cookie("XSRF-TOKEN").orElse(""));
            }
            if (body != null) {
                b.header("Content-Type", rawContentType != null ? rawContentType : "application/json");
                String text = body instanceof String s ? s : JSON.writeValueAsString(body);
                b.method(method, HttpRequest.BodyPublishers.ofString(text));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = null;
            if (res.body() != null && !res.body().isBlank() && res.body().trim().matches("^[\\[{].*")) {
                json = JSON.readTree(res.body());
            }
            return new Response(res.statusCode(), res.headers(), json, res.body());
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        }
    }
}
