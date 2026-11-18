package io.roastedroot.typewire.wasi.p2.sockets.netty;

import io.netty.resolver.dns.DnsNameResolverTimeoutException;
import io.netty.util.concurrent.Future;
import io.roastedroot.typewire.wasi.p2.sockets.NameResolver;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** A {@link NameResolver} backed by a Netty one, which cancels Netty's lookup along with its own. */
public final class NettyNameResolver implements NameResolver {

    private final io.netty.resolver.NameResolver<InetAddress> resolver;

    NettyNameResolver(io.netty.resolver.NameResolver<InetAddress> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** Resolves the names a guest looks up with {@code resolver}, which the embedder closes. */
    public static NameResolver of(io.netty.resolver.NameResolver<InetAddress> resolver) {
        return new NettyNameResolver(resolver);
    }

    @Override
    public CompletableFuture<List<InetAddress>> resolveAll(String name) {
        Future<List<InetAddress>> resolving;
        try {
            resolving = resolver.resolveAll(name);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<List<InetAddress>> lookup = new CompletableFuture<>();
        resolving.addListener(
                done -> {
                    if (resolving.isSuccess()) {
                        lookup.complete(resolving.getNow());
                    } else {
                        lookup.completeExceptionally(failure(resolving.cause()));
                    }
                });
        lookup.whenComplete(
                (addresses, failure) -> {
                    if (lookup.isCancelled()) {
                        resolving.cancel(false);
                    }
                });
        return lookup;
    }

    /** {@code cause} as {@link NameResolver} reports it, since Netty marks a timeout its own way. */
    private static Throwable failure(Throwable cause) {
        for (Throwable c = cause; c != null; c = c.getCause()) {
            if (c instanceof DnsNameResolverTimeoutException) {
                SocketTimeoutException timeout = new SocketTimeoutException(cause.getMessage());
                timeout.initCause(cause);
                return timeout;
            }
        }
        return cause;
    }
}
