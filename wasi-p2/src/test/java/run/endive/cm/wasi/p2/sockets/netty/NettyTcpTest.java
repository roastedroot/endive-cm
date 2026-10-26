package run.endive.cm.wasi.p2.sockets.netty;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.HostNetwork;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;

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
