package run.endive.cm.wasi.p2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.clocks.wallclock.Datetime;

class WasiPreview2Test {

    /** A clock subscription is a pollable the same host can poll. */
    @Test
    void theHostsShareOneEventLoop() {
        try (var wasi = WasiPreview2.builder().build()) {
            var timeout = wasi.monotonicClock().subscribeDuration(BigInteger.valueOf(1_000_000));

            assertEquals(List.of(0L), wasi.poll().poll(List.of(timeout)));
        }
    }

    @Test
    void closingShutsDownTheEventLoopItCreated() {
        var wasi = WasiPreview2.builder().build();
        wasi.close();

        assertThrows(
                RejectedExecutionException.class,
                () -> wasi.monotonicClock().subscribeDuration(BigInteger.valueOf(1_000_000)));
    }

    @Test
    void closingLeavesAGivenEventLoopRunning() {
        EventExecutor executor = new DefaultEventExecutor();
        try {
            WasiPreview2.builder().withEventLoopExecutor(executor).build().close();

            assertFalse(executor.isShuttingDown());
        } finally {
            executor.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Test
    void aGivenWallClockIsRead() {
        Clock fixed = Clock.fixed(Instant.ofEpochSecond(42), ZoneOffset.UTC);

        try (var wasi = WasiPreview2.builder().withWallClock(fixed, Duration.ofNanos(1)).build()) {
            assertEquals(new Datetime(BigInteger.valueOf(42), 0L), wasi.wallClock().now());
        }
    }
}
