package io.roastedroot.typewire.wasi.p2.filesystem.types;

import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCode;
import io.roastedroot.typewire.wasi.p2.bindings.wasi.filesystem.types.ErrorCodeException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.Map;

/**
 * The ways a filesystem operation fails, as the exception the bindings turn into an {@code
 * error-code}.
 *
 * <p>The JDK reports many failures only through the host's message, in a {@link
 * FileSystemException} or in the plain {@link IOException} a file channel throws. The messages the
 * C libraries of Linux and macOS give in English are recognized, while a JVM running under another
 * locale reports those failures as {@code io}.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/filesystem/wit/types.wit">wasi:filesystem/types</a>
 */
public final class ErrorCodes {

    /** The messages {@code strerror} gives for an {@code errno} that has no exception of its own. */
    private static final Map<String, ErrorCode> REASONS =
            Map.ofEntries(
                    Map.entry("Not a directory", ErrorCode.NOT_DIRECTORY),
                    Map.entry("Is a directory", ErrorCode.IS_DIRECTORY),
                    Map.entry("File name too long", ErrorCode.NAME_TOO_LONG),
                    Map.entry("No space left on device", ErrorCode.INSUFFICIENT_SPACE),
                    Map.entry("Disk quota exceeded", ErrorCode.QUOTA),
                    Map.entry("Invalid cross-device link", ErrorCode.CROSS_DEVICE),
                    Map.entry("Cross-device link", ErrorCode.CROSS_DEVICE),
                    Map.entry("Too many links", ErrorCode.TOO_MANY_LINKS),
                    Map.entry("Too many levels of symbolic links", ErrorCode.LOOP),
                    Map.entry("Directory not empty", ErrorCode.NOT_EMPTY),
                    Map.entry("Read-only file system", ErrorCode.READ_ONLY),
                    Map.entry("Operation not permitted", ErrorCode.NOT_PERMITTED),
                    Map.entry("Permission denied", ErrorCode.ACCESS),
                    Map.entry("Device or resource busy", ErrorCode.BUSY),
                    Map.entry("Resource busy", ErrorCode.BUSY),
                    Map.entry("Text file busy", ErrorCode.TEXT_FILE_BUSY),
                    Map.entry("File too large", ErrorCode.FILE_TOO_LARGE),
                    Map.entry("Invalid argument", ErrorCode.INVALID),
                    Map.entry("Operation not supported", ErrorCode.UNSUPPORTED),
                    Map.entry("Illegal seek", ErrorCode.INVALID_SEEK),
                    Map.entry("Bad file descriptor", ErrorCode.BAD_DESCRIPTOR));

    private ErrorCodes() {}

    /** An action on the host file system that returns a value. */
    @FunctionalInterface
    interface HostCall<T> {
        T call() throws IOException;
    }

    /** An action on the host file system. */
    @FunctionalInterface
    interface HostRun {
        void run() throws IOException;
    }

    /** Runs {@code action}, reporting each way the host can fail as an {@code error-code}. */
    static <T> T call(HostCall<T> action) {
        try {
            return action.call();
        } catch (IOException
                | UncheckedIOException
                | DirectoryIteratorException
                | UnsupportedOperationException
                | SecurityException e) {
            throw failed(e);
        }
    }

    /** Runs {@code action}, reporting each way the host can fail as an {@code error-code}. */
    static void run(HostRun action) {
        call(
                () -> {
                    action.run();
                    return null;
                });
    }

    public static ErrorCodeException error(ErrorCode code) {
        return new ErrorCodeException(code);
    }

    /** The operation failed because of {@code cause}, reported as {@code io} when no code fits. */
    public static ErrorCodeException failed(Throwable cause) {
        ErrorCode code = codeOf(cause);
        return error(code == null ? ErrorCode.IO : code);
    }

    /** The code {@code cause} stands for, or {@code null} when it is not a filesystem failure. */
    public static ErrorCode codeOf(Throwable cause) {
        if (cause instanceof UncheckedIOException || cause instanceof DirectoryIteratorException) {
            return codeOf(cause.getCause());
        }
        if (cause instanceof NoSuchFileException) {
            return ErrorCode.NO_ENTRY;
        }
        if (cause instanceof FileAlreadyExistsException) {
            return ErrorCode.EXIST;
        }
        if (cause instanceof DirectoryNotEmptyException) {
            return ErrorCode.NOT_EMPTY;
        }
        if (cause instanceof NotDirectoryException) {
            return ErrorCode.NOT_DIRECTORY;
        }
        if (cause instanceof AccessDeniedException) {
            return ErrorCode.ACCESS;
        }
        if (cause instanceof AtomicMoveNotSupportedException) {
            return ErrorCode.CROSS_DEVICE;
        }
        if (cause instanceof NotLinkException) {
            return ErrorCode.INVALID;
        }
        if (cause instanceof FileSystemLoopException) {
            return ErrorCode.LOOP;
        }
        if (cause instanceof ReadOnlyFileSystemException) {
            return ErrorCode.READ_ONLY;
        }
        if (cause instanceof SecurityException) {
            return ErrorCode.ACCESS;
        }
        if (cause instanceof UnsupportedOperationException) {
            return ErrorCode.UNSUPPORTED;
        }
        if (cause instanceof ClosedChannelException
                || cause instanceof NonReadableChannelException
                || cause instanceof NonWritableChannelException) {
            return ErrorCode.BAD_DESCRIPTOR;
        }
        if (cause instanceof IOException) {
            String reason =
                    cause instanceof FileSystemException
                            ? ((FileSystemException) cause).getReason()
                            : cause.getMessage();
            ErrorCode code = reasonCode(reason);
            return code == null ? ErrorCode.IO : code;
        }
        return null;
    }

    /** The code a host message stands for, which the JDK may extend with details of its own. */
    private static ErrorCode reasonCode(String reason) {
        if (reason == null) {
            return null;
        }
        for (Map.Entry<String, ErrorCode> known : REASONS.entrySet()) {
            if (reason.startsWith(known.getKey())) {
                return known.getValue();
            }
        }
        return null;
    }
}
