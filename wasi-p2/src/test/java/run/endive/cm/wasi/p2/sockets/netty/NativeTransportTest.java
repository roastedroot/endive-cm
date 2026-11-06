package run.endive.cm.wasi.p2.sockets.netty;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringIoHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import run.endive.cm.runtime.Tuple2;
import run.endive.cm.wasi.p2.WasiPreview2;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.InputStream;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.OutputStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.ipnamelookup.ResolveAddressStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpSocketAddress;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.Network;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.tcp.TcpSocket;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.IncomingDatagram;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.IncomingDatagramStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.OutgoingDatagram;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.OutgoingDatagramStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.udp.UdpSocket;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;

/**
 * The network runs on a scheduler using one of Netty's native transports, with the channels that
 * transport needs. Each test runs only where its transport is available.
 */
class NativeTransportTest {

    private static final IpSocketAddress LOOPBACK =
            IpAddresses.fromInet(new InetSocketAddress("127.0.0.1", 0));

    @Test
    void theNetworkRunsOnEpoll() throws IOException {
        assumeTrue(Epoll.isAvailable(), "epoll is not available here");

        resolvesLocalhostOn(EpollIoHandler.newFactory());
        exchangesBytesOn(EpollIoHandler.newFactory());
        reportsAnAddressInUseOn(EpollIoHandler.newFactory());
        refusesIpv4OnIpv6On(EpollIoHandler.newFactory());
        keepsIpv4ApartFromIpv6On(EpollIoHandler.newFactory());
        setsTheHopLimitOn(EpollIoHandler.newFactory());
        readsBackBufferSizesOn(EpollIoHandler.newFactory());
        keepsIpv6ApartFromIpv4On(EpollIoHandler.newFactory());
        sendsDatagramsOn(EpollIoHandler.newFactory());
        limitsADatagramSocketAnewAtOnceOn(EpollIoHandler.newFactory());
        sendsDatagramsOnceUnlimitedOn(EpollIoHandler.newFactory());
        picksTheLocalAddressALimitUsesOn(EpollIoHandler.newFactory());
        setsTheUnicastHopLimitOn(EpollIoHandler.newFactory());
        keepsUdpIpv6ApartFromIpv4On(EpollIoHandler.newFactory());
    }

    @Test
    void theNetworkRunsOnIoUring() throws IOException {
        assumeTrue(IoUring.isAvailable(), "io_uring is not available here");

        resolvesLocalhostOn(IoUringIoHandler.newFactory());
        exchangesBytesOn(IoUringIoHandler.newFactory());
        reportsAnAddressInUseOn(IoUringIoHandler.newFactory());
        refusesIpv4OnIpv6On(IoUringIoHandler.newFactory());
        keepsIpv4ApartFromIpv6On(IoUringIoHandler.newFactory());
        setsTheHopLimitOn(IoUringIoHandler.newFactory());
        readsBackBufferSizesOn(IoUringIoHandler.newFactory());
        sendsDatagramsOn(IoUringIoHandler.newFactory());
        limitsADatagramSocketAnewAtOnceOn(IoUringIoHandler.newFactory());
        sendsDatagramsOnceUnlimitedOn(IoUringIoHandler.newFactory());
        picksTheLocalAddressALimitUsesOn(IoUringIoHandler.newFactory());
        setsTheUnicastHopLimitOn(IoUringIoHandler.newFactory());
        keepsUdpIpv6ApartFromIpv4On(IoUringIoHandler.newFactory());
    }

    @Test
    void theNetworkRunsOnKQueue() throws IOException {
        assumeTrue(KQueue.isAvailable(), "kqueue is not available here");

        resolvesLocalhostOn(KQueueIoHandler.newFactory());
        exchangesBytesOn(KQueueIoHandler.newFactory());
        reportsAnAddressInUseOn(KQueueIoHandler.newFactory());
        refusesIpv4OnIpv6On(KQueueIoHandler.newFactory());
        keepsIpv4ApartFromIpv6On(KQueueIoHandler.newFactory());
        setsTheHopLimitOn(KQueueIoHandler.newFactory());
        readsBackBufferSizesOn(KQueueIoHandler.newFactory());
        keepsIpv6ApartFromIpv4On(KQueueIoHandler.newFactory());
        sendsDatagramsOn(KQueueIoHandler.newFactory());
        limitsADatagramSocketAnewAtOnceOn(KQueueIoHandler.newFactory());
        sendsDatagramsOnceUnlimitedOn(KQueueIoHandler.newFactory());
        picksTheLocalAddressALimitUsesOn(KQueueIoHandler.newFactory());
        setsTheUnicastHopLimitOn(KQueueIoHandler.newFactory());
        keepsUdpIpv6ApartFromIpv4On(KQueueIoHandler.newFactory());
    }

    /** Netty's resolver answers localhost from the hosts file, on the transport's channels. */
    private static void resolvesLocalhostOn(IoHandlerFactory transport) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.builder().allowIpNameLookup().build())
                        .build()) {
            Pollable timeout =
                    wasi.monotonicClock().subscribeDuration(BigInteger.valueOf(1_000_000));
            ResolveAddressStream stream =
                    wasi.ipNameLookup()
                            .resolveAddresses(
                                    wasi.instanceNetwork().instanceNetwork(), "localhost");

            assertEquals(List.of(0L), wasi.poll().poll(List.of(timeout)));
            wasi.poll().poll(List.of(stream.subscribe()));
            assertNotNull(stream.resolveNextAddress());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /** A guest connects to itself over loopback, on the transport's TCP channels. */
    private static void exchangesBytesOn(IoHandlerFactory transport) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            Network network = wasi.instanceNetwork().instanceNetwork();
            TcpSocket listener = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV4);
            listener.startBind(network, LOOPBACK);
            finish(wasi, listener, listener::finishBind);
            listener.startListen();
            finish(wasi, listener, listener::finishListen);

            TcpSocket client = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV4);
            client.startConnect(network, listener.localAddress());
            OutputStream out = finish(wasi, client, client::finishConnect).f1();
            InputStream in = finish(wasi, listener, listener::accept).f1();

            out.blockingWriteAndFlush("native".getBytes(UTF_8));
            ByteArrayOutputStream read = new ByteArrayOutputStream();
            while (read.size() < 6) {
                read.writeBytes(in.blockingRead(BigInteger.valueOf(6 - read.size())));
            }
            assertEquals("native", read.toString(UTF_8));
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /** The transport reports its failures in a form the host recognizes. */
    private static void reportsAnAddressInUseOn(IoHandlerFactory transport) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                        WasiPreview2.builder()
                                .withScheduler(group)
                                .withNetworkPolicy(NetworkPolicy.allowAll())
                                .build();
                ServerSocket taken = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            TcpSocket socket = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV4);
            socket.startBind(
                    wasi.instanceNetwork().instanceNetwork(),
                    IpAddresses.fromInet(new InetSocketAddress("127.0.0.1", taken.getLocalPort())));
            ErrorCodeException e =
                    assertThrows(
                            ErrorCodeException.class,
                            () -> finish(wasi, socket, socket::finishBind));

            assertEquals(ErrorCode.ADDRESS_IN_USE, e.error());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /** An IPv4 socket on the wildcard address stays IPv4, and its guest sees no IPv6 peer. */
    private static void keepsIpv4ApartFromIpv6On(IoHandlerFactory transport) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            TcpSocket listener = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV4);
            IpSocketAddress wildcard = IpAddresses.fromInet(new InetSocketAddress("0.0.0.0", 0));
            listener.startBind(wasi.instanceNetwork().instanceNetwork(), wildcard);
            finish(wasi, listener, listener::finishBind);
            listener.startListen();
            finish(wasi, listener, listener::finishListen);
            InetSocketAddress bound = IpAddresses.toInet(listener.localAddress());
            assertEquals(new InetSocketAddress("0.0.0.0", bound.getPort()), bound);

            if (NettyTcpTest.hasIpv6Loopback()) {
                try (Socket ipv6 = new Socket("::1", bound.getPort())) {
                    assertEquals(-1, ipv6.getInputStream().read());
                } catch (IOException e) {
                    assertInstanceOf(SocketException.class, e);
                }
            }
            try (Socket ipv4 = new Socket("127.0.0.1", bound.getPort())) {
                TcpSocket accepted = finish(wasi, listener, listener::accept).f0();
                assertEquals(
                        new InetSocketAddress("127.0.0.1", ipv4.getLocalPort()),
                        IpAddresses.toInet(accepted.remoteAddress()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /** A buffer size reads back as it was set, on the listener and the connections it accepts. */
    private static void readsBackBufferSizesOn(IoHandlerFactory transport) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            Network network = wasi.instanceNetwork().instanceNetwork();
            TcpSocket listener = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV4);
            listener.setReceiveBufferSize(BigInteger.valueOf(0x10000));
            assertEquals(BigInteger.valueOf(0x10000), listener.receiveBufferSize());
            listener.startBind(network, LOOPBACK);
            finish(wasi, listener, listener::finishBind);
            listener.startListen();
            finish(wasi, listener, listener::finishListen);
            listener.setSendBufferSize(BigInteger.valueOf(0x10000));

            TcpSocket client = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV4);
            client.startConnect(network, listener.localAddress());
            finish(wasi, client, client::finishConnect);
            TcpSocket accepted = finish(wasi, listener, listener::accept).f0();

            assertEquals(BigInteger.valueOf(0x10000), listener.receiveBufferSize());
            assertEquals(BigInteger.valueOf(0x10000), accepted.receiveBufferSize());
            assertEquals(BigInteger.valueOf(0x10000), accepted.sendBufferSize());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /** A connection inherits the hop limit of the listener that accepted it. */
    private static void setsTheHopLimitOn(IoHandlerFactory transport) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            Network network = wasi.instanceNetwork().instanceNetwork();
            TcpSocket listener = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV4);
            assertTrue(listener.hopLimit() > 0);
            listener.setHopLimit((short) 42);
            listener.startBind(network, LOOPBACK);
            finish(wasi, listener, listener::finishBind);
            listener.startListen();
            finish(wasi, listener, listener::finishListen);

            TcpSocket client = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV4);
            client.startConnect(network, listener.localAddress());
            finish(wasi, client, client::finishConnect);
            TcpSocket accepted = finish(wasi, listener, listener::accept).f0();

            assertEquals((short) 42, listener.hopLimit());
            assertEquals((short) 42, accepted.hopLimit());
            if (NettyTcpTest.hasIpv6Loopback()) {
                TcpSocket ipv6 = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV6);
                assertTrue(ipv6.hopLimit() > 0);
                ipv6.setHopLimit((short) 255);
                assertEquals((short) 255, ipv6.hopLimit());
            }
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /** An IPv6 listener on the wildcard address takes no IPv4 connection. */
    private static void refusesIpv4OnIpv6On(IoHandlerFactory transport) {
        if (!NettyTcpTest.hasIpv6Loopback()) {
            return;
        }
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            TcpSocket listener = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV6);
            listener.startBind(
                    wasi.instanceNetwork().instanceNetwork(),
                    IpAddresses.fromInet(new InetSocketAddress("::", 0)));
            finish(wasi, listener, listener::finishBind);
            listener.startListen();
            finish(wasi, listener, listener::finishListen);
            int port = IpAddresses.toInet(listener.localAddress()).getPort();

            assertThrows(ConnectException.class, () -> new Socket("127.0.0.1", port).close());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * An IPv6 socket leaves IPv4 the same port. Not on io_uring, whose IPv4 sockets are IPv6 ones
     * that Linux binds as taking IPv4 traffic.
     */
    private static void keepsIpv6ApartFromIpv4On(IoHandlerFactory transport) {
        if (!NettyTcpTest.hasIpv6Loopback()) {
            return;
        }
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            Network network = wasi.instanceNetwork().instanceNetwork();
            TcpSocket ipv6 = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV6);
            ipv6.startBind(network, IpAddresses.fromInet(new InetSocketAddress("::", 0)));
            finish(wasi, ipv6, ipv6::finishBind);
            ipv6.startListen();
            finish(wasi, ipv6, ipv6::finishListen);
            int port = IpAddresses.toInet(ipv6.localAddress()).getPort();

            TcpSocket ipv4 = wasi.tcpCreateSocket().createTcpSocket(IpAddressFamily.IPV4);
            ipv4.startBind(network, IpAddresses.fromInet(new InetSocketAddress("0.0.0.0", port)));
            finish(wasi, ipv4, ipv4::finishBind);
            ipv4.startListen();
            finish(wasi, ipv4, ipv4::finishListen);
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * A guest socket limited to another sends it a datagram over loopback, on the transport's UDP
     * channels.
     */
    private static void sendsDatagramsOn(IoHandlerFactory transport) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            UdpSocket receiver = bound(wasi, IpAddressFamily.IPV4, LOOPBACK);
            UdpSocket sender = bound(wasi, IpAddressFamily.IPV4, LOOPBACK);
            OutgoingDatagramStream out = sender.stream(receiver.localAddress()).f1();
            IncomingDatagramStream in = receiver.stream(null).f0();

            while (out.checkSend().signum() == 0) {
                wasi.poll().poll(List.of(out.subscribe()));
            }
            out.send(List.of(new OutgoingDatagram("native".getBytes(UTF_8), null)));
            IncomingDatagram received = receiveOne(wasi, in);

            assertEquals("native", new String(received.data(), UTF_8));
            assertEquals(sender.localAddress(), received.remoteAddress());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /** Limiting a socket bound to the wildcard address picks its source address. */
    private static void picksTheLocalAddressALimitUsesOn(IoHandlerFactory transport) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            UdpSocket socket =
                    bound(
                            wasi,
                            IpAddressFamily.IPV4,
                            IpAddresses.fromInet(new InetSocketAddress("0.0.0.0", 0)));
            UdpSocket peer = bound(wasi, IpAddressFamily.IPV4, LOOPBACK);
            int port = IpAddresses.toInet(socket.localAddress()).getPort();

            socket.stream(peer.localAddress());

            assertEquals(
                    IpAddresses.fromInet(new InetSocketAddress("127.0.0.1", port)),
                    socket.localAddress());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /** A socket limited to one remote and then to none sends and receives anywhere again. */
    private static void sendsDatagramsOnceUnlimitedOn(IoHandlerFactory transport)
            throws IOException {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            UdpSocket socket = bound(wasi, IpAddressFamily.IPV4, freeLoopbackPort());
            UdpSocket first = bound(wasi, IpAddressFamily.IPV4, LOOPBACK);
            UdpSocket second = bound(wasi, IpAddressFamily.IPV4, LOOPBACK);
            Tuple2<IncomingDatagramStream, OutgoingDatagramStream> limited =
                    socket.stream(first.localAddress());
            send(wasi, limited.f1(), "limited", null);
            limited.f0().drop();
            limited.f1().drop();
            Tuple2<IncomingDatagramStream, OutgoingDatagramStream> unlimited = socket.stream(null);
            send(wasi, unlimited.f1(), "unlimited", second.localAddress());

            Tuple2<IncomingDatagramStream, OutgoingDatagramStream> secondStreams =
                    second.stream(null);
            IncomingDatagram received = receiveOne(wasi, secondStreams.f0());
            send(wasi, secondStreams.f1(), "reply", received.remoteAddress());

            assertEquals("unlimited", new String(received.data(), UTF_8));
            assertEquals("reply", new String(receiveOne(wasi, unlimited.f0()).data(), UTF_8));
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * A socket limited anew while the event loop is still busy is limited as last asked. io_uring
     * connects a datagram channel asynchronously.
     */
    private static void limitsADatagramSocketAnewAtOnceOn(IoHandlerFactory transport)
            throws IOException {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        CountDownLatch busy = new CountDownLatch(1);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            UdpSocket socket = bound(wasi, IpAddressFamily.IPV4, freeLoopbackPort());
            UdpSocket peer = bound(wasi, IpAddressFamily.IPV4, LOOPBACK);
            group.execute(busy::countDown);
            group.execute(() -> sleep(100));
            busy.await();
            for (IpSocketAddress remote :
                    Arrays.asList(peer.localAddress(), null, peer.localAddress())) {
                Tuple2<IncomingDatagramStream, OutgoingDatagramStream> streams =
                        socket.stream(remote);
                streams.f0().drop();
                streams.f1().drop();
            }
            Tuple2<IncomingDatagramStream, OutgoingDatagramStream> limited =
                    socket.stream(peer.localAddress());
            Tuple2<IncomingDatagramStream, OutgoingDatagramStream> peerStreams = peer.stream(null);
            send(wasi, limited.f1(), "hello", null);
            IncomingDatagram hello = receiveOne(wasi, peerStreams.f0());
            send(wasi, peerStreams.f1(), "reply", hello.remoteAddress());

            assertEquals("reply", new String(receiveOne(wasi, limited.f0()).data(), UTF_8));
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void setsTheUnicastHopLimitOn(IoHandlerFactory transport) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                WasiPreview2.builder()
                        .withScheduler(group)
                        .withNetworkPolicy(NetworkPolicy.allowAll())
                        .build()) {
            UdpSocket ipv4 = wasi.udpCreateSocket().createUdpSocket(IpAddressFamily.IPV4);
            assertTrue(ipv4.unicastHopLimit() > 0);
            ipv4.setUnicastHopLimit((short) 42);
            assertEquals((short) 42, ipv4.unicastHopLimit());
            if (NettyTcpTest.hasIpv6Loopback()) {
                UdpSocket ipv6 = wasi.udpCreateSocket().createUdpSocket(IpAddressFamily.IPV6);
                assertTrue(ipv6.unicastHopLimit() > 0);
                ipv6.setUnicastHopLimit((short) 255);
                assertEquals((short) 255, ipv6.unicastHopLimit());
            }
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * A UDP socket on IPv6's wildcard address leaves IPv4 the same port, and the IPv4 datagrams
     * sent to it.
     */
    private static void keepsUdpIpv6ApartFromIpv4On(IoHandlerFactory transport) {
        if (!NettyTcpTest.hasIpv6Loopback()) {
            return;
        }
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport);
        try (var wasi =
                        WasiPreview2.builder()
                                .withScheduler(group)
                                .withNetworkPolicy(NetworkPolicy.allowAll())
                                .build();
                DatagramSocket host = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            UdpSocket ipv6 =
                    bound(
                            wasi,
                            IpAddressFamily.IPV6,
                            IpAddresses.fromInet(new InetSocketAddress("::", 0)));
            int port = IpAddresses.toInet(ipv6.localAddress()).getPort();
            UdpSocket ipv4 =
                    bound(
                            wasi,
                            IpAddressFamily.IPV4,
                            IpAddresses.fromInet(new InetSocketAddress("0.0.0.0", port)));
            IncomingDatagramStream ipv4In = ipv4.stream(null).f0();
            IncomingDatagramStream ipv6In = ipv6.stream(null).f0();

            byte[] four = "four".getBytes(UTF_8);
            host.send(
                    new DatagramPacket(
                            four, four.length, new InetSocketAddress("127.0.0.1", port)));

            assertEquals("four", new String(receiveOne(wasi, ipv4In).data(), UTF_8));
            assertTrue(ipv6In.receive(BigInteger.ONE).isEmpty());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /** A loopback address no socket holds. Linux unbinds a socket bound to port 0 as it disconnects. */
    private static IpSocketAddress freeLoopbackPort() throws IOException {
        try (DatagramSocket probe = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            return IpAddresses.fromInet((InetSocketAddress) probe.getLocalSocketAddress());
        }
    }

    private static void send(
            WasiPreview2 wasi, OutgoingDatagramStream out, String data, IpSocketAddress to) {
        while (out.checkSend().signum() == 0) {
            wasi.poll().poll(List.of(out.subscribe()));
        }
        out.send(List.of(new OutgoingDatagram(data.getBytes(UTF_8), to)));
    }

    private static IncomingDatagram receiveOne(WasiPreview2 wasi, IncomingDatagramStream in) {
        while (true) {
            List<IncomingDatagram> received = in.receive(BigInteger.ONE);
            if (!received.isEmpty()) {
                return received.get(0);
            }
            wasi.poll().poll(List.of(in.subscribe()));
        }
    }

    private static UdpSocket bound(
            WasiPreview2 wasi, IpAddressFamily family, IpSocketAddress local) {
        UdpSocket socket = wasi.udpCreateSocket().createUdpSocket(family);
        socket.startBind(wasi.instanceNetwork().instanceNetwork(), local);
        while (true) {
            try {
                socket.finishBind();
                return socket;
            } catch (ErrorCodeException e) {
                if (e.error() != ErrorCode.WOULD_BLOCK) {
                    throw e;
                }
                wasi.poll().poll(List.of(socket.subscribe()));
            }
        }
    }

    /** Retries {@code finish} each time the socket's pollable is ready, as a guest does. */
    private static <T> T finish(WasiPreview2 wasi, TcpSocket socket, Supplier<T> finish) {
        while (true) {
            try {
                return finish.get();
            } catch (ErrorCodeException e) {
                if (e.error() != ErrorCode.WOULD_BLOCK) {
                    throw e;
                }
                wasi.poll().poll(List.of(socket.subscribe()));
            }
        }
    }

    private static void finish(WasiPreview2 wasi, TcpSocket socket, Runnable finish) {
        finish(
                wasi,
                socket,
                () -> {
                    finish.run();
                    return null;
                });
    }
}
