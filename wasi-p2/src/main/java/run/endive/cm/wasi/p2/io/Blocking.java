package run.endive.cm.wasi.p2.io;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import run.endive.runtime.WasmInterruptedException;
import run.endive.wasm.WasmEngineException;

/**
 * Guards the host operations that wait on the calling thread. The event loop completes what they
 * wait for, so waiting on it would never end, and every such operation is refused there whether or
 * not it would actually wait.
 *
 * <p>Waiting parks the thread without holding a monitor, so a virtual thread running a guest gives
 * up its carrier while it waits.
 *
 * <p>Interrupting the waiting thread cancels the guest, the way Endive cancels a guest that is
 * running or waiting in {@code memory.atomic.wait}. The wait ends with a {@link
 * WasmInterruptedException} and the thread stays interrupted. The guest's call is abandoned
 * partway, so its instance should not be called again.
 */
public final class Blocking {

    private Blocking() {}

    /** Refuses to continue on {@code eventLoopExecutor}'s own thread. */
    public static void check(EventExecutor eventLoopExecutor) {
        if (eventLoopExecutor.inEventLoop()) {
            throw new WasmEngineException(
                    "Blocking host operations cannot be executed from within the EventLoop");
        }
    }

    /**
     * Waits for {@code future} to complete, refusing to on {@code eventLoopExecutor}'s thread.
     *
     * @throws WasmInterruptedException when the waiting thread is interrupted
     */
    public static void await(EventExecutor eventLoopExecutor, Future<?> future) {
        awaitAny(eventLoopExecutor, List.of(future));
    }

    /**
     * Waits for at least one of {@code futures} to complete, refusing to on {@code
     * eventLoopExecutor}'s thread.
     *
     * @throws WasmInterruptedException when the waiting thread is interrupted
     */
    public static void awaitAny(
            EventExecutor eventLoopExecutor, List<? extends Future<?>> futures) {
        check(eventLoopExecutor);
        if (futures.stream().anyMatch(Future::isDone)) {
            return;
        }
        CountDownLatch anyDone = new CountDownLatch(1);
        GenericFutureListener<Future<Object>> listener = done -> anyDone.countDown();
        for (Future<?> future : futures) {
            future.addListener(listener);
        }
        try {
            anyDone.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WasmInterruptedException("Thread interrupted");
        } finally {
            for (Future<?> future : futures) {
                future.removeListener(listener);
            }
        }
    }
}
