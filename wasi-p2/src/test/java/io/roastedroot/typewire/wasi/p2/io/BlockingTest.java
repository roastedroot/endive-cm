package io.roastedroot.typewire.wasi.p2.io;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.endive.runtime.WasmInterruptedException;

class BlockingTest {

    private ScheduledExecutorService scheduler;

    @BeforeEach
    void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void stopScheduler() {
        scheduler.shutdownNow();
    }

    @Test
    void awaitReturnsOnceTheFutureCompletes() {
        CompletableFuture<Void> later = new CompletableFuture<>();
        scheduler.schedule(() -> later.complete(null), 20, TimeUnit.MILLISECONDS);

        Blocking.await(later);

        assertTrue(later.isDone());
    }

    @Test
    void awaitAnyReturnsOnceOneFutureCompletes() {
        CompletableFuture<Void> never = new CompletableFuture<>();
        CompletableFuture<Void> later = new CompletableFuture<>();
        scheduler.schedule(() -> later.complete(null), 20, TimeUnit.MILLISECONDS);

        Blocking.awaitAny(List.of(never, later));

        assertTrue(later.isDone());
        assertFalse(never.isDone());
    }

    /** A failed future has completed too, so it ends the wait. */
    @Test
    void aFailedFutureEndsTheWait() {
        CompletableFuture<Void> later = new CompletableFuture<>();
        scheduler.schedule(
                () -> later.completeExceptionally(new IllegalStateException("failed")),
                20,
                TimeUnit.MILLISECONDS);

        Blocking.await(later);

        assertTrue(later.isDone());
    }

    @Test
    void aCancelledFutureEndsTheWait() {
        CompletableFuture<Void> later = new CompletableFuture<>();
        scheduler.schedule(() -> later.cancel(false), 20, TimeUnit.MILLISECONDS);

        Blocking.await(later);

        assertTrue(later.isCancelled());
    }

    @Test
    void waitingForNothingIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Blocking.awaitAny(List.of()));
    }

    /** An interrupt cancels the guest, ending the wait and leaving the thread interrupted. */
    @Test
    void anInterruptEndsTheWaitAndStaysSet() throws InterruptedException {
        CompletableFuture<Void> never = new CompletableFuture<>();
        AtomicReference<RuntimeException> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfter = new AtomicBoolean();
        Thread waiter =
                new Thread(
                        () -> {
                            try {
                                Blocking.await(never);
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
        CompletableFuture<Void> never = new CompletableFuture<>();
        Thread.currentThread().interrupt();
        try {
            assertThrows(WasmInterruptedException.class, () -> Blocking.await(never));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
