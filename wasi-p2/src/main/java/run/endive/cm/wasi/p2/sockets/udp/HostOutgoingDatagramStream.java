package run.endive.cm.wasi.p2.sockets.udp;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.OutgoingDatagram;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.OutgoingDatagramStream;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.ErrorCodes;
import run.endive.runtime.TrapException;

/**
 * The host side of the {@code wasi:sockets} {@code outgoing-datagram-stream} resource, which sends
 * the datagrams of a {@link HostUdpSocket}.
 *
 * <p>Each datagram sent to an address of the guest's choosing needs the network's policy to allow
 * it.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp</a>
 */
final class HostOutgoingDatagramStream implements OutgoingDatagramStream {

    /** The most datagrams one {@link #checkSend} permits. */
    static final int MAX_PERMITS = 64;

    /** The largest payload of an IPv4 datagram, below the IP and UDP headers. */
    static final int MAX_IPV4_PAYLOAD = 65_507;

    /** The largest payload of an IPv6 datagram that is not a jumbogram. */
    static final int MAX_IPV6_PAYLOAD = 65_527;

    private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

    private final HostUdpSocket socket;
    private final InetSocketAddress remote;
    private final CompletableFuture<Void> association;
    private int permitted;

    /**
     * @param remote the only address datagrams are sent to, or {@code null} for any
     * @param association completes once the socket's channel is limited to {@code remote}
     */
    HostOutgoingDatagramStream(
            HostUdpSocket socket, InetSocketAddress remote, CompletableFuture<Void> association) {
        this.socket = socket;
        this.remote = remote;
        this.association = association;
    }

    /**
     * Permits as many datagrams as the channel takes now, up to {@value #MAX_PERMITS}. Nothing is
     * permitted until the socket is limited to its remote.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp, check-send</a>
     */
    @Override
    public BigInteger checkSend() {
        if (socket.isClosed()) {
            throw ErrorCodes.error(ErrorCode.INVALID_STATE);
        }
        permitted = 0;
        if (!association.isDone()) {
            return BigInteger.ZERO;
        }
        Throwable failed = HostUdpSocket.failureOf(association);
        if (failed != null) {
            throw ErrorCodes.failed(failed);
        }
        try {
            permitted = Math.min(socket.channel().sendCapacity(), MAX_PERMITS);
        } catch (IOException e) {
            throw ErrorCodes.failed(e);
        }
        return BigInteger.valueOf(permitted);
    }

    /**
     * Sends each datagram in turn until one fails. Sending more than the last {@link #checkSend}
     * permitted traps, and each call uses up the permit.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp, send</a>
     */
    @Override
    public BigInteger send(List<OutgoingDatagram> datagrams) {
        if (datagrams.isEmpty()) {
            return BigInteger.ZERO;
        }
        if (datagrams.size() > permitted) {
            throw new TrapException(
                    "send of "
                            + datagrams.size()
                            + " datagrams exceeds the "
                            + permitted
                            + " check-send permitted");
        }
        permitted = 0;
        int sent = 0;
        for (OutgoingDatagram datagram : datagrams) {
            DatagramPacket packet;
            try {
                packet = packet(datagram);
            } catch (ErrorCodeException e) {
                if (sent == 0) {
                    throw e;
                }
                break;
            }
            socket.channel().send(packet);
            sent++;
        }
        return BigInteger.valueOf(sent);
    }

    /** Ready once {@link #checkSend} would permit a datagram or fail. */
    @Override
    public Pollable subscribe() {
        return socket.poll().pollable(this::readiness);
    }

    @Override
    public void drop() {
        socket.streamDropped();
    }

    private CompletableFuture<?> readiness() {
        if (socket.isClosed() || association.isCompletedExceptionally()) {
            return DONE;
        }
        return association.isDone() ? socket.channel().sendReadiness() : association;
    }

    /** {@code datagram} as a packet addressed to where it may be sent. */
    private DatagramPacket packet(OutgoingDatagram datagram) {
        if (socket.isClosed()) {
            throw ErrorCodes.error(ErrorCode.INVALID_STATE);
        }
        InetSocketAddress target;
        if (remote != null) {
            if (datagram.remoteAddress() != null
                    && !remote.equals(socket.destination(datagram.remoteAddress()))) {
                throw ErrorCodes.error(ErrorCode.INVALID_ARGUMENT);
            }
            target = remote;
        } else {
            if (datagram.remoteAddress() == null) {
                throw ErrorCodes.error(ErrorCode.INVALID_ARGUMENT);
            }
            target = socket.destination(datagram.remoteAddress());
            socket.network().checkAddress(target, NetworkPolicy.AddressUse.UDP_OUTGOING_DATAGRAM);
        }
        byte[] data = datagram.data();
        int max =
                socket.addressFamily() == IpAddressFamily.IPV4
                        ? MAX_IPV4_PAYLOAD
                        : MAX_IPV6_PAYLOAD;
        if (data.length > max) {
            throw ErrorCodes.error(ErrorCode.DATAGRAM_TOO_LARGE);
        }
        return new DatagramPacket(data, data.length, target);
    }
}
