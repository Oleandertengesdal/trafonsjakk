package no.trafonsjakk.online;

import java.math.BigDecimal;

/**
 * How much time players get. {@link Kind#LIVE}: a clock with a starting time and a Fischer increment
 * added after each move ("10+5"). {@link Kind#DAILY}: a number of days for each move, which doesn't
 * accumulate ("3d").
 */
public record TimeControl(Kind kind, int initialSeconds, int incrementSeconds, int daysPerMove) {

    public enum Kind { LIVE, DAILY }

    public static final int MIN_INITIAL_SECONDS = 60;
    public static final int MAX_INITIAL_SECONDS = 3 * 3600;
    public static final int MAX_INCREMENT_SECONDS = 60;
    public static final int MAX_DAYS = 14;

    public TimeControl {
        if (kind == null) {
            throw new IllegalArgumentException("No kind of time control");
        }
        if (kind == Kind.LIVE && (initialSeconds < MIN_INITIAL_SECONDS || initialSeconds > MAX_INITIAL_SECONDS
                || incrementSeconds < 0 || incrementSeconds > MAX_INCREMENT_SECONDS || daysPerMove != 0)) {
            throw new IllegalArgumentException("Starting time must be 1–180 minutes and the increment 0–60 seconds");
        }
        if (kind == Kind.DAILY && (daysPerMove < 1 || daysPerMove > MAX_DAYS || initialSeconds != 0 || incrementSeconds != 0)) {
            throw new IllegalArgumentException("A daily game gives 1–14 days per move");
        }
    }

    public static TimeControl live(int initialSeconds, int incrementSeconds) {
        return new TimeControl(Kind.LIVE, initialSeconds, incrementSeconds, 0);
    }

    public static TimeControl daily(int days) {
        return new TimeControl(Kind.DAILY, 0, 0, days);
    }

    /** "10+5" (minutes + seconds) or "3d". Stored on the game, and shown as is in both languages. */
    public String label() {
        if (kind == Kind.DAILY) {
            return daysPerMove + "d";
        }
        String minutes = BigDecimal.valueOf(initialSeconds).divide(BigDecimal.valueOf(60)).stripTrailingZeros().toPlainString();
        return minutes + "+" + incrementSeconds;
    }

    public boolean live() {
        return kind == Kind.LIVE;
    }
}
