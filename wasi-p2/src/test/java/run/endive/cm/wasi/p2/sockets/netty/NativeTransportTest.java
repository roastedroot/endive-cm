package run.endive.cm.wasi.p2.sockets.netty;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.math.BigInteger;
import java.net.InetSocketAddress;
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
    }

    @Test
    void theNetworkRunsOnIoUring() {
        assumeTrue(IoUring.isAvailable(), "io_uring is not available here");

        resolvesLocalhostOn(IoUringIoHandler.newFactory());
        exchangesBytesOn(IoUringIoHandler.newFactory());
    }

    @Test
    void theNetworkRunsOnKQueue() {
        assumeTrue(KQueue.isAvailable(), "kqueue is not available here");

        resolvesLocalhostOn(KQueueIoHandler.newFactory());
        exchangesBytesOn(KQueueIoHandler.newFactory());
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
