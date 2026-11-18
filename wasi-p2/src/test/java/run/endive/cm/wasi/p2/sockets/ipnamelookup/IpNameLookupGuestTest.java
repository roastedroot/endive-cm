package run.endive.cm.wasi.p2.sockets.ipnamelookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.endive.cm.runtime.ComponentInstance;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.wasi.p2.Guests;
import run.endive.cm.wasi.p2.WasiPreview2;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;
import run.endive.runtime.TrapException;

/**
 * A guest resolving names through {@code wasi:sockets/ip-name-lookup}, end to end through the
 * generated world.
 */
class IpNameLookupGuestTest {

    private static final NetworkPolicy LOOKUPS =
            NetworkPolicy.builder().allowIpNameLookup().build();

    private static WasmComponent component;
    private static EventLoopGroup group;

    @BeforeAll
    static void buildComponent(@TempDir Path temp) {
        component = Guests.build("ip-name-lookup", temp);
        group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    }

    @AfterAll
    static void stopEventLoopGroup() {
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
    }

    /** The resolver answers after a delay, so the guest has to wait on the stream's pollable. */
    @Test
    void aGuestWaitsForEveryAddressOfAName() {
        TestResolver resolver =
                new TestResolver(
                        name ->
                                TestResolver.foundAfter(
                                        20, "192.0.2.1", "2001:db8::1", "192.0.2.2"));

        try (var wasi = wasi(resolver)) {
            ComponentInstance guest = instantiate(wasi);

            assertEquals(3L, guest.export("count").apply("example.test")[0]);
            assertEquals(0xc0000201L, guest.export("first-ipv4").apply("example.test")[0]);
        }
    }

    @Test
    void anIpAddressReachesTheGuestWithoutTheResolver() {
        try (var wasi = wasi(TestResolver.unused())) {
            ComponentInstance guest = instantiate(wasi);

            assertEquals(0xc6336407L, guest.export("first-ipv4").apply("198.51.100.7")[0]);
        }
    }

    /** The guest traps on any error, so the counts above were not errors read as addresses. */
    @Test
    void aNameWithoutAddressesTrapsWhereTheGuestExpectsSome() {
        try (var wasi = wasi(unresolvable())) {
            var count = instantiate(wasi).export("count");

            assertThrows(TrapException.class, () -> count.apply("missing.test"));
        }
    }

    @Test
    void eachFailureReachesTheGuestAsItsErrorCode() {
        try (var wasi = wasi(unresolvable())) {
            var lookupError = instantiate(wasi).export("lookup-error");

            assertEquals(code(ErrorCode.NAME_UNRESOLVABLE), lookupError.apply("missing.test")[0]);
            assertEquals(code(ErrorCode.INVALID_ARGUMENT), lookupError.apply("not a name")[0]);
            assertEquals((short) 255, lookupError.apply("192.0.2.1")[0]);
        }
    }

    @Test
    void aGuestIsDeniedNameLookupByDefault() {
        try (var wasi = WasiPreview2.builder().build()) {
            var lookupError = instantiate(wasi).export("lookup-error");

            assertEquals(
                    code(ErrorCode.PERMANENT_RESOLVER_FAILURE), lookupError.apply("localhost")[0]);
        }
    }

    /** Netty's resolver answers localhost from the hosts file, without asking a DNS server. */
    @Test
    void theDefaultResolverFindsLocalhost() {
        try (var wasi =
                WasiPreview2.builder().withScheduler(group).withNetworkPolicy(LOOKUPS).build()) {
            Long count = (Long) instantiate(wasi).export("count").apply("localhost")[0];

            assertTrue(count > 0, "count " + count);
        }
    }

    private static WasiPreview2 wasi(TestResolver resolver) {
        return WasiPreview2.builder()
                .withScheduler(group)
                .withNetworkPolicy(LOOKUPS)
                .withNameResolver(resolver)
                .build();
    }

    private static ComponentInstance instantiate(WasiPreview2 wasi) {
        return wasi.instantiate(new ComponentStore(), component).instance();
    }

    private static TestResolver unresolvable() {
        return new TestResolver(name -> TestResolver.failed(new UnknownHostException(name)));
    }

    private static Short code(ErrorCode code) {
        return (short) code.ordinal();
    }
}
