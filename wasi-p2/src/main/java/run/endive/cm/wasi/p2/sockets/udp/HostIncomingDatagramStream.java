package run.endive.cm.wasi.p2.sockets.udp;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.IncomingDatagram;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.IncomingDatagramStream;
import run.endive.cm.wasi.p2.sockets.network.ErrorCodes;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;

/**
 * The host side of the {@code wasi:sockets} {@code incoming-datagram-stream} resource, which
 * receives the datagrams of a {@link HostUdpSocket}.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp</a>
 */
final class HostIncomingDatagramStream implements IncomingDatagramStream {

    private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

    private final HostUdpSocket socket;
    private final InetSocketAddress remote;
    private final CompletableFuture<Void> association;
    private IOException failure;

    /**
     * @param remote the only address datagrams are taken from, or {@code null} for any
     * @param association completes once the socket's channel is limited to {@code remote}
     */
    HostIncomingDatagramStream(
            HostUdpSocket socket, InetSocketAddress remote, CompletableFuture<Void> association) {
        this.socket = socket;
        this.remote = remote;
        this.association = association;
    }

    /**
     * Takes up to {@code maxResults} of the datagrams waiting, without blocking. A failure met
     * after some datagrams were taken is reported by the next call.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp, receive</a>
     */
    @Override
    public List<IncomingDatagram> receive(BigInteger maxResults) {
        requireAssociated();
        if (failure != null) {
            IOException reported = failure;
            failure = null;
            throw ErrorCodes.failed(reported);
        }
        long max = maxResults.min(BigInteger.valueOf(Integer.MAX_VALUE)).longValue();
        List<IncomingDatagram> received = new ArrayList<>();
        while (received.size() < max) {
            DatagramPacket datagram;
            try {
                datagram = socket.channel().receive();
            } catch (IOException e) {
                if (received.isEmpty()) {
                    throw ErrorCodes.failed(e);
                }
                failure = e;
                break;
            }
            if (datagram == null) {
                break;
            }
            InetSocketAddress sender = (InetSocketAddress) datagram.getSocketAddress();
            if (remote == null || remote.equals(sender)) {
                received.add(
                        new IncomingDatagram(
                                Arrays.copyOfRange(
                                        datagram.getData(),
                                        datagram.getOffset(),
                                        datagram.getOffset() + datagram.getLength()),
                                IpAddresses.fromInet(sender)));
            }
        }
        return received;
    }

    /** Ready once a datagram or a failure is waiting, or the socket is closed. */
    @Override
    public Pollable subscribe() {
        return socket.poll().pollable(this::readiness);
    }

    @Override
    public void drop() {
        socket.streamDropped();
    }

    private CompletableFuture<?> readiness() {
        if (socket.isClosed() || failure != null || association.isCompletedExceptionally()) {
            return DONE;
        }
        return association.isDone() ? socket.channel().receiveReadiness() : association;
    }

    /**
     * Refuses a closed socket with {@code invalid-state}, and reports the failure to limit it to
     * the remote. Nothing is received until it is limited.
     */
    private void requireAssociated() {
        if (socket.isClosed()) {
            throw ErrorCodes.error(ErrorCode.INVALID_STATE);
        }
        if (association.isDone()) {
            Throwable failed = HostUdpSocket.failureOf(association);
            if (failed != null) {
                throw ErrorCodes.failed(failed);
            }
        }
    }
}
