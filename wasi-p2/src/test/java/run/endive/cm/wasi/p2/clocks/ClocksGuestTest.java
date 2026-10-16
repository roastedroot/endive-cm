package run.endive.cm.wasi.p2.clocks;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.wasi.p2.Guests;
import run.endive.cm.wasi.p2.WasiPreview2;
import run.endive.runtime.TrapException;

/**
 * A guest driving {@code wasi:clocks} and the {@code wasi:io/poll} pollables they return, end to
 * end through the generated world.
 *
 * <p>The guest traps unless every value it reads is what these tests arranged, so a passing run
 * shows the values crossed rather than only that the calls happened.
 */
class ClocksGuestTest {

    private static final Instant WALL_TIME = Instant.ofEpochSecond(1_700_000_000L, 5);

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent(@TempDir Path temp) {
        component = Guests.build("clocks", temp);
    }

    /**
     * Polling answers only the subscription that elapsed, and the guest measures at least the
     * 20 ms it polled for and the 5 ms it blocked for.
     */
    @Test
    void aGuestPollsAndBlocksOnClockSubscriptions() {
        try (var wasi = wasiAt(WALL_TIME)) {
            Object[] result =
                    wasi.instantiate(new ComponentStore(), component)
                            .instance()
                            .export("run")
                            .apply();

            BigInteger elapsed = (BigInteger) result[0];
            assertTrue(
                    elapsed.compareTo(BigInteger.valueOf(Duration.ofMillis(25).toNanos())) >= 0,
                    "elapsed " + elapsed);
        }
    }

    /** The guest traps on a wall time it was not promised, so the one above really arrived. */
    @Test
    void aWallTimeTheGuestDoesNotExpectTraps() {
        try (var wasi = wasiAt(WALL_TIME.plusNanos(1))) {
            var run = wasi.instantiate(new ComponentStore(), component).instance().export("run");

            assertThrows(TrapException.class, run::apply);
        }
    }

    private static WasiPreview2 wasiAt(Instant wallTime) {
        return WasiPreview2.builder()
                .withWallClock(Clock.fixed(wallTime, ZoneOffset.UTC), Duration.ofNanos(1))
                .build();
    }
}
