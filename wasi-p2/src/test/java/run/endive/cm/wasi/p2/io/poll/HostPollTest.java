package run.endive.cm.wasi.p2.io.poll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.runtime.TrapException;
import run.endive.wasm.WasmEngineException;

class HostPollTest {

    private EventExecutor executor;
    private HostPoll host;

    @BeforeEach
    void startExecutor() {
        executor = new DefaultEventExecutor();
        host = new HostPoll(executor);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
    }

    /** A failed future counts as ready, since an error is reported by marking its source ready. */
    @Test
    void pollReturnsTheIndexOfEveryReadyPollable() {
        Promise<Void> pending = executor.newPromise();
        Promise<Void> failed = executor.newPromise();
        failed.setFailure(new IllegalStateException("failed"));

        List<Long> ready =
                host.poll(
                        List.of(
                                host.pollable(executor.newSucceededFuture(null)),
                                host.pollable(pending),
                                host.pollable(failed)));

        assertEquals(List.of(0L, 2L), ready);
    }

    /** A borrowed handle may be listed more than once, and each position is reported. */
    @Test
    void aPollableListedTwiceIsReportedAtEachIndex() {
        Pollable done = host.pollable(executor.newSucceededFuture(null));
        Pollable pending = host.pollable(executor.newPromise());

        assertEquals(List.of(0L, 2L), host.poll(List.of(done, pending, done)));
    }

    @Test
    void pollWaitsUntilAPollableIsReady() {
        Promise<Void> later = executor.newPromise();
        executor.schedule(() -> later.setSuccess(null), 50, TimeUnit.MILLISECONDS);

        long start = System.nanoTime();
        List<Long> ready =
                host.poll(List.of(host.pollable(executor.newPromise()), host.pollable(later)));

        assertEquals(List.of(1L), ready);
        assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(40));
    }

    @Test
    void pollingNothingTraps() {
        assertThrows(TrapException.class, () -> host.poll(List.of()));
    }

    /** Polling is blocking whether or not a pollable is ready, so the event loop may never ask. */
    @Test
    void pollingOnTheEventLoopIsRefused() {
        Pollable done = host.pollable(executor.newSucceededFuture(null));

        assertRefusedOnTheEventLoop(() -> host.poll(List.of(done)));
    }

    @Test
    void blockingOnTheEventLoopIsRefused() {
        Pollable done = host.pollable(executor.newSucceededFuture(null));

        assertRefusedOnTheEventLoop(
                () -> {
                    done.block();
                    return null;
                });
    }

    private void assertRefusedOnTheEventLoop(Callable<?> task) {
        ExecutionException e =
                assertThrows(ExecutionException.class, () -> executor.submit(task).get());
        assertInstanceOf(WasmEngineException.class, e.getCause());
    }

    @Test
    void aPollableFromElsewhereIsRefused() {
        Pollable foreign =
                new Pollable() {
                    @Override
                    public Boolean ready() {
                        return true;
                    }

                    @Override
                    public void block() {}
                };

        assertThrows(IllegalArgumentException.class, () -> host.poll(List.of(foreign)));
    }

    @Test
    void readyReflectsTheFuture() {
        Promise<Void> promise = executor.newPromise();
        Pollable pollable = host.pollable(promise);

        assertFalse(pollable.ready());
        promise.setSuccess(null);
        assertTrue(pollable.ready());
    }

    @Test
    void blockWaitsForTheFuture() {
        Promise<Void> later = executor.newPromise();
        executor.schedule(() -> later.setSuccess(null), 20, TimeUnit.MILLISECONDS);

        host.pollable(later).block();

        assertTrue(later.isDone());
    }

    /** A source that stops being ready supplies a new future, and the pollable follows it. */
    @Test
    void aSuppliedPollableFollowsTheCurrentFuture() {
        AtomicReference<Promise<Void>> current =
                new AtomicReference<>(executor.<Void>newPromise().setSuccess(null));
        Pollable pollable = host.pollable(current::get);

        assertTrue(pollable.ready());
        current.set(executor.newPromise());
        assertFalse(pollable.ready());
    }

    @Test
    void pollWaitsOnTheFutureSuppliedWhenItIsCalled() {
        AtomicReference<Promise<Void>> current =
                new AtomicReference<>(executor.<Void>newPromise().setSuccess(null));
        Pollable pollable = host.pollable(current::get);
        Promise<Void> later = executor.newPromise();
        current.set(later);
        executor.schedule(() -> later.setSuccess(null), 20, TimeUnit.MILLISECONDS);

        assertEquals(List.of(0L), host.poll(List.of(pollable)));
        assertTrue(later.isDone());
    }

    @Test
    void blockWaitsOnTheFutureSuppliedWhenItIsCalled() {
        AtomicReference<Promise<Void>> current =
                new AtomicReference<>(executor.<Void>newPromise().setSuccess(null));
        Pollable pollable = host.pollable(current::get);
        Promise<Void> later = executor.newPromise();
        current.set(later);
        executor.schedule(() -> later.setSuccess(null), 20, TimeUnit.MILLISECONDS);

        pollable.block();

        assertTrue(later.isDone());
    }

    /** A supplied future belongs to its source, which other pollables may still wait on. */
    @Test
    void droppingASuppliedPollableLeavesItsFutureRunning() {
        Promise<Void> shared = executor.newPromise();

        host.pollable(() -> shared).drop();

        assertFalse(shared.isDone());
    }

    @Test
    void droppingAnOwnedPollableCancelsItsFuture() {
        Promise<Void> timer = executor.newPromise();

        host.ownedPollable(timer).drop();

        assertTrue(timer.isCancelled());
    }

    /** A shared future may still be awaited by something other than the dropped pollable. */
    @Test
    void droppingASharedPollableLeavesItsFutureRunning() {
        Promise<Void> shared = executor.newPromise();

        host.pollable(shared).drop();

        assertFalse(shared.isDone());
    }
}
