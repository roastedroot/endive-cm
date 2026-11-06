package run.endive.cm.wasi.p2.sockets.ipnamelookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import run.endive.cm.wasi.p2.bindings.wasi.io.poll.Pollable;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.ipnamelookup.ResolveAddressStream;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCodeException;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.IpAddress;
import run.endive.cm.wasi.p2.io.poll.HostPoll;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.cm.wasi.p2.sockets.network.HostNetwork;
import run.endive.cm.wasi.p2.sockets.network.IpAddresses;

class HostIpNameLookupTest {

    private static final HostNetwork LOOKUPS =
            new HostNetwork(NetworkPolicy.builder().allowIpNameLookup().build());

    @Test
    void aResolvedNameGivesEachAddressInOrderThenNone() throws UnknownHostException {
        TestResolver resolver =
                new TestResolver(name -> TestResolver.found("192.0.2.1", "2001:db8::1"));

        ResolveAddressStream stream = lookup(resolver).resolveAddresses(LOOKUPS, "example.test");

        assertEquals(address("192.0.2.1"), stream.resolveNextAddress());
        assertEquals(address("2001:db8::1"), stream.resolveNextAddress());
        assertNull(stream.resolveNextAddress());
        assertNull(stream.resolveNextAddress());
        assertEquals(List.of("example.test"), resolver.asked());
    }

    @Test
    void aLookupInProgressWouldBlockUntilItsPollableIsReady() throws UnknownHostException {
        CompletableFuture<List<InetAddress>> pending = TestResolver.pending();
        ResolveAddressStream stream =
                lookup(new TestResolver(name -> pending)).resolveAddresses(LOOKUPS, "slow.test");
        Pollable ready = stream.subscribe();

        assertFalse(ready.ready());
        assertEquals(ErrorCode.WOULD_BLOCK, errorOf(stream));

        pending.complete(List.of(InetAddress.getByName("192.0.2.9")));

        assertTrue(ready.ready());
        assertEquals(address("192.0.2.9"), stream.resolveNextAddress());
    }

    @Test
    void aFailedLookupIsReportedOnceThenTheStreamIsExhausted() {
        ResolveAddressStream stream =
                lookup(
                                new TestResolver(
                                        name ->
                                                TestResolver.failed(
                                                        new UnknownHostException(name))))
                        .resolveAddresses(LOOKUPS, "missing.test");

        assertEquals(ErrorCode.NAME_UNRESOLVABLE, errorOf(stream));
        assertNull(stream.resolveNextAddress());
    }

    /** A name whose lookup times out may resolve later. */
    @Test
    void aLookupTimingOutIsATemporaryFailure() {
        ResolveAddressStream stream =
                lookup(
                                new TestResolver(
                                        name ->
                                                TestResolver.failed(
                                                        new SocketTimeoutException(name))))
                        .resolveAddresses(LOOKUPS, "slow.test");

        assertEquals(ErrorCode.TEMPORARY_RESOLVER_FAILURE, errorOf(stream));
    }

    @Test
    void aResolverThrowingIsAPermanentFailure() {
        ResolveAddressStream stream =
                lookup(
                                new TestResolver(
                                        name -> {
                                            throw new IllegalStateException();
                                        }))
                        .resolveAddresses(LOOKUPS, "example.test");

        assertEquals(ErrorCode.PERMANENT_RESOLVER_FAILURE, errorOf(stream));
    }

    @Test
    void aResolverFailingForAnotherReasonIsAPermanentFailure() {
        ResolveAddressStream stream =
                lookup(new TestResolver(name -> TestResolver.failed(new IllegalStateException())))
                        .resolveAddresses(LOOKUPS, "example.test");

        assertEquals(ErrorCode.PERMANENT_RESOLVER_FAILURE, errorOf(stream));
    }

    @Test
    void droppingTheStreamCancelsTheLookup() {
        TestResolver resolver = new TestResolver(name -> TestResolver.pending());
        ResolveAddressStream stream = lookup(resolver).resolveAddresses(LOOKUPS, "slow.test");

        stream.drop();

        assertTrue(resolver.lookups().get(0).isCancelled());
    }

    @Test
    void anIpAddressIsParsedWithoutTheResolver() throws UnknownHostException {
        HostIpNameLookup lookup = lookup(TestResolver.unused());

        assertEquals(address("192.0.2.1"), first(lookup, "192.0.2.1"));
        assertEquals(address("2001:db8::1"), first(lookup, "2001:db8::1"));
        assertEquals(address("2001:db8::1"), first(lookup, "[2001:db8::1]"));
    }

    @Test
    void anIpv4MappedAddressIsGivenAsIpv4() throws UnknownHostException {
        assertEquals(
                address("192.0.2.1"), first(lookup(TestResolver.unused()), "::ffff:192.0.2.1"));
    }

    @Test
    void aResolvedIpv4MappedAddressIsGivenAsIpv4() throws UnknownHostException {
        InetAddress mapped = IpAddresses.toInet(address("::ffff:192.0.2.1"));
        TestResolver resolver =
                new TestResolver(name -> CompletableFuture.completedFuture(List.of(mapped)));

        assertEquals(address("192.0.2.1"), first(lookup(resolver), "mapped.test"));
    }

    @Test
    void aUnicodeNameIsResolvedInAscii() {
        TestResolver resolver = new TestResolver(name -> TestResolver.found());

        lookup(resolver).resolveAddresses(LOOKUPS, "bücher.example");

        assertEquals(List.of("xn--bcher-kva.example"), resolver.asked());
    }

    @Test
    void aNameEndingInADotIsResolved() {
        TestResolver resolver = new TestResolver(name -> TestResolver.found());

        lookup(resolver).resolveAddresses(LOOKUPS, "example.test.");

        assertEquals(List.of("example.test."), resolver.asked());
    }

    @Test
    void anInvalidNameIsAnInvalidArgument() {
        HostIpNameLookup lookup = lookup(TestResolver.unused());

        for (String name :
                List.of(
                        "",
                        ".",
                        "a..b",
                        "has space.test",
                        "user@example.test",
                        "example.test/path",
                        "example.test:80",
                        "192.0.2.1:80",
                        "256.0.0.1",
                        "[192.0.2.1]",
                        "fe80::1%eth0",
                        ".:",
                        "x".repeat(64) + ".test",
                        "a.".repeat(127) + "a")) {
            ErrorCodeException e =
                    assertThrows(
                            ErrorCodeException.class,
                            () -> lookup.resolveAddresses(LOOKUPS, name),
                            name);
            assertEquals(ErrorCode.INVALID_ARGUMENT, e.error(), name);
        }
    }

    /** The interface lists no {@code access-denied}, so wasmtime reports a permanent failure. */
    @Test
    void aNetworkWithoutNameLookupFailsPermanently() {
        HostNetwork tcpOnly = new HostNetwork(NetworkPolicy.builder().allowTcp().build());

        ErrorCodeException e =
                assertThrows(
                        ErrorCodeException.class,
                        () -> lookup(TestResolver.unused()).resolveAddresses(tcpOnly, "192.0.2.1"));

        assertEquals(ErrorCode.PERMANENT_RESOLVER_FAILURE, e.error());
    }

    private static HostIpNameLookup lookup(TestResolver resolver) {
        return new HostIpNameLookup(new HostPoll(), resolver);
    }

    private static IpAddress first(HostIpNameLookup lookup, String name) {
        return lookup.resolveAddresses(LOOKUPS, name).resolveNextAddress();
    }

    private static ErrorCode errorOf(ResolveAddressStream stream) {
        return assertThrows(ErrorCodeException.class, stream::resolveNextAddress).error();
    }

    private static IpAddress address(String literal) throws UnknownHostException {
        return IpAddresses.fromInet(InetAddress.getByName(literal));
    }
}
