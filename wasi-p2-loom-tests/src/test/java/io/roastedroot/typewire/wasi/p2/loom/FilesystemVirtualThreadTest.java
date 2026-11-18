package io.roastedroot.typewire.wasi.p2.loom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import io.roastedroot.typewire.wasi.p2.Guests;
import io.roastedroot.typewire.wasi.p2.WasiPreview2;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The filesystem guest writes a file through a stream and reads one to its end while running on a
 * virtual thread.
 */
class FilesystemVirtualThreadTest {

    private static final int GUESTS = 64;

    /** The wall clock time the clocks guest expects to read. */
    private static final Clock CLOCKS_GUEST_TIME =
            Clock.fixed(Instant.ofEpochSecond(1_700_000_000L, 5), ZoneOffset.UTC);

    /** The least time a clocks guest spends waiting on its timers, of 20 ms and then 5 ms. */
    private static final long CLOCKS_GUEST_WAIT = Duration.ofMillis(25).toNanos();

    private static WasmComponent component;
    private static WasmComponent clocks;

    @BeforeAll
    static void buildComponents(@TempDir Path temp) {
        component = Guests.build("filesystem", temp.resolve("filesystem"));
        clocks = Guests.build("clocks", temp.resolve("clocks"));
    }

    @Test
    void aGuestStreamsHostFilesOnAVirtualThread(@TempDir Path temp) throws Exception {
        Path data = createData(temp);

        try (ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor()) {
            virtualThreads.submit(() -> runStreams(WasiPreview2.builder(), data)).get();
        }

        assertEquals("streamed", Files.readString(data.resolve("out.txt")));
    }

    /** Each guest has its own sandbox, and none of them holds up another. */
    @Test
    void manyGuestsStreamFilesOnVirtualThreadsAtOnce() throws IOException {
        streamManyGuests(data -> runStreams(WasiPreview2.builder(), data));
    }

    /** One scheduler serves every guest, and closing a guest's host leaves it running. */
    @Test
    void manyGuestsShareOneScheduler() throws IOException {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
        try {
            streamManyGuests(
                    data -> runStreams(WasiPreview2.builder().withScheduler(scheduler), data));

            assertFalse(scheduler.isShutdown());
        } finally {
            scheduler.shutdownNow();
        }
    }

    /**
     * Each guest also waits on clock subscriptions, so every one of them puts timers on the shared
     * scheduler. Those it drops are removed, and the others fire.
     */
    @Test
    void manyGuestsWaitOnTimersOfOneSharedScheduler() throws Exception {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
        scheduler.setRemoveOnCancelPolicy(true);
        try {
            streamManyGuests(
                    data -> {
                        try (var wasi =
                                WasiPreview2.builder()
                                        .withScheduler(scheduler)
                                        .withWallClock(CLOCKS_GUEST_TIME, Duration.ofNanos(1))
                                        .withDirectory("data", data)
                                        .build()) {
                            BigInteger elapsed =
                                    (BigInteger)
                                            wasi.instantiate(new ComponentStore(), clocks)
                                                    .instance()
                                                    .export("run")
                                                    .apply()[0];
                            assertTrue(
                                    elapsed.compareTo(BigInteger.valueOf(CLOCKS_GUEST_WAIT)) >= 0,
                                    "elapsed " + elapsed);
                            runStreams(wasi);
                        }
                    });

            assertTrue(scheduler.getQueue().isEmpty(), "timers left " + scheduler.getQueue());
            scheduler.shutdown();
            assertTrue(scheduler.awaitTermination(10, TimeUnit.SECONDS));
            assertTrue(scheduler.getCompletedTaskCount() >= 2L * GUESTS);
        } finally {
            scheduler.shutdownNow();
        }
    }

    /** Runs {@code guest} {@link #GUESTS} times at once, each on a virtual thread with its own sandbox. */
    private static void streamManyGuests(Consumer<Path> guest) throws IOException {
        List<FileSystem> sandboxes = new ArrayList<>();
        try {
            List<Path> directories = new ArrayList<>();
            for (int i = 0; i < GUESTS; i++) {
                FileSystem fs = ZeroFs.newFileSystem(Configuration.unix());
                sandboxes.add(fs);
                directories.add(createData(fs.getPath("/")));
            }

            assertTimeoutPreemptively(
                    Duration.ofSeconds(60),
                    () -> {
                        try (ExecutorService virtualThreads =
                                Executors.newVirtualThreadPerTaskExecutor()) {
                            List<Future<?>> runs = new ArrayList<>();
                            for (Path data : directories) {
                                runs.add(virtualThreads.submit(() -> guest.accept(data)));
                            }
                            for (Future<?> run : runs) {
                                run.get();
                            }
                        }
                    });

            for (Path data : directories) {
                assertEquals("streamed", Files.readString(data.resolve("out.txt")));
            }
        } finally {
            for (FileSystem fs : sandboxes) {
                fs.close();
            }
        }
    }

    /** What the guest expects to find in the directory it gets as {@code data}. */
    private static Path createData(Path parent) throws IOException {
        Path data = Files.createDirectories(parent.resolve("data"));
        Files.writeString(data.resolve("hello.txt"), "hello");
        Files.createDirectory(data.resolve("sub"));
        return data;
    }

    private static void runStreams(WasiPreview2.Builder builder, Path data) {
        try (var wasi = builder.withDirectory("data", data).build()) {
            runStreams(wasi);
        }
    }

    private static void runStreams(WasiPreview2 wasi) {
        assertTrue(Thread.currentThread().isVirtual());
        var instance = wasi.instantiate(new ComponentStore(), component).instance();
        instance.export("write-file").apply();
        instance.export("read-stream").apply();
    }
}
