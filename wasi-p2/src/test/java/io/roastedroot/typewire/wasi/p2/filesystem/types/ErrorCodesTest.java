package io.roastedroot.typewire.wasi.p2.filesystem.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCodeException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.ReadOnlyFileSystemException;
import org.junit.jupiter.api.Test;

class ErrorCodesTest {

    @Test
    void eachFileSystemExceptionHasItsCode() {
        assertEquals(ErrorCode.NO_ENTRY, ErrorCodes.codeOf(new NoSuchFileException("a")));
        assertEquals(ErrorCode.EXIST, ErrorCodes.codeOf(new FileAlreadyExistsException("a")));
        assertEquals(ErrorCode.NOT_EMPTY, ErrorCodes.codeOf(new DirectoryNotEmptyException("a")));
        assertEquals(ErrorCode.NOT_DIRECTORY, ErrorCodes.codeOf(new NotDirectoryException("a")));
        assertEquals(ErrorCode.READ_ONLY, ErrorCodes.codeOf(new ReadOnlyFileSystemException()));
        assertEquals(ErrorCode.BAD_DESCRIPTOR, ErrorCodes.codeOf(new ClosedChannelException()));
    }

    /** The JDK reports these only through the host's message. */
    @Test
    void aHostMessageHasItsCode() {
        assertEquals(ErrorCode.NOT_DIRECTORY, ErrorCodes.codeOf(reason("Not a directory")));
        assertEquals(ErrorCode.NAME_TOO_LONG, ErrorCodes.codeOf(reason("File name too long")));
        assertEquals(
                ErrorCode.INSUFFICIENT_SPACE, ErrorCodes.codeOf(reason("No space left on device")));
        assertEquals(ErrorCode.CROSS_DEVICE, ErrorCodes.codeOf(reason("Cross-device link")));
        assertEquals(
                ErrorCode.LOOP,
                ErrorCodes.codeOf(
                        reason(
                                "Too many levels of symbolic links or unable to access attributes"
                                        + " of symbolic link")));
        assertEquals(ErrorCode.IO, ErrorCodes.codeOf(reason("Something unheard of")));
        assertEquals(
                ErrorCode.CROSS_DEVICE,
                ErrorCodes.codeOf(new AtomicMoveNotSupportedException("a", "b", "other mount")));
    }

    /** A file channel reports a failed read, write or truncation as a plain IOException. */
    @Test
    void aChannelMessageHasItsCode() {
        assertEquals(
                ErrorCode.INSUFFICIENT_SPACE,
                ErrorCodes.codeOf(new IOException("No space left on device")));
        assertEquals(ErrorCode.QUOTA, ErrorCodes.codeOf(new IOException("Disk quota exceeded")));
        assertEquals(
                ErrorCode.FILE_TOO_LARGE, ErrorCodes.codeOf(new IOException("File too large")));
    }

    @Test
    void aWrappedFailureHasTheCodeOfItsCause() {
        assertEquals(
                ErrorCode.ACCESS,
                ErrorCodes.codeOf(new DirectoryIteratorException(new AccessDeniedException("a"))));
        assertEquals(
                ErrorCode.NO_ENTRY,
                ErrorCodes.codeOf(new UncheckedIOException(new NoSuchFileException("a"))));
    }

    /** What the host reports without a checked exception still becomes an error code. */
    @Test
    void anUncheckedHostFailureBecomesAnErrorCode() {
        ErrorCodeException e =
                assertThrows(
                        ErrorCodeException.class,
                        () ->
                                ErrorCodes.run(
                                        () -> {
                                            throw new ReadOnlyFileSystemException();
                                        }));

        assertEquals(ErrorCode.READ_ONLY, e.error());
    }

    @Test
    void anyOtherIoFailureIsIo() {
        assertEquals(ErrorCode.IO, ErrorCodes.codeOf(new IOException("disk on fire")));
    }

    @Test
    void aFailureOutsideTheFileSystemHasNoCode() {
        assertNull(ErrorCodes.codeOf(new IllegalStateException()));
    }

    @Test
    void failingWithoutACodeReportsIo() {
        assertEquals(ErrorCode.IO, ErrorCodes.failed(new IllegalStateException()).error());
        assertEquals(ErrorCode.NO_ENTRY, ErrorCodes.failed(new NoSuchFileException("a")).error());
    }

    private static FileSystemException reason(String message) {
        return new FileSystemException("a", null, message);
    }
}
