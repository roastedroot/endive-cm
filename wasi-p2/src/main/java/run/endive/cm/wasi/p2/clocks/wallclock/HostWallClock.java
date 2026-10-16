package run.endive.cm.wasi.p2.clocks.wallclock;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import run.endive.cm.wasi.p2.bindings.wasi.clocks.wallclock.Datetime;
import run.endive.cm.wasi.p2.bindings.wasi.clocks.wallclock.Host;
import run.endive.runtime.TrapException;

/**
 * The host side of {@code wasi:clocks/wall-clock}, reading the time from a {@link Clock}.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/clocks/wit/wall-clock.wit">wasi:clocks/wall-clock</a>
 */
public class HostWallClock implements Host {

    /** What the system clock reliably resolves on common platforms. */
    private static final Duration SYSTEM_RESOLUTION = Duration.ofNanos(1_000);

    private final Clock clock;
    private final Datetime resolution;

    /** Reads the system clock. */
    public HostWallClock() {
        this(Clock.systemUTC(), SYSTEM_RESOLUTION);
    }

    /**
     * @param clock the clock to read
     * @param resolution the time one tick of {@code clock} stands for
     */
    public HostWallClock(Clock clock, Duration resolution) {
        this.clock = clock;
        this.resolution = datetime(resolution.getSeconds(), resolution.getNano());
    }

    /** Traps for a time before the Unix epoch, which a {@code datetime} cannot represent. */
    @Override
    public Datetime now() {
        Instant now = clock.instant();
        if (now.isBefore(Instant.EPOCH)) {
            throw new TrapException("wall clock time " + now + " is before the Unix epoch");
        }
        return datetime(now.getEpochSecond(), now.getNano());
    }

    @Override
    public Datetime resolution() {
        return resolution;
    }

    private static Datetime datetime(long seconds, int nanoseconds) {
        return new Datetime(BigInteger.valueOf(seconds), (long) nanoseconds);
    }
}
