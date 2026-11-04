package run.endive.cm.wasi.p2.sockets.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.util.concurrent.Future;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddressFamily;

/** What the network's TCP and UDP channels share in driving Netty. */
final class NettyChannelSupport {

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

    static SocketProtocolFamily protocolFamily(IpAddressFamily family) {
        return family == IpAddressFamily.IPV4
                ? SocketProtocolFamily.INET
                : SocketProtocolFamily.INET6;
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
