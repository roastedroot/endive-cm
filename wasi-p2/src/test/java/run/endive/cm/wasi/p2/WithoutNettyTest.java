package run.endive.cm.wasi.p2;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.wasi.p2.bindings.wasi.sockets.network.ErrorCode;
import run.endive.cm.wasi.p2.sockets.NetworkPolicy;

/**
 * An embedder without Netty runs guests denied the network, and is told what to add when granting
 * one access. The build runs this with Netty left off the class path, and nothing else does.
 */
class WithoutNettyTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent(@TempDir Path temp) {
        component = Guests.build("ip-name-lookup", temp);
    }

    @Test
    void nettyIsMissing() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("io.netty.channel.EventLoopGroup"));
    }

    /** Frameworks list a class's methods, which fails if a signature names a missing class. */
    @Test
    void theHostsMethodsAreListedWithoutNetty() {
        assertDoesNotThrow(WasiPreview2.class::getMethods);
        assertDoesNotThrow(WasiPreview2.Builder.class::getMethods);
    }

    @Test
    void aGuestDeniedTheNetworkRunsWithoutNetty() {
        try (var wasi = WasiPreview2.builder().build()) {
            var lookupError =
                    wasi.instantiate(new ComponentStore(), component)
                            .instance()
                            .export("lookup-error");

            assertEquals(
                    (short) ErrorCode.PERMANENT_RESOLVER_FAILURE.ordinal(),
                    lookupError.apply("localhost")[0]);
        }
    }

    @Test
    void grantingTheNetworkWithoutNettySaysWhatToAdd() {
        var builder = WasiPreview2.builder().withNetworkPolicy(NetworkPolicy.allowAll());

        IllegalStateException e = assertThrows(IllegalStateException.class, builder::build);

        assertTrue(e.getMessage().contains("io.netty:netty-resolver-dns"), e.getMessage());
    }
}
