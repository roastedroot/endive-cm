package run.endive.cm.wasi.p2.clocks.monotonicclock;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Ticker;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import run.endive.cm.wasi.p2.bindings.wasi.clocks.monotonicclock.Host;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.io.poll.HostPoll;

/**
 * The host side of {@code wasi:clocks/monotonic-clock}, which reads the ticker of the event loop
 * that also schedules its subscriptions, so an instant and a deadline are measured alike.
 *
 * <p>Instants count nanoseconds from the moment this host was created, which keeps them far from
 * overflowing a {@code u64}.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/clocks/wit/monotonic-clock.wit">wasi:clocks/monotonic-clock</a>
 */
public class HostMonotonicClock implements Host {

    private static final BigInteger RESOLUTION = BigInteger.ONE;
    private static final BigInteger MAX_DELAY = BigInteger.valueOf(Long.MAX_VALUE);

    private final EventExecutor eventLoopExecutor;
    private final HostPoll poll;
    private final Ticker ticker;
    private final long origin;

    /**
     * @param eventLoopExecutor the event loop scheduling subscriptions, whose ticker this reads
     * @param poll the host whose pollables a subscription returns
     */
    public HostMonotonicClock(EventExecutor eventLoopExecutor, HostPoll poll) {
        this.eventLoopExecutor = eventLoopExecutor;
        this.poll = poll;
        this.ticker = eventLoopExecutor.ticker();
        this.origin = ticker.nanoTime();
    }

    @Override
    public BigInteger now() {
        return BigInteger.valueOf(ticker.nanoTime() - origin);
    }

    @Override
    public BigInteger resolution() {
        return RESOLUTION;
    }

    /** Returns a pollable that is ready once {@code when} has passed, which it already has if it is not ahead. */
    @Override
    public Pollable subscribeInstant(BigInteger when) {
        return after(when.subtract(now()));
    }

    @Override
    public Pollable subscribeDuration(BigInteger when) {
        return after(when);
    }

    /** A delay too long for the scheduler is clamped, since it would never elapse anyway. */
    private Pollable after(BigInteger delay) {
        if (delay.signum() <= 0) {
            return poll.ownedPollable(eventLoopExecutor.newSucceededFuture(null));
        }
        return poll.ownedPollable(
                eventLoopExecutor.schedule(
                        () -> {}, delay.min(MAX_DELAY).longValueExact(), TimeUnit.NANOSECONDS));
    }
}
