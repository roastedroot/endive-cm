package run.endive.cm.wasi.p2.loom;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.wasi.p2.Guests;
import run.endive.cm.wasi.p2.WasiPreview2;

class CliVirtualThreadTest {

    /**
     * A command parks its virtual thread while it waits for stdin, carries on once input arrives,
     * and hands back its exit code.
     */
    @Test
    void aCommandRunsOnAVirtualThread(@TempDir Path temp) throws Exception {
        WasmComponent component = Guests.build("cli", temp);
        PipedOutputStream typing = new PipedOutputStream();
        PipedInputStream typed = new PipedInputStream(typing);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        AtomicInteger exitCode = new AtomicInteger(-1);

        try (var wasi =
                WasiPreview2.builder()
                        .withArguments(List.of("c7"))
                        .withStdin(typed)
                        .withStdout(stdout)
                        .build()) {
            Thread guest =
                    Thread.ofVirtual()
                            .start(() -> exitCode.set(wasi.run(new ComponentStore(), component)));

            assertTimeoutPreemptively(
                    Duration.ofSeconds(30),
                    () -> {
                        while (guest.getState() != Thread.State.WAITING) {
                            assertTrue(guest.isAlive(), "the guest ended before reading stdin");
                            Thread.sleep(1);
                        }
                        typing.write("typed".getBytes(UTF_8));
                        typing.close();
                        guest.join();
                    });
        }

        assertEquals(7, exitCode.get());
        assertEquals("c7\ntyped", stdout.toString(UTF_8));
    }
}
