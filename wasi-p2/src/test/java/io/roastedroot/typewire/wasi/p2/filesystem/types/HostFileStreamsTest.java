package io.roastedroot.typewire.wasi.p2.filesystem.types;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.DescriptorFlags;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCodeException;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.InputStream;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.OutputStream;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.StreamError;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.io.streams.StreamErrorException;
import io.roastedroot.typewire.wasi.p2.io.poll.HostPoll;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The streams a file descriptor opens, over a file {@code /file.txt} holding {@code hello}.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types</a>
 */
class HostFileStreamsTest {

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
        path = Files.write(fs.getPath("/file.txt"), bytes("hello"));
    }

    @AfterEach
    void closeFileSystem() throws IOException {
        fs.close();
    }

    @Test
    void aReadStreamReadsOnFromItsOffset() {
        InputStream stream = open(READ).readViaStream(BigInteger.ONE);

        assertArrayEquals(bytes("el"), stream.read(len(2)));
        assertArrayEquals(bytes("lo"), stream.read(len(10)));
    }

    @Test
    void aReadStreamClosesAtTheEndOfTheFile() {
        InputStream stream = open(READ).readViaStream(BigInteger.valueOf(5));

        assertClosed(() -> stream.read(len(1)));
        assertClosed(() -> stream.blockingRead(len(1)));
    }

    @Test
    void aReadStreamStartingPastTheEndIsClosed() {
        InputStream stream = open(READ).readViaStream(BigInteger.ONE.shiftLeft(64));

        assertClosed(() -> stream.read(len(1)));
    }

    @Test
    void aReadOfNothingSucceedsWhileOpen() {
        InputStream stream = open(READ).readViaStream(BigInteger.ZERO);

        assertEquals(0, stream.read(BigInteger.ZERO).length);
    }

    @Test
    void aBlockingReadReturnsAtOnce() {
        InputStream stream = open(READ).readViaStream(BigInteger.ZERO);

        assertArrayEquals(bytes("hello"), stream.blockingRead(len(10)));
    }

    @Test
    void skipMovesTheStreamOn() {
        InputStream stream = open(READ).readViaStream(BigInteger.ZERO);

        assertEquals(len(3), stream.skip(len(3)));
        assertArrayEquals(bytes("lo"), stream.read(len(10)));
    }

    /** A file is always ready, so polling a stream over one returns at once. */
    @Test
    void aFileStreamIsAlwaysReady() {
        HostFile file = open(READ_WRITE);

        assertEquals(
                List.of(0L, 1L),
                poll.poll(
                        List.of(
                                file.readViaStream(BigInteger.ZERO).subscribe(),
                                file.writeViaStream(BigInteger.ZERO).subscribe())));
    }

    @Test
    void streamsOverOneFileDoNotInterfere() {
        HostFile file = open(READ);
        InputStream first = file.readViaStream(BigInteger.ZERO);
        InputStream second = file.readViaStream(BigInteger.ZERO);

        first.read(len(3));

        assertArrayEquals(bytes("hel"), second.read(len(3)));
    }

    @Test
    void aWriteStreamWritesOnFromItsOffset() throws IOException {
        OutputStream stream = open(WRITE).writeViaStream(BigInteger.ONE);

        assertEquals(len(HostFileOutputStream.CAPACITY), stream.checkWrite());
        stream.write(bytes("EL"));
        stream.write(bytes("LO!"));
        stream.blockingFlush();

        assertEquals("hELLO!", Files.readString(path));
    }

    @Test
    void aBlockingWriteAndFlushReachesTheFile() throws IOException {
        open(WRITE).writeViaStream(BigInteger.ZERO).blockingWriteAndFlush(bytes("J"));

        assertEquals("Jello", Files.readString(path));
    }

    @Test
    void anAppendStreamWritesWhereverTheEndIs() throws IOException {
        HostFile file = open(READ_WRITE);
        OutputStream stream = file.appendViaStream();

        stream.checkWrite();
        stream.write(bytes(" world"));
        file.write(bytes("!!"), BigInteger.valueOf(11));
        stream.checkWrite();
        stream.write(bytes("?"));

        assertEquals("hello world!!?", Files.readString(path));
    }

    @Test
    void splicingCopiesFromOneFileStreamToAnother() throws IOException {
        Path copy = Files.createFile(fs.getPath("/copy.txt"));
        InputStream source = open(READ).readViaStream(BigInteger.ZERO);
        OutputStream sink =
                HostFile.open(copy, WRITE, poll, false, false, false)
                        .writeViaStream(BigInteger.ZERO);

        assertEquals(len(5), sink.blockingSplice(source, len(100)));
        assertEquals("hello", Files.readString(copy));
    }

    @Test
    void eachStreamNeedsTheFlagItsDescriptorWasOpenedWith() {
        assertFails(ErrorCode.BAD_DESCRIPTOR, () -> open(WRITE).readViaStream(BigInteger.ZERO));
        assertFails(ErrorCode.BAD_DESCRIPTOR, () -> open(READ).writeViaStream(BigInteger.ZERO));
        assertFails(ErrorCode.BAD_DESCRIPTOR, () -> open(READ).appendViaStream());
    }

    @Test
    void aWriteStreamOffsetTheHostCannotReachIsTooLarge() {
        assertFails(
                ErrorCode.FILE_TOO_LARGE,
                () -> open(WRITE).writeViaStream(BigInteger.ONE.shiftLeft(63)));
    }

    /** A guest may drop the descriptor and go on reading a stream it opened. */
    @Test
    void aStreamOutlivesItsDescriptor() {
        HostFile file = open(READ);
        InputStream stream = file.readViaStream(BigInteger.ZERO);

        file.drop();

        assertArrayEquals(bytes("hello"), stream.read(len(10)));
    }

    @Test
    void theFileClosesOnceItsDescriptorAndEveryStreamAreDropped() {
        HostFile file = open(READ);
        HostFile other = open(READ);
        InputStream stream = file.readViaStream(BigInteger.ZERO);

        stream.drop();
        assertArrayEquals(bytes("h"), file.read(BigInteger.ONE, BigInteger.ZERO).f0());

        file.drop();
        assertFails(ErrorCode.BAD_DESCRIPTOR, () -> file.read(BigInteger.ONE, BigInteger.ZERO));
        assertArrayEquals(bytes("h"), other.read(BigInteger.ONE, BigInteger.ZERO).f0());
    }

    /**
     * A stream reports a host failure once, carrying it so that {@code filesystem-error-code} can
     * read its code, and is closed after it.
     */
    @Test
    void aFailedReadCarriesItsErrorCode() throws IOException {
        FileChannel channel = FileChannel.open(path);
        channel.close();
        HostFileInputStream stream =
                new HostFileInputStream(poll, new SharedChannel(channel, new OpenFiles()), 0);

        StreamErrorException failure =
                assertThrows(StreamErrorException.class, () -> stream.read(len(1)));

        StreamError.LastOperationFailed failed =
                assertInstanceOf(StreamError.LastOperationFailed.class, failure.error());
        assertEquals(ErrorCode.BAD_DESCRIPTOR, new HostTypes().filesystemErrorCode(failed.value()));
        assertClosed(() -> stream.read(len(1)));
    }

    @Test
    void aFailedWriteCarriesItsErrorCode() throws IOException {
        FileChannel channel = FileChannel.open(path);
        channel.close();
        HostFileOutputStream stream =
                HostFileOutputStream.at(poll, new SharedChannel(channel, new OpenFiles()), 0);

        stream.checkWrite();
        StreamErrorException failure =
                assertThrows(StreamErrorException.class, () -> stream.write(bytes("x")));

        assertTrue(failure.error() instanceof StreamError.LastOperationFailed);
        assertClosed(stream::checkWrite);
    }

    private HostFile open(DescriptorFlags flags) {
        return HostFile.open(path, flags, poll, false, false, false);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(UTF_8);
    }

    private static BigInteger len(long length) {
        return BigInteger.valueOf(length);
    }

    private static void assertClosed(Executable operation) {
        StreamErrorException closed = assertThrows(StreamErrorException.class, operation);
        assertInstanceOf(StreamError.Closed.class, closed.error());
    }

    private static void assertFails(ErrorCode expected, Executable operation) {
        assertEquals(expected, assertThrows(ErrorCodeException.class, operation).error());
    }
}
