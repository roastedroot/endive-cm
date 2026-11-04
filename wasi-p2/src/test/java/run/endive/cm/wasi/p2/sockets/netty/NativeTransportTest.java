package run.endive.cm.wasi.p2.sockets.netty;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
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
    void theNetworkRunsOnEpoll() {
        assumeTrue(Epoll.isAvailable(), "epoll is not available here");

        resolvesLocalhostOn(EpollIoHandler.newFactory());
        exchangesBytesOn(EpollIoHandler.newFactory());
        reportsAnAddressInUseOn(EpollIoHandler.newFactory());
        refusesIpv4OnIpv6On(EpollIoHandler.newFactory());
        setsTheHopLimitOn(EpollIoHandler.newFactory());
        keepsIpv6ApartFromIpv4On(EpollIoHandler.newFactory());
        sendsDatagramsOn(EpollIoHandler.newFactory());
    }

    @Test
    void theNetworkRunsOnIoUring() {
        assumeTrue(IoUring.isAvailable(), "io_uring is not available here");

        resolvesLocalhostOn(IoUringIoHandler.newFactory());
        exchangesBytesOn(IoUringIoHandler.newFactory());
        reportsAnAddressInUseOn(IoUringIoHandler.newFactory());
        refusesIpv4OnIpv6On(IoUringIoHandler.newFactory());
        setsTheHopLimitOn(IoUringIoHandler.newFactory());
        sendsDatagramsOn(IoUringIoHandler.newFactory());
    }

    @Test
    void theNetworkRunsOnKQueue() {
        assumeTrue(KQueue.isAvailable(), "kqueue is not available here");

        resolvesLocalhostOn(KQueueIoHandler.newFactory());
        exchangesBytesOn(KQueueIoHandler.newFactory());
        reportsAnAddressInUseOn(KQueueIoHandler.newFactory());
        refusesIpv4OnIpv6On(KQueueIoHandler.newFactory());
        setsTheHopLimitOn(KQueueIoHandler.newFactory());
        keepsIpv6ApartFromIpv4On(KQueueIoHandler.newFactory());
        sendsDatagramsOn(KQueueIoHandler.newFactory());
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
            UdpSocket receiver = bound(wasi);
            UdpSocket sender = bound(wasi);
            OutgoingDatagramStream out = sender.stream(receiver.localAddress()).f1();
            IncomingDatagramStream in = receiver.stream(null).f0();

            while (out.checkSend().signum() == 0) {
                wasi.poll().poll(List.of(out.subscribe()));
            }
            out.send(List.of(new OutgoingDatagram("native".getBytes(UTF_8), null)));
            List<IncomingDatagram> received = in.receive(BigInteger.ONE);
            while (received.isEmpty()) {
                wasi.poll().poll(List.of(in.subscribe()));
                received = in.receive(BigInteger.ONE);
            }

            assertEquals("native", new String(received.get(0).data(), UTF_8));
            assertEquals(sender.localAddress(), received.get(0).remoteAddress());
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    private static UdpSocket bound(WasiPreview2 wasi) {
        UdpSocket socket = wasi.udpCreateSocket().createUdpSocket(IpAddressFamily.IPV4);
        socket.startBind(wasi.instanceNetwork().instanceNetwork(), LOOPBACK);
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
