package io.roastedroot.typewire.bindgen.it.handletypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.handletypes.example.handletypes.error.Error;
import io.roastedroot.typewire.bindgen.it.handletypes.example.handletypes.streams.Host;
import io.roastedroot.typewire.bindgen.it.handletypes.example.handletypes.streams.InputStream;
import io.roastedroot.typewire.bindgen.it.handletypes.example.handletypes.streams.Opened;
import io.roastedroot.typewire.bindgen.it.handletypes.example.handletypes.streams.StreamError;
import io.roastedroot.typewire.bindgen.it.handletypes.example.handletypes.streams.StreamErrorException;
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
 * A world carrying resource handles inside a record and a variant, written for this test because
 * none of the bindgen! examples does.
 *
 * <p>The guest traps unless read fails with the error case the host threw, and it asks the error
 * interface about the error that case carried, so a passing call shows the handle inside the
 * variant reached the guest as one the declaring interface accepts.
 */
@Bindgen(world = "handle-types")
public class HandleTypesTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/handle-types.wat"),
                        Components.text("/wit/handle-types.wit"),
                        "handle-types");
    }

    /** A handle inside a variant carried by a thrown error reaches the guest intact. */
    @Test
    void aHandleInsideAVariantReachesTheGuest() {
        Streams streams = new Streams(true);

        long length = instantiate(streams).run();

        assertEquals("disk full".length(), length);
    }

    /** A record handed back carries ownership of its handle, which arrives as the host's object. */
    @Test
    void aHandleInsideARecordComesBackAsItsObject() {
        Streams streams = new Streams(true);

        instantiate(streams).run();

        assertEquals(1, streams.closed.size());
        assertSame(streams.input, streams.closed.get(0).input());
        assertEquals("log", streams.closed.get(0).label());
    }

    /** The guest traps unless read fails, so the error above really arrived. */
    @Test
    void aReadTheGuestDoesNotExpectTraps() {
        HandleTypesWorld bindings = instantiate(new Streams(false));

        assertThrows(TrapException.class, bindings::run);
    }

    private static HandleTypesWorld instantiate(Streams streams) {
        return HandleTypesWorld.instantiate(new ComponentStore(), component, () -> streams);
    }

    /** The host side of {@code example:handle-types/streams}. */
    private static final class Streams implements Host {

        private final Input input;
        private final List<Opened> closed = new ArrayList<>();

        Streams(boolean failing) {
            this.input = new Input(failing);
        }

        @Override
        public Opened open() {
            return new Opened(input, "log");
        }

        @Override
        public void close(Opened o) {
            closed.add(o);
        }
    }

    private static final class Input implements InputStream {

        private final boolean failing;
        private final Error error = () -> "disk full";

        Input(boolean failing) {
            this.failing = failing;
        }

        @Override
        public byte[] read(BigInteger len) {
            if (failing) {
                throw new StreamErrorException(new StreamError.LastOperationFailed(error));
            }
            return new byte[0];
        }
    }
}
