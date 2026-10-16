package run.endive.cm.wasi.p2;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.wasi.p2.bindings.ImportsWorld;
import run.endive.cm.wasi.p2.clocks.monotonicclock.HostMonotonicClock;
import run.endive.cm.wasi.p2.clocks.wallclock.HostWallClock;
import run.endive.cm.wasi.p2.io.poll.HostPoll;

/**
 * The WASI preview 2 interfaces this host implements, ready to satisfy a component's imports.
 *
 * <p>Blocking calls such as {@code poll} wait on the thread the guest runs on, while the event
 * loop completes what they wait for. A guest must therefore never run on that event loop.
 *
 * <pre>{@code
 * try (var wasi = WasiPreview2.builder().build()) {
 *     ImportsWorld world = wasi.instantiate(new ComponentStore(), component);
 * }
 * }</pre>
 */
public final class WasiPreview2 implements ImportsWorld.Imports, AutoCloseable {

    private final EventExecutor eventLoopExecutor;
    private final boolean ownsEventLoop;
    private final HostPoll poll;
    private final HostMonotonicClock monotonicClock;
    private final HostWallClock wallClock;

    private WasiPreview2(Builder builder) {
        this.ownsEventLoop = builder.eventLoopExecutor == null;
        this.eventLoopExecutor =
                ownsEventLoop ? new DefaultEventExecutor() : builder.eventLoopExecutor;
        this.poll = new HostPoll(eventLoopExecutor);
        this.monotonicClock = new HostMonotonicClock(eventLoopExecutor, poll);
        this.wallClock =
                builder.clock == null
                        ? new HostWallClock()
                        : new HostWallClock(builder.clock, builder.clockResolution);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Instantiates {@code component}, satisfying its WASI imports with this host. */
    public ImportsWorld instantiate(ComponentStore store, WasmComponent component) {
        return ImportsWorld.instantiate(store, component, this);
    }

    @Override
    public HostPoll poll() {
        return poll;
    }

    @Override
    public HostMonotonicClock monotonicClock() {
        return monotonicClock;
    }

    @Override
    public HostWallClock wallClock() {
        return wallClock;
    }

    /** Shuts down the event loop, unless the builder was given an external one to use. */
    @Override
    public void close() {
        if (ownsEventLoop) {
            eventLoopExecutor.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    public static final class Builder {

        private EventExecutor eventLoopExecutor;
        private Clock clock;
        private Duration clockResolution;

        private Builder() {}

        /**
         * The event loop where non-blocking IO and other general events run. By default, one
         * is created and then shut down on {@link #close()}. When set by the embedder, it is
         * their responsibility to shut it down cleanly.
         */
        public Builder withEventLoopExecutor(EventExecutor eventLoopExecutor) {
            this.eventLoopExecutor = eventLoopExecutor;
            return this;
        }

        /**
         * The clock {@code wasi:clocks/wall-clock} reads, which defaults to the system clock.
         *
         * @param resolution the time one tick of {@code clock} stands for
         */
        public Builder withWallClock(Clock clock, Duration resolution) {
            this.clock = clock;
            this.clockResolution = resolution;
            return this;
        }

        public WasiPreview2 build() {
            return new WasiPreview2(this);
        }
    }
}
