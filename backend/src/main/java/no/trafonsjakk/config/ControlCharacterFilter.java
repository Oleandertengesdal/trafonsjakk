package no.trafonsjakk.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects NUL characters in the path or query string up front (PostgreSQL can't take them, and no
 * legitimate URL here contains one). Request bodies are covered by {@code @PlainText}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ControlCharacterFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (hasNul(request.getRequestURI()) || hasNul(request.getServletPath()) || hasNulInParameters(request)) {
            response.setStatus(400);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,"
                    + "\"detail\":\"The request contains characters that aren't allowed.\",\"code\":\"request.malformed\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean hasNulInParameters(HttpServletRequest request) {
        String q = request.getQueryString();
        if (q == null || !(q.contains("%00") || q.contains("\u0000"))) {
            return false;
        }
        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            if (hasNul(e.getKey())) {
                return true;
            }
            for (String v : e.getValue()) {
                if (hasNul(v)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasNul(String s) {
        return s != null && (s.indexOf('\u0000') >= 0 || s.contains("%00"));
    }
}
