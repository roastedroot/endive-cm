package run.endive.cm.wasi.p2.sockets.tcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.util.List;
import jdk.net.ExtendedSocketOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import run.endive.cm.runtime.Tuple2;
import run.endive.cm.runtime.Tuple3;
import run.endive.cm.runtime.Tuple8;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.InputStream;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.OutputStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpSocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Ipv6SocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.tcp.ShutdownType;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.tcp.TcpSocket;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.HostNetwork;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;

class HostTcpSocketTest {

    private static final HostNetwork NETWORK = new HostNetwork(NetworkPolicy.allowAll());
    private static final IpSocketAddress LOCAL = address("127.0.0.1", 8080);
    private static final IpSocketAddress REMOTE = address("192.0.2.1", 80);

    private final HostPoll poll = new HostPoll();
    private final TestTcpChannel channel = new TestTcpChannel(poll);
    private final HostTcpSocket socket = new HostTcpSocket(poll, IpAddressFamily.IPV4, channel);

    @Test
    void aBindFinishesOnceTheChannelIsBound() {
        socket.startBind(NETWORK, LOCAL);
        Pollable ready = socket.subscribe();

        assertFalse(ready.ready());
        assertEquals(ErrorCode.WOULD_BLOCK, errorOf(socket::finishBind));

        channel.bound.complete(null);

        assertTrue(ready.ready());
        socket.finishBind();
        assertEquals(LOCAL, socket.localAddress());
        assertEquals(List.of("bind /127.0.0.1:8080"), channel.calls);
    }

    @Test
    void aFailedBindLeavesTheSocketUnboundToTryAgain() {
        socket.startBind(NETWORK, LOCAL);
        channel.bound.completeExceptionally(new BindException("Address already in use"));

        assertEquals(ErrorCode.ADDRESS_IN_USE, errorOf(socket::finishBind));
        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::localAddress));

        socket.startBind(NETWORK, address("127.0.0.1", 0));
        assertEquals(2, channel.calls.size());
    }

    @Test
    void finishingWhatWasNeverStartedIsNotInProgress() {
        assertEquals(ErrorCode.NOT_IN_PROGRESS, errorOf(socket::finishBind));
        assertEquals(ErrorCode.NOT_IN_PROGRESS, errorOf(socket::finishConnect));
        assertEquals(ErrorCode.NOT_IN_PROGRESS, errorOf(socket::finishListen));
    }

    @Test
    void aSocketIsBoundOnce() {
        socket.startBind(NETWORK, LOCAL);

        assertEquals(ErrorCode.INVALID_STATE, errorOf(() -> socket.startBind(NETWORK, LOCAL)));

        channel.bound.complete(null);
        socket.finishBind();

        assertEquals(ErrorCode.INVALID_STATE, errorOf(() -> socket.startBind(NETWORK, LOCAL)));
    }

    /** A refused address leaves the socket unbound, so it may still be bound. */
    @Test
    void aSocketIsBoundOnlyToAUnicastAddressOfItsFamily() {
        assertEquals(
                ErrorCode.INVALID_ARGUMENT,
                errorOf(() -> socket.startBind(NETWORK, address("::1", 8080))));
        assertEquals(
                ErrorCode.INVALID_ARGUMENT,
                errorOf(() -> socket.startBind(NETWORK, address("224.0.0.1", 8080))));
        assertEquals(
                ErrorCode.INVALID_ARGUMENT,
                errorOf(() -> socket.startBind(NETWORK, address("255.255.255.255", 8080))));

        socket.startBind(NETWORK, address("0.0.0.0", 0));
    }

    @Test
    void anIpv6SocketIsNotBoundToAnIpv4MappedAddress() {
        HostTcpSocket ipv6 = new HostTcpSocket(poll, IpAddressFamily.IPV6, channel);
        IpSocketAddress mapped =
                new IpSocketAddress.Ipv6(
                        new Ipv6SocketAddress(
                                8080, 0L, Tuple8.of(0, 0, 0, 0, 0, 0xffff, 0x7f00, 1), 0L));

        assertEquals(ErrorCode.INVALID_ARGUMENT, errorOf(() -> ipv6.startBind(NETWORK, mapped)));
    }

    @Test
    void thePolicyDecidesWhichAddressesMayBeBound() {
        HostNetwork connectOnly =
                new HostNetwork(
                        NetworkPolicy.builder()
                                .allowTcp()
                                .allowAddresses(
                                        (address, use) ->
                                                use == NetworkPolicy.AddressUse.TCP_CONNECT)
                                .build());

        assertEquals(ErrorCode.ACCESS_DENIED, errorOf(() -> socket.startBind(connectOnly, LOCAL)));
        assertEquals(List.of(), channel.calls);
    }

    @Test
    void aConnectHandsOverTheStreamsOnceConnected() {
        socket.startConnect(NETWORK, REMOTE);
        Pollable ready = socket.subscribe();

        assertFalse(ready.ready());
        assertEquals(ErrorCode.WOULD_BLOCK, errorOf(socket::finishConnect));

        channel.connected.complete(null);
        Tuple2<InputStream, OutputStream> streams = socket.finishConnect();

        assertTrue(ready.ready());
        assertSame(channel.input(), streams.f0());
        assertSame(channel.output(), streams.f1());
        assertEquals(REMOTE, socket.remoteAddress());
    }

    @Test
    void aFailedConnectClosesTheSocket() {
        socket.startConnect(NETWORK, REMOTE);
        channel.connected.completeExceptionally(new ConnectException("Connection refused"));

        assertEquals(ErrorCode.CONNECTION_REFUSED, errorOf(socket::finishConnect));
        assertTrue(channel.closed);
        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::keepAliveEnabled));
        assertEquals(ErrorCode.INVALID_STATE, errorOf(() -> socket.startConnect(NETWORK, REMOTE)));
    }

    @Test
    void aConnectToTheWildcardAddressOrPortZeroClosesTheSocket() {
        assertEquals(
                ErrorCode.INVALID_ARGUMENT,
                errorOf(() -> socket.startConnect(NETWORK, address("0.0.0.0", 80))));
        assertTrue(channel.closed);

        TestTcpChannel other = new TestTcpChannel(poll);
        HostTcpSocket portZero = new HostTcpSocket(poll, IpAddressFamily.IPV4, other);
        assertEquals(
                ErrorCode.INVALID_ARGUMENT,
                errorOf(() -> portZero.startConnect(NETWORK, address("192.0.2.1", 0))));
        assertTrue(other.closed);
    }

    @Test
    void aConnectThePolicyDeniesClosesTheSocket() {
        HostNetwork bindOnly =
                new HostNetwork(
                        NetworkPolicy.builder()
                                .allowTcp()
                                .allowAddresses(
                                        (address, use) -> use == NetworkPolicy.AddressUse.TCP_BIND)
                                .build());

        assertEquals(ErrorCode.ACCESS_DENIED, errorOf(() -> socket.startConnect(bindOnly, REMOTE)));
        assertTrue(channel.closed);
        assertEquals(List.of(), channel.calls);
    }

    @Test
    void aBoundSocketConnectsOnlyOnTheNetworkItWasBoundOn() {
        bind();
        HostNetwork other = new HostNetwork(NetworkPolicy.allowAll());

        assertEquals(ErrorCode.INVALID_ARGUMENT, errorOf(() -> socket.startConnect(other, REMOTE)));
    }

    /** A call in the wrong state is refused without changing the state. */
    @Test
    void aConnectWhileBindingLeavesTheBindInProgress() {
        socket.startBind(NETWORK, LOCAL);

        assertEquals(ErrorCode.INVALID_STATE, errorOf(() -> socket.startConnect(NETWORK, REMOTE)));

        assertFalse(channel.closed);
        channel.bound.complete(null);
        socket.finishBind();
    }

    @Test
    void onlyABoundSocketListens() {
        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::startListen));
    }

    @Test
    void aListeningSocketAcceptsTheConnectionsWaiting() {
        listen();
        Pollable ready = socket.subscribe();

        assertTrue(socket.isListening());
        assertFalse(ready.ready());
        assertEquals(ErrorCode.WOULD_BLOCK, errorOf(socket::accept));

        InetSocketAddress peer = new InetSocketAddress("192.0.2.7", 40000);
        TestTcpChannel arrived = channel.arrive(peer);

        assertTrue(ready.ready());
        Tuple3<TcpSocket, InputStream, OutputStream> accepted = socket.accept();
        assertEquals(IpAddresses.fromInet(peer), accepted.f0().remoteAddress());
        assertEquals(IpAddressFamily.IPV4, accepted.f0().addressFamily());
        assertFalse(accepted.f0().isListening());
        assertSame(arrived.input(), accepted.f1());
        assertSame(arrived.output(), accepted.f2());
        assertFalse(ready.ready());
    }

    @Test
    void aFailedListenClosesTheSocket() {
        bind();
        socket.startListen();
        channel.listening.completeExceptionally(new BindException("Address already in use"));

        assertEquals(ErrorCode.ADDRESS_IN_USE, errorOf(socket::finishListen));
        assertTrue(channel.closed);
        assertFalse(socket.isListening());
    }

    @Test
    void onlyAListeningSocketAccepts() {
        bind();

        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::accept));
    }

    @Test
    void theBacklogIsTakenWhenListeningStarts() {
        socket.setListenBacklogSize(BigInteger.TEN);
        listen();

        assertEquals("listen 10", channel.calls.get(1));
        assertEquals(
                ErrorCode.NOT_SUPPORTED,
                errorOf(() -> socket.setListenBacklogSize(BigInteger.ONE)));
    }

    @Test
    void aConnectedSocketTakesNoBacklog() {
        connect();

        assertEquals(
                ErrorCode.INVALID_STATE,
                errorOf(() -> socket.setListenBacklogSize(BigInteger.ONE)));
    }

    @Test
    void theBacklogDefaultsToOneHundredTwentyEight() {
        listen();

        assertEquals("listen " + HostTcpSocket.DEFAULT_BACKLOG, channel.calls.get(1));
    }

    @Test
    void anOptionIsReadAndWrittenOnTheChannel() {
        socket.setKeepAliveEnabled(true);

        assertTrue(socket.keepAliveEnabled());
        assertEquals(true, channel.options.get(StandardSocketOptions.SO_KEEPALIVE));
        assertEquals(BigInteger.valueOf(65536), socket.receiveBufferSize());
    }

    @Test
    void keepAliveTimesAreRoundedUpToWholeSeconds() {
        socket.setKeepAliveIdleTime(BigInteger.valueOf(1_500_000_000L));
        socket.setKeepAliveInterval(BigInteger.ONE);

        assertEquals(2, channel.options.get(ExtendedSocketOptions.TCP_KEEPIDLE));
        assertEquals(BigInteger.valueOf(2_000_000_000L), socket.keepAliveIdleTime());
        assertEquals(BigInteger.valueOf(1_000_000_000L), socket.keepAliveInterval());
    }

    @Test
    void valuesTooLargeForTheHostAreClamped() {
        socket.setKeepAliveIdleTime(BigInteger.TEN.pow(30));
        socket.setKeepAliveCount(1000L);
        socket.setReceiveBufferSize(BigInteger.ONE.shiftLeft(40));

        assertEquals(
                HostTcpSocket.MAX_KEEP_ALIVE_SECONDS,
                channel.options.get(ExtendedSocketOptions.TCP_KEEPIDLE));
        assertEquals(HostTcpSocket.MAX_KEEP_ALIVE_COUNT, socket.keepAliveCount().intValue());
        assertEquals(BigInteger.valueOf(Integer.MAX_VALUE), socket.receiveBufferSize());
    }

    @Test
    void zeroIsRefusedForEveryOptionTakingAValue() {
        List<Executable> setters =
                List.of(
                        () -> socket.setListenBacklogSize(BigInteger.ZERO),
                        () -> socket.setKeepAliveIdleTime(BigInteger.ZERO),
                        () -> socket.setKeepAliveInterval(BigInteger.ZERO),
                        () -> socket.setKeepAliveCount(0L),
                        () -> socket.setHopLimit((short) 0),
                        () -> socket.setReceiveBufferSize(BigInteger.ZERO),
                        () -> socket.setSendBufferSize(BigInteger.ZERO));

        for (Executable setter : setters) {
            assertEquals(ErrorCode.INVALID_ARGUMENT, errorOf(setter));
        }
    }

    @Test
    void anOptionTheHostLacksIsNotSupported() {
        channel.options.remove(ExtendedSocketOptions.TCP_KEEPIDLE);

        assertEquals(ErrorCode.NOT_SUPPORTED, errorOf(socket::keepAliveIdleTime));
        assertEquals(ErrorCode.NOT_SUPPORTED, errorOf(socket::hopLimit));
        assertEquals(ErrorCode.NOT_SUPPORTED, errorOf(() -> socket.setHopLimit((short) 64)));
    }

    @Test
    void onlyAConnectedSocketShutsDown() {
        assertEquals(ErrorCode.INVALID_STATE, errorOf(() -> socket.shutdown(ShutdownType.BOTH)));

        connect();
        socket.shutdown(ShutdownType.SEND);
        socket.shutdown(ShutdownType.RECEIVE);

        assertEquals(
                List.of("shutdown false true", "shutdown true false"), channel.calls.subList(1, 3));
    }

    @Test
    void aSocketWithNothingInProgressIsReady() {
        assertTrue(socket.subscribe().ready());

        connect();

        assertTrue(socket.subscribe().ready());
    }

    @Test
    void anUnboundSocketHasNoAddresses() {
        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::localAddress));
        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::remoteAddress));
        assertEquals(IpAddressFamily.IPV4, socket.addressFamily());
    }

    @Test
    void droppingTheSocketClosesTheChannel() {
        socket.startConnect(NETWORK, REMOTE);

        socket.drop();

        assertTrue(channel.closed);
    }

    private void bind() {
        socket.startBind(NETWORK, LOCAL);
        channel.bound.complete(null);
        socket.finishBind();
    }

    private void listen() {
        bind();
        socket.startListen();
        channel.listening.complete(null);
        socket.finishListen();
    }

    private void connect() {
        socket.startConnect(NETWORK, REMOTE);
        channel.connected.complete(null);
        socket.finishConnect();
    }

    private static IpSocketAddress address(String ip, int port) {
        return IpAddresses.fromInet(new InetSocketAddress(ip, port));
    }

    private static ErrorCode errorOf(Executable call) {
        return assertThrows(ErrorCodeException.class, call).error();
    }
}
