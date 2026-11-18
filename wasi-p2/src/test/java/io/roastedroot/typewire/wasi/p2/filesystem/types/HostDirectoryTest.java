package io.roastedroot.typewire.wasi.p2.filesystem.types;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.clocks.wallclock.Datetime;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.Descriptor;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorStat;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorType;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DirectoryEntry;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DirectoryEntryStream;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCodeException;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.NewTimestamp;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.OpenFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.PathFlags;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A directory {@code /sandbox} granted read and mutation access, beside {@code /outside}, on a
 * file system with the {@code unix} attribute view.
 */
class HostDirectoryTest {

    private static final PathFlags NOFOLLOW = PathFlags.of();
    private static final PathFlags FOLLOW = PathFlags.of(PathFlags.Flag.SYMLINK_FOLLOW);
    private static final OpenFlags EXISTING = OpenFlags.of();
    private static final DescriptorFlags READ = DescriptorFlags.of(DescriptorFlags.Flag.READ);
    private static final DescriptorFlags READ_WRITE =
            DescriptorFlags.of(DescriptorFlags.Flag.READ, DescriptorFlags.Flag.WRITE);
    private static final DescriptorFlags MUTATE =
            DescriptorFlags.of(DescriptorFlags.Flag.READ, DescriptorFlags.Flag.MUTATE_DIRECTORY);

    private final HostPoll poll = new HostPoll();
    private FileSystem fs;
    private Path root;
    private HostDirectory sandbox;

    @BeforeEach
    void createSandbox() throws IOException {
        // The unix view is what counts hard links, as it does on a real Unix host.
        fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder()
                                .setAttributeViews("basic", "unix")
                                .build());
        root = Files.createDirectories(fs.getPath("/sandbox"));
        Files.createDirectories(root.resolve("dir"));
        Files.write(root.resolve("file.txt"), "hello".getBytes(UTF_8));
        Files.createDirectories(fs.getPath("/outside"));
        sandbox = new HostDirectory(root, MUTATE, poll);
    }

    @AfterEach
    void closeFileSystem() throws IOException {
        fs.close();
    }

    @Test
    void anExistingFileOpensForReading() {
        Descriptor file = sandbox.openAt(NOFOLLOW, "file.txt", EXISTING, READ);

        assertInstanceOf(HostFile.class, file);
        assertArrayEquals("hello".getBytes(UTF_8), file.read(BigInteger.TEN, BigInteger.ZERO).f0());
    }

    @Test
    void aDirectoryOpensAsADirectory() {
        Descriptor dir = sandbox.openAt(NOFOLLOW, "dir", EXISTING, READ);

        assertInstanceOf(HostDirectory.class, dir);
        assertEquals(DescriptorType.DIRECTORY, dir.getType());
    }

    @Test
    void createMakesAFileThatDidNotExist() {
        Descriptor file =
                sandbox.openAt(
                        NOFOLLOW, "dir/new.txt", OpenFlags.of(OpenFlags.Flag.CREATE), READ_WRITE);
        file.write("made".getBytes(UTF_8), BigInteger.ZERO);

        assertTrue(Files.isRegularFile(root.resolve("dir/new.txt")));
        assertEquals(BigInteger.valueOf(4), sandbox.statAt(NOFOLLOW, "dir/new.txt").size());
    }

    @Test
    void createWithoutWriteStillCreates() {
        sandbox.openAt(NOFOLLOW, "new.txt", OpenFlags.of(OpenFlags.Flag.CREATE), READ);

        assertTrue(Files.isRegularFile(root.resolve("new.txt")));
    }

    @Test
    void anExclusiveCreateOfAnExistingFileFails() {
        assertFails(
                ErrorCode.EXIST,
                () ->
                        sandbox.openAt(
                                NOFOLLOW,
                                "file.txt",
                                OpenFlags.of(OpenFlags.Flag.CREATE, OpenFlags.Flag.EXCLUSIVE),
                                READ_WRITE));
    }

    @Test
    void truncateEmptiesAnExistingFile() {
        sandbox.openAt(NOFOLLOW, "file.txt", OpenFlags.of(OpenFlags.Flag.TRUNCATE), READ_WRITE);

        assertEquals(BigInteger.ZERO, sandbox.statAt(NOFOLLOW, "file.txt").size());
    }

    @Test
    void aMissingFileHasNoEntry() {
        assertFails(ErrorCode.NO_ENTRY, () -> sandbox.openAt(NOFOLLOW, "missing", EXISTING, READ));
    }

    @Test
    void theDirectoryFlagRefusesAFile() {
        OpenFlags directory = OpenFlags.of(OpenFlags.Flag.DIRECTORY);

        assertFails(
                ErrorCode.NOT_DIRECTORY,
                () -> sandbox.openAt(NOFOLLOW, "file.txt", directory, READ));
        assertFails(ErrorCode.NO_ENTRY, () -> sandbox.openAt(NOFOLLOW, "missing", directory, READ));
        assertFails(
                ErrorCode.INVALID,
                () ->
                        sandbox.openAt(
                                NOFOLLOW,
                                "new",
                                OpenFlags.of(OpenFlags.Flag.CREATE, OpenFlags.Flag.DIRECTORY),
                                READ));
    }

    @Test
    void aDirectoryCannotBeOpenedForWriting() {
        assertFails(
                ErrorCode.IS_DIRECTORY,
                () -> sandbox.openAt(NOFOLLOW, "dir", EXISTING, READ_WRITE));
    }

    @Test
    void anUnfollowedLinkIsALoop() throws IOException {
        Files.createSymbolicLink(root.resolve("link"), fs.getPath("file.txt"));

        assertFails(ErrorCode.LOOP, () -> sandbox.openAt(NOFOLLOW, "link", EXISTING, READ));
        assertInstanceOf(HostFile.class, sandbox.openAt(FOLLOW, "link", EXISTING, READ));
    }

    @Test
    void openingOutsideTheDirectoryIsNotPermitted() {
        assertFails(
                ErrorCode.NOT_PERMITTED,
                () -> sandbox.openAt(NOFOLLOW, "../outside", EXISTING, READ));
    }

    /** A directory opened without mutation access passes the restriction on to what it opens. */
    @Test
    void aReadOnlyDirectoryRefusesEverythingThatMutates() {
        HostDirectory readOnly = new HostDirectory(root, READ, poll);
        OpenFlags create = OpenFlags.of(OpenFlags.Flag.CREATE);

        assertFails(
                ErrorCode.READ_ONLY,
                () -> readOnly.openAt(NOFOLLOW, "file.txt", EXISTING, READ_WRITE));
        assertFails(ErrorCode.READ_ONLY, () -> readOnly.openAt(NOFOLLOW, "new", create, READ));
        assertFails(ErrorCode.READ_ONLY, () -> readOnly.openAt(NOFOLLOW, "dir", EXISTING, MUTATE));
        assertFails(ErrorCode.READ_ONLY, () -> readOnly.createDirectoryAt("new"));
        assertFails(ErrorCode.READ_ONLY, () -> readOnly.unlinkFileAt("file.txt"));
        assertFails(ErrorCode.READ_ONLY, () -> readOnly.removeDirectoryAt("dir"));
        assertFails(ErrorCode.READ_ONLY, () -> readOnly.renameAt("file.txt", readOnly, "b"));
        assertFails(ErrorCode.READ_ONLY, () -> readOnly.symlinkAt("file.txt", "link"));
        assertTrue(Files.exists(root.resolve("file.txt")));
    }

    @Test
    void aFileOpenedFromADirectoryNeverCarriesMutateDirectory() {
        Descriptor file = sandbox.openAt(NOFOLLOW, "file.txt", EXISTING, MUTATE);

        assertFalse(file.getFlags().has(DescriptorFlags.Flag.MUTATE_DIRECTORY));
    }

    @Test
    void statAtFollowsALinkOnlyWhenAsked() throws IOException {
        Files.createSymbolicLink(root.resolve("link"), fs.getPath("file.txt"));

        assertEquals(DescriptorType.SYMBOLIC_LINK, sandbox.statAt(NOFOLLOW, "link").type());
        DescriptorStat followed = sandbox.statAt(FOLLOW, "link");
        assertEquals(DescriptorType.REGULAR_FILE, followed.type());
        assertEquals(BigInteger.valueOf(5), followed.size());
    }

    @Test
    void statDescribesTheDirectoryItself() {
        DescriptorStat stat = sandbox.stat();

        assertEquals(DescriptorType.DIRECTORY, stat.type());
        assertTrue(stat.dataModificationTimestamp() != null);
    }

    @Test
    void createDirectoryAtMakesADirectoryOnce() {
        sandbox.createDirectoryAt("dir/sub");

        assertTrue(Files.isDirectory(root.resolve("dir/sub")));
        assertFails(ErrorCode.EXIST, () -> sandbox.createDirectoryAt("dir/sub"));
    }

    @Test
    void removeDirectoryAtRemovesOnlyAnEmptyDirectory() throws IOException {
        Files.createFile(root.resolve("dir/inside"));

        assertFails(ErrorCode.NOT_EMPTY, () -> sandbox.removeDirectoryAt("dir"));
        assertFails(ErrorCode.NOT_DIRECTORY, () -> sandbox.removeDirectoryAt("file.txt"));
        assertFails(ErrorCode.INVALID, () -> sandbox.removeDirectoryAt("."));

        Files.delete(root.resolve("dir/inside"));
        sandbox.removeDirectoryAt("dir");
        assertFalse(Files.exists(root.resolve("dir")));
    }

    @Test
    void unlinkFileAtRemovesAnythingButADirectory() throws IOException {
        Files.createSymbolicLink(root.resolve("link"), fs.getPath("file.txt"));

        sandbox.unlinkFileAt("link");
        assertFalse(Files.exists(root.resolve("link"), LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(root.resolve("file.txt")));

        sandbox.unlinkFileAt("file.txt");
        assertFalse(Files.exists(root.resolve("file.txt")));
        assertFails(ErrorCode.IS_DIRECTORY, () -> sandbox.unlinkFileAt("dir"));
    }

    @Test
    void readDirectoryListsEachEntryWithItsType() throws IOException {
        Files.createSymbolicLink(root.resolve("link"), fs.getPath("file.txt"));

        Map<String, DescriptorType> entries = new HashMap<>();
        for (DirectoryEntry entry : readAll(sandbox.readDirectory())) {
            entries.put(entry.name(), entry.type());
        }

        assertEquals(
                Map.of(
                        "dir", DescriptorType.DIRECTORY,
                        "file.txt", DescriptorType.REGULAR_FILE,
                        "link", DescriptorType.SYMBOLIC_LINK),
                entries);
    }

    @Test
    void eachReadOfADirectoryStartsAtTheBeginning() {
        DirectoryEntryStream first = sandbox.readDirectory();
        first.readDirectoryEntry();

        assertEquals(2, readAll(sandbox.readDirectory()).size());
        assertEquals(1, readAll(first).size());
    }

    @Test
    void renameAtMovesAFileBetweenDirectories() {
        HostDirectory dir = (HostDirectory) sandbox.openAt(NOFOLLOW, "dir", EXISTING, MUTATE);

        sandbox.renameAt("file.txt", dir, "moved.txt");

        assertFalse(Files.exists(root.resolve("file.txt")));
        assertTrue(Files.exists(root.resolve("dir/moved.txt")));
    }

    @Test
    void renameAtReplacesAnExistingFile() throws IOException {
        Files.write(root.resolve("other.txt"), "other".getBytes(UTF_8));

        sandbox.renameAt("other.txt", sandbox, "file.txt");

        assertEquals("other", Files.readString(root.resolve("file.txt")));
    }

    @Test
    void renameAtKeepsDirectoriesAndFilesApart() throws IOException {
        Files.createDirectories(root.resolve("empty"));

        assertFails(ErrorCode.NOT_DIRECTORY, () -> sandbox.renameAt("dir", sandbox, "file.txt"));
        assertFails(ErrorCode.IS_DIRECTORY, () -> sandbox.renameAt("file.txt", sandbox, "empty"));
        assertFails(ErrorCode.INVALID, () -> sandbox.renameAt("dir", sandbox, "dir/inside"));
    }

    @Test
    void renameAtNeedsADirectoryToRenameInto() {
        Descriptor file = sandbox.openAt(NOFOLLOW, "file.txt", EXISTING, READ);

        assertFails(ErrorCode.NOT_DIRECTORY, () -> sandbox.renameAt("dir", file, "x"));
    }

    @Test
    void aSymbolicLinkReadsBackAsItWasWritten() {
        sandbox.symlinkAt("dir/../file.txt", "link");

        assertEquals("dir/../file.txt", sandbox.readlinkAt("link"));
        assertEquals(DescriptorType.REGULAR_FILE, sandbox.statAt(FOLLOW, "link").type());
    }

    @Test
    void anAbsoluteSymbolicLinkIsNotPermitted() {
        assertFails(ErrorCode.NOT_PERMITTED, () -> sandbox.symlinkAt("/outside", "link"));
    }

    /** A link reaching outside may be written, but nothing resolves through it. */
    @Test
    void anEscapingSymbolicLinkCannotBeFollowed() {
        sandbox.symlinkAt("../outside", "link");

        assertFails(ErrorCode.NOT_PERMITTED, () -> sandbox.statAt(FOLLOW, "link"));
    }

    @Test
    void readlinkAtRefusesAnAbsoluteTargetAndAFile() throws IOException {
        Files.createSymbolicLink(root.resolve("escape"), fs.getPath("/outside"));

        assertFails(ErrorCode.NOT_PERMITTED, () -> sandbox.readlinkAt("escape"));
        assertFails(ErrorCode.INVALID, () -> sandbox.readlinkAt("file.txt"));
    }

    @Test
    void linkAtGivesAFileASecondName() {
        sandbox.linkAt(NOFOLLOW, "file.txt", sandbox, "dir/also.txt");

        assertTrue(
                sandbox.openAt(NOFOLLOW, "file.txt", EXISTING, READ)
                        .isSameObject(sandbox.openAt(NOFOLLOW, "dir/also.txt", EXISTING, READ)));
        assertEquals(BigInteger.TWO, sandbox.statAt(NOFOLLOW, "file.txt").linkCount());
    }

    @Test
    void linkAtRefusesADirectoryAndAnExistingName() {
        assertFails(ErrorCode.NOT_PERMITTED, () -> sandbox.linkAt(NOFOLLOW, "dir", sandbox, "d2"));
        assertFails(ErrorCode.NO_ENTRY, () -> sandbox.linkAt(NOFOLLOW, "missing", sandbox, "m2"));
        assertFails(ErrorCode.EXIST, () -> sandbox.linkAt(NOFOLLOW, "file.txt", sandbox, "dir"));
    }

    @Test
    void setTimesAtSetsTheGivenTimes() {
        Datetime time = new Datetime(BigInteger.valueOf(1_000_000), 500L);

        sandbox.setTimesAt(
                NOFOLLOW,
                "file.txt",
                new NewTimestamp.Timestamp(time),
                new NewTimestamp.Timestamp(time));

        DescriptorStat stat = sandbox.statAt(NOFOLLOW, "file.txt");
        assertEquals(time, stat.dataModificationTimestamp());
        assertEquals(time, stat.dataAccessTimestamp());
    }

    @Test
    void noChangeLeavesATimeAsItWas() {
        Datetime before = sandbox.statAt(NOFOLLOW, "file.txt").dataModificationTimestamp();
        Datetime access = new Datetime(BigInteger.valueOf(1_000_000), 0L);

        sandbox.setTimesAt(
                NOFOLLOW,
                "file.txt",
                new NewTimestamp.Timestamp(access),
                new NewTimestamp.NoChange());

        assertEquals(before, sandbox.statAt(NOFOLLOW, "file.txt").dataModificationTimestamp());
    }

    @Test
    void theMetadataHashChangesWhenAFileDoes() {
        var before = sandbox.metadataHashAt(NOFOLLOW, "file.txt");
        assertEquals(before, sandbox.metadataHashAt(NOFOLLOW, "file.txt"));

        sandbox.openAt(NOFOLLOW, "file.txt", EXISTING, READ_WRITE)
                .write("longer contents".getBytes(UTF_8), BigInteger.ZERO);

        assertNotEquals(before, sandbox.metadataHashAt(NOFOLLOW, "file.txt"));
    }

    @Test
    void aDirectoryRefusesWhatOnlyAFileSupports() {
        assertFails(ErrorCode.BAD_DESCRIPTOR, () -> sandbox.read(BigInteger.ONE, BigInteger.ZERO));
        assertFails(ErrorCode.BAD_DESCRIPTOR, () -> sandbox.write(new byte[1], BigInteger.ZERO));
        assertFails(ErrorCode.BAD_DESCRIPTOR, () -> sandbox.setSize(BigInteger.ZERO));
    }

    @Test
    void twoDescriptorsForOneDirectoryAreTheSameObject() {
        Descriptor again = sandbox.openAt(NOFOLLOW, ".", EXISTING, READ);

        assertTrue(sandbox.isSameObject(again));
        assertFalse(sandbox.isSameObject(sandbox.openAt(NOFOLLOW, "dir", EXISTING, READ)));
    }

    private static List<DirectoryEntry> readAll(DirectoryEntryStream stream) {
        List<DirectoryEntry> entries = new ArrayList<>();
        for (DirectoryEntry entry = stream.readDirectoryEntry();
                entry != null;
                entry = stream.readDirectoryEntry()) {
            entries.add(entry);
        }
        assertNull(stream.readDirectoryEntry());
        return entries;
    }

    private static void assertFails(ErrorCode expected, Executable operation) {
        assertEquals(expected, assertThrows(ErrorCodeException.class, operation).error());
    }
}
