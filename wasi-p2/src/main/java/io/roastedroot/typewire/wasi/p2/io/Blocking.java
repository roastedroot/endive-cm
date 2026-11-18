package io.roastedroot.typewire.wasi.p2.io;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import run.endive.runtime.WasmInterruptedException;

/**
 * Waits on the calling thread for the futures that host operations depend on.
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

    /**
     * Waits for {@code future} to complete, whether it succeeds or fails.
     *
     * @throws WasmInterruptedException when the waiting thread is interrupted
     */
    public static void await(CompletableFuture<?> future) {
        awaitAny(List.of(future));
    }

    /**
     * Waits for at least one of {@code futures} to complete, whether it succeeds or fails.
     *
     * @throws IllegalArgumentException when {@code futures} is empty, since the wait would never end
     * @throws WasmInterruptedException when the waiting thread is interrupted
     */
    public static void awaitAny(List<? extends CompletableFuture<?>> futures) {
        if (futures.isEmpty()) {
            throw new IllegalArgumentException("Nothing to wait for");
        }
        if (futures.stream().anyMatch(CompletableFuture::isDone)) {
            return;
        }
        try {
            CompletableFuture.anyOf(futures.toArray(new CompletableFuture<?>[0])).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WasmInterruptedException("Thread interrupted");
        } catch (ExecutionException | CancellationException e) {
            // A future that failed has completed too.
        }
    }
}
