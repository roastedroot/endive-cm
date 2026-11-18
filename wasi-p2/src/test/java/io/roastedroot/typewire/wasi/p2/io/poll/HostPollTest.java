package io.roastedroot.typewire.wasi.p2.io.poll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.poll.Pollable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.endive.runtime.TrapException;

class HostPollTest {

    private ScheduledExecutorService scheduler;
    private HostPoll host;

    @BeforeEach
    void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        host = new HostPoll();
    }

    @AfterEach
    void stopScheduler() {
        scheduler.shutdownNow();
    }

    /** A failed future counts as ready, since an error is reported by marking its source ready. */
    @Test
    void pollReturnsTheIndexOfEveryReadyPollable() {
        List<Long> ready =
                host.poll(
                        List.of(
                                host.pollable(CompletableFuture.completedFuture(null)),
                                host.pollable(new CompletableFuture<>()),
                                host.pollable(
                                        CompletableFuture.failedFuture(
                                                new IllegalStateException("failed")))));

        assertEquals(List.of(0L, 2L), ready);
    }

    /** A borrowed handle may be listed more than once, and each position is reported. */
    @Test
    void aPollableListedTwiceIsReportedAtEachIndex() {
        Pollable done = host.pollable(CompletableFuture.completedFuture(null));
        Pollable pending = host.pollable(new CompletableFuture<>());

        assertEquals(List.of(0L, 2L), host.poll(List.of(done, pending, done)));
    }

    @Test
    void pollWaitsUntilAPollableIsReady() {
        CompletableFuture<Void> later = completeAfter(50);

        long start = System.nanoTime();
        List<Long> ready =
                host.poll(List.of(host.pollable(new CompletableFuture<>()), host.pollable(later)));

        assertEquals(List.of(1L), ready);
        assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(40));
    }

    /** A pollable whose future fails while the guest waits ends the wait as ready. */
    @Test
    void pollWakesWhenAFutureFails() {
        CompletableFuture<Void> later = new CompletableFuture<>();
        scheduler.schedule(
                () -> later.completeExceptionally(new IllegalStateException("failed")),
                20,
                TimeUnit.MILLISECONDS);

        assertEquals(List.of(0L), host.poll(List.of(host.pollable(later))));
    }

    @Test
    void pollingNothingTraps() {
        assertThrows(TrapException.class, () -> host.poll(List.of()));
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
        CompletableFuture<Void> future = new CompletableFuture<>();
        Pollable pollable = host.pollable(future);

        assertFalse(pollable.ready());
        future.complete(null);
        assertTrue(pollable.ready());
    }

    @Test
    void blockWaitsForTheFuture() {
        CompletableFuture<Void> later = completeAfter(20);

        host.pollable(later).block();

        assertTrue(later.isDone());
    }

    /** A source that stops being ready supplies a new future, and the pollable follows it. */
    @Test
    void aSuppliedPollableFollowsTheCurrentFuture() {
        AtomicReference<CompletableFuture<Void>> current =
                new AtomicReference<>(CompletableFuture.completedFuture(null));
        Pollable pollable = host.pollable(current::get);

        assertTrue(pollable.ready());
        current.set(new CompletableFuture<>());
        assertFalse(pollable.ready());
    }

    @Test
    void pollWaitsOnTheFutureSuppliedWhenItIsCalled() {
        AtomicReference<CompletableFuture<Void>> current =
                new AtomicReference<>(CompletableFuture.completedFuture(null));
        Pollable pollable = host.pollable(current::get);
        CompletableFuture<Void> later = completeAfter(20);
        current.set(later);

        assertEquals(List.of(0L), host.poll(List.of(pollable)));
        assertTrue(later.isDone());
    }

    @Test
    void blockWaitsOnTheFutureSuppliedWhenItIsCalled() {
        AtomicReference<CompletableFuture<Void>> current =
                new AtomicReference<>(CompletableFuture.completedFuture(null));
        Pollable pollable = host.pollable(current::get);
        CompletableFuture<Void> later = completeAfter(20);
        current.set(later);

        pollable.block();

        assertTrue(later.isDone());
    }

    /** A supplied future belongs to its source, which other pollables may still wait on. */
    @Test
    void droppingASuppliedPollableLeavesItsFutureRunning() {
        CompletableFuture<Void> shared = new CompletableFuture<>();

        host.pollable(() -> shared).drop();

        assertFalse(shared.isDone());
    }

    @Test
    void droppingAnOwnedPollableCancelsItsFuture() {
        CompletableFuture<Void> timer = new CompletableFuture<>();

        host.ownedPollable(timer).drop();

        assertTrue(timer.isCancelled());
    }

    /** A shared future may still be awaited by something other than the dropped pollable. */
    @Test
    void droppingASharedPollableLeavesItsFutureRunning() {
        CompletableFuture<Void> shared = new CompletableFuture<>();

        host.pollable(shared).drop();

        assertFalse(shared.isDone());
    }

    private CompletableFuture<Void> completeAfter(long millis) {
        CompletableFuture<Void> later = new CompletableFuture<>();
        scheduler.schedule(() -> later.complete(null), millis, TimeUnit.MILLISECONDS);
        return later;
    }
}
