package io.roastedroot.typewire.bindgen.it.useresources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.useresources.example.useresources.error.Error;
import io.roastedroot.typewire.bindgen.it.useresources.example.useresources.poll.Pollable;
import io.roastedroot.typewire.bindgen.it.useresources.example.useresources.streams.InputStream;
import io.roastedroot.typewire.runtime.Bindgen;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.runtime.TrapException;

/**
 * A world whose interface uses resources other interfaces declare, written for this test because
 * none of the bindgen! examples uses a resource from another interface.
 *
 * <p>The guest traps unless the handles one interface hands out are the ones the interface
 * declaring them accepts, so a passing call shows the used resources share one runtime type rather
 * than only that each call happened.
 */
@Bindgen(world = "use-resources")
public class UseResourcesTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/use-resources.wat"),
                        Components.text("/wit/use-resources.wit"),
                        "use-resources");
    }

    /** An error streams hands out is one the error interface answers for. */
    @Test
    void aUsedResourceIsAcceptedByTheInterfaceDeclaringIt() {
        Hosts hosts = new Hosts(true, "disk full");

        long length = instantiate(hosts).run();

        assertEquals("disk full".length(), length);
    }

    /** A pollable streams hands out arrives at poll as the object streams made. */
    @Test
    void aUsedResourceArrivesAsTheObjectBehindIt() {
        Hosts hosts = new Hosts(true, "disk full");

        instantiate(hosts).run();

        assertEquals(1, hosts.polled.size());
        assertSame(hosts.input.subscribed, hosts.polled.get(0));
    }

    /** The guest traps unless its pollable is ready, so the one above really arrived. */
    @Test
    void aPollableTheGuestDoesNotExpectTraps() {
        UseResourcesWorld bindings = instantiate(new Hosts(false, "disk full"));

        assertThrows(TrapException.class, bindings::run);
    }

    private static UseResourcesWorld instantiate(Hosts hosts) {
        return UseResourcesWorld.instantiate(new ComponentStore(), component, hosts);
    }

    /** The host side of every interface the world imports. */
    private static final class Hosts
            implements UseResourcesWorld.Imports,
                    io.roastedroot.typewire.bindgen.it.useresources.example.useresources.poll.Host,
                    io.roastedroot.typewire.bindgen.it.useresources.example.useresources.streams
                            .Host {

        private final Input input;
        private final Error error;
        private final List<Pollable> polled = new ArrayList<>();

        Hosts(boolean ready, String message) {
            this.input = new Input(ready);
            this.error = () -> message;
        }

        @Override
        public io.roastedroot.typewire.bindgen.it.useresources.example.useresources.poll.Host
                poll() {
            return this;
        }

        @Override
        public List<Long> poll(List<Pollable> in) {
            polled.addAll(in);
            List<Long> ready = new ArrayList<>();
            for (int i = 0; i < in.size(); i++) {
                if (in.get(i).ready()) {
                    ready.add((long) i);
                }
            }
            return ready;
        }

        @Override
        public io.roastedroot.typewire.bindgen.it.useresources.example.useresources.streams.Host
                streams() {
            return this;
        }

        @Override
        public InputStream openInput() {
            return input;
        }

        @Override
        public Error lastError() {
            return error;
        }

        @Override
        public Boolean recorded(Error e) {
            return e == error;
        }
    }

    private static final class Input implements InputStream {

        private final Pollable subscribed;

        Input(boolean ready) {
            this.subscribed = () -> ready;
        }

        @Override
        public Pollable subscribe() {
            return subscribed;
        }
    }
}
