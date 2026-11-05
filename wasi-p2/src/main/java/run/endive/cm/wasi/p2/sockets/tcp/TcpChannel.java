package run.endive.cm.wasi.p2.sockets.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.concurrent.CompletableFuture;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.io.streams.HostInputStream;
import run.endive.cm.wasi.p2.io.streams.HostOutputStream;

/**
 * One TCP socket of the host's transport, which a {@link HostTcpSocket} drives. {@link
 * HostTcpSocket} checks every call against the socket's state first, so a channel only performs
 * what the guest is allowed to ask for. No method blocks.
 *
 * <p>A failure is reported as the exception the transport met, which is turned into an {@code
 * error-code} for the guest.
 */
public interface TcpChannel {

    /** Opens the channels of a transport. */
    @FunctionalInterface
    interface Factory {

        /**
         * @throws IOException when the host cannot create another socket
         */
        TcpChannel open(IpAddressFamily family) throws IOException;
    }

    /** Starts binding to {@code local}, completing once the channel is bound. */
    CompletableFuture<Void> bind(InetSocketAddress local);

    /**
     * Starts connecting to {@code remote}, completing once the channel is connected. An unbound
     * channel is bound to an address the host picks.
     */
    CompletableFuture<Void> connect(InetSocketAddress remote);

    /**
     * Starts listening on the address the channel is bound to, completing once connections may
     * be accepted.
     *
     * @param backlog how many connections may wait to be accepted, which the host may adjust
     */
    CompletableFuture<Void> listen(int backlog);

    /**
     * The next connection waiting to be accepted, already connected, or {@code null} when none is
     * waiting.
     *
     * @throws IOException when a waiting connection failed before it could be accepted
     */
    TcpChannel accept() throws IOException;

    /** A future that is complete while {@link #accept} would return a connection or fail. */
    CompletableFuture<?> acceptReadiness();

    /** The address the channel is bound to, or {@code null} while it is not bound. */
    InetSocketAddress localAddress();

    /** The address the channel is connected to, or {@code null} while it is not connected. */
    InetSocketAddress remoteAddress();

    /**
     * A future that completes once a connection has ended, because it failed, the peer reset it,
     * or both of its directions were shut down.
     */
    CompletableFuture<?> terminated();

    /** The stream of what the peer sends a connected channel, the same one each time. */
    HostInputStream input();

    /** The stream of what a connected channel sends the peer, the same one each time. */
    HostOutputStream output();

    /**
     * @throws UnsupportedOperationException when the host does not support {@code option}
     */
    <T> T getOption(SocketOption<T> option) throws IOException;

    /**
     * Sets {@code option}. A listening channel applies it to the connections it accepts.
     *
     * @throws UnsupportedOperationException when the host does not support {@code option}
     */
    <T> void setOption(SocketOption<T> option, T value) throws IOException;

    /**
     * Shuts down the directions given of a connected channel. Shutting down a direction again
     * has no effect.
     */
    void shutdown(boolean receive, boolean send);

    /** Closes the channel, abandoning any operation in progress. */
    void close();
}
