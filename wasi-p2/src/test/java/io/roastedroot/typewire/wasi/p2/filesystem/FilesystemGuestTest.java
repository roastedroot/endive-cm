package io.roastedroot.typewire.wasi.p2.filesystem;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.roastedroot.typewire.runtime.ComponentFunction;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import io.roastedroot.typewire.wasi.p2.Guests;
import io.roastedroot.typewire.wasi.p2.WasiPreview2;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.endive.runtime.TrapException;

/**
 * A guest driving {@code wasi:filesystem} and the {@code wasi:io/streams} it opens, end to end
 * through the generated world, against an in-memory directory preopened as {@code data}.
 *
 * <p>The guest traps unless every value it reads is what these tests arranged, so a passing run
 * shows the values crossed rather than only that the calls happened.
 */
class FilesystemGuestTest {

    private static WasmComponent component;

    private FileSystem fs;
    private Path data;

    @BeforeAll
    static void buildComponent(@TempDir Path temp) {
        component = Guests.build("filesystem", temp);
    }

    @BeforeEach
    void createFiles() throws IOException {
        fs = ZeroFs.newFileSystem(Configuration.unix());
        data = Files.createDirectories(fs.getPath("/data"));
        Files.write(data.resolve("hello.txt"), "hello".getBytes(UTF_8));
        Files.createDirectory(data.resolve("sub"));
        Files.createDirectories(fs.getPath("/outside"));
    }

    @AfterEach
    void closeFileSystem() throws IOException {
        fs.close();
    }

    @Test
    void aGuestReadsAPreopenedFile() {
        try (var wasi = wasi("data")) {
            export(wasi, "read-file").apply();
        }
    }

    /** The guest traps on contents it was not promised, so the read above really arrived. */
    @Test
    void contentsTheGuestDoesNotExpectTrap() throws IOException {
        Files.write(data.resolve("hello.txt"), "jello".getBytes(UTF_8));

        try (var wasi = wasi("data")) {
            assertThrows(TrapException.class, export(wasi, "read-file")::apply);
        }
    }

    /** The guest finds its directory by name, and traps on any other. */
    @Test
    void aPreopenTheGuestDoesNotExpectTraps() {
        try (var wasi = wasi("other")) {
            assertThrows(TrapException.class, export(wasi, "read-file")::apply);
        }
    }

    @Test
    void aGuestCreatesAFileAndWritesItThroughAStream() throws IOException {
        try (var wasi = wasi("data")) {
            export(wasi, "write-file").apply();
        }

        assertEquals("streamed", Files.readString(data.resolve("out.txt")));
    }

    /** The stream outlives the descriptor the guest dropped, and closes at the end of the file. */
    @Test
    void aGuestReadsAFileThroughAStreamToItsEnd() {
        try (var wasi = wasi("data")) {
            export(wasi, "read-stream").apply();
        }
    }

    @Test
    void streamContentsTheGuestDoesNotExpectTrap() throws IOException {
        Files.write(data.resolve("hello.txt"), "hello!".getBytes(UTF_8));

        try (var wasi = wasi("data")) {
            assertThrows(TrapException.class, export(wasi, "read-stream")::apply);
        }
    }

    /** An embedder running Netty passes its event loop as the scheduler, and streams still work. */
    @Test
    void aGuestStreamsFilesWithANettyEventLoopAsTheScheduler() throws IOException {
        EventExecutor eventLoop = new DefaultEventExecutor();
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(eventLoop)
                        .withDirectory("data", data)
                        .build()) {
            export(wasi, "write-file").apply();
            export(wasi, "read-stream").apply();
        } finally {
            eventLoop.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }

        assertEquals("streamed", Files.readString(data.resolve("out.txt")));
    }

    @Test
    void aGuestLearnsWhyAnOpenFailed() {
        try (var wasi = wasi("data")) {
            ComponentFunction openError = export(wasi, "open-error");

            assertEquals(ErrorCode.NO_ENTRY.ordinal(), code(openError.apply("missing.txt")));
            assertEquals(ErrorCode.NOT_PERMITTED.ordinal(), code(openError.apply("../outside")));
            assertEquals(ErrorCode.NOT_PERMITTED.ordinal(), code(openError.apply("/outside")));
            assertEquals(255, code(openError.apply("hello.txt")));
        }
    }

    @Test
    void aGuestListsADirectory() throws IOException {
        try (var wasi = wasi("data")) {
            ComponentFunction listDirectory = export(wasi, "list-directory");

            assertEquals(2L, code(listDirectory.apply()));
            Files.createFile(data.resolve("another.txt"));
            assertEquals(3L, code(listDirectory.apply()));
        }
    }

    private WasiPreview2 wasi(String preopenName) {
        return WasiPreview2.builder().withDirectory(preopenName, data).build();
    }

    private static ComponentFunction export(WasiPreview2 wasi, String name) {
        return wasi.instantiate(new ComponentStore(), component).instance().export(name);
    }

    private static long code(Object[] result) {
        return ((Number) result[0]).longValue();
    }
}
