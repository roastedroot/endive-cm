package io.roastedroot.typewire.bindgen.it.interfaceimports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.interfaceimports.example.interfaceimports.logging.Host;
import io.roastedroot.typewire.bindgen.it.interfaceimports.example.interfaceimports.logging.Level;
import io.roastedroot.typewire.runtime.Bindgen;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The world of wasmtime's interface-imports bindgen example, with one addition. That world imports
 * without exporting, so an {@code export run: func()} was added to give a test a way into the guest.
 *
 * <p>An interface named by its id rather than written inline is the same shape either way, which is
 * the claim this example is here to check rather than leave inferred. The enum it declares becomes
 * a Java enum in the interface's own package.
 */
@Bindgen(world = "with-imports")
public class InterfaceImportsTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/with-imports.wat"),
                        Components.text("/wit/with-imports.wit"),
                        "with-imports");
    }

    /** The guest reaches the imported interface, which the embedder implements. */
    @Test
    void theGuestCallsTheImportedInterface() {
        Recorder recorder = new Recorder();

        WithImportsWorld.instantiate(new ComponentStore(), component, () -> recorder).run();

        assertEquals(2, recorder.entries.size());
    }

    /** An enum argument arrives as the case the guest named, converted at the boundary. */
    @Test
    void anEnumArgumentArrivesAsItsCase() {
        Recorder recorder = new Recorder();

        WithImportsWorld.instantiate(new ComponentStore(), component, () -> recorder).run();

        assertEquals(List.of(Level.WARN, Level.ERROR), recorder.levels);
    }

    /** The string travels beside the enum, so both cross on the same call. */
    @Test
    void aStringArgumentArrivesBesideIt() {
        Recorder recorder = new Recorder();

        WithImportsWorld.instantiate(new ComponentStore(), component, () -> recorder).run();

        assertEquals(List.of("warn: starting", "error: done"), recorder.entries);
    }

    /** The enum is a Java enum, so a case compares by identity rather than by label. */
    @Test
    void theGeneratedEnumCarriesEveryCase() {
        assertEquals(4, Level.values().length);
        assertSame(Level.DEBUG, Level.valueOf("DEBUG"));
        assertTrue(List.of(Level.values()).contains(Level.INFO));
    }

    @Test
    void nothingIsCalledBeforeTheGuestRuns() {
        Recorder recorder = new Recorder();

        WithImportsWorld.instantiate(new ComponentStore(), component, () -> recorder);

        assertTrue(recorder.entries.isEmpty());
    }

    /** The host side of {@code example:interface-imports/logging}. */
    private static final class Recorder implements Host {

        private final List<String> entries = new ArrayList<>();
        private final List<Level> levels = new ArrayList<>();

        @Override
        public void log(Level level, String msg) {
            levels.add(level);
            entries.add(level.name().toLowerCase() + ": " + msg);
        }
    }
}
