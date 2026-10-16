package run.endive.cm.wasi.p2.clocks.monotonicclock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.io.poll.HostPoll;

class HostMonotonicClockTest {

    private static final BigInteger MILLISECOND = BigInteger.valueOf(1_000_000);
    private static final BigInteger MAX_U64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    private EventExecutor executor;
    private HostPoll poll;
    private HostMonotonicClock clock;

    @BeforeEach
    void startExecutor() {
        executor = new DefaultEventExecutor();
        poll = new HostPoll(executor);
        clock = new HostMonotonicClock(executor, poll);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
    }

    @Test
    void nowNeverDecreases() {
        BigInteger previous = clock.now();
        assertTrue(previous.signum() >= 0);
        for (int i = 0; i < 1_000; i++) {
            BigInteger next = clock.now();
            assertTrue(next.compareTo(previous) >= 0);
            previous = next;
        }
    }

    @Test
    void resolutionIsOneNanosecond() {
        assertEquals(BigInteger.ONE, clock.resolution());
    }

    @Test
    void aDurationIsReadyOnlyOnceItHasElapsed() {
        BigInteger start = clock.now();
        Pollable pollable = clock.subscribeDuration(MILLISECOND.multiply(BigInteger.valueOf(50)));

        assertFalse(pollable.ready());
        pollable.block();

        assertTrue(pollable.ready());
        assertTrue(
                clock.now().subtract(start).compareTo(MILLISECOND.multiply(BigInteger.valueOf(50)))
                        >= 0);
    }

    @Test
    void anInstantIsReadyOnlyOnceItHasPassed() {
        BigInteger when = clock.now().add(MILLISECOND.multiply(BigInteger.valueOf(30)));
        Pollable pollable = clock.subscribeInstant(when);

        assertFalse(pollable.ready());
        pollable.block();

        assertTrue(clock.now().compareTo(when) >= 0);
    }

    @Test
    void aZeroDurationIsReadyAtOnce() {
        assertTrue(clock.subscribeDuration(BigInteger.ZERO).ready());
    }

    @Test
    void anInstantAlreadyPassedIsReadyAtOnce() {
        assertTrue(clock.subscribeInstant(BigInteger.ZERO).ready());
    }

    /** A subscription is a timeout a guest can poll alongside other pollables. */
    @Test
    void theShorterOfTwoSubscriptionsIsReadyFirst() {
        Pollable longer = clock.subscribeDuration(MILLISECOND.multiply(BigInteger.valueOf(10_000)));
        Pollable shorter = clock.subscribeDuration(MILLISECOND.multiply(BigInteger.valueOf(20)));

        assertEquals(List.of(1L), poll.poll(List.of(longer, shorter)));
        longer.drop();
    }

    /** The largest duration exceeds what the scheduler takes, so it is clamped rather than refused. */
    @Test
    void theLargestDurationNeverElapses() {
        Pollable pollable = clock.subscribeDuration(MAX_U64);

        assertFalse(pollable.ready());
        pollable.drop();
    }
}
