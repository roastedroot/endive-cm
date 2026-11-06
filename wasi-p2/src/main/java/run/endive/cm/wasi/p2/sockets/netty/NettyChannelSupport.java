package run.endive.cm.wasi.p2.sockets.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.util.concurrent.Future;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;

/** What the network's TCP and UDP channels share in driving Netty. */
final class NettyChannelSupport {

    /** How long dropping a socket waits for its address to be free. */
    static final long CLOSE_MILLIS = 1000;

    /** How many passes of the event loop over its I/O free a closed socket's address. */
    private static final int CLOSE_PASSES = 3;

    private NettyChannelSupport() {}

    /** {@code future} as a future of the JDK, which completes as it does. */
    static CompletableFuture<Void> completable(Future<?> future) {
        CompletableFuture<Void> completable = new CompletableFuture<>();
        future.addListener(
                done -> {
                    if (done.isSuccess()) {
                        completable.complete(null);
                    } else if (done.isCancelled()) {
                        completable.cancel(false);
                    } else {
                        completable.completeExceptionally(done.cause());
                    }
                });
        return completable;
    }

    /** Completes {@code target} as {@code source} completes. */
    static void completeWith(CompletableFuture<Void> source, CompletableFuture<Void> target) {
        source.whenComplete(
                (ignored, failure) -> {
                    if (failure == null) {
                        target.complete(null);
                    } else {
                        target.completeExceptionally(failure);
                    }
                });
    }

    /** Closes {@code channel} once its registration ends, at once if it never registered. */
    static void closeOnceRegistered(Channel channel, CompletableFuture<Void> registration) {
        registration.whenComplete(
                (ignored, failure) -> {
                    if (failure == null) {
                        channel.close();
                    } else {
                        channel.unsafe().closeForcibly();
                    }
                });
    }

    /**
     * Waits a while for {@code channel}, which is closing, to free its address. NIO closes a socket
     * only on the selector's next pass, and io_uring closes one through its ring, both after the
     * channel reports it closed.
     */
    static void awaitClosed(Channel channel) {
        if (!channel.isRegistered()) {
            return;
        }
        EventLoop loop = channel.eventLoop();
        if (loop.inEventLoop() || loop.isShuttingDown()) {
            return;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLOSE_MILLIS);
        if (!channel.closeFuture().awaitUninterruptibly(CLOSE_MILLIS)) {
            return;
        }
        for (int pass = 0; pass < CLOSE_PASSES; pass++) {
            long left = deadline - System.nanoTime();
            try {
                if (left <= 0
                        || !loop.schedule(() -> {}, 1, TimeUnit.MILLISECONDS)
                                .awaitUninterruptibly(left, TimeUnit.NANOSECONDS)) {
                    return;
                }
            } catch (RejectedExecutionException e) {
                return;
            }
        }
    }

    static SocketProtocolFamily protocolFamily(IpAddressFamily family) {
        return family == IpAddressFamily.IPV4
                ? SocketProtocolFamily.INET
                : SocketProtocolFamily.INET6;
    }

    /**
     * Whether {@code peer} is of another family than the socket of {@code family} it reached. An
     * IPv6 socket on NIO takes IPv4 peers, and an IPv4 TCP socket on io_uring bound to the wildcard
     * address takes IPv6 peers.
     *
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/tcp-create-socket.wit">wasi:sockets/tcp-create-socket, create-tcp-socket</a>
     * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/sockets/wit/udp-create-socket.wit">wasi:sockets/udp-create-socket, create-udp-socket</a>
     */
    static boolean isOfTheOtherFamily(IpAddressFamily family, InetSocketAddress peer) {
        if (peer == null) {
            return false;
        }
        InetAddress ip = peer.getAddress();
        boolean ipv4 = ip instanceof Inet4Address || IpAddresses.isIpv4Mapped(ip);
        return ipv4 != (family == IpAddressFamily.IPV4);
    }

    /** What Netty wrapped, which tells the guest why the host failed. */
    static IOException unwrapped(ChannelException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        return cause instanceof IOException ? (IOException) cause : new IOException(e);
    }
}
