package run.endive.cm.wasi.p2.sockets.tcp;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import jdk.net.ExtendedSocketOptions;
import run.endive.cm.runtime.Tuple2;
import run.endive.cm.runtime.Tuple3;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.InputStream;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.OutputStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpSocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Network;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.tcp.ShutdownType;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.tcp.TcpSocket;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.ErrorCodes;
import run.endive.cm.wasi.p2.sockets.network.HostNetwork;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;

/**
 * The host side of the {@code wasi:sockets} {@code tcp-socket} resource, which follows the
 * socket's state and checks every call against it before passing it to a {@link TcpChannel}.
 *
 * <p>Binding and connecting need the network's policy to allow the address. The guest calls the
 * socket from one thread at a time.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp</a>
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/TcpSocketOperationalSemantics.md">Operational semantics of WASI TCP sockets</a>
 */
public class HostTcpSocket implements TcpSocket {

    /** How many connections may wait to be accepted, unless the guest asks for another number. */
    static final int DEFAULT_BACKLOG = 128;

    /** The longest keep-alive time, in seconds, that every host accepts. */
    static final int MAX_KEEP_ALIVE_SECONDS = Short.MAX_VALUE;

    /** The most keep-alive probes that every host accepts. */
    static final int MAX_KEEP_ALIVE_COUNT = Byte.MAX_VALUE;

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final byte[] LIMITED_BROADCAST = {-1, -1, -1, -1};
    private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/TcpSocketOperationalSemantics.md#states">States</a>
     */
    private enum State {
        UNBOUND,
        BIND_IN_PROGRESS,
        BOUND,
        LISTEN_IN_PROGRESS,
        LISTENING,
        CONNECT_IN_PROGRESS,
        CONNECTED,
        CLOSED
    }

    private final HostPoll poll;
    private final IpAddressFamily family;
    private final TcpChannel channel;
    private State state;
    private HostNetwork network;
    private CompletableFuture<Void> pending = DONE;
    private int backlog = DEFAULT_BACKLOG;

    /** An unbound socket on {@code channel}, which is open and not yet bound. */
    public HostTcpSocket(HostPoll poll, IpAddressFamily family, TcpChannel channel) {
        this(poll, family, channel, State.UNBOUND, null);
    }

    private HostTcpSocket(
            HostPoll poll,
            IpAddressFamily family,
            TcpChannel channel,
            State state,
            HostNetwork network) {
        this.poll = poll;
        this.family = family;
        this.channel = channel;
        this.state = state;
        this.network = network;
    }

    /**
     * Starts binding to {@code localAddress}. A failure leaves the socket unbound, so the guest may
     * try again.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, start-bind</a>
     */
    @Override
    public void startBind(Network network, IpSocketAddress localAddress) {
        requireState(State.UNBOUND);
        HostNetwork host = HostNetwork.of(network);
        InetSocketAddress local = unicast(localAddress);
        host.checkAddress(local, NetworkPolicy.AddressUse.TCP_BIND);
        this.network = host;
        pending = channel.bind(local);
        state = State.BIND_IN_PROGRESS;
    }

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, finish-bind</a>
     */
    @Override
    public void finishBind() {
        Throwable failure = outcome(State.BIND_IN_PROGRESS);
        if (failure != null) {
            state = State.UNBOUND;
            network = null;
            throw ErrorCodes.failed(failure);
        }
        state = State.BOUND;
    }

    /**
     * Starts connecting to {@code remoteAddress}, binding an unbound socket to an address the host
     * picks. Any failure other than calling it in the wrong state closes the socket.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, start-connect</a>
     */
    @Override
    public void startConnect(Network network, IpSocketAddress remoteAddress) {
        requireState(State.UNBOUND, State.BOUND);
        try {
            HostNetwork host = HostNetwork.of(network);
            if (this.network != null && this.network != host) {
                throw ErrorCodes.error(ErrorCode.INVALID_ARGUMENT);
            }
            InetSocketAddress remote = unicast(remoteAddress);
            if (remote.getAddress().isAnyLocalAddress() || remote.getPort() == 0) {
                throw ErrorCodes.error(ErrorCode.INVALID_ARGUMENT);
            }
            host.checkAddress(remote, NetworkPolicy.AddressUse.TCP_CONNECT);
            this.network = host;
            pending = channel.connect(remote);
            state = State.CONNECT_IN_PROGRESS;
        } catch (ErrorCodeException e) {
            close();
            throw e;
        }
    }

    /**
     * Hands the guest the connection's streams once it is connected. A failure closes the socket.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, finish-connect</a>
     */
    @Override
    public Tuple2<InputStream, OutputStream> finishConnect() {
        Throwable failure = outcome(State.CONNECT_IN_PROGRESS);
        if (failure != null) {
            close();
            throw ErrorCodes.failed(failure);
        }
        state = State.CONNECTED;
        return Tuple2.of(channel.input(), channel.output());
    }

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, start-listen</a>
     */
    @Override
    public void startListen() {
        requireState(State.BOUND);
        pending = channel.listen(backlog);
        state = State.LISTEN_IN_PROGRESS;
    }

    /**
     * A failure closes the socket.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, finish-listen</a>
     */
    @Override
    public void finishListen() {
        Throwable failure = outcome(State.LISTEN_IN_PROGRESS);
        if (failure != null) {
            close();
            throw ErrorCodes.failed(failure);
        }
        state = State.LISTENING;
    }

    /**
     * Hands the guest the next waiting connection, as a connected socket with its streams.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, accept</a>
     */
    @Override
    public Tuple3<TcpSocket, InputStream, OutputStream> accept() {
        requireState(State.LISTENING);
        TcpChannel accepted;
        try {
            accepted = channel.accept();
        } catch (IOException e) {
            throw ErrorCodes.failed(e);
        }
        if (accepted == null) {
            throw ErrorCodes.error(ErrorCode.WOULD_BLOCK);
        }
        TcpSocket socket = new HostTcpSocket(poll, family, accepted, State.CONNECTED, network);
        return Tuple3.of(socket, accepted.input(), accepted.output());
    }

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, local-address</a>
     */
    @Override
    public IpSocketAddress localAddress() {
        requireState(
                State.BOUND,
                State.LISTEN_IN_PROGRESS,
                State.LISTENING,
                State.CONNECT_IN_PROGRESS,
                State.CONNECTED);
        return known(channel.localAddress());
    }

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, remote-address</a>
     */
    @Override
    public IpSocketAddress remoteAddress() {
        requireState(State.CONNECTED);
        return known(channel.remoteAddress());
    }

    @Override
    public Boolean isListening() {
        return state() == State.LISTENING;
    }

    @Override
    public IpAddressFamily addressFamily() {
        return family;
    }

    /**
     * Takes effect when the socket starts listening, since the host cannot change it afterwards.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, set-listen-backlog-size</a>
     */
    @Override
    public void setListenBacklogSize(BigInteger value) {
        requirePositive(value.signum());
        if (state == State.LISTEN_IN_PROGRESS || state == State.LISTENING) {
            throw ErrorCodes.error(ErrorCode.NOT_SUPPORTED);
        }
        requireState(State.UNBOUND, State.BIND_IN_PROGRESS, State.BOUND);
        backlog = clampToInt(value);
    }

    @Override
    public Boolean keepAliveEnabled() {
        return option(StandardSocketOptions.SO_KEEPALIVE);
    }

    @Override
    public void setKeepAliveEnabled(Boolean value) {
        setOption(StandardSocketOptions.SO_KEEPALIVE, value);
    }

    @Override
    public BigInteger keepAliveIdleTime() {
        return nanos(option(ExtendedSocketOptions.TCP_KEEPIDLE));
    }

    /**
     * Rounded up to whole seconds, and clamped to {@value #MAX_KEEP_ALIVE_SECONDS} of them.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, set-keep-alive-idle-time</a>
     */
    @Override
    public void setKeepAliveIdleTime(BigInteger value) {
        setOption(ExtendedSocketOptions.TCP_KEEPIDLE, seconds(value));
    }

    @Override
    public BigInteger keepAliveInterval() {
        return nanos(option(ExtendedSocketOptions.TCP_KEEPINTERVAL));
    }

    /**
     * Rounded up to whole seconds, and clamped to {@value #MAX_KEEP_ALIVE_SECONDS} of them.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, set-keep-alive-interval</a>
     */
    @Override
    public void setKeepAliveInterval(BigInteger value) {
        setOption(ExtendedSocketOptions.TCP_KEEPINTERVAL, seconds(value));
    }

    @Override
    public Long keepAliveCount() {
        return option(ExtendedSocketOptions.TCP_KEEPCOUNT).longValue();
    }

    /**
     * Clamped to {@value #MAX_KEEP_ALIVE_COUNT}.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, set-keep-alive-count</a>
     */
    @Override
    public void setKeepAliveCount(Long value) {
        requirePositive(value);
        setOption(ExtendedSocketOptions.TCP_KEEPCOUNT, (int) Math.min(value, MAX_KEEP_ALIVE_COUNT));
    }

    /** Java offers no way to read the hop limit of a TCP socket. */
    @Override
    public Short hopLimit() {
        requireOpen();
        throw ErrorCodes.error(ErrorCode.NOT_SUPPORTED);
    }

    /** Java offers no way to set the hop limit of a TCP socket. */
    @Override
    public void setHopLimit(Short value) {
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
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, set-receive-buffer-size</a>
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
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, set-send-buffer-size</a>
     */
    @Override
    public void setSendBufferSize(BigInteger value) {
        requirePositive(value.signum());
        setOption(StandardSocketOptions.SO_SNDBUF, clampToInt(value));
    }

    /**
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/TcpSocketOperationalSemantics.md#pollable-readiness">Pollable readiness</a>
     */
    @Override
    public Pollable subscribe() {
        return poll.pollable(this::readiness);
    }

    /**
     * Closes the connection's streams in the directions given. The socket closes once both
     * directions are shut down and the peer has finished sending.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp.wit">wasi:sockets/tcp, shutdown</a>
     */
    @Override
    public void shutdown(ShutdownType shutdownType) {
        requireState(State.CONNECTED);
        channel.shutdown(shutdownType != ShutdownType.SEND, shutdownType != ShutdownType.RECEIVE);
    }

    /** Closes the channel, abandoning any operation in progress. */
    @Override
    public void drop() {
        close();
    }

    private void close() {
        state = State.CLOSED;
        pending = DONE;
        channel.close();
    }

    /** Ready once the operation in progress completes, or a connection waits to be accepted. */
    private CompletableFuture<?> readiness() {
        return state() == State.LISTENING ? channel.acceptReadiness() : pending;
    }

    /**
     * The failure of the operation {@code inProgress} started, or {@code null} once it succeeded.
     */
    private Throwable outcome(State inProgress) {
        if (state() != inProgress) {
            throw ErrorCodes.error(ErrorCode.NOT_IN_PROGRESS);
        }
        if (!pending.isDone()) {
            throw ErrorCodes.error(ErrorCode.WOULD_BLOCK);
        }
        CompletableFuture<Void> done = pending;
        pending = DONE;
        try {
            done.join();
            return null;
        } catch (CompletionException | CancellationException e) {
            return e;
        }
    }

    /**
     * The socket's state, which moves from connected to closed once the connection ends.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/TcpSocketOperationalSemantics.md#transitions">Transitions</a>
     */
    private State state() {
        if (state == State.CONNECTED && channel.terminated().isDone()) {
            state = State.CLOSED;
        }
        return state;
    }

    private void requireState(State... allowed) {
        if (!Arrays.asList(allowed).contains(state())) {
            throw ErrorCodes.error(ErrorCode.INVALID_STATE);
        }
    }

    private void requireOpen() {
        if (state() == State.CLOSED) {
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

    /**
     * {@code address} as a Java address, refused with {@code invalid-argument} unless it is a
     * unicast address of the socket's family. An IPv4-mapped IPv6 address is refused too.
     */
    private InetSocketAddress unicast(IpSocketAddress address) {
        if (IpAddresses.familyOf(address) != family) {
            throw ErrorCodes.error(ErrorCode.INVALID_ARGUMENT);
        }
        InetSocketAddress inet = IpAddresses.toInet(address);
        InetAddress ip = inet.getAddress();
        if (ip.isMulticastAddress()
                || IpAddresses.isIpv4Mapped(ip)
                || (ip instanceof Inet4Address
                        && Arrays.equals(ip.getAddress(), LIMITED_BROADCAST))) {
            throw ErrorCodes.error(ErrorCode.INVALID_ARGUMENT);
        }
        return inet;
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

    /** {@code nanos} in whole seconds, rounded up so that it never becomes zero. */
    private static int seconds(BigInteger nanos) {
        requirePositive(nanos.signum());
        BigInteger[] split = nanos.divideAndRemainder(NANOS_PER_SECOND);
        BigInteger seconds = split[1].signum() == 0 ? split[0] : split[0].add(BigInteger.ONE);
        return seconds.min(BigInteger.valueOf(MAX_KEEP_ALIVE_SECONDS)).intValueExact();
    }

    private static BigInteger nanos(int seconds) {
        return BigInteger.valueOf(seconds).multiply(NANOS_PER_SECOND);
    }
}
