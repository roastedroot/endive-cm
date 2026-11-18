package io.roastedroot.typewire.wasi.p2.clocks.monotonicclock;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.clocks.monotonicclock.Host;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.poll.Pollable;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The host side of {@code wasi:clocks/monotonic-clock}, which reads {@link System#nanoTime()}, the
 * same time source a {@link ScheduledExecutorService} measures its delays with.
 *
 * <p>Instants count nanoseconds from the moment this host was created, which keeps them far from
 * overflowing a {@code u64}.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/clocks/wit/monotonic-clock.wit">wasi:clocks/monotonic-clock</a>
 */
public class HostMonotonicClock implements Host {

    private static final BigInteger RESOLUTION = BigInteger.ONE;
    private static final BigInteger MAX_DELAY = BigInteger.valueOf(Long.MAX_VALUE);

    private final ScheduledExecutorService scheduler;
    private final HostPoll poll;
    private final long origin;

    /**
     * @param scheduler the executor that completes subscriptions once they elapse
     * @param poll the host whose pollables a subscription returns
     */
    public HostMonotonicClock(ScheduledExecutorService scheduler, HostPoll poll) {
        this.scheduler = scheduler;
        this.poll = poll;
        this.origin = System.nanoTime();
    }

    @Override
    public BigInteger now() {
        return BigInteger.valueOf(System.nanoTime() - origin);
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

    /**
     * A delay too long for the scheduler is clamped, since it would never elapse anyway. Dropping
     * the pollable removes its timer from the scheduler.
     */
    private Pollable after(BigInteger delay) {
        if (delay.signum() <= 0) {
            return poll.ownedPollable(CompletableFuture.completedFuture(null));
        }
        CompletableFuture<Void> elapsed = new CompletableFuture<>();
        ScheduledFuture<?> timer =
                scheduler.schedule(
                        () -> elapsed.complete(null),
                        delay.min(MAX_DELAY).longValueExact(),
                        TimeUnit.NANOSECONDS);
        elapsed.whenComplete((ignored, cause) -> timer.cancel(false));
        return poll.ownedPollable(elapsed);
    }
}
