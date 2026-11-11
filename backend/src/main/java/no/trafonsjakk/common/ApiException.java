package no.trafonsjakk.common;

import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * An error that is safe to show to the client. Rendered as RFC 7807 by {@link GlobalExceptionHandler}.
 * <p>
 * {@code code} is a stable identifier (e.g. {@code game.not_found}) that the frontend translates
 * (Norwegian/English); {@code params} carries values for the translated text. The English
 * {@code message} is the fallback {@code detail}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> params;
    private final Duration retryAfter;

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> params,
                        Duration retryAfter) {
        super(message);
        this.status = status;
        this.code = code;
        this.params = params == null ? Map.of() : Map.copyOf(params);
        this.retryAfter = retryAfter;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> params() {
        return params;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    /** Returns a copy with parameters for the translated message. */
    public ApiException with(String key, Object value) {
        java.util.HashMap<String, Object> p = new java.util.HashMap<>(params);
        p.put(key, value);
        return new ApiException(status, code, getMessage(), p, retryAfter);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, null, null);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, null, null);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message, null, null);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, null, null);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message, null, null);
    }

    public static ApiException tooManyRequests(String code, String message, Duration retryAfter) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message, null, retryAfter);
    }
}
