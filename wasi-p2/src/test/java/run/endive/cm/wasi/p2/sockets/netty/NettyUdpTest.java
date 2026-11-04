package run.endive.cm.wasi.p2.sockets.netty;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import run.endive.cm.runtime.Tuple2;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpSocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.IncomingDatagram;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.IncomingDatagramStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.OutgoingDatagram;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.OutgoingDatagramStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.UdpSocket;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.HostNetwork;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;

/** Guest sockets send datagrams to each other and to the host over loopback, on a NIO event loop. */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class NettyUdpTest {

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
    void twoSocketsExchangeDatagrams() {
        UdpSocket a = bound(IpAddressFamily.IPV4, LOOPBACK);
        UdpSocket b = bound(IpAddressFamily.IPV4, LOOPBACK);
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> aStreams = a.stream(null);
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> bStreams = b.stream(null);

        send(aStreams.f1(), "ping", b.localAddress());
        IncomingDatagram ping = receiveOne(bStreams.f0());
        send(bStreams.f1(), "pong", ping.remoteAddress());
        IncomingDatagram pong = receiveOne(aStreams.f0());

        assertEquals("ping", text(ping));
        assertEquals(a.localAddress(), ping.remoteAddress());
        assertEquals("pong", text(pong));
        assertEquals(b.localAddress(), pong.remoteAddress());
    }

    @Test
    void aSocketLimitedToARemoteReceivesOnlyFromIt() {
        UdpSocket limited = bound(IpAddressFamily.IPV4, LOOPBACK);
        UdpSocket peer = bound(IpAddressFamily.IPV4, LOOPBACK);
        UdpSocket other = bound(IpAddressFamily.IPV4, LOOPBACK);
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams =
                limited.stream(peer.localAddress());
        OutgoingDatagramStream peerOut = peer.stream(null).f1();
        OutgoingDatagramStream otherOut = other.stream(null).f1();

        send(otherOut, "ignored", limited.localAddress());
        send(peerOut, "wanted", limited.localAddress());
        send(streams.f1(), "reply", null);

        assertEquals("wanted", text(receiveOne(streams.f0())));
        assertEquals(peer.localAddress(), limited.remoteAddress());
    }

    @Test
    void aSocketIsLimitedAnewEachTimeItStreams() {
        UdpSocket socket = bound(IpAddressFamily.IPV4, LOOPBACK);
        UdpSocket first = bound(IpAddressFamily.IPV4, LOOPBACK);
        UdpSocket second = bound(IpAddressFamily.IPV4, LOOPBACK);
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> firstStreams =
                socket.stream(first.localAddress());
        send(firstStreams.f1(), "one", null);
        firstStreams.f0().drop();
        firstStreams.f1().drop();

        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> secondStreams =
                socket.stream(second.localAddress());
        send(secondStreams.f1(), "two", null);
        secondStreams.f0().drop();
        secondStreams.f1().drop();

        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> anyStreams = socket.stream(null);
        send(anyStreams.f1(), "three", first.localAddress());

        IncomingDatagramStream firstIn = first.stream(null).f0();
        assertEquals("one", text(receiveOne(firstIn)));
        assertEquals("three", text(receiveOne(firstIn)));
        assertEquals("two", text(receiveOne(second.stream(null).f0())));
        assertEquals(ErrorCode.INVALID_STATE, errorOf(socket::remoteAddress));
    }

    /** The host refuses a datagram to a port nothing listens on, which a limited socket hears of. */
    @Test
    void aRefusedDatagramReachesALimitedSocket() throws IOException {
        UdpSocket socket = bound(IpAddressFamily.IPV4, LOOPBACK);
        Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams =
                socket.stream(address(new InetSocketAddress("127.0.0.1", freePort())));

        send(streams.f1(), "anyone?", null);
        ErrorCodeException e =
                assertThrows(ErrorCodeException.class, () -> receiveOne(streams.f0()));

        assertEquals(ErrorCode.CONNECTION_REFUSED, e.error());
    }

    @Test
    void aGuestSocketTalksToAHostSocket() throws IOException {
        try (DatagramSocket host = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            UdpSocket socket = bound(IpAddressFamily.IPV4, LOOPBACK);
            Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams =
                    socket.stream(address((InetSocketAddress) host.getLocalSocketAddress()));

            send(streams.f1(), "hello", null);
            DatagramPacket packet = new DatagramPacket(new byte[64], 64);
            host.receive(packet);
            host.send(new DatagramPacket("HELLO".getBytes(UTF_8), 5, packet.getSocketAddress()));

            assertEquals("hello", new String(packet.getData(), 0, packet.getLength(), UTF_8));
            assertEquals("HELLO", text(receiveOne(streams.f0())));
        }
    }

    @Test
    void anIpv6SocketExchangesOverIpv6Loopback() {
        IpSocketAddress loopback = address(new InetSocketAddress("::1", 0));
        UdpSocket a = bound(IpAddressFamily.IPV6, loopback);
        UdpSocket b = bound(IpAddressFamily.IPV6, loopback);

        send(a.stream(null).f1(), "six", b.localAddress());
        IncomingDatagram received = receiveOne(b.stream(null).f0());

        assertEquals("six", text(received));
        assertTrue(received.remoteAddress() instanceof IpSocketAddress.Ipv6);
        assertEquals(a.localAddress(), received.remoteAddress());
    }

    @Test
    void bufferSizesAreSetOnTheChannel() {
        UdpSocket socket = create(IpAddressFamily.IPV4);

        socket.setReceiveBufferSize(BigInteger.valueOf(32 * 1024));

        assertTrue(socket.receiveBufferSize().compareTo(BigInteger.ZERO) > 0);
        assertTrue(socket.sendBufferSize().compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    void closingTheNetworkClosesEverySocketLeftOpen() {
        bound(IpAddressFamily.IPV4, LOOPBACK);
        create(IpAddressFamily.IPV6);

        assertEquals(2, network.udp().openCount());
        network.close();

        assertEquals(0, network.udp().openCount());
    }

    @Test
    void droppingEverySocketLeavesNoneOpen() {
        bound(IpAddressFamily.IPV4, LOOPBACK).drop();
        create(IpAddressFamily.IPV4).drop();

        assertEquals(0, network.udp().openCount());
    }

    private UdpSocket create(IpAddressFamily family) {
        return network.udpCreateSocket().createUdpSocket(family);
    }

    private UdpSocket bound(IpAddressFamily family, IpSocketAddress local) {
        UdpSocket socket = create(family);
        socket.startBind(NETWORK, local);
        while (true) {
            try {
                socket.finishBind();
                return socket;
            } catch (ErrorCodeException e) {
                if (e.error() != ErrorCode.WOULD_BLOCK) {
                    throw e;
                }
                poll.poll(List.of(socket.subscribe()));
            }
        }
    }

    /** Sends one datagram, waiting until the stream permits it. */
    private void send(OutgoingDatagramStream out, String data, IpSocketAddress to) {
        while (out.checkSend().signum() == 0) {
            poll.poll(List.of(out.subscribe()));
        }
        BigInteger sent = out.send(List.of(new OutgoingDatagram(data.getBytes(UTF_8), to)));
        assertEquals(BigInteger.ONE, sent);
    }

    /** Waits for the next datagram. */
    private IncomingDatagram receiveOne(IncomingDatagramStream in) {
        while (true) {
            List<IncomingDatagram> received = in.receive(BigInteger.ONE);
            if (!received.isEmpty()) {
                return received.get(0);
            }
            poll.poll(List.of(in.subscribe()));
        }
    }

    private static String text(IncomingDatagram datagram) {
        return new String(datagram.data(), UTF_8);
    }

    private static ErrorCode errorOf(Executable call) {
        return assertThrows(ErrorCodeException.class, call).error();
    }

    private static int freePort() throws IOException {
        try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static IpSocketAddress address(InetSocketAddress address) {
        return IpAddresses.fromInet(address);
    }
}
