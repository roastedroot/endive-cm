package run.endive.cm.wasi.p2.clocks.wallclock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.clocks.wallclock.Datetime;
import run.endive.runtime.TrapException;

class HostWallClockTest {

    @Test
    void nowIsSecondsAndNanosecondsSinceTheEpoch() {
        Instant instant = Instant.ofEpochSecond(1_700_000_000L, 123_456_789L);
        HostWallClock clock =
                new HostWallClock(Clock.fixed(instant, ZoneOffset.UTC), Duration.ofNanos(1));

        assertEquals(new Datetime(BigInteger.valueOf(1_700_000_000L), 123_456_789L), clock.now());
    }

    @Test
    void aTimeBeforeTheEpochTraps() {
        HostWallClock clock =
                new HostWallClock(
                        Clock.fixed(Instant.EPOCH.minusNanos(1), ZoneOffset.UTC),
                        Duration.ofNanos(1));

        assertThrows(TrapException.class, clock::now);
    }

    @Test
    void resolutionSplitsIntoSecondsAndNanoseconds() {
        HostWallClock clock = new HostWallClock(Clock.systemUTC(), Duration.ofMillis(1_500));

        assertEquals(new Datetime(BigInteger.ONE, 500_000_000L), clock.resolution());
    }

    @Test
    void theSystemClockReadsTheCurrentTime() {
        HostWallClock clock = new HostWallClock();

        Datetime now = clock.now();

        assertTrue(now.seconds().compareTo(BigInteger.valueOf(1_700_000_000L)) > 0);
        assertTrue(now.nanoseconds() < 1_000_000_000L);
        assertEquals(new Datetime(BigInteger.ZERO, 1_000L), clock.resolution());
    }
}
