package run.endive.cm.wasi.p2.sockets.netty;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import run.endive.cm.runtime.Tuple2;
import run.endive.cm.runtime.Tuple3;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.InputStream;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.OutputStream;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamError;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamErrorException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpSocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.tcp.ShutdownType;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.tcp.TcpSocket;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.io.streams.HostOutputStream;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.HostNetwork;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;
import run.endive.cm.wasi.p2.sockets.tcp.TcpChannel;

/** Guest sockets talk to each other and to the host over loopback, on a NIO event loop. */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class NettyTcpTest {

    private static final HostNetwork NETWORK = new HostNetwork(NetworkPolicy.allowAll());
    private static final IpSocketAddress LOOPBACK = address(new InetSocketAddress("127.0.0.1", 0));

    private final HostPoll poll = new HostPoll();
    private EventLoopGroup group;
    private NettyNetwork network;

    @BeforeEach
    void start() {
        group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        network = NettyNetwork.start(poll, NetworkPolicy.allowAll(), group, null);
    }

    @AfterEach
    void stop() {
        network.close();
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
    }

    @Test
    void aClientAndAServerExchangeBytes() {
        Connection connection = connect();

        connection.clientOut.blockingWriteAndFlush("ping".getBytes(UTF_8));
        assertEquals("ping", new String(readExactly(connection.serverIn, 4), UTF_8));

        connection.serverOut.blockingWriteAndFlush("pong".getBytes(UTF_8));
        assertEquals("pong", new String(readExactly(connection.clientIn, 4), UTF_8));

        assertEquals(connection.client.localAddress(), connection.server.remoteAddress());
        assertEquals(connection.server.localAddress(), connection.client.remoteAddress());
    }

    /** The reader pauses while the guest leaves its input unread, and loses nothing. */
    @Test
    void moreThanTheStreamsHoldArrivesWhole() {
        Connection connection = connect();
        byte[] sent = new byte[5 * NettyTcpChannel.STREAM_CAPACITY + 123];
        new Random(42).nextBytes(sent);

        CompletableFuture<Void> writing =
                CompletableFuture.runAsync(() -> writeAll(connection.clientOut, sent));

        assertArrayEquals(sent, readExactly(connection.serverIn, sent.length));
        writing.join();
    }

    @Test
    void shuttingDownSendingEndsThePeersInput() {
        Connection connection = connect();

        connection.clientOut.checkWrite();
        connection.clientOut.write("last".getBytes(UTF_8));
        connection.client.shutdown(ShutdownType.SEND);

        assertEquals("last", new String(readExactly(connection.serverIn, 4), UTF_8));
        assertClosed(connection.serverIn);
        assertClosed(() -> connection.clientOut.checkWrite());

        connection.serverOut.blockingWriteAndFlush("still open".getBytes(UTF_8));
        assertEquals("still open", new String(readExactly(connection.clientIn, 10), UTF_8));
    }

    @Test
    void droppingASocketDeliversWhatWasWrittenThenCloses() {
        Connection connection = connect();

        connection.clientOut.checkWrite();
        connection.clientOut.write("bye".getBytes(UTF_8));
        connection.client.drop();

        assertEquals("bye", new String(readExactly(connection.serverIn, 3), UTF_8));
        assertClosed(connection.serverIn);
    }

    /** Shutting down does not close the socket, which stays connected while the peer is open. */
    @Test
    void shuttingDownBothDirectionsKeepsTheSocketConnected() {
        Connection connection = connect();

        connection.client.shutdown(ShutdownType.BOTH);
        assertClosed(connection.serverIn);

        connection.client.shutdown(ShutdownType.BOTH);
        assertEquals(connection.server.localAddress(), connection.client.remoteAddress());
    }

    /** A peer that stops reading cannot keep a dropped socket open. */
    @Test
    void droppingASocketClosesItAtOnceWhenThePeerStopsReading() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            TcpChannel channel = network.tcp().open(IpAddressFamily.IPV4);
            channel.connect((InetSocketAddress) server.getLocalSocketAddress())
                    .get(5, TimeUnit.SECONDS);

            try (Socket peer = server.accept()) {
                fillUntilBlocked(channel.output());
                channel.close();

                channel.terminated().get(5, TimeUnit.SECONDS);
            }
        }
    }

    /** Neither socket closes while the peer may still send, since TCP allows half closure. */
    @Test
    void bothSocketsCloseOnceEachHasStoppedSending() {
        Connection connection = connect();

        connection.client.shutdown(ShutdownType.SEND);
        assertClosed(connection.serverIn);
        assertEquals(connection.client.localAddress(), connection.server.remoteAddress());

        connection.server.shutdown(ShutdownType.SEND);
        assertClosed(connection.clientIn);

        awaitClosed(connection.client);
        awaitClosed(connection.server);
    }

    @Test
    void aSocketClosesWhenThePeerResetsTheConnection() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            TcpSocket client = create();
            client.startConnect(
                    NETWORK, address((InetSocketAddress) server.getLocalSocketAddress()));
            Tuple2<InputStream, OutputStream> streams = finish(client, client::finishConnect);

            try (Socket accepted = server.accept()) {
                accepted.setSoLinger(true, 0);
            }

            assertThrows(StreamErrorException.class, () -> readExactly(streams.f0(), 1));
            awaitClosed(client);
        }
    }

    @Test
    void aConnectionRefusedIsReported() throws IOException {
        TcpSocket client = create();

        client.startConnect(NETWORK, address(new InetSocketAddress("127.0.0.1", freePort())));
        ErrorCodeException e =
                assertThrows(ErrorCodeException.class, () -> finish(client, client::finishConnect));

        assertEquals(ErrorCode.CONNECTION_REFUSED, e.error());
    }

    @Test
    void anAddressInUseIsNotBound() throws IOException {
        try (ServerSocket taken = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            TcpSocket socket = create();

            socket.startBind(
                    NETWORK, address(new InetSocketAddress("127.0.0.1", taken.getLocalPort())));
            ErrorCodeException e =
                    assertThrows(
                            ErrorCodeException.class, () -> finish(socket, socket::finishBind));

            assertEquals(ErrorCode.ADDRESS_IN_USE, e.error());
        }
    }

    @Test
    void aListenerKeepsTheAddressItWasBoundTo() {
        TcpSocket listener = create();
        listener.startBind(NETWORK, LOOPBACK);
        finish(listener, listener::finishBind);
        IpSocketAddress bound = listener.localAddress();

        listener.startListen();
        finish(listener, listener::finishListen);

        assertEquals(bound, listener.localAddress());
        assertTrue(listener.isListening());
    }

    /** Each connection fills a backlog of one, which pauses accepting until the guest takes it. */
    @Test
    void aListenerWithABacklogOfOneKeepsAccepting() throws IOException {
        TcpSocket listener = create();
        listener.setListenBacklogSize(BigInteger.ONE);
        listener.startBind(NETWORK, LOOPBACK);
        finish(listener, listener::finishBind);
        listener.startListen();
        finish(listener, listener::finishListen);
        InetSocketAddress bound = IpAddresses.toInet(listener.localAddress());

        for (int i = 0; i < 100; i++) {
            try (Socket client = new Socket(bound.getAddress(), bound.getPort())) {
                finish(listener, listener::accept).f0().drop();
            }
        }
    }

    @Test
    void aConnectionThatEndedBeforeItWasAcceptedStillDelivers() throws IOException {
        TcpSocket listener = create();
        listener.startBind(NETWORK, LOOPBACK);
        finish(listener, listener::finishBind);
        listener.startListen();
        finish(listener, listener::finishListen);
        InetSocketAddress bound = IpAddresses.toInet(listener.localAddress());

        try (Socket client = new Socket(bound.getAddress(), bound.getPort())) {
            client.getOutputStream().write("early".getBytes(UTF_8));
        }
        InputStream in = finish(listener, listener::accept).f1();

        assertEquals("early", new String(readExactly(in, 5), UTF_8));
        assertClosed(in);
    }

    /** Connecting picks the address a wildcard-bound socket sends from. */
    @Test
    void aSocketBoundToTheWildcardAddressReportsTheAddressItConnectedFrom() {
        TcpSocket listener = create();
        listener.startBind(NETWORK, LOOPBACK);
        finish(listener, listener::finishBind);
        listener.startListen();
        finish(listener, listener::finishListen);

        TcpSocket client = create();
        client.startBind(NETWORK, address(new InetSocketAddress("0.0.0.0", 0)));
        finish(client, client::finishBind);
        client.startConnect(NETWORK, listener.localAddress());
        client.localAddress();
        finish(client, client::finishConnect);
        TcpSocket accepted = finish(listener, listener::accept).f0();

        assertEquals(accepted.remoteAddress(), client.localAddress());
    }

    /** Dropping a listener frees its address at once, so the guest may bind it again. */
    @Test
    void aDroppedListenersAddressIsFreeAtOnce() {
        IpSocketAddress bound = LOOPBACK;
        for (int i = 0; i < 20; i++) {
            TcpSocket listener = create();
            listener.startBind(NETWORK, bound);
            finish(listener, listener::finishBind);
            listener.startListen();
            finish(listener, listener::finishListen);
            bound = listener.localAddress();
            listener.drop();
        }
    }

    @Test
    void anAcceptedConnectionInheritsTheListenersOptions() {
        TcpSocket listener = create();
        listener.setKeepAliveEnabled(true);
        listener.startBind(NETWORK, LOOPBACK);
        finish(listener, listener::finishBind);
        listener.startListen();
        finish(listener, listener::finishListen);
        listener.setSendBufferSize(BigInteger.valueOf(32 * 1024));

        TcpSocket client = create();
        client.startConnect(NETWORK, listener.localAddress());
        finish(client, client::finishConnect);
        TcpSocket accepted = finish(listener, listener::accept).f0();

        assertTrue(listener.keepAliveEnabled());
        assertTrue(accepted.keepAliveEnabled());
        assertTrue(accepted.sendBufferSize().compareTo(BigInteger.ZERO) > 0);
    }

    /** Linux reports twice the size it was given, which the JDK halves for NIO. */
    @Test
    void aBufferSizeReadsBackAsItWasSet() {
        TcpSocket listener = create();
        listener.setReceiveBufferSize(BigInteger.valueOf(0x10000));
        listener.startBind(NETWORK, LOOPBACK);
        finish(listener, listener::finishBind);
        listener.startListen();
        finish(listener, listener::finishListen);
        listener.setSendBufferSize(BigInteger.valueOf(0x10000));

        TcpSocket client = create();
        client.startConnect(NETWORK, listener.localAddress());
        finish(client, client::finishConnect);
        TcpSocket accepted = finish(listener, listener::accept).f0();

        assertEquals(BigInteger.valueOf(0x10000), listener.receiveBufferSize());
        assertEquals(BigInteger.valueOf(0x10000), accepted.receiveBufferSize());
        assertEquals(BigInteger.valueOf(0x10000), accepted.sendBufferSize());
    }

    /** Java offers NIO no way to read or set the hop limit of a TCP socket. */
    @Test
    void theHopLimitIsNotSupportedOnNio() {
        TcpSocket socket = create();

        assertEquals(
                ErrorCode.NOT_SUPPORTED,
                assertThrows(ErrorCodeException.class, socket::hopLimit).error());
        assertEquals(
                ErrorCode.NOT_SUPPORTED,
                assertThrows(ErrorCodeException.class, () -> socket.setHopLimit((short) 64))
                        .error());
    }

    @Test
    void closingTheNetworkClosesEverySocketLeftOpen() {
        Connection connection = connect();

        assertEquals(3, network.tcp().openCount());
        network.close();

        assertEquals(0, network.tcp().openCount());
        assertClosed(connection.serverIn);
    }

    @Test
    void droppingEverySocketLeavesNoneOpen() {
        Connection connection = connect();

        connection.client.drop();
        connection.server.drop();
        connection.listener.drop();

        assertEquals(0, network.tcp().openCount());
    }

    @Test
    void anIpv6SocketConnectsOverIpv6Loopback() {
        assumeTrue(hasIpv6Loopback(), "IPv6 loopback is not available here");
        TcpSocket listener = network.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV6);
        listener.startBind(NETWORK, address(new InetSocketAddress("::1", 0)));
        finish(listener, listener::finishBind);
        listener.startListen();
        finish(listener, listener::finishListen);

        TcpSocket client = network.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV6);
        client.startConnect(NETWORK, listener.localAddress());
        finish(client, client::finishConnect);
        TcpSocket accepted = finish(listener, listener::accept).f0();

        assertInstanceOf(IpSocketAddress.Ipv6.class, accepted.remoteAddress());
        assertEquals(client.localAddress(), accepted.remoteAddress());
    }

    /** WASI's IPv6 sockets take no IPv4 traffic, which a NIO listener would otherwise accept. */
    @Test
    void anIpv6ListenerTurnsAwayIpv4Peers() throws IOException {
        assumeTrue(hasIpv6Loopback(), "IPv6 loopback is not available here");
        TcpSocket listener = network.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV6);
        listener.startBind(NETWORK, address(new InetSocketAddress("::", 0)));
        finish(listener, listener::finishBind);
        listener.startListen();
        finish(listener, listener::finishListen);
        int port = IpAddresses.toInet(listener.localAddress()).getPort();

        try (Socket ipv4 = new Socket("127.0.0.1", port)) {
            assertEndedByPeer(ipv4);
        }
        try (Socket ipv6 = new Socket("::1", port)) {
            TcpSocket accepted = finish(listener, listener::accept).f0();

            assertEquals(
                    new InetSocketAddress("::1", ipv6.getLocalPort()),
                    IpAddresses.toInet(accepted.remoteAddress()));
        }
    }

    /** Whether the host has IPv6 loopback, which some CI machines lack. */
    static boolean hasIpv6Loopback() {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("::1"))) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Connection connect() {
        Connection connection = new Connection();
        connection.listener = create();
        connection.listener.startBind(NETWORK, LOOPBACK);
        finish(connection.listener, connection.listener::finishBind);
        connection.listener.startListen();
        finish(connection.listener, connection.listener::finishListen);

        connection.client = create();
        connection.client.startConnect(NETWORK, connection.listener.localAddress());
        Tuple2<InputStream, OutputStream> clientStreams =
                finish(connection.client, connection.client::finishConnect);
        connection.clientIn = clientStreams.f0();
        connection.clientOut = clientStreams.f1();

        Tuple3<TcpSocket, InputStream, OutputStream> accepted =
                finish(connection.listener, connection.listener::accept);
        connection.server = accepted.f0();
        connection.serverIn = accepted.f1();
        connection.serverOut = accepted.f2();
        return connection;
    }

    private TcpSocket create() {
        return network.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV4);
    }

    /** Retries {@code finish} each time the socket's pollable is ready, as a guest does. */
    private <T> T finish(TcpSocket socket, Supplier<T> finish) {
        while (true) {
            try {
                return finish.get();
            } catch (ErrorCodeException e) {
                if (e.error() != ErrorCode.WOULD_BLOCK) {
                    throw e;
                }
                poll.poll(List.of(socket.subscribe()));
            }
        }
    }

    private void finish(TcpSocket socket, Runnable finish) {
        finish(
                socket,
                () -> {
                    finish.run();
                    return null;
                });
    }

    /** Waits for the connection to end, which closes the socket without the guest asking. */
    private static void awaitClosed(TcpSocket socket) {
        while (true) {
            try {
                socket.remoteAddress();
            } catch (ErrorCodeException e) {
                assertEquals(ErrorCode.INVALID_STATE, e.error());
                return;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private static byte[] readExactly(InputStream in, int length) {
        ByteArrayOutputStream read = new ByteArrayOutputStream();
        while (read.size() < length) {
            read.writeBytes(in.blockingRead(BigInteger.valueOf(length - read.size())));
        }
        return read.toByteArray();
    }

    private static void writeAll(OutputStream out, byte[] bytes) {
        for (int offset = 0; offset < bytes.length; offset += 4096) {
            out.blockingWriteAndFlush(
                    Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + 4096)));
        }
    }

    /** Writes until the host takes no more, as happens once the peer stops reading. */
    private static void fillUntilBlocked(HostOutputStream out) throws Exception {
        byte[] chunk = new byte[64 * 1024];
        while (true) {
            try {
                out.readiness().get(500, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                return;
            }
            out.write(
                    Arrays.copyOf(
                            chunk, (int) Math.min(out.checkWrite().longValue(), chunk.length)));
        }
    }

    /** The peer closed the connection, gracefully or by resetting it. */
    private static void assertEndedByPeer(Socket socket) {
        try {
            assertEquals(-1, socket.getInputStream().read());
        } catch (IOException e) {
            assertInstanceOf(SocketException.class, e);
        }
    }

    private static void assertClosed(InputStream in) {
        assertClosed(() -> in.blockingRead(BigInteger.ONE));
    }

    private static void assertClosed(Executable operation) {
        StreamErrorException e = assertThrows(StreamErrorException.class, operation);
        assertInstanceOf(StreamError.Closed.class, e.error());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static IpSocketAddress address(InetSocketAddress address) {
        return IpAddresses.fromInet(address);
    }

    /** Both ends of a connection, and the listener that accepted it. */
    private static final class Connection {
        TcpSocket listener;
        TcpSocket client;
        InputStream clientIn;
        OutputStream clientOut;
        TcpSocket server;
        InputStream serverIn;
        OutputStream serverOut;
    }
}
