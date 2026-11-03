package run.endive.cm.wasi.p2.sockets.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.concurrent.CompletableFuture;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;

/**
 * One UDP socket of the host's transport, which a {@link HostUdpSocket} drives. {@link
 * HostUdpSocket} checks every call against the socket's state first, so a channel only performs
 * what the guest is allowed to ask for. No method blocks.
 *
 * <p>A failure is reported as the exception the transport met, which is turned into an {@code
 * error-code} for the guest.
 */
public interface UdpChannel {

    /** Opens the channels of a transport. */
    @FunctionalInterface
    interface Factory {

        /**
         * @throws IOException when the host cannot create another socket
         */
        UdpChannel open(IpAddressFamily family) throws IOException;
    }

    /** Starts binding to {@code local}, completing once the channel is bound. */
    CompletableFuture<Void> bind(InetSocketAddress local);

    /**
     * Starts limiting a bound channel to {@code remote}, or lifting any limit when {@code remote}
     * is {@code null}. It completes once the channel sends to and receives from only that address.
     */
    CompletableFuture<Void> connect(InetSocketAddress remote);

    /** The address the channel is bound to, or {@code null} while it is not bound. */
    InetSocketAddress localAddress();

    /**
     * The next datagram received, with the address of its sender, or {@code null} when none is
     * waiting.
     *
     * @throws IOException when the channel failed to receive, such as when the remote it is limited
     *     to refused a datagram it sent
     */
    DatagramPacket receive() throws IOException;

    /** A future that is complete while {@link #receive} would return a datagram or fail. */
    CompletableFuture<?> receiveReadiness();

    /**
     * How many datagrams the channel takes now.
     *
     * @throws IOException when a datagram sent earlier failed
     */
    int sendCapacity() throws IOException;

    /** A future that is complete while {@link #sendCapacity} would be positive or fail. */
    CompletableFuture<?> sendReadiness();

    /**
     * Queues {@code datagram} to be sent to its address. The channel keeps the datagram's array,
     * which the caller must not change afterwards. A failure is reported by {@link #sendCapacity}.
     */
    void send(DatagramPacket datagram);

    /**
     * @throws UnsupportedOperationException when the host does not support {@code option}
     */
    <T> T getOption(SocketOption<T> option) throws IOException;

    /**
     * @throws UnsupportedOperationException when the host does not support {@code option}
     */
    <T> void setOption(SocketOption<T> option, T value) throws IOException;

    /** Closes the channel, discarding what it has not sent. */
    void close();
}
