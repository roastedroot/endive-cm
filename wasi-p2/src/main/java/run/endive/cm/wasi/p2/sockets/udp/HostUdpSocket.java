package run.endive.cm.wasi.p2.sockets.udp;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import run.endive.cm.runtime.Tuple2;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpSocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Network;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.IncomingDatagramStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.OutgoingDatagramStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.UdpSocket;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.ErrorCodes;
import run.endive.cm.wasi.p2.sockets.network.HostNetwork;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;
import run.endive.runtime.TrapException;

/**
 * The host side of the {@code wasi:sockets} {@code udp-socket} resource, which follows the
 * socket's state and checks every call against it before passing it to a {@link UdpChannel}.
 *
 * <p>Binding, and limiting the socket to a remote address, need the network's policy to allow the
 * address. The guest calls the socket and its streams from one thread at a time.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp</a>
 */
public class HostUdpSocket implements UdpSocket {

    private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

    private enum State {
        UNBOUND,
        BIND_IN_PROGRESS,
        BOUND,
        CLOSED
    }

    private final HostPoll poll;
    private final IpAddressFamily family;
    private final UdpChannel channel;
    private State state = State.UNBOUND;
    private HostNetwork network;
    private CompletableFuture<Void> pending = DONE;
    private InetSocketAddress remote;
    private int openStreams;

    /** An unbound socket on {@code channel}, which is open and not yet bound. */
    public HostUdpSocket(HostPoll poll, IpAddressFamily family, UdpChannel channel) {
        this.poll = poll;
        this.family = family;
        this.channel = channel;
    }

    /**
     * Starts binding to {@code localAddress}. A failure leaves the socket unbound, so the guest may
     * try again.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp, start-bind</a>
     */
    @Override
    public void startBind(Network network, IpSocketAddress localAddress) {
        requireState(State.UNBOUND);
        HostNetwork host = HostNetwork.of(network);
        InetSocketAddress local = inFamily(localAddress);
        host.checkAddress(local, NetworkPolicy.AddressUse.UDP_BIND);
        this.network = host;
        pending = channel.bind(local);
        state = State.BIND_IN_PROGRESS;
    }

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp, finish-bind</a>
     */
    @Override
    public void finishBind() {
        if (state != State.BIND_IN_PROGRESS) {
            throw ErrorCodes.error(ErrorCode.NOT_IN_PROGRESS);
        }
        if (!pending.isDone()) {
            throw ErrorCodes.error(ErrorCode.WOULD_BLOCK);
        }
        Throwable failure = failureOf(pending);
        pending = DONE;
        if (failure != null) {
            state = State.UNBOUND;
            network = null;
            throw ErrorCodes.failed(failure);
        }
        state = State.BOUND;
    }

    /**
     * Hands the guest a new pair of streams, limited to {@code remoteAddress} unless it is {@code
     * null}. Datagrams flow once the channel is limited, and a failure to limit it is reported by
     * the streams. The streams of an earlier call have to be dropped first, or the guest traps.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp, stream</a>
     */
    @Override
    public Tuple2<IncomingDatagramStream, OutgoingDatagramStream> stream(
            IpSocketAddress remoteAddress) {
        requireState(State.BOUND);
        InetSocketAddress limit = null;
        if (remoteAddress != null) {
            limit = destination(remoteAddress);
            network.checkAddress(limit, NetworkPolicy.AddressUse.UDP_CONNECT);
        }
        if (openStreams > 0) {
            throw new TrapException(
                    "stream was called before the streams it returned earlier were dropped");
        }
        CompletableFuture<Void> association = channel.connect(limit);
        remote = limit;
        openStreams = 2;
        return Tuple2.of(
                new HostIncomingDatagramStream(this, limit, association),
                new HostOutgoingDatagramStream(this, limit, association));
    }

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp, local-address</a>
     */
    @Override
    public IpSocketAddress localAddress() {
        requireState(State.BOUND);
        return known(channel.localAddress());
    }

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp, remote-address</a>
     */
    @Override
    public IpSocketAddress remoteAddress() {
        requireState(State.BOUND);
        return known(remote);
    }

    @Override
    public IpAddressFamily addressFamily() {
        return family;
    }

    /** Java offers no way to read the hop limit of a UDP socket. */
    @Override
    public Short unicastHopLimit() {
        requireOpen();
        throw ErrorCodes.error(ErrorCode.NOT_SUPPORTED);
    }

    /** Java offers no way to set the hop limit of a UDP socket. */
    @Override
    public void setUnicastHopLimit(Short value) {
        requirePositive(value);
        requireOpen();
        throw ErrorCodes.error(ErrorCode.NOT_SUPPORTED);
    }

    @Override
    public BigInteger receiveBufferSize() {
        return BigInteger.valueOf(option(StandardSocketOptions.SO_RCVBUF));
    }

    /**
     * Clamped to the largest {@code int}, which the host may reduce further.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp, set-receive-buffer-size</a>
     */
    @Override
    public void setReceiveBufferSize(BigInteger value) {
        requirePositive(value.signum());
        setOption(StandardSocketOptions.SO_RCVBUF, clampToInt(value));
    }

    @Override
    public BigInteger sendBufferSize() {
        return BigInteger.valueOf(option(StandardSocketOptions.SO_SNDBUF));
    }

    /**
     * Clamped to the largest {@code int}, which the host may reduce further.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp.wit">wasi:sockets/udp, set-send-buffer-size</a>
     */
    @Override
    public void setSendBufferSize(BigInteger value) {
        requirePositive(value.signum());
        setOption(StandardSocketOptions.SO_SNDBUF, clampToInt(value));
    }

    /** Ready once a bind in progress completes. */
    @Override
    public Pollable subscribe() {
        return poll.pollable(() -> pending);
    }

    /** Closes the channel, which ends the socket's streams. */
    @Override
    public void drop() {
        state = State.CLOSED;
        pending = DONE;
        channel.close();
    }

    HostPoll poll() {
        return poll;
    }

    UdpChannel channel() {
        return channel;
    }

    HostNetwork network() {
        return network;
    }

    boolean isClosed() {
        return state == State.CLOSED;
    }

    /** Called as the guest drops each stream {@link #stream} returned. */
    void streamDropped() {
        openStreams--;
    }

    /**
     * {@code address} as a Java address a datagram may be sent to, refused with {@code
     * invalid-argument} when it is the wildcard address or port 0.
     */
    InetSocketAddress destination(IpSocketAddress address) {
        InetSocketAddress inet = inFamily(address);
        if (inet.getAddress().isAnyLocalAddress() || inet.getPort() == 0) {
            throw ErrorCodes.error(ErrorCode.INVALID_ARGUMENT);
        }
        return inet;
    }

    /** The failure {@code future} completed with, or {@code null} once it succeeded. */
    static Throwable failureOf(CompletableFuture<?> future) {
        try {
            future.join();
            return null;
        } catch (CompletionException | CancellationException e) {
            return e;
        }
    }

    /**
     * {@code address} as a Java address, refused with {@code invalid-argument} unless it is of the
     * socket's family. An IPv4-mapped IPv6 address is refused too.
     */
    private InetSocketAddress inFamily(IpSocketAddress address) {
        if (IpAddresses.familyOf(address) != family) {
            throw ErrorCodes.error(ErrorCode.INVALID_ARGUMENT);
        }
        InetSocketAddress inet = IpAddresses.toInet(address);
        if (IpAddresses.isIpv4Mapped(inet.getAddress())) {
            throw ErrorCodes.error(ErrorCode.INVALID_ARGUMENT);
        }
        return inet;
    }

    private void requireState(State... allowed) {
        if (!Arrays.asList(allowed).contains(state)) {
            throw ErrorCodes.error(ErrorCode.INVALID_STATE);
        }
    }

    private void requireOpen() {
        if (state == State.CLOSED) {
            throw ErrorCodes.error(ErrorCode.INVALID_STATE);
        }
    }

    private <T> T option(SocketOption<T> option) {
        requireOpen();
        try {
            return channel.getOption(option);
        } catch (IOException | UnsupportedOperationException e) {
            throw ErrorCodes.failed(e);
        }
    }

    private <T> void setOption(SocketOption<T> option, T value) {
        requireOpen();
        try {
            channel.setOption(option, value);
        } catch (IOException | UnsupportedOperationException e) {
            throw ErrorCodes.failed(e);
        }
    }

    private static IpSocketAddress known(InetSocketAddress address) {
        if (address == null) {
            throw ErrorCodes.error(ErrorCode.INVALID_STATE);
        }
        return IpAddresses.fromInet(address);
    }

    private static void requirePositive(long value) {
        if (value <= 0) {
            throw ErrorCodes.error(ErrorCode.INVALID_ARGUMENT);
        }
    }

    private static int clampToInt(BigInteger value) {
        return value.min(MAX_INT).intValueExact();
    }
}
