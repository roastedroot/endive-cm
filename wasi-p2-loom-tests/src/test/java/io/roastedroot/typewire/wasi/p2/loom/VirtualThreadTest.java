package io.roastedroot.typewire.wasi.p2.loom;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import io.roastedroot.typewire.wasi.p2.Guests;
import io.roastedroot.typewire.wasi.p2.WasiPreview2;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VirtualThreadTest {

    /** The default virtual thread scheduler never grows past this many carriers. */
    private static final int MAX_CARRIERS =
            Math.max(256, Runtime.getRuntime().availableProcessors());

    /**
     * More guests wait than there can ever be carriers, and only another virtual thread can wake
     * them. That works only if each waiting guest gives up its carrier.
     */
    @Test
    void waitingGuestsGiveUpTheirCarriers() {
        HostPoll poll = new HostPoll();
        CompletableFuture<Void> wakeUp = new CompletableFuture<>();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        Thread.Builder virtual =
                Thread.ofVirtual().uncaughtExceptionHandler((t, e) -> failures.add(e));

        assertTimeoutPreemptively(
                Duration.ofSeconds(30),
                () -> {
                    List<Thread> waiters = new ArrayList<>();
                    for (int i = 0; i < 2 * MAX_CARRIERS; i++) {
                        waiters.add(virtual.start(() -> poll.poll(List.of(poll.pollable(wakeUp)))));
                    }
                    for (Thread waiter : waiters) {
                        while (waiter.getState() != Thread.State.WAITING) {
                            assertTrue(waiter.isAlive(), "a guest stopped waiting early");
                            Thread.sleep(1);
                        }
                    }

                    virtual.start(() -> wakeUp.complete(null)).join();
                    for (Thread waiter : waiters) {
                        waiter.join();
                    }
                });

        assertTrue(failures.isEmpty(), () -> "guests failed: " + failures);
    }

    /** The clocks guest polls and blocks on subscriptions while running on a virtual thread. */
    @Test
    void aGuestRunsOnAVirtualThread(@TempDir Path temp) throws Exception {
        WasmComponent component = Guests.build("clocks", temp);
        Clock wallClock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L, 5), ZoneOffset.UTC);

        try (var wasi =
                        WasiPreview2.builder()
                                .withWallClock(wallClock, Duration.ofNanos(1))
                                .build();
                ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Object[]> result =
                    virtualThreads.submit(
                            () ->
                                    wasi.instantiate(new ComponentStore(), component)
                                            .instance()
                                            .export("run")
                                            .apply());

            BigInteger elapsed = (BigInteger) result.get()[0];
            assertTrue(
                    elapsed.compareTo(BigInteger.valueOf(Duration.ofMillis(25).toNanos())) >= 0,
                    "elapsed " + elapsed);
        }
    }
}
