package io.roastedroot.typewire.bindgen.it.worldexports;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.worldexports.my.project.host.Host;
import io.roastedroot.typewire.runtime.Bindgen;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The world of wasmtime's world-exports bindgen example, unchanged.
 *
 * <p>An exported interface is an instance, so it becomes a wrapper class reached through an
 * accessor. The imported interface is named by its package rather than written inline, and one of
 * its functions takes a {@code list<u8>}, which arrives as a list of the Java type carrying
 * {@code u8}.
 */
@Bindgen(world = "hello-world", path = "wit/world-exports.wit")
public class WorldExportsTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/world-exports.wat"),
                        Components.text("/wit/world-exports.wit"),
                        "hello-world");
    }

    /** Calling through the exported interface enters the guest, which calls back into the host. */
    @Test
    void anExportedInterfaceReachesTheImportedOne() {
        MyHost host = new MyHost();

        instantiate(host).demo().run();

        assertEquals(1, host.randomCalls);
        assertEquals(1, host.hashed.size());
    }

    /**
     * {@code list<u8>} arrives as a {@code byte[]} copied out of memory in one read.
     * A {@code u8} above 127 reads back as a negative {@code byte} holding the same bits.
     */
    @Test
    void aByteListArgumentArrivesAsAnArray() {
        MyHost host = new MyHost();

        instantiate(host).demo().run();

        assertArrayEquals(new byte[] {10, 20, (byte) 255}, host.hashed.get(0));
    }

    @Test
    void theExportedInterfaceWrapperIsBuiltOnce() {
        HelloWorldWorld bindings = instantiate(new MyHost());

        assertSame(bindings.demo(), bindings.demo());
    }

    @Test
    void nothingIsCalledBeforeTheGuestRuns() {
        MyHost host = new MyHost();

        instantiate(host);

        assertEquals(0, host.randomCalls);
    }

    private static HelloWorldWorld instantiate(Host host) {
        return HelloWorldWorld.instantiate(new ComponentStore(), component, () -> host);
    }

    /** The host side of {@code my:project/host}. */
    private static final class MyHost implements Host {

        private final List<byte[]> hashed = new ArrayList<>();
        private int randomCalls;

        @Override
        public Long genRandomInteger() {
            randomCalls++;
            return 7L;
        }

        @Override
        public String sha256(byte[] bytes) {
            hashed.add(bytes.clone());
            return "digest";
        }
    }
}
