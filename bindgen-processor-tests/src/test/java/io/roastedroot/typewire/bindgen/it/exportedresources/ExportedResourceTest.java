package io.roastedroot.typewire.bindgen.it.exportedresources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.exportedresources.exports.example.exportedresources.logging.Level;
import io.roastedroot.typewire.bindgen.it.exportedresources.exports.example.exportedresources.logging.Logger;
import io.roastedroot.typewire.runtime.Bindgen;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The world of wasmtime's exported-resources bindgen example, with one addition. That world exports
 * nothing a test could read a destructor count from, so an {@code export drops: func() -> u32} was
 * added.
 *
 * <p>The resource lives inside the component, so the handle runs the other way from an imported
 * one. Nothing destroys it on the embedder's behalf, which is why the wrapper is
 * {@link AutoCloseable} and closing it is what runs the guest's destructor.
 */
@Bindgen(world = "export-some-resources")
public class ExportedResourceTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/export-some-resources.wat"),
                        Components.text("/wit/export-some-resources.wit"),
                        "export-some-resources");
    }

    /** The constructor runs inside the guest and hands back a handle the wrapper holds. */
    @Test
    void theHostConstructsAGuestResource() {
        ExportSomeResourcesWorld bindings = instantiate();

        Logger logger = bindings.logging().logger(Level.WARN);

        assertEquals(Level.WARN, logger.getMaxLevel());
    }

    /** State set through one call is visible from the next, so the handle names the same thing. */
    @Test
    void aMethodChangesStateTheGuestKeeps() {
        Logger logger = instantiate().logging().logger(Level.WARN);

        logger.setMaxLevel(Level.DEBUG);

        assertEquals(Level.DEBUG, logger.getMaxLevel());
    }

    /** A method taking an enum and a string, both lowered into the guest. */
    @Test
    void aMethodTakesEveryArgument() {
        Logger logger = instantiate().logging().logger(Level.WARN);

        logger.log(Level.ERROR, "something happened");

        assertEquals(Level.ERROR, logger.getMaxLevel());
    }

    /** Two constructions are two resources, each with state of its own. */
    @Test
    void everyConstructionIsItsOwnResource() {
        var logging = instantiate().logging();

        Logger first = logging.logger(Level.WARN);
        Logger second = logging.logger(Level.DEBUG);

        assertNotSame(first, second);
        assertEquals(Level.WARN, first.getMaxLevel());
        assertEquals(Level.DEBUG, second.getMaxLevel());
    }

    /** Closing runs the guest's destructor, which nothing else would have run. */
    @Test
    void closingRunsTheGuestDestructor() {
        ExportSomeResourcesWorld bindings = instantiate();
        Logger logger = bindings.logging().logger(Level.WARN);

        assertEquals(Long.valueOf(0L), bindings.drops());
        logger.close();

        assertEquals(Long.valueOf(1L), bindings.drops());
    }

    /** Closing twice is harmless, so the wrapper suits try-with-resources. */
    @Test
    void closingTwiceRunsTheDestructorOnce() {
        ExportSomeResourcesWorld bindings = instantiate();
        Logger logger = bindings.logging().logger(Level.WARN);

        logger.close();
        logger.close();

        assertEquals(Long.valueOf(1L), bindings.drops());
    }

    /** What try-with-resources does, which is the point of the wrapper being closeable. */
    @Test
    void aResourceMayBeClosedByTryWithResources() {
        ExportSomeResourcesWorld bindings = instantiate();

        try (Logger logger = bindings.logging().logger(Level.INFO)) {
            assertEquals(Level.INFO, logger.getMaxLevel());
        }

        assertEquals(Long.valueOf(1L), bindings.drops());
    }

    private static ExportSomeResourcesWorld instantiate() {
        return ExportSomeResourcesWorld.instantiate(new ComponentStore(), component, new Imports());
    }

    /** The world imports nothing, so this carries only the interfaces it exports. */
    private static final class Imports implements ExportSomeResourcesWorld.Imports {}
}
