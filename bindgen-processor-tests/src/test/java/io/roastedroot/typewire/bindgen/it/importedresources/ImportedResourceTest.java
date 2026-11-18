package io.roastedroot.typewire.bindgen.it.importedresources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.importedresources.example.importedresources.logging.Host;
import io.roastedroot.typewire.bindgen.it.importedresources.example.importedresources.logging.Level;
import io.roastedroot.typewire.bindgen.it.importedresources.example.importedresources.logging.Logger;
import io.roastedroot.typewire.runtime.Bindgen;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The world of wasmtime's imported-resources bindgen example, with one addition. That world imports
 * without exporting, so an {@code export run: func()} was added to give a test a way into the guest.
 *
 * <p>A handle carries an integer across the ABI rather than an object, so the bindings keep a table
 * mapping one to the other. An enum crosses as a variant, so the generated Java enum converts at
 * the boundary.
 */
@Bindgen(world = "import-some-resources")
public class ImportedResourceTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/import-some-resources.wat"),
                        Components.text("/wit/import-some-resources.wit"),
                        "import-some-resources");
    }

    /** The guest constructs a host resource, passing an enum the bindings convert before the call. */
    @Test
    void theGuestConstructsAResourceWithAnEnumArgument() {
        HostLogging logging = new HostLogging();

        instantiate(logging).run();

        assertEquals(1, logging.built.size());
        assertEquals(Level.WARN, logging.built.get(0).constructedAt);
    }

    /**
     * The guest traps unless the level it reads back is the one it set, so this passing means the
     * enum survived being lowered into the guest and lifted out again.
     */
    @Test
    void anEnumResultReachesTheGuestIntact() {
        HostLogging logging = new HostLogging();

        instantiate(logging).run();

        assertEquals(1, logging.built.size());
    }

    /** A method taking an enum and a string, both converted before the call. */
    @Test
    void aMethodReceivesEveryArgument() {
        HostLogging logging = new HostLogging();

        instantiate(logging).run();

        HostLogger logger = logging.built.get(0);
        assertEquals(Level.DEBUG, logger.maxLevel);
        assertEquals(List.of("info: hi"), logger.logged);
    }

    /** Dropping the owned handle in the guest runs the destructor, which reaches the host. */
    @Test
    void droppingTheHandleReachesTheHost() {
        HostLogging logging = new HostLogging();

        instantiate(logging).run();

        assertTrue(logging.built.get(0).dropped, "expected the dropped handle to reach the host");
    }

    private static ImportSomeResourcesWorld instantiate(HostLogging logging) {
        return ImportSomeResourcesWorld.instantiate(new ComponentStore(), component, () -> logging);
    }

    /** The host side of {@code example:imported-resources/logging}. */
    private static final class HostLogging implements Host {

        private final List<HostLogger> built = new ArrayList<>();

        @Override
        public Logger logger(Level maxLevel) {
            HostLogger logger = new HostLogger(maxLevel);
            built.add(logger);
            return logger;
        }
    }

    /** One logger, recording everything the guest does to it. */
    private static final class HostLogger implements Logger {

        private final List<String> logged = new ArrayList<>();
        private final Level constructedAt;
        private Level maxLevel;
        private boolean dropped;

        HostLogger(Level maxLevel) {
            this.constructedAt = maxLevel;
            this.maxLevel = maxLevel;
        }

        @Override
        public Level getMaxLevel() {
            return maxLevel;
        }

        @Override
        public void setMaxLevel(Level level) {
            maxLevel = level;
        }

        @Override
        public void log(Level level, String msg) {
            logged.add(level.name().toLowerCase() + ": " + msg);
        }

        @Override
        public void drop() {
            dropped = true;
        }
    }
}
