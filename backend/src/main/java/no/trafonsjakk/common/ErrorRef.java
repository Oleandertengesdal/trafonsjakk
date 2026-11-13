package no.trafonsjakk.common;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A short reference for an unexpected error, shown to the person and written to the log next to the
 * stack trace, so "it said K7Q2MX" finds the right log line. Not a secret, just unique enough.
 */
public final class ErrorRef {

    /** No 0/O or 1/I/L, so it can be read out loud or typed from a screenshot. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    private ErrorRef() {
    }

    public static String next() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        char[] ref = new char[6];
        for (int i = 0; i < ref.length; i++) {
            ref[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(ref);
    }
}
