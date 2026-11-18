package io.roastedroot.typewire.wasi.p2.filesystem.types;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.runtime.Tuple2;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorType;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCodeException;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.PathFlags;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** A file {@code /file.txt} holding {@code hello}, on a file system with only basic attributes. */
class HostFileTest {

    private static final DescriptorFlags READ = DescriptorFlags.of(DescriptorFlags.Flag.READ);
    private static final DescriptorFlags WRITE = DescriptorFlags.of(DescriptorFlags.Flag.WRITE);
    private static final DescriptorFlags READ_WRITE =
            DescriptorFlags.of(DescriptorFlags.Flag.READ, DescriptorFlags.Flag.WRITE);

    private final HostPoll poll = new HostPoll();
    private FileSystem fs;
    private Path path;

    @BeforeEach
    void createFile() throws IOException {
        fs = ZeroFs.newFileSystem(Configuration.unix());
        path = Files.write(fs.getPath("/file.txt"), "hello".getBytes(UTF_8));
    }

    @AfterEach
    void closeFileSystem() throws IOException {
        fs.close();
    }

    @Test
    void aReadReachingTheEndSaysSo() {
        Tuple2<byte[], Boolean> read = open(READ).read(BigInteger.TEN, BigInteger.ZERO);

        assertArrayEquals("hello".getBytes(UTF_8), read.f0());
        assertTrue(read.f1());
    }

    @Test
    void aReadShortOfTheEndSaysSo() {
        Tuple2<byte[], Boolean> read = open(READ).read(BigInteger.TWO, BigInteger.ONE);

        assertArrayEquals("el".getBytes(UTF_8), read.f0());
        assertFalse(read.f1());
    }

    @Test
    void aReadPastTheEndIsEmpty() {
        Tuple2<byte[], Boolean> read = open(READ).read(BigInteger.TEN, BigInteger.valueOf(100));

        assertEquals(0, read.f0().length);
        assertTrue(read.f1());
    }

    /** A guest may ask for far more than it can hold, so one read is capped. */
    @Test
    void oneReadReturnsNoMoreThanTheCap() throws IOException {
        Files.write(path, new byte[HostFile.MAX_READ + 10]);

        Tuple2<byte[], Boolean> read =
                open(READ).read(BigInteger.ONE.shiftLeft(40), BigInteger.ZERO);

        assertEquals(HostFile.MAX_READ, read.f0().length);
        assertFalse(read.f1());
    }

    @Test
    void readingDoesNotMoveAnyPosition() {
        HostFile file = open(READ);

        file.read(BigInteger.TWO, BigInteger.ZERO);

        assertArrayEquals("he".getBytes(UTF_8), file.read(BigInteger.TWO, BigInteger.ZERO).f0());
    }

    @Test
    void aWriteOverwritesInPlace() throws IOException {
        BigInteger written = open(READ_WRITE).write("J".getBytes(UTF_8), BigInteger.ZERO);

        assertEquals(BigInteger.ONE, written);
        assertEquals("Jello", Files.readString(path));
    }

    @Test
    void aWritePastTheEndFillsTheGapWithZeros() throws IOException {
        open(WRITE).write(new byte[] {7}, BigInteger.valueOf(8));

        assertArrayEquals(
                new byte[] {'h', 'e', 'l', 'l', 'o', 0, 0, 0, 7}, Files.readAllBytes(path));
    }

    @Test
    void anOffsetTheHostCannotReachIsTooLarge() {
        assertFails(
                ErrorCode.FILE_TOO_LARGE,
                () -> open(WRITE).write(new byte[1], BigInteger.ONE.shiftLeft(63)));
    }

    @Test
    void eachOperationNeedsItsFlag() {
        assertFails(
                ErrorCode.BAD_DESCRIPTOR, () -> open(WRITE).read(BigInteger.ONE, BigInteger.ZERO));
        assertFails(ErrorCode.BAD_DESCRIPTOR, () -> open(READ).write(new byte[1], BigInteger.ZERO));
        assertFails(ErrorCode.BAD_DESCRIPTOR, () -> open(READ).setSize(BigInteger.ZERO));
    }

    @Test
    void setSizeShrinksAndGrows() throws IOException {
        HostFile file = open(READ_WRITE);

        file.setSize(BigInteger.TWO);
        assertEquals("he", Files.readString(path));

        file.setSize(BigInteger.valueOf(4));
        assertArrayEquals(new byte[] {'h', 'e', 0, 0}, Files.readAllBytes(path));
    }

    @Test
    void syncingAFileNotOpenForWritingSucceeds() {
        HostFile file = open(READ);

        file.sync();
        file.syncData();
    }

    @Test
    void statDescribesTheFile() {
        assertEquals(DescriptorType.REGULAR_FILE, open(READ).stat().type());
        assertEquals(BigInteger.valueOf(5), open(READ).stat().size());
        // Without the unix view, a host cannot count links, so one is reported.
        assertEquals(BigInteger.ONE, open(READ).stat().linkCount());
    }

    @Test
    void aFileRefusesWhatOnlyADirectorySupports() {
        HostFile file = open(READ);

        assertFails(ErrorCode.NOT_DIRECTORY, file::readDirectory);
        assertFails(ErrorCode.NOT_DIRECTORY, () -> file.statAt(PathFlags.of(), "x"));
        assertFails(ErrorCode.NOT_DIRECTORY, () -> file.createDirectoryAt("x"));
    }

    @Test
    void aDroppedFileIsClosed() {
        HostFile file = open(READ);

        file.drop();

        assertFails(ErrorCode.BAD_DESCRIPTOR, () -> file.read(BigInteger.ONE, BigInteger.ZERO));
    }

    private HostFile open(DescriptorFlags flags) {
        return HostFile.open(path, flags, poll, false, false, false);
    }

    private static void assertFails(ErrorCode expected, Executable operation) {
        assertEquals(expected, assertThrows(ErrorCodeException.class, operation).error());
    }
}
