package io.roastedroot.typewire.wasi.p2.filesystem.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCodeException;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A sandbox at {@code /sandbox} beside a directory {@code /outside} it must never reach.
 *
 * @see <a href="https://github.com/WebAssembly/wasi-filesystem/blob/main/path-resolution.md">WASI filesystem path resolution</a>
 */
class PathResolverTest {

    private FileSystem fs;
    private Path sandbox;

    @BeforeEach
    void createSandbox() throws IOException {
        fs = ZeroFs.newFileSystem(Configuration.unix());
        sandbox = Files.createDirectories(fs.getPath("/sandbox"));
        Files.createDirectories(sandbox.resolve("dir/nested"));
        Files.createFile(sandbox.resolve("dir/file.txt"));
        Files.createDirectories(fs.getPath("/outside"));
        Files.createFile(fs.getPath("/outside/secret.txt"));
    }

    @AfterEach
    void closeFileSystem() throws IOException {
        fs.close();
    }

    @Test
    void aRelativePathResolvesWithinTheDirectory() {
        assertEquals(
                sandbox.resolve("dir/file.txt"),
                PathResolver.resolve(sandbox, "dir/file.txt", true));
    }

    @Test
    void dotAndRepeatedSeparatorsAreSkipped() {
        assertEquals(sandbox, PathResolver.resolve(sandbox, ".", true));
        assertEquals(
                sandbox.resolve("dir/file.txt"),
                PathResolver.resolve(sandbox, "./dir//./file.txt", true));
    }

    @Test
    void dotDotWithinTheDirectoryIsAllowed() {
        assertEquals(
                sandbox.resolve("dir/file.txt"),
                PathResolver.resolve(sandbox, "dir/nested/../file.txt", true));
    }

    @Test
    void aPathThatDoesNotExistYetStillResolves() {
        assertEquals(
                sandbox.resolve("dir/new.txt"), PathResolver.resolve(sandbox, "dir/new.txt", true));
    }

    @Test
    void anAbsolutePathIsNotPermitted() {
        assertFails(ErrorCode.NOT_PERMITTED, () -> PathResolver.resolve(sandbox, "/outside", true));
    }

    @Test
    void dotDotAboveTheDirectoryIsNotPermitted() {
        assertFails(ErrorCode.NOT_PERMITTED, () -> PathResolver.resolve(sandbox, "..", true));
        assertFails(
                ErrorCode.NOT_PERMITTED,
                () -> PathResolver.resolve(sandbox, "dir/../../outside/secret.txt", true));
    }

    /** Reaching outside is refused even on a path that comes back in afterwards. */
    @Test
    void leavingAndReenteringTheDirectoryIsNotPermitted() {
        assertFails(
                ErrorCode.NOT_PERMITTED,
                () -> PathResolver.resolve(sandbox, "../sandbox/dir", true));
    }

    @Test
    void anEmptyPathHasNoEntry() {
        assertFails(ErrorCode.NO_ENTRY, () -> PathResolver.resolve(sandbox, "", true));
    }

    /** A link's target is relative to the directory holding the link, not to the base. */
    @Test
    void aRelativeLinkResolvesFromItsOwnDirectory() throws IOException {
        Files.createSymbolicLink(sandbox.resolve("dir/nested/up"), fs.getPath("../file.txt"));

        assertEquals(
                sandbox.resolve("dir/file.txt"),
                PathResolver.resolve(sandbox, "dir/nested/up", true));
    }

    @Test
    void aLinkInTheMiddleOfAPathIsFollowed() throws IOException {
        Files.createSymbolicLink(sandbox.resolve("alias"), fs.getPath("dir"));

        assertEquals(
                sandbox.resolve("dir/file.txt"),
                PathResolver.resolve(sandbox, "alias/file.txt", false));
    }

    @Test
    void aFinalLinkIsLeftUnfollowedUnlessAsked() throws IOException {
        Files.createSymbolicLink(sandbox.resolve("link"), fs.getPath("dir/file.txt"));

        assertEquals(sandbox.resolve("link"), PathResolver.resolve(sandbox, "link", false));
        assertEquals(sandbox.resolve("dir/file.txt"), PathResolver.resolve(sandbox, "link", true));
    }

    /** A trailing slash names what the link points at, as it does in POSIX. */
    @Test
    void aTrailingSlashFollowsAFinalLink() throws IOException {
        Files.createSymbolicLink(sandbox.resolve("alias"), fs.getPath("dir"));

        assertEquals(sandbox.resolve("dir"), PathResolver.resolve(sandbox, "alias/", false));
    }

    @Test
    void aLinkToAnAbsolutePathIsNotPermitted() throws IOException {
        Files.createSymbolicLink(sandbox.resolve("escape"), fs.getPath("/outside/secret.txt"));

        assertFails(ErrorCode.NOT_PERMITTED, () -> PathResolver.resolve(sandbox, "escape", true));
    }

    @Test
    void aLinkReachingAboveTheDirectoryIsNotPermitted() throws IOException {
        Files.createSymbolicLink(sandbox.resolve("dir/escape"), fs.getPath("../../outside"));

        assertFails(
                ErrorCode.NOT_PERMITTED,
                () -> PathResolver.resolve(sandbox, "dir/escape/secret.txt", false));
    }

    /**
     * A file being created does not exist yet, so a check of its real path after the fact cannot
     * see the link before it. Resolving each component in turn does.
     */
    @Test
    void anEscapingLinkBeforeAFileNotYetCreatedIsNotPermitted() throws IOException {
        Files.createSymbolicLink(sandbox.resolve("out"), fs.getPath("../outside"));

        assertFails(
                ErrorCode.NOT_PERMITTED, () -> PathResolver.resolve(sandbox, "out/new.txt", false));
    }

    @Test
    void aLinkCycleIsALoop() throws IOException {
        Files.createSymbolicLink(sandbox.resolve("a"), fs.getPath("b"));
        Files.createSymbolicLink(sandbox.resolve("b"), fs.getPath("a"));

        assertFails(ErrorCode.LOOP, () -> PathResolver.resolve(sandbox, "a", true));
    }

    @Test
    void aChainOfLinksWithinTheLimitResolves() throws IOException {
        Path previous = fs.getPath("dir");
        for (int i = 0; i < PathResolver.MAX_SYMLINK_EXPANSIONS; i++) {
            Files.createSymbolicLink(sandbox.resolve("link" + i), previous);
            previous = fs.getPath("link" + i);
        }

        assertEquals(
                sandbox.resolve("dir"), PathResolver.resolve(sandbox, previous.toString(), true));
    }

    /**
     * A host whose separator is a backslash would otherwise read one guest component as several,
     * letting {@code ..} hide inside it.
     */
    @Test
    void aComponentTheHostReadsAsSeveralIsNotPermitted() throws IOException {
        try (FileSystem windows = ZeroFs.newFileSystem(Configuration.windows())) {
            Path base = Files.createDirectories(windows.getPath("C:\\sandbox"));

            assertFails(
                    ErrorCode.NOT_PERMITTED,
                    () -> PathResolver.resolve(base, "dir\\..\\..\\outside", true));
            assertFails(
                    ErrorCode.NOT_PERMITTED, () -> PathResolver.resolve(base, "C:\\outside", true));
        }
    }

    @Test
    void aComponentTheHostCannotRepresentIsInvalid() {
        assertFails(ErrorCode.INVALID, () -> PathResolver.resolve(sandbox, "bad\0name", true));
    }

    private static void assertFails(ErrorCode expected, Executable resolution) {
        assertEquals(expected, assertThrows(ErrorCodeException.class, resolution).error());
    }
}
