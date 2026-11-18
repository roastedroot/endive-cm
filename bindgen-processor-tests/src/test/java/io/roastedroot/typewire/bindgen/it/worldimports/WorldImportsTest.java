package io.roastedroot.typewire.bindgen.it.worldimports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.worldimports.mycustomhost.Host;
import io.roastedroot.typewire.runtime.Bindgen;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A world importing both bare functions and an interface.
 *
 * <p>An imported interface is an instance rather than a function, so the generated bindings build
 * one with {@link io.roastedroot.typewire.runtime.HostInstance} and give the embedder a Java interface of its
 * own to implement. The guest hands the greeting it was given straight back through {@code log},
 * which is what shows the value survived the round trip rather than only that both were called.
 */
@Bindgen(world = "my-world")
public class WorldImportsTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/my-world.wat"),
                        Components.text("/wit/my-world.wit"),
                        "my-world");
    }

    @Test
    void everyImportIsReachedFromTheGuest() {
        Recorder recorder = new Recorder("hello");

        MyWorldWorld.instantiate(new ComponentStore(), component, recorder).run();

        assertTrue(recorder.greeted, "expected the guest to call the imported greet");
        assertEquals(List.of("hello"), recorder.logged);
        assertEquals(1, recorder.ticks, "expected the guest to call tick on the interface");
    }

    /** The greeting travels into guest memory and back out, so what is logged is what was given. */
    @Test
    void aValueFromOneImportReachesAnother() {
        Recorder recorder = new Recorder("a longer greeting");

        MyWorldWorld.instantiate(new ComponentStore(), component, recorder).run();

        assertEquals(List.of("a longer greeting"), recorder.logged);
    }

    @Test
    void theInterfaceIsAskedForOnlyOnce() {
        Recorder recorder = new Recorder("hello");

        MyWorldWorld world = MyWorldWorld.instantiate(new ComponentStore(), component, recorder);
        world.run();
        world.run();

        assertEquals(
                1, recorder.handedOut, "expected the interface to be resolved at instantiation");
        assertEquals(2, recorder.ticks);
    }

    @Test
    void nothingIsCalledBeforeTheGuestRuns() {
        Recorder recorder = new Recorder("hello");

        MyWorldWorld.instantiate(new ComponentStore(), component, recorder);

        assertFalse(recorder.greeted);
        assertEquals(0, recorder.ticks);
    }

    /** One object implementing both the world's imports and the interface it imports. */
    private static final class Recorder implements MyWorldWorld.Imports, Host {

        private final String greeting;
        private final List<String> logged = new ArrayList<>();
        private boolean greeted;
        private int ticks;
        private int handedOut;

        Recorder(String greeting) {
            this.greeting = greeting;
        }

        @Override
        public String greet() {
            greeted = true;
            return greeting;
        }

        @Override
        public void log(String msg) {
            logged.add(msg);
        }

        @Override
        public Host myCustomHost() {
            handedOut++;
            return this;
        }

        @Override
        public void tick() {
            ticks++;
        }
    }
}
