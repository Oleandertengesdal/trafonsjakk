package no.trafonsjakk.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/** Hand-made access tokens for attack tests. The test profile's secret is public, so we can sign. */
public final class Jwts {

    public static final String TEST_SECRET = "test-only-jwt-secret-not-used-anywhere-else-0123456789";

    private Jwts() {
    }

    public static JWTClaimsSet.Builder claims(long userId, String username, String role) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer("trafonsjakk")
                .subject(String.valueOf(userId))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("username", username)
                .claim("role", role);
    }

    public static String sign(JWTClaimsSet claims, String secret) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sign(JWTClaimsSet claims) {
        return sign(claims, TEST_SECRET);
    }

    /** {"alg":"none"}: no signature at all. */
    public static String unsigned(JWTClaimsSet claims) {
        return new PlainJWT(claims).serialize();
    }

    /** The claims of a real token, readable without verifying. */
    public static Map<String, Object> peek(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet().getClaims();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
