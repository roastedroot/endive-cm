package run.endive.cm.wasi.p2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import run.endive.cm.runtime.Tuple2;
import run.endive.cm.wasi.p2.bindings.wasi.clocks.wallclock.Datetime;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamError;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamErrorException;

class WasiPreview2Test {

    /** A clock subscription is a pollable the same host can poll. */
    @Test
    void aClockSubscriptionCanBePolled() {
        try (var wasi = WasiPreview2.builder().build()) {
            var timeout = wasi.monotonicClock().subscribeDuration(BigInteger.valueOf(1_000_000));

            assertEquals(List.of(0L), wasi.poll().poll(List.of(timeout)));
        }
    }

    @Test
    void closingShutsDownTheSchedulerItCreated() {
        var wasi = WasiPreview2.builder().build();
        wasi.close();

        assertThrows(
                RejectedExecutionException.class,
                () -> wasi.monotonicClock().subscribeDuration(BigInteger.valueOf(1_000_000)));
    }

    @Test
    void closingLeavesAGivenSchedulerRunning() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            WasiPreview2.builder().withScheduler(scheduler).build().close();

            assertFalse(scheduler.isShutdown());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void aGivenWallClockIsRead() {
        Clock fixed = Clock.fixed(Instant.ofEpochSecond(42), ZoneOffset.UTC);

        try (var wasi = WasiPreview2.builder().withWallClock(fixed, Duration.ofNanos(1)).build()) {
            assertEquals(new Datetime(BigInteger.valueOf(42), 0L), wasi.wallClock().now());
        }
    }

    @Test
    void aWallClockNeedsAResolution() {
        Clock fixed = Clock.fixed(Instant.ofEpochSecond(42), ZoneOffset.UTC);

        assertThrows(
                NullPointerException.class,
                () -> WasiPreview2.builder().withWallClock(fixed, null));
    }

    @Test
    void eachGivenDirectoryIsPreopened() throws IOException {
        try (FileSystem fs = ZeroFs.newFileSystem(Configuration.unix());
                var wasi =
                        WasiPreview2.builder()
                                .withDirectory("/data", Files.createDirectory(fs.getPath("/data")))
                                .withDirectory(".", Files.createDirectory(fs.getPath("/scratch")))
                                .build()) {
            assertEquals(
                    List.of("/data", "."),
                    wasi.preopens().getDirectories().stream()
                            .map(Tuple2::f1)
                            .collect(Collectors.toList()));
        }
    }

    @Test
    void noDirectoryIsPreopenedByDefault() {
        try (var wasi = WasiPreview2.builder().build()) {
            assertEquals(List.of(), wasi.preopens().getDirectories());
        }
    }

    /** By default there are no arguments, no variables, no terminals and nothing to read. */
    @Test
    void theCommandLineIsEmptyByDefault() {
        try (var wasi = WasiPreview2.builder().build()) {
            assertEquals(List.of(), wasi.environment().getArguments());
            assertEquals(List.of(), wasi.environment().getEnvironment());
            assertNull(wasi.terminalStdin().getTerminalStdin());
            assertNull(wasi.terminalStdout().getTerminalStdout());
            assertNull(wasi.terminalStderr().getTerminalStderr());
            StreamErrorException e =
                    assertThrows(
                            StreamErrorException.class,
                            () -> wasi.stdin().getStdin().read(BigInteger.ONE));
            assertInstanceOf(StreamError.Closed.class, e.error());
        }
    }

    /** Stdout and stderr take whatever the guest writes and discard it. */
    @Test
    void outputIsDiscardedByDefault() {
        try (var wasi = WasiPreview2.builder().build()) {
            wasi.stdout().getStdout().blockingWriteAndFlush("out".getBytes(UTF_8));
            wasi.stderr().getStderr().blockingWriteAndFlush("err".getBytes(UTF_8));
        }
    }

    @Test
    void aVariableAddedAgainKeepsItsPlaceAndTakesTheLastValue() {
        try (var wasi =
                WasiPreview2.builder()
                        .withEnvironment("A", "1")
                        .withEnvironment("B", "2")
                        .withEnvironment("A", "3")
                        .build()) {
            assertEquals(
                    List.of(Tuple2.of("A", "3"), Tuple2.of("B", "2")),
                    wasi.environment().getEnvironment());
        }
    }

    /** A stream given without a terminal flag is not a terminal, whatever was given before it. */
    @Test
    void aStreamGivenAloneIsNotATerminal() {
        try (var wasi =
                WasiPreview2.builder()
                        .withStdout(new ByteArrayOutputStream(), true)
                        .withStdout(new ByteArrayOutputStream())
                        .build()) {
            assertNull(wasi.terminalStdout().getTerminalStdout());
        }
    }

    @Test
    void theSystemStreamsCanBeInherited() {
        InputStream in = System.in;
        PrintStream out = System.out;
        PrintStream err = System.err;
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream("typed".getBytes(UTF_8)));
            System.setOut(new PrintStream(written, true, UTF_8));
            System.setErr(new PrintStream(written, true, UTF_8));

            try (var wasi = WasiPreview2.builder().inheritSystemStreams().build()) {
                assertArrayEquals(
                        "typed".getBytes(UTF_8),
                        wasi.stdin().getStdin().blockingRead(BigInteger.TEN));
                wasi.stdout().getStdout().blockingWriteAndFlush("out ".getBytes(UTF_8));
                wasi.stderr().getStderr().blockingWriteAndFlush("err".getBytes(UTF_8));
                boolean console = System.console() != null;
                assertEquals(console, wasi.terminalStdin().getTerminalStdin() != null);
                assertEquals(console, wasi.terminalStdout().getTerminalStdout() != null);
                assertEquals(console, wasi.terminalStderr().getTerminalStderr() != null);
            }
        } finally {
            System.setIn(in);
            System.setOut(out);
            System.setErr(err);
        }
        assertEquals("out err", written.toString(UTF_8));
    }
}
