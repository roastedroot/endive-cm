package run.endive.cm.wasi.p2.sockets.network;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;

/**
 * The ways a socket operation fails, as the exception the bindings turn into an {@code
 * error-code}.
 *
 * <p>The JDK reports many failures only as an {@link IOException} carrying the host's message, so
 * the messages the C libraries of Linux and macOS give in English are recognized too. Netty's
 * native transports put the call that failed before the message.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/network.wit">wasi:sockets/network</a>
 */
public final class ErrorCodes {

    /** The messages {@code strerror} gives for an {@code errno} that has no exception of its own. */
    private static final Map<String, ErrorCode> REASONS =
            Map.ofEntries(
                    Map.entry("Address already in use", ErrorCode.ADDRESS_IN_USE),
                    Map.entry("Cannot assign requested address", ErrorCode.ADDRESS_NOT_BINDABLE),
                    Map.entry("Can't assign requested address", ErrorCode.ADDRESS_NOT_BINDABLE),
                    Map.entry("Connection refused", ErrorCode.CONNECTION_REFUSED),
                    Map.entry("Connection reset", ErrorCode.CONNECTION_RESET),
                    Map.entry("Broken pipe", ErrorCode.CONNECTION_RESET),
                    Map.entry("Software caused connection abort", ErrorCode.CONNECTION_ABORTED),
                    Map.entry("Connection timed out", ErrorCode.TIMEOUT),
                    Map.entry("Operation timed out", ErrorCode.TIMEOUT),
                    Map.entry("Network is unreachable", ErrorCode.REMOTE_UNREACHABLE),
                    Map.entry("Host is unreachable", ErrorCode.REMOTE_UNREACHABLE),
                    Map.entry("No route to host", ErrorCode.REMOTE_UNREACHABLE),
                    Map.entry("Message too long", ErrorCode.DATAGRAM_TOO_LARGE),
                    Map.entry("Permission denied", ErrorCode.ACCESS_DENIED),
                    Map.entry("Operation not permitted", ErrorCode.ACCESS_DENIED),
                    Map.entry("Too many open files", ErrorCode.NEW_SOCKET_LIMIT),
                    Map.entry("Address family not supported", ErrorCode.NOT_SUPPORTED),
                    Map.entry("Protocol family unavailable", ErrorCode.NOT_SUPPORTED),
                    Map.entry("Socket is not connected", ErrorCode.INVALID_STATE),
                    Map.entry("Transport endpoint is not connected", ErrorCode.INVALID_STATE),
                    Map.entry("No buffer space available", ErrorCode.OUT_OF_MEMORY),
                    Map.entry("Cannot allocate memory", ErrorCode.OUT_OF_MEMORY),
                    Map.entry("Invalid argument", ErrorCode.INVALID_ARGUMENT));

    /** What Netty's native transports put before the host's message, such as {@code bind(..)}. */
    private static final Pattern NATIVE_CALL =
            Pattern.compile("^\\w+\\(\\.\\.\\) failed with error\\(-?\\d+\\): ");

    private ErrorCodes() {}

    public static ErrorCodeException error(ErrorCode code) {
        return new ErrorCodeException(code);
    }

    /** The operation failed because of {@code cause}, reported as {@code unknown} when no code fits. */
    public static ErrorCodeException failed(Throwable cause) {
        ErrorCode code = codeOf(cause);
        return error(code == null ? ErrorCode.UNKNOWN : code);
    }

    /** The code {@code cause} stands for, or {@code null} when it is not a network failure. */
    public static ErrorCode codeOf(Throwable cause) {
        if (cause instanceof UncheckedIOException
                || cause instanceof CompletionException
                || cause instanceof ExecutionException
                || cause instanceof InvocationTargetException) {
            return cause.getCause() == null ? null : codeOf(cause.getCause());
        }
        if (cause instanceof UnknownHostException) {
            return ErrorCode.NAME_UNRESOLVABLE;
        }
        if (cause instanceof NoRouteToHostException) {
            return ErrorCode.REMOTE_UNREACHABLE;
        }
        if (cause instanceof PortUnreachableException) {
            return ErrorCode.CONNECTION_REFUSED;
        }
        if (cause instanceof SocketTimeoutException) {
            return ErrorCode.TIMEOUT;
        }
        if (cause instanceof ClosedChannelException
                || cause instanceof AlreadyBoundException
                || cause instanceof AlreadyConnectedException
                || cause instanceof NotYetBoundException
                || cause instanceof NotYetConnectedException) {
            return ErrorCode.INVALID_STATE;
        }
        if (cause instanceof ConnectionPendingException) {
            return ErrorCode.CONCURRENCY_CONFLICT;
        }
        if (cause instanceof UnresolvedAddressException
                || cause instanceof UnsupportedAddressTypeException) {
            return ErrorCode.INVALID_ARGUMENT;
        }
        if (cause instanceof SecurityException) {
            return ErrorCode.ACCESS_DENIED;
        }
        if (cause instanceof UnsupportedOperationException) {
            return ErrorCode.NOT_SUPPORTED;
        }
        if (!(cause instanceof IOException)) {
            return null;
        }
        ErrorCode code = reasonCode(cause.getMessage());
        if (code == null && cause.getCause() != null) {
            code = codeOf(cause.getCause());
        }
        if (code != null) {
            return code;
        }
        if (cause instanceof BindException) {
            return ErrorCode.ADDRESS_IN_USE;
        }
        if (cause instanceof ConnectException) {
            return ErrorCode.CONNECTION_REFUSED;
        }
        return ErrorCode.UNKNOWN;
    }

    /** The code a host message stands for, which the JDK or Netty may extend with details. */
    private static ErrorCode reasonCode(String reason) {
        if (reason == null) {
            return null;
        }
        String message = NATIVE_CALL.matcher(reason).replaceFirst("");
        for (Map.Entry<String, ErrorCode> known : REASONS.entrySet()) {
            if (message.startsWith(known.getKey())) {
                return known.getValue();
            }
        }
        return null;
    }
}
