package io.roastedroot.typewire.wasi.p2.filesystem.types;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.roastedroot.typewire.wasi.p2.WasiPreview2;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.Descriptor;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCodeException;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.MetadataHashValue;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.OpenFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.PathFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.OutputStream;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/**
 * A sandbox on the host's own file system, whose errors and links behave as the in-memory one
 * cannot show. {@code secret} sits beside the sandbox, outside it.
 */
class DefaultFileSystemTest {

    private static final PathFlags NOFOLLOW = PathFlags.of();
    private static final OpenFlags EXISTING = OpenFlags.of();
    private static final DescriptorFlags READ = DescriptorFlags.of(DescriptorFlags.Flag.READ);
    private static final DescriptorFlags READ_WRITE =
            DescriptorFlags.of(DescriptorFlags.Flag.READ, DescriptorFlags.Flag.WRITE);
    private static final DescriptorFlags MUTATE =
            DescriptorFlags.of(DescriptorFlags.Flag.READ, DescriptorFlags.Flag.MUTATE_DIRECTORY);

    @TempDir Path temp;
    private Path root;
    private HostDirectory sandbox;

    @BeforeEach
    void createSandbox() throws IOException {
        root = Files.createDirectory(temp.resolve("sandbox"));
        Path secret = Files.createDirectory(temp.resolve("secret"));
        Files.write(secret.resolve("passwd"), "TOPSECRET".getBytes(UTF_8));
        sandbox = new HostDirectory(root, MUTATE, new HostPoll());
    }

    /** An open directory swapped for a link to the outside no longer leads anywhere. */
    @Test
    void aDirectoryReplacedByAnEscapingLinkIsNotFollowed() {
        sandbox.createDirectoryAt("d");
        Descriptor d = sandbox.openAt(NOFOLLOW, "d", EXISTING, READ);
        sandbox.removeDirectoryAt("d");
        sandbox.symlinkAt("../secret", "d");

        assertFails(ErrorCode.NO_ENTRY, () -> d.openAt(NOFOLLOW, "passwd", EXISTING, READ));
        assertFails(ErrorCode.NO_ENTRY, d::readDirectory);
        assertFails(ErrorCode.NO_ENTRY, d::stat);
    }

    /** An open file keeps describing itself after it is renamed. */
    @Test
    void anOpenFileIsDescribedAfterItIsRenamed() {
        Descriptor file =
                sandbox.openAt(NOFOLLOW, "a.txt", OpenFlags.of(OpenFlags.Flag.CREATE), READ_WRITE);
        file.write("hello".getBytes(UTF_8), BigInteger.ZERO);

        sandbox.renameAt("a.txt", sandbox, "b.txt");

        assertEquals(BigInteger.valueOf(5), file.stat().size());
        file.metadataHash();
    }

    /** A new file under the old name is not mistaken for the one still open. */
    @Test
    void aFileReplacedUnderItsNameIsNotTheOpenOne() throws IOException {
        Descriptor file =
                sandbox.openAt(NOFOLLOW, "a.txt", OpenFlags.of(OpenFlags.Flag.CREATE), READ_WRITE);
        file.write("hello".getBytes(UTF_8), BigInteger.ZERO);
        sandbox.unlinkFileAt("a.txt");
        Files.write(root.resolve("a.txt"), "a much longer replacement".getBytes(UTF_8));

        assertEquals(BigInteger.valueOf(5), file.stat().size());
        assertEquals(BigInteger.ZERO, file.stat().linkCount());
        assertFalse(file.isSameObject(sandbox.openAt(NOFOLLOW, "a.txt", EXISTING, READ)));
    }

    @Test
    void descriptorsForOneFileStayTheSameObjectAfterARename() {
        sandbox.openAt(NOFOLLOW, "e", OpenFlags.of(OpenFlags.Flag.CREATE), READ_WRITE);
        Descriptor first = sandbox.openAt(NOFOLLOW, "e", EXISTING, READ);
        Descriptor second = sandbox.openAt(NOFOLLOW, "e", EXISTING, READ);

        sandbox.renameAt("e", sandbox, "f");

        assertTrue(first.isSameObject(second));
    }

    /** Some hosts link what a symbolic link points to, so linking the link itself is refused. */
    @Test
    void linkingASymbolicLinkIsUnsupported() {
        sandbox.symlinkAt("../secret/passwd", "s");

        assertFails(ErrorCode.UNSUPPORTED, () -> sandbox.linkAt(NOFOLLOW, "s", sandbox, "h"));
        assertFalse(Files.exists(root.resolve("h")));
    }

    /** As in POSIX, a component the rest of the path goes through has to be a directory. */
    @Test
    void aPathThroughAFileIsNotADirectory() throws IOException {
        Files.write(root.resolve("plain"), "x".getBytes(UTF_8));

        assertFails(
                ErrorCode.NOT_DIRECTORY, () -> sandbox.openAt(NOFOLLOW, "plain/", EXISTING, READ));
        assertFails(ErrorCode.NOT_DIRECTORY, () -> sandbox.statAt(NOFOLLOW, "plain/.."));
        assertFails(
                ErrorCode.NOT_DIRECTORY,
                () ->
                        sandbox.openAt(
                                NOFOLLOW, "plain/x", OpenFlags.of(OpenFlags.Flag.CREATE), READ));
        assertFails(ErrorCode.NOT_DIRECTORY, () -> sandbox.createDirectoryAt("plain/x"));
        assertFails(ErrorCode.NO_ENTRY, () -> sandbox.statAt(NOFOLLOW, "missing/.."));
    }

    @Test
    void aTrailingSlashNamesADirectory() {
        sandbox.createDirectoryAt("made/");

        assertTrue(Files.isDirectory(root.resolve("made")));
        assertFails(
                ErrorCode.IS_DIRECTORY,
                () ->
                        sandbox.openAt(
                                NOFOLLOW, "new/", OpenFlags.of(OpenFlags.Flag.CREATE), READ_WRITE));
        assertFalse(Files.exists(root.resolve("new")));
    }

    @Test
    void aNameTooLongForTheHostSaysSo() {
        assertFails(ErrorCode.NAME_TOO_LONG, () -> sandbox.createDirectoryAt("n".repeat(300)));
    }

    /** Nothing in a read-only directory changes, through it or through what is opened below it. */
    @Test
    void aReadOnlyDirectoryRefusesEveryChange() throws IOException {
        Files.createDirectory(root.resolve("sub"));
        Files.write(root.resolve("sub/file"), "x".getBytes(UTF_8));
        try (var wasi = WasiPreview2.builder().withReadOnlyDirectory("data", root).build()) {
            Descriptor data = wasi.preopens().getDirectories().get(0).f0();
            Descriptor sub = data.openAt(NOFOLLOW, "sub", EXISTING, READ);

            assertFalse(data.getFlags().has(DescriptorFlags.Flag.MUTATE_DIRECTORY));
            assertFails(ErrorCode.READ_ONLY, () -> data.createDirectoryAt("made"));
            assertFails(ErrorCode.READ_ONLY, () -> sub.unlinkFileAt("file"));
            assertFails(
                    ErrorCode.READ_ONLY, () -> sub.openAt(NOFOLLOW, "file", EXISTING, READ_WRITE));
            assertFails(ErrorCode.READ_ONLY, () -> data.openAt(NOFOLLOW, "sub", EXISTING, MUTATE));
        }
        assertTrue(Files.exists(root.resolve("sub/file")));
    }

    @Test
    void aDirectoryIsGrantedOnceUnderEachName() throws IOException {
        Path file = Files.write(root.resolve("plain"), "x".getBytes(UTF_8));
        WasiPreview2.Builder builder = WasiPreview2.builder().withDirectory("data", root);

        assertThrows(IllegalArgumentException.class, () -> builder.withDirectory("data", temp));
        assertThrows(IllegalArgumentException.class, () -> builder.withDirectory("other", file));
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.withReadOnlyDirectory("missing", root.resolve("missing")));
    }

    /** Closing the host closes what a guest left open, which a later use then finds. */
    @Test
    void closingTheHostClosesTheFilesAGuestKept() {
        Descriptor file;
        try (var wasi = WasiPreview2.builder().withDirectory("data", root).build()) {
            Descriptor data = wasi.preopens().getDirectories().get(0).f0();
            file = data.openAt(NOFOLLOW, "kept", OpenFlags.of(OpenFlags.Flag.CREATE), READ_WRITE);
        }

        assertFails(ErrorCode.BAD_DESCRIPTOR, () -> file.read(BigInteger.ONE, BigInteger.ZERO));
    }

    /** Once the host is closed, a guest still holding a directory opens nothing more. */
    @Test
    void aClosedHostOpensNoMoreFiles() {
        Descriptor data;
        try (var wasi = WasiPreview2.builder().withDirectory("data", root).build()) {
            data = wasi.preopens().getDirectories().get(0).f0();
        }

        assertFails(
                ErrorCode.BAD_DESCRIPTOR,
                () ->
                        data.openAt(
                                NOFOLLOW, "late", OpenFlags.of(OpenFlags.Flag.CREATE), READ_WRITE));
        assertFalse(Files.exists(root.resolve("late")));
    }

    /** A named pipe is read and written without offsets, and opening one would block. */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void aNamedPipeIsUnsupported() throws IOException, InterruptedException {
        Path fifo = root.resolve("fifo");
        Process mkfifo = new ProcessBuilder("mkfifo", fifo.toString()).start();
        assumeTrue(mkfifo.waitFor() == 0, "mkfifo is available");

        assertFails(ErrorCode.UNSUPPORTED, () -> sandbox.openAt(NOFOLLOW, "fifo", EXISTING, READ));
    }

    /** The host renames over the old file in one step rather than removing it first. */
    @Test
    void aRenameReplacesAnExistingFile() throws IOException {
        Files.write(root.resolve("tmp"), "new".getBytes(UTF_8));
        Files.write(root.resolve("config"), "old".getBytes(UTF_8));

        sandbox.renameAt("tmp", sandbox, "config");

        assertEquals("new", Files.readString(root.resolve("config")));
        assertFalse(Files.exists(root.resolve("tmp")));
    }

    /** As with {@code O_EXCL}, a link in the way is an existing file, even one that dangles. */
    @Test
    void anExclusiveCreateRefusesALinkInTheWay() {
        sandbox.symlinkAt("target", "lock");
        OpenFlags exclusive = OpenFlags.of(OpenFlags.Flag.CREATE, OpenFlags.Flag.EXCLUSIVE);

        assertFails(
                ErrorCode.EXIST,
                () ->
                        sandbox.openAt(
                                PathFlags.of(PathFlags.Flag.SYMLINK_FOLLOW),
                                "lock",
                                exclusive,
                                READ_WRITE));
        assertFalse(Files.exists(root.resolve("target")));
    }

    /** A file asked to be written synchronously opens with the host doing so, and says it does. */
    @Test
    void theSyncFlagsAreHonored() {
        DescriptorFlags synced =
                DescriptorFlags.of(
                        DescriptorFlags.Flag.READ,
                        DescriptorFlags.Flag.WRITE,
                        DescriptorFlags.Flag.FILE_INTEGRITY_SYNC,
                        DescriptorFlags.Flag.DATA_INTEGRITY_SYNC,
                        DescriptorFlags.Flag.REQUESTED_WRITE_SYNC);

        Descriptor file =
                sandbox.openAt(NOFOLLOW, "journal", OpenFlags.of(OpenFlags.Flag.CREATE), synced);
        file.write("entry".getBytes(UTF_8), BigInteger.ZERO);

        assertEquals(synced, file.getFlags());
        assertEquals(BigInteger.valueOf(5), file.stat().size());
    }

    /** Renaming an open file leaves its hash as it was, and changing it does not. */
    @Test
    void theHashOfAnOpenFileSurvivesARename() {
        Descriptor file =
                sandbox.openAt(NOFOLLOW, "a.txt", OpenFlags.of(OpenFlags.Flag.CREATE), READ_WRITE);
        file.write("hello".getBytes(UTF_8), BigInteger.ZERO);
        MetadataHashValue before = file.metadataHash();

        sandbox.renameAt("a.txt", sandbox, "b.txt");

        assertEquals(before, file.metadataHash());
        file.write("more".getBytes(UTF_8), BigInteger.valueOf(5));
        assertNotEquals(before, file.metadataHash());
    }

    /** A file sized zero but holding data, as under {@code /proc}, is read all the same. */
    @Test
    @EnabledOnOs(OS.LINUX)
    void aFileSizedZeroIsStillRead() throws IOException {
        HostDirectory proc =
                new HostDirectory(Path.of("/proc/self").toRealPath(), READ, new HostPoll());
        Descriptor status = proc.openAt(NOFOLLOW, "status", EXISTING, READ);

        byte[] read = status.read(BigInteger.valueOf(4096), BigInteger.ZERO).f0();

        assertTrue(new String(read, UTF_8).startsWith("Name:"));
    }

    /** Appends from two streams at once each land at the end, so neither overwrites the other. */
    @Test
    void appendsFromTwoStreamsAllLand() throws InterruptedException {
        sandbox.openAt(NOFOLLOW, "log", OpenFlags.of(OpenFlags.Flag.CREATE), READ_WRITE);
        int records = 500;
        Thread[] writers = new Thread[2];
        for (int w = 0; w < writers.length; w++) {
            OutputStream log =
                    sandbox.openAt(NOFOLLOW, "log", EXISTING, READ_WRITE).appendViaStream();
            String name = String.valueOf((char) ('a' + w));
            writers[w] =
                    new Thread(
                            () -> {
                                for (int i = 0; i < records; i++) {
                                    log.blockingWriteAndFlush(
                                            String.format("%s%08d\n", name, i).getBytes(UTF_8));
                                }
                            });
            writers[w].start();
        }
        for (Thread writer : writers) {
            writer.join();
        }

        assertEquals(BigInteger.valueOf(2L * records * 10), sandbox.statAt(NOFOLLOW, "log").size());
    }

    private static void assertFails(ErrorCode expected, Executable operation) {
        ErrorCodeException e = assertThrows(ErrorCodeException.class, operation);
        assertEquals(expected, e.error());
    }
}
