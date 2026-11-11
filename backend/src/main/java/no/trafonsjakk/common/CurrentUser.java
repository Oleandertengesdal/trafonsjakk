package no.trafonsjakk.common;

import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller, extracted from the access token. */
public record CurrentUser(long id, String username, boolean admin) {

    public static CurrentUser of(Jwt jwt) {
        if (jwt == null) {
            throw ApiException.unauthorized("auth.not_logged_in", "Not logged in");
        }
        return new CurrentUser(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("username"),
                "ADMIN".equals(jwt.getClaimAsString("role")));
    }
}
