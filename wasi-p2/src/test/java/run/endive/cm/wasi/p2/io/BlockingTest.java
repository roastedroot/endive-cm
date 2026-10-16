package run.endive.cm.wasi.p2.io;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.endive.runtime.WasmInterruptedException;
import run.endive.wasm.WasmEngineException;

class BlockingTest {

    private EventExecutor executor;

    @BeforeEach
    void startExecutor() {
        executor = new DefaultEventExecutor();
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
    }

    @Test
    void awaitReturnsOnceTheFutureCompletes() {
        Promise<Void> later = executor.newPromise();
        executor.schedule(() -> later.setSuccess(null), 20, TimeUnit.MILLISECONDS);

        Blocking.await(executor, later);

        assertTrue(later.isDone());
    }

    @Test
    void awaitAnyReturnsOnceOneFutureCompletes() {
        Promise<Void> never = executor.newPromise();
        Promise<Void> later = executor.newPromise();
        executor.schedule(() -> later.setSuccess(null), 20, TimeUnit.MILLISECONDS);

        Blocking.awaitAny(executor, List.of(never, later));

        assertTrue(later.isDone());
        assertFalse(never.isDone());
    }

    /** A failed future has completed too, so it ends the wait. */
    @Test
    void aFailedFutureEndsTheWait() {
        Promise<Void> later = executor.newPromise();
        executor.schedule(
                () -> later.setFailure(new IllegalStateException("failed")),
                20,
                TimeUnit.MILLISECONDS);

        Blocking.await(executor, later);

        assertTrue(later.isDone());
    }

    @Test
    void waitingOnTheEventLoopIsRefused() {
        Promise<Void> done = executor.<Void>newPromise().setSuccess(null);

        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> executor.submit(() -> Blocking.await(executor, done)).get());
        assertInstanceOf(WasmEngineException.class, e.getCause());
    }

    /** An interrupt cancels the guest, ending the wait and leaving the thread interrupted. */
    @Test
    void anInterruptEndsTheWaitAndStaysSet() throws InterruptedException {
        Promise<Void> never = executor.newPromise();
        AtomicReference<RuntimeException> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfter = new AtomicBoolean();
        Thread waiter =
                new Thread(
                        () -> {
                            try {
                                Blocking.await(executor, never);
                            } catch (RuntimeException e) {
                                thrown.set(e);
                            }
                            interruptedAfter.set(Thread.currentThread().isInterrupted());
                        });
        waiter.start();

        waiter.interrupt();
        waiter.join(1_000);

        assertFalse(waiter.isAlive());
        assertInstanceOf(WasmInterruptedException.class, thrown.get());
        assertTrue(interruptedAfter.get());
        assertFalse(never.isDone());
    }

    /** A thread interrupted before it would wait is cancelled without waiting at all. */
    @Test
    void anInterruptedThreadDoesNotWait() {
        Promise<Void> never = executor.newPromise();
        Thread.currentThread().interrupt();
        try {
            assertThrows(WasmInterruptedException.class, () -> Blocking.await(executor, never));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
