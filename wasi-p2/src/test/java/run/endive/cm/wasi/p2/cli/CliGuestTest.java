package run.endive.cm.wasi.p2.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.wasi.p2.Guests;
import run.endive.cm.wasi.p2.WasiPreview2;
import run.endive.runtime.TrapException;

/**
 * A command run through {@code wasi:cli/run}, which writes its arguments, its environment and
 * then its stdin to stdout, and tells stderr which of its streams are terminals.
 *
 * <p>The first argument picks how the guest ends, so each test sees one way of reaching an exit
 * code.
 */
class CliGuestTest {

    private static WasmComponent component;

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @BeforeAll
    static void buildComponent(@TempDir Path temp) {
        component = Guests.build("cli", temp);
    }

    @Test
    void aGuestReadsItsArgumentsAndEnvironment() {
        WasiPreview2.Builder builder =
                streams()
                        .withArguments(List.of("ok", "two words"))
                        .withEnvironment("HOME", "/home/guest")
                        .withEnvironment("EMPTY", "");

        assertEquals(0, run(builder));
        assertEquals("ok\ntwo words\nHOME=/home/guest\nEMPTY=\n", stdout.toString(UTF_8));
    }

    @Test
    void aGuestCopiesStdinToStdout() {
        byte[] input = "first line\nsecond line\n".getBytes(UTF_8);

        assertEquals(0, run(streams().withStdin(new ByteArrayInputStream(input))));
        assertEquals("first line\nsecond line\n", stdout.toString(UTF_8));
    }

    /** A stdin far larger than the buffer pauses the reading thread many times over. */
    @Test
    void aLargeStdinReachesTheGuestWhole() {
        byte[] input = new byte[1 << 20];
        new Random(42).nextBytes(input);

        assertEquals(0, run(streams().withStdin(new ByteArrayInputStream(input))));
        assertArrayEquals(input, stdout.toByteArray());
    }

    @Test
    void aGuestSeesAFailingStdinAndTraps() {
        InputStream failing =
                new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("unplugged");
                    }
                };

        assertThrows(TrapException.class, () -> run(streams().withStdin(failing)));
    }

    @Test
    void aGuestLearnsWhichStreamsAreTerminals() {
        WasiPreview2.Builder builder =
                WasiPreview2.builder()
                        .withStdin(new ByteArrayInputStream(new byte[0]), true)
                        .withStdout(stdout, false)
                        .withStderr(stderr, true);

        assertEquals(0, run(builder));
        assertEquals("yny\n", stderr.toString(UTF_8));
    }

    @Test
    void noStreamIsATerminalByDefault() {
        assertEquals(0, run(streams()));
        assertEquals("nnn\n", stderr.toString(UTF_8));
    }

    @Test
    void returningErrExitsWithOne() {
        assertEquals(1, run(streams().withArguments(List.of("err"))));
    }

    /** What the guest wrote before exiting has reached stdout. */
    @Test
    void exitingWithOkExitsWithZero() {
        assertEquals(0, run(streams().withArguments(List.of("success"))));
        assertEquals("success\n", stdout.toString(UTF_8));
    }

    @Test
    void exitingWithErrExitsWithOne() {
        assertEquals(1, run(streams().withArguments(List.of("failure"))));
    }

    @Test
    void exitingWithACodeExitsWithThatCode() {
        assertEquals(42, run(streams().withArguments(List.of("c42"))));
        assertEquals(255, run(streams().withArguments(List.of("c255"))));
    }

    @Test
    void aComponentWithoutRunIsRefused(@TempDir Path temp) {
        WasmComponent random = Guests.build("random", temp);

        try (var wasi = WasiPreview2.builder().build()) {
            assertThrows(
                    IllegalArgumentException.class, () -> wasi.run(new ComponentStore(), random));
        }
    }

    /** Closing the host leaves the embedder's streams open. */
    @Test
    void theEmbeddersStreamsAreLeftOpen() {
        ClosingInput in = new ClosingInput(new ByteArrayInputStream(bytes("in")));
        ClosingOutput out = new ClosingOutput();

        assertEquals(0, run(WasiPreview2.builder().withStdin(in).withStdout(out).withStderr(out)));
        assertFalse(in.closed);
        assertFalse(out.closed);
    }

    private WasiPreview2.Builder streams() {
        return WasiPreview2.builder().withStdout(stdout).withStderr(stderr);
    }

    private static int run(WasiPreview2.Builder builder) {
        try (var wasi = builder.build()) {
            return wasi.run(new ComponentStore(), component);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(UTF_8);
    }

    private static final class ClosingInput extends FilterInputStream {

        private boolean closed;

        ClosingInput(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class ClosingOutput extends ByteArrayOutputStream {

        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
