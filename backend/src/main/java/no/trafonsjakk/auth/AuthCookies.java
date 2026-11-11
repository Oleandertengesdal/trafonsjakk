package no.trafonsjakk.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import no.trafonsjakk.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Writes and clears the HttpOnly auth cookies. */
@Component
public class AuthCookies {

    public static final String ACCESS_COOKIE = "tf_at";
    public static final String REFRESH_COOKIE = "tf_rt";
    private static final String ACCESS_PATH = "/api";
    private static final String REFRESH_PATH = "/api/auth";

    private final AppProperties props;

    public AuthCookies(AppProperties props) {
        this.props = props;
    }

    public void write(HttpServletResponse response, TokenService.IssuedTokens tokens) {
        add(response, cookie(ACCESS_COOKIE, tokens.accessToken(), ACCESS_PATH, "Lax", props.jwt().accessTokenTtl()));
        add(response, cookie(REFRESH_COOKIE, tokens.refreshToken(), REFRESH_PATH, "Strict", props.jwt().refreshTokenTtl()));
    }

    public void clear(HttpServletResponse response) {
        add(response, cookie(ACCESS_COOKIE, "", ACCESS_PATH, "Lax", Duration.ZERO));
        add(response, cookie(REFRESH_COOKIE, "", REFRESH_PATH, "Strict", Duration.ZERO));
    }

    private ResponseCookie cookie(String name, String value, String path, String sameSite, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.cookies().secure())
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
