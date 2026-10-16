package run.endive.cm.wasi.p2.io.poll;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Host;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.io.Blocking;
import run.endive.runtime.TrapException;

/**
 * The host side of {@code wasi:io/poll}, where each pollable is ready while the Netty future it
 * currently stands for is complete.
 *
 * <p>Polling and blocking wait on the calling thread, so neither may run on the event loop that
 * completes the futures.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/io/wit/poll.wit">wasi:io/poll</a>
 */
public class HostPoll implements Host {

    private final EventExecutor eventLoopExecutor;

    public HostPoll(EventExecutor eventLoopExecutor) {
        this.eventLoopExecutor = eventLoopExecutor;
    }

    /**
     * Creates a pollable that is ready once {@code future} completes, whether it succeeds or fails. Dropping it
     * leaves the future running, so the future may be shared with whatever else waits on it.
     */
    public Pollable pollable(Future<?> future) {
        return new FuturePollable(() -> future, false);
    }

    /**
     * Creates a pollable that is ready while the future {@code readiness} currently supplies is
     * complete. It is asked again each time the pollable is checked, so a source that stops being
     * ready, such as a stream whose buffered input was read, supplies a new future. Dropping the
     * pollable leaves every future running.
     */
    public Pollable pollable(Supplier<? extends Future<?>> readiness) {
        return new FuturePollable(readiness, false);
    }

    /**
     * Creates a pollable that is ready once {@code future} completes, which cancels the future when the guest
     * drops it. Suits a future that exists only to serve this pollable, such as a timer.
     */
    public Pollable ownedPollable(Future<?> future) {
        return new FuturePollable(() -> future, true);
    }

    /**
     * Waits until at least one pollable is ready and returns the index of every ready one.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/io/wit/poll.wit">wasi:io/poll</a>
     */
    @Override
    public List<Long> poll(List<Pollable> pollables) {
        Blocking.check(eventLoopExecutor);
        if (pollables.isEmpty()) {
            throw new TrapException("poll requires at least one pollable");
        }
        List<Future<?>> futures = new ArrayList<>(pollables.size());
        for (Pollable pollable : pollables) {
            futures.add(futureOf(pollable));
        }

        Blocking.awaitAny(eventLoopExecutor, futures);

        List<Long> ready = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            if (futures.get(i).isDone()) {
                ready.add((long) i);
            }
        }
        return ready;
    }

    private static Future<?> futureOf(Pollable pollable) {
        if (!(pollable instanceof FuturePollable)) {
            throw new IllegalArgumentException(
                    "pollable " + pollable + " was not created by " + HostPoll.class.getName());
        }
        return ((FuturePollable) pollable).readiness.get();
    }

    private final class FuturePollable implements Pollable {

        private final Supplier<? extends Future<?>> readiness;
        private final boolean owned;

        private FuturePollable(Supplier<? extends Future<?>> readiness, boolean owned) {
            this.readiness = readiness;
            this.owned = owned;
        }

        @Override
        public Boolean ready() {
            return readiness.get().isDone();
        }

        @Override
        public void block() {
            Blocking.await(eventLoopExecutor, readiness.get());
        }

        @Override
        public void drop() {
            if (owned) {
                readiness.get().cancel(false);
            }
        }
    }
}
