package no.trafonsjakk.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/**
 * RFC 7807 bodies with a stable {@code code} for errors raised by the security filters (which run
 * before Spring MVC's exception handler), so the frontend can translate them like any other error.
 */
final class ProblemWriter {

    private ProblemWriter() {
    }

    static AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> write(request, response, HttpStatus.UNAUTHORIZED, "auth.not_logged_in",
                "Please log in.");
    }

    static AccessDeniedHandler forbidden() {
        return (request, response, ex) -> {
            if (ex instanceof CsrfException) {
                write(request, response, HttpStatus.FORBIDDEN, "csrf",
                        "Missing or invalid CSRF token. Reload the page and try again.");
            } else {
                write(request, response, HttpStatus.FORBIDDEN, "forbidden", "You don't have permission to do that.");
            }
        };
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
                              String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // All values are fixed strings except the path, which is escaped.
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\",\"instance\":\""
                + escape(request.getRequestURI()) + "\",\"code\":\"" + code + "\"}");
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20 || c == '<' || c == '>') {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
