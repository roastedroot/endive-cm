package io.roastedroot.typewire.wasi.p2.sockets.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.resolver.InetNameResolver;
import io.netty.resolver.dns.DnsNameResolverTimeoutException;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class NettyNameResolverTest {

    private final Pending netty = new Pending();
    private final NettyNameResolver resolver = new NettyNameResolver(netty);

    @Test
    void aNettyLookupCompletesTheLookup() throws Exception {
        CompletableFuture<List<InetAddress>> lookup = resolver.resolveAll("example.test");
        List<InetAddress> found = List.of(InetAddress.getByName("192.0.2.1"));

        netty.promise.setSuccess(found);

        assertEquals("example.test", netty.asked);
        assertEquals(found, lookup.get());
    }

    @Test
    void aNettyFailureFailsTheLookup() {
        CompletableFuture<List<InetAddress>> lookup = resolver.resolveAll("missing.test");
        UnknownHostException missing = new UnknownHostException("missing.test");

        netty.promise.setFailure(missing);

        assertSame(missing, causeOf(lookup));
    }

    /** Netty gives up on a name after its queries time out, which it marks its own way. */
    @Test
    void aNettyTimeoutFailsTheLookupWithASocketTimeout() {
        CompletableFuture<List<InetAddress>> lookup = resolver.resolveAll("slow.test");
        UnknownHostException gaveUp = new UnknownHostException("failed to resolve 'slow.test'");
        gaveUp.initCause(
                new DnsNameResolverTimeoutException(
                        new InetSocketAddress("192.0.2.53", 53),
                        new DefaultDnsQuestion("slow.test", DnsRecordType.A),
                        "query timed out"));

        netty.promise.setFailure(gaveUp);

        Throwable cause = causeOf(lookup);
        assertTrue(cause instanceof SocketTimeoutException, String.valueOf(cause));
        assertSame(gaveUp, cause.getCause());
    }

    @Test
    void cancellingTheLookupCancelsNettys() {
        resolver.resolveAll("slow.test").cancel(false);

        assertTrue(netty.promise.isCancelled());
    }

    private static Throwable causeOf(CompletableFuture<?> lookup) {
        return assertThrows(ExecutionException.class, lookup::get).getCause();
    }

    /** A Netty resolver leaving each lookup for the test to complete. */
    private static final class Pending extends InetNameResolver {

        private String asked;
        private Promise<List<InetAddress>> promise;

        Pending() {
            super(ImmediateEventExecutor.INSTANCE);
        }

        @Override
        protected void doResolve(String host, Promise<InetAddress> promise) {
            promise.setFailure(new UnsupportedOperationException("only resolveAll is used"));
        }

        @Override
        protected void doResolveAll(String host, Promise<List<InetAddress>> promise) {
            this.asked = host;
            this.promise = promise;
        }
    }
}
