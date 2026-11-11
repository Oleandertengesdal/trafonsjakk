package no.trafonsjakk.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import no.trafonsjakk.auth.AuthCookies;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests from the HttpOnly access-token cookie.
 * <p>
 * Deliberately not Spring's OAuth2 resource-server support: that treats every request carrying a
 * token as a "bearer" request and exempts it from CSRF protection, which is only safe when the
 * token comes from an Authorization header. Ours comes from a cookie the browser sends
 * automatically, so CSRF must stay on (see AuthApiTest#stateChangingRequestsNeedTheCsrfHeaderEvenWhenLoggedIn).
 * <p>
 * A valid signature isn't enough. The token's login session ({@code sid}) must still be live and the
 * account enabled, and its role must match the database (one indexed lookup per request). So logout,
 * a password change, disabling an account, a role change or detected refresh-token theft take effect
 * immediately instead of when the 15-minute token expires. See SecurityApiTest.
 * <p>
 * An invalid, expired or stale token simply leaves the request unauthenticated: protected endpoints
 * then answer 401, the frontend refreshes (which issues a token with the current role, or fails and
 * signs the user out), and public endpoints like login keep working.
 */
final class CookieJwtAuthenticationFilter extends OncePerRequestFilter {

    /** Current role name of an enabled user whose session is live; empty otherwise. */
    @FunctionalInterface
    interface SessionCheck {
        Optional<String> liveRole(long userId, UUID sessionId);
    }

    private final JwtDecoder decoder;
    private final Converter<Jwt, AbstractAuthenticationToken> converter;
    private final SessionCheck sessions;

    CookieJwtAuthenticationFilter(JwtDecoder decoder, Converter<Jwt, AbstractAuthenticationToken> converter,
                                  SessionCheck sessions) {
        this.decoder = decoder;
        this.converter = converter;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null && request.getCookies() != null) {
            // A browser can hold more than one cookie with this name (e.g. a stale one with other
            // attributes); use the first that is valid instead of failing on whichever comes first.
            for (Cookie cookie : request.getCookies()) {
                if (!AuthCookies.ACCESS_COOKIE.equals(cookie.getName()) || cookie.getValue().isBlank()) {
                    continue;
                }
                AbstractAuthenticationToken auth = authenticate(cookie.getValue());
                if (auth != null) {
                    auth.setDetails(request.getRemoteAddr());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    break;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private AbstractAuthenticationToken authenticate(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return isLive(jwt) ? converter.convert(jwt) : null;
        } catch (JwtException | IllegalArgumentException e) {
            // expired, tampered, signed with an old key, or malformed claims
            return null;
        }
    }

    private boolean isLive(Jwt jwt) {
        String sid = jwt.getClaimAsString("sid");
        if (sid == null) {
            return false;
        }
        long userId = Long.parseLong(jwt.getSubject());
        return sessions.liveRole(userId, UUID.fromString(sid))
                .map(role -> role.equals(jwt.getClaimAsString("role")))
                .orElse(false);
    }
}
