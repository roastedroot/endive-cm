package io.roastedroot.typewire.bindgen.it.resourcehandles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.resourcehandles.example.resourcehandles.streams.Host;
import io.roastedroot.typewire.bindgen.it.resourcehandles.example.resourcehandles.streams.InputStream;
import io.roastedroot.typewire.bindgen.it.resourcehandles.example.resourcehandles.streams.OutputStream;
import io.roastedroot.typewire.bindgen.it.resourcehandles.example.resourcehandles.streams.Pollable;
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
 * A world handing resource handles across outside a constructor or a receiver, written for this
 * test because none of the bindgen! examples does.
 *
 * <p>The guest traps unless the pollable it hands back to the host is the one the host made, so a
 * passing call shows each handle reached the object behind it rather than only that a call
 * happened.
 */
@Bindgen(world = "resource-handles")
public class ResourceHandlesTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/resource-handles.wat"),
                        Components.text("/wit/resource-handles.wit"),
                        "resource-handles");
    }

    /** A borrowed argument arrives as the object the host minted a handle for. */
    @Test
    void aBorrowedArgumentArrivesAsTheObjectBehindIt() {
        Streams streams = new Streams(true, 3);

        BigInteger moved = instantiate(streams).run();

        assertEquals(BigInteger.valueOf(3), moved);
        assertSame(streams.input, streams.output.spliced.get(0));
    }

    /** A handle a method returns reaches the guest and comes back as the object it stands for. */
    @Test
    void aHandleAMethodReturnsComesBackAsItsObject() {
        Streams streams = new Streams(true, 3);

        instantiate(streams).run();

        assertEquals(List.of(streams.input.subscribed), streams.polled);
    }

    /** The guest traps unless its pollable is ready, so the one above really arrived. */
    @Test
    void aPollableTheGuestDoesNotExpectTraps() {
        ResourceHandlesWorld bindings = instantiate(new Streams(false, 3));

        assertThrows(TrapException.class, bindings::run);
    }

    private static ResourceHandlesWorld instantiate(Streams streams) {
        return ResourceHandlesWorld.instantiate(new ComponentStore(), component, () -> streams);
    }

    /** The host side of {@code example:resource-handles/streams}. */
    private static final class Streams implements Host {

        private final Input input;
        private final Output output = new Output();
        private final List<Pollable> polled = new ArrayList<>();

        Streams(boolean ready, long available) {
            this.input = new Input(ready, available);
        }

        @Override
        public InputStream openInput() {
            return input;
        }

        @Override
        public OutputStream openOutput() {
            return output;
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
    }

    private static final class Input implements InputStream {

        private final long available;
        private final Pollable subscribed;

        Input(boolean ready, long available) {
            this.available = available;
            this.subscribed = () -> ready;
        }

        @Override
        public Pollable subscribe() {
            return subscribed;
        }
    }

    private static final class Output implements OutputStream {

        private final List<InputStream> spliced = new ArrayList<>();

        @Override
        public BigInteger splice(InputStream src, BigInteger len) {
            spliced.add(src);
            return len.min(BigInteger.valueOf(((Input) src).available));
        }
    }
}
