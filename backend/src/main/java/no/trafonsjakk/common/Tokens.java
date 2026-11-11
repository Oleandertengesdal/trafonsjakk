package no.trafonsjakk.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** Random secrets and one-way hashes for tokens that are stored in the database. */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** No 0/O/1/I/L to keep invite codes easy to read aloud. */
    private static final char[] INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private Tokens() {
    }

    /** 256-bit URL-safe random token. */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Human-friendly invite code like {@code K7QX4-MZP2R}. */
    public static String inviteCode() {
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                sb.append('-');
            }
            sb.append(INVITE_ALPHABET[RANDOM.nextInt(INVITE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** Uppercases and strips separators so "k7qx4 mzp2r" matches "K7QX4-MZP2R". */
    public static String normalizeInviteCode(String code) {
        return code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
