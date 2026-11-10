package run.endive.cm.wasi.p2.sockets.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;

class ErrorCodesTest {

    @Test
    void eachNetworkExceptionHasItsCode() {
        assertEquals(ErrorCode.NAME_UNRESOLVABLE, ErrorCodes.codeOf(new UnknownHostException()));
        assertEquals(ErrorCode.REMOTE_UNREACHABLE, ErrorCodes.codeOf(new NoRouteToHostException()));
        assertEquals(
                ErrorCode.CONNECTION_REFUSED, ErrorCodes.codeOf(new PortUnreachableException()));
        assertEquals(ErrorCode.TIMEOUT, ErrorCodes.codeOf(new SocketTimeoutException()));
        assertEquals(ErrorCode.INVALID_STATE, ErrorCodes.codeOf(new ClosedChannelException()));
        assertEquals(ErrorCode.INVALID_STATE, ErrorCodes.codeOf(new AlreadyConnectedException()));
        assertEquals(
                ErrorCode.CONCURRENCY_CONFLICT,
                ErrorCodes.codeOf(new ConnectionPendingException()));
        assertEquals(
                ErrorCode.INVALID_ARGUMENT, ErrorCodes.codeOf(new UnresolvedAddressException()));
        assertEquals(ErrorCode.ACCESS_DENIED, ErrorCodes.codeOf(new SecurityException()));
        assertEquals(
                ErrorCode.NOT_SUPPORTED, ErrorCodes.codeOf(new UnsupportedOperationException()));
    }

    /** The JDK and Netty report these only through the host's message, sometimes extended. */
    @Test
    void aHostMessageHasItsCode() {
        assertEquals(
                ErrorCode.ADDRESS_NOT_BINDABLE,
                ErrorCodes.codeOf(new BindException("Cannot assign requested address")));
        assertEquals(
                ErrorCode.ADDRESS_NOT_BINDABLE,
                ErrorCodes.codeOf(new BindException("Can't assign requested address")));
        assertEquals(
                ErrorCode.CONNECTION_RESET,
                ErrorCodes.codeOf(new IOException("Connection reset by peer")));
        assertEquals(
                ErrorCode.CONNECTION_RESET, ErrorCodes.codeOf(new SocketException("Broken pipe")));
        assertEquals(
                ErrorCode.CONNECTION_ABORTED,
                ErrorCodes.codeOf(new IOException("Software caused connection abort")));
        assertEquals(
                ErrorCode.TIMEOUT,
                ErrorCodes.codeOf(new ConnectException("Operation timed out: /192.0.2.1:80")));
        assertEquals(
                ErrorCode.REMOTE_UNREACHABLE,
                ErrorCodes.codeOf(new SocketException("Network is unreachable")));
        assertEquals(
                ErrorCode.DATAGRAM_TOO_LARGE,
                ErrorCodes.codeOf(new IOException("Message too long")));
        assertEquals(
                ErrorCode.NEW_SOCKET_LIMIT,
                ErrorCodes.codeOf(new SocketException("Too many open files")));
        assertEquals(
                ErrorCode.NOT_SUPPORTED,
                ErrorCodes.codeOf(new SocketException("Protocol family unavailable")));
    }

    @Test
    void aNativeTransportsMessageHasItsCode() {
        assertEquals(
                ErrorCode.ADDRESS_IN_USE,
                ErrorCodes.codeOf(
                        new IOException(
                                "bind(..) failed with error(-98): Address already in use")));
        assertEquals(
                ErrorCode.TIMEOUT,
                ErrorCodes.codeOf(
                        new ConnectException(
                                "connect(..) failed with error(-110): Connection timed out")));
    }

    @Test
    void aBindOrConnectFailureWithoutAKnownMessageHasItsUsualCode() {
        assertEquals(ErrorCode.ADDRESS_IN_USE, ErrorCodes.codeOf(new BindException()));
        assertEquals(
                ErrorCode.ADDRESS_IN_USE,
                ErrorCodes.codeOf(new BindException("Address already in use: bind")));
        assertEquals(ErrorCode.CONNECTION_REFUSED, ErrorCodes.codeOf(new ConnectException()));
    }

    @Test
    void aWrappedFailureHasTheCodeOfItsCause() {
        assertEquals(
                ErrorCode.CONNECTION_REFUSED,
                ErrorCodes.codeOf(new CompletionException(new ConnectException())));
        assertEquals(
                ErrorCode.TIMEOUT,
                ErrorCodes.codeOf(new UncheckedIOException(new SocketTimeoutException())));
    }

    /** Netty opens NIO channels of a family through reflection, wrapping what the JDK throws. */
    @Test
    void aFailureNettyWrappedHasTheCodeOfItsCause() {
        assertEquals(
                ErrorCode.NOT_SUPPORTED,
                ErrorCodes.codeOf(
                        new IOException(
                                new InvocationTargetException(
                                        new UnsupportedOperationException("IPv6 not available")))));
    }

    @Test
    void anyOtherIoFailureIsUnknown() {
        assertEquals(ErrorCode.UNKNOWN, ErrorCodes.codeOf(new IOException("cable on fire")));
    }

    @Test
    void aFailureOutsideTheNetworkHasNoCode() {
        assertNull(ErrorCodes.codeOf(new IllegalStateException()));
        assertNull(ErrorCodes.codeOf(new CompletionException(null)));
    }

    @Test
    void failingWithoutACodeReportsUnknown() {
        assertEquals(ErrorCode.UNKNOWN, ErrorCodes.failed(new IllegalStateException()).error());
        assertEquals(ErrorCode.TIMEOUT, ErrorCodes.failed(new SocketTimeoutException()).error());
    }
}
