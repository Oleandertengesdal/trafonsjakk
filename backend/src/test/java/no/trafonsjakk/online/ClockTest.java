package no.trafonsjakk.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import no.trafonsjakk.chess.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClockTest {

    static final Instant T0 = Instant.parse("2026-12-24T18:00:00Z");

    static Instant at(long ms) {
        return T0.plusMillis(ms);
    }

    /** Plays moves at the given times (ms after T0), each thought about for exactly as long as it took. */
    static Clock play(Clock c, long... times) {
        for (long t : times) {
            c = c.afterMove(at(t), null);
        }
        return c;
    }

    // ---- time controls ----------------------------------------------------------------------------------

    @Test
    void describesTimeControlsTheUsualWay() {
        assertThat(TimeControl.live(600, 5).label()).isEqualTo("10+5");
        assertThat(TimeControl.live(180, 2).label()).isEqualTo("3+2");
        assertThat(TimeControl.live(90, 0).label()).isEqualTo("1.5+0");
        assertThat(TimeControl.daily(3).label()).isEqualTo("3d");
    }

    @ParameterizedTest
    @CsvSource({"59, 0", "10801, 0", "600, -1", "600, 61"})
    void refusesSillyLiveClocks(int initial, int increment) {
        assertThatThrownBy(() -> TimeControl.live(initial, increment)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0", "15"})
    void refusesSillyDailyClocks(int days) {
        assertThatThrownBy(() -> TimeControl.daily(days)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- live clocks ------------------------------------------------------------------------------------

    @Test
    void theFirstMoveOfEachSideIsFree() {
        Clock c = Clock.start(TimeControl.live(300, 3), T0);
        // White thinks 20 s, Black 25 s: neither is charged, and neither gets the increment
        c = play(c, 20_000, 45_000);
        assertThat(c.remainingMs(Color.WHITE, at(45_000))).isEqualTo(300_000);
        assertThat(c.remainingMs(Color.BLACK, at(45_000))).isEqualTo(300_000);
        assertThat(c.running()).isTrue();
    }

    @Test
    void chargesThinkingTimeAndAddsTheIncrement() {
        Clock c = play(Clock.start(TimeControl.live(300, 3), T0), 1_000, 2_000);
        // White now thinks for 10 s: 300 − 10 + 3
        c = c.afterMove(at(12_000), null);
        assertThat(c.remainingMs(Color.WHITE, at(12_000))).isEqualTo(293_000);
        // Black's clock is running: 4 s later Black has 4 s less
        assertThat(c.remainingMs(Color.BLACK, at(16_000))).isEqualTo(296_000);
        assertThat(c.toMove()).isEqualTo(Color.BLACK);
    }

    @Test
    void theOpponentsClockDoesNotMoveWhileYouThink() {
        Clock c = play(Clock.start(TimeControl.live(60, 0), T0), 1_000, 2_000);
        assertThat(c.remainingMs(Color.BLACK, at(50_000))).isEqualTo(60_000);
        assertThat(c.remainingMs(Color.WHITE, at(50_000))).isEqualTo(12_000);
    }

    @Test
    void forgivesUpToALittleNetworkDelayButNoMore() {
        Clock c = play(Clock.start(TimeControl.live(300, 0), T0), 1_000, 2_000);
        // 5 s passed on the server; the phone says the player thought for 4.2 s (0.8 s was the network)
        Clock honest = c.afterMove(at(7_000), 4_200L);
        assertThat(honest.remainingMs(Color.WHITE, at(7_000))).isEqualTo(295_800);
        // Claiming 0 s only saves the allowance, not the whole time
        Clock greedy = c.afterMove(at(7_000), 0L);
        assertThat(greedy.remainingMs(Color.WHITE, at(7_000))).isEqualTo(300_000 - 5_000 + Clock.LAG_ALLOWANCE.toMillis());
        // And claiming more than really passed is capped at what passed
        Clock odd = c.afterMove(at(7_000), 60_000L);
        assertThat(odd.remainingMs(Color.WHITE, at(7_000))).isEqualTo(295_000);
    }

    @Test
    void runsOutOfTimeOnlyAfterTheAllowanceHasPassed() {
        Clock c = play(Clock.start(TimeControl.live(60, 0), T0), 1_000, 2_000);
        Instant flag = at(2_000 + 60_000);
        assertThat(c.remainingMs(Color.WHITE, flag)).isZero();
        // A move made just in time may still be on its way, so nobody has lost yet
        assertThat(c.timedOut(flag)).isNull();
        assertThat(c.timedOut(flag.plus(Clock.LAG_ALLOWANCE))).isEqualTo(Color.WHITE);
        // Shown time never goes below zero
        assertThat(c.remainingMs(Color.WHITE, flag.plusSeconds(30))).isZero();
    }

    @Test
    void aMoveThatArrivesTooLateLoses() {
        Clock c = play(Clock.start(TimeControl.live(60, 0), T0), 1_000, 2_000);
        // 61 s after Black's move, and the phone says 61 s too: White was out of time
        assertThat(c.wouldFlag(at(63_000), 61_000L)).isTrue();
        // 60.5 s on the server, 59.9 s on the phone: in time
        assertThat(c.wouldFlag(at(62_500), 59_900L)).isFalse();
    }

    @Test
    void theFirstMoveMustComeQuicklyOrTheGameIsCancelled() {
        Clock c = Clock.start(TimeControl.live(600, 5), T0);
        assertThat(c.abortDue(T0.plus(Clock.FIRST_MOVE_LIMIT).minusMillis(1))).isFalse();
        assertThat(c.abortDue(T0.plus(Clock.FIRST_MOVE_LIMIT))).isTrue();
        // Black's first move too
        Clock afterWhite = c.afterMove(at(5_000), null);
        assertThat(afterWhite.abortDue(at(5_000).plus(Clock.FIRST_MOVE_LIMIT))).isTrue();
        // After both first moves the clock takes over
        Clock running = afterWhite.afterMove(at(6_000), null);
        assertThat(running.abortDue(at(6_000).plus(Clock.FIRST_MOVE_LIMIT))).isFalse();
        assertThat(running.abortable()).isFalse();
    }

    // ---- daily games ------------------------------------------------------------------------------------

    @Test
    void dailyGamesGiveEachMoveItsOwnDeadline() {
        Clock c = Clock.start(TimeControl.daily(2), T0);
        Duration twoDays = Duration.ofDays(2);
        assertThat(c.deadline()).isEqualTo(T0.plus(twoDays));
        // Whatever White used doesn't carry over: Black gets two full days
        c = c.afterMove(at(Duration.ofHours(40).toMillis()), null);
        assertThat(c.deadline()).isEqualTo(at(Duration.ofHours(40).toMillis()).plus(twoDays));
        assertThat(c.remainingMs(Color.BLACK, at(Duration.ofHours(41).toMillis()))).isEqualTo(Duration.ofHours(47).toMillis());
    }

    @Test
    void aDailyGameIsLostByMissingTheDeadline() {
        Clock c = play(Clock.start(TimeControl.daily(1), T0), 1_000, 2_000);
        assertThat(c.timedOut(at(2_000).plus(Duration.ofDays(1)).minusSeconds(1))).isNull();
        assertThat(c.timedOut(at(2_000).plus(Duration.ofDays(1)))).isEqualTo(Color.WHITE);
    }

    @Test
    void aDailyGameWhereNobodyStartsIsCancelledWhenTheFirstDeadlinePasses() {
        Clock c = Clock.start(TimeControl.daily(3), T0);
        assertThat(c.abortDue(T0.plus(Duration.ofDays(3)).minusSeconds(1))).isFalse();
        assertThat(c.abortDue(T0.plus(Duration.ofDays(3)))).isTrue();
        assertThat(c.timedOut(T0.plus(Duration.ofDays(4)))).isNull();
    }

    @Test
    void stopsWhenTheGameEnds() {
        Clock c = play(Clock.start(TimeControl.live(60, 0), T0), 1_000, 2_000).stop(at(10_000));
        assertThat(c.running()).isFalse();
        assertThat(c.remainingMs(Color.WHITE, at(500_000))).isEqualTo(52_000);
        assertThat(c.timedOut(at(500_000))).isNull();
    }
}
