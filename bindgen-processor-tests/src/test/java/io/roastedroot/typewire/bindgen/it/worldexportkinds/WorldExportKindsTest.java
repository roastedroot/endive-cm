package io.roastedroot.typewire.bindgen.it.worldexportkinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.runtime.Bindgen;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.runtime.TrapException;

/**
 * The world of wasmtime's all-world-export-kinds bindgen example, unchanged.
 *
 * <p>Every shape a world's exports take at once, which is a function of the world's own, an
 * interface written inline, and an interface exported by its id. The last two differ only in
 * whether the name carries a package qualification, so both become a wrapper class named after the
 * last segment.
 */
@Bindgen(world = "with-exports")
public class WorldExportKindsTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/with-exports.wat"),
                        Components.text("/wit/with-exports.wit"),
                        "with-exports");
    }

    /** A function the world exports in its own right. */
    @Test
    void aTopLevelExportIsCallable() {
        Log log = new Log();

        instantiate(log).run();

        assertEquals(List.of("ran"), log.messages);
    }

    /** An interface written inline in the world, reached through its accessor. */
    @Test
    void anInlineExportedInterfaceIsCallable() {
        Log log = new Log();
        WithExportsWorld bindings = instantiate(log);

        assertEquals("ok", bindings.environment().get("HOME"));
        assertEquals(List.of("HOME"), log.messages);
    }

    /** Two string arguments, both lowered into guest memory. */
    @Test
    void anInlineExportedInterfaceTakesSeveralArguments() {
        Log log = new Log();

        instantiate(log).environment().set("HOME", "/root");

        assertEquals(List.of("/root"), log.messages);
    }

    /**
     * An interface exported by its id rather than written inline. The guest traps unless it is
     * handed the values below, so this passing shows {@code u64} crossing intact.
     */
    @Test
    void anInterfaceExportedByIdIsCallable() {
        WithExportsWorld bindings = instantiate(new Log());

        assertEquals("kb", bindings.units().bytesToString(BigInteger.valueOf(1024)));
        assertEquals("5s", bindings.units().durationToString(BigInteger.valueOf(5), 500L));
    }

    /** The trap above is live, so a value the guest does not expect is refused. */
    @Test
    void aValueTheGuestDoesNotExpectTraps() {
        WithExportsWorld bindings = instantiate(new Log());

        assertThrows(TrapException.class, () -> bindings.units().bytesToString(BigInteger.ONE));
    }

    @Test
    void everyExportedInterfaceWrapperIsBuiltOnce() {
        WithExportsWorld bindings = instantiate(new Log());

        assertSame(bindings.environment(), bindings.environment());
        assertSame(bindings.units(), bindings.units());
    }

    private static WithExportsWorld instantiate(Log log) {
        return WithExportsWorld.instantiate(new ComponentStore(), component, log);
    }

    /** The world's one import, recording what the guest logs. */
    private static final class Log implements WithExportsWorld.Imports {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String msg) {
            messages.add(msg);
        }
    }
}
