package no.trafonsjakk.online;

import java.time.Duration;
import java.time.Instant;
import no.trafonsjakk.chess.Color;

/**
 * The clock of an online game, kept by the server. Immutable: each move gives a new one.
 * <ul>
 *   <li><b>Live:</b> each side has a time bank; thinking time is taken off it and the increment added
 *       after each move. The first move of each side is free, but has to come within
 *       {@link #FIRST_MOVE_LIMIT} or the game is cancelled (as on lichess).</li>
 *   <li><b>Daily:</b> each move has its own deadline, so many days to move; the first two moves too,
 *       but missing those cancels the game instead of losing it.</li>
 *   <li><b>Network delay:</b> the server measures from the moment it stored the opponent's move, which
 *       includes the time the move took to reach the phone and the answer to come back. The phone says
 *       how long the player really thought; the server believes it, but never saves more than
 *       {@link #LAG_ALLOWANCE} per move that way. For the same reason a flag only counts as fallen once
 *       the allowance has also passed: a move made in time may still be on its way.</li>
 * </ul>
 * Times are stored as what was left when the turn started ({@code whiteMs}, {@code blackMs}) plus
 * when it started, so nothing has to tick on the server.
 */
public record Clock(TimeControl control, long whiteMs, long blackMs, Color toMove, int plies, Instant turnStartedAt,
                    boolean running) {

    public static final Duration LAG_ALLOWANCE = Duration.ofMillis(1500);
    public static final Duration FIRST_MOVE_LIMIT = Duration.ofSeconds(60);

    public static Clock start(TimeControl control, Instant now) {
        long bank = control.live() ? control.initialSeconds() * 1000L : 0;
        return new Clock(control, bank, bank, Color.WHITE, 0, now, true);
    }

    /** Both sides have made their first move, so the live clock is counting. */
    private boolean counting() {
        return plies >= 2;
    }

    private long stored(Color side) {
        return side == Color.WHITE ? whiteMs : blackMs;
    }

    private long elapsed(Instant now) {
        return Math.max(0, Duration.between(turnStartedAt, now).toMillis());
    }

    private static long charge(long elapsed, Long thinkMs) {
        if (thinkMs == null) {
            return elapsed;
        }
        return Math.clamp(thinkMs, Math.max(0, elapsed - LAG_ALLOWANCE.toMillis()), elapsed);
    }

    /** The clock after the side to move moves at {@code now}, having thought for {@code thinkMs} by its own count. */
    public Clock afterMove(Instant now, Long thinkMs) {
        long w = whiteMs;
        long b = blackMs;
        if (control.live() && counting()) {
            long left = Math.max(0, stored(toMove) - charge(elapsed(now), thinkMs)) + control.incrementSeconds() * 1000L;
            if (toMove == Color.WHITE) {
                w = left;
            } else {
                b = left;
            }
        }
        return new Clock(control, w, b, toMove.other(), plies + 1, now, running);
    }

    /** Stops the clock (the game ended), keeping what the side to move had left. */
    public Clock stop(Instant now) {
        if (!running) {
            return this;
        }
        long w = remainingMs(Color.WHITE, now);
        long b = remainingMs(Color.BLACK, now);
        return new Clock(control, control.live() ? w : 0, control.live() ? b : 0, toMove, plies, now, false);
    }

    /** Time left for {@code side} at {@code now}: never below zero. Daily: until the deadline. */
    public long remainingMs(Color side, Instant now) {
        if (!control.live()) {
            if (running && side == toMove) {
                return Math.max(0, Duration.between(now, deadline()).toMillis());
            }
            return Duration.ofDays(control.daysPerMove()).toMillis();
        }
        if (!running || side != toMove || !counting()) {
            return stored(side);
        }
        return Math.max(0, stored(side) - elapsed(now));
    }

    /** Daily games: when the side to move has to have moved. Null for live games. */
    public Instant deadline() {
        return control.live() ? null : turnStartedAt.plus(Duration.ofDays(control.daysPerMove()));
    }

    /** Who has run out of time at {@code now} (taking network delay into account), or null. */
    public Color timedOut(Instant now) {
        if (!running || !counting()) {
            return null; // before both first moves, a missed move cancels the game instead (see abortDue)
        }
        if (control.live()) {
            return stored(toMove) - elapsed(now) <= -LAG_ALLOWANCE.toMillis() ? toMove : null;
        }
        return !now.isBefore(deadline()) ? toMove : null;
    }

    /** Whether a move arriving now, thought about for {@code thinkMs}, came too late. */
    public boolean wouldFlag(Instant now, Long thinkMs) {
        if (!running || !counting()) {
            return false;
        }
        if (control.live()) {
            return stored(toMove) - charge(elapsed(now), thinkMs) <= 0;
        }
        return !now.isBefore(deadline());
    }

    /** One of the first two moves is overdue: the game should be cancelled without a result. */
    public boolean abortDue(Instant now) {
        if (!abortable()) {
            return false;
        }
        Instant limit = control.live() ? turnStartedAt.plus(FIRST_MOVE_LIMIT) : deadline();
        return !now.isBefore(limit);
    }

    /** Before both players have moved, either may call the game off. */
    public boolean abortable() {
        return running && !counting();
    }
}
