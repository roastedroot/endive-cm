package run.endive.cm.bindgen.it.staticfunctions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.cm.bindgen.it.Components;
import run.endive.cm.bindgen.it.staticfunctions.example.staticfunctions.hostcounters.Counter;
import run.endive.cm.bindgen.it.staticfunctions.example.staticfunctions.hostcounters.Host;
import run.endive.cm.bindgen.it.staticfunctions.exports.example.staticfunctions.guestcounters.Guest;
import run.endive.cm.bindgen.it.staticfunctions.exports.example.staticfunctions.guestcounters.Tally;
import run.endive.cm.runtime.Bindgen;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.runtime.TrapException;

/**
 * A world whose resources carry static functions in both directions.
 *
 * <p>A static has no receiver, so what it hands back is what decides its shape. One returning an
 * {@code own} handle to its own resource mints a resource the way a constructor does, and one
 * returning a plain value is an ordinary call.
 */
@Bindgen(world = "static-functions")
public class StaticFunctionTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/static-functions.wat"),
                        Components.text("/wit/static-functions.wit"),
                        "static-functions");
    }

    /** The guest reaches a host resource without holding one, and is handed a new handle. */
    @Test
    void aHostStaticMakesAResource() {
        HostCounters counters = new HostCounters();

        instantiate(counters).run();

        assertEquals(List.of(7L), counters.opened);
        assertTrue(counters.made.get(0).dropped, "expected the dropped handle to reach the host");
    }

    /** The guest traps unless it reads back 7, so this passing means the handle named the counter. */
    @Test
    void aHostStaticHandingBackAnotherValueTraps() {
        HostCounters counters = new HostCounters();
        counters.skew = 1;

        assertThrows(TrapException.class, () -> instantiate(counters).run());
    }

    /** A static returning a plain value takes no table, and the value reaches the guest and back. */
    @Test
    void aHostStaticReturnsAPlainValue() {
        HostCounters counters = new HostCounters();

        assertEquals(Long.valueOf(41L), instantiate(counters).run());
    }

    /** The guest traps unless the count is 41, which is what shows the u32 crossing intact. */
    @Test
    void aHostStaticReturningAnotherCountTraps() {
        HostCounters counters = new HostCounters();
        counters.count = 40;

        assertThrows(TrapException.class, () -> instantiate(counters).run());
    }

    /** A guest static mints a resource inside the component, which the wrapper then holds. */
    @Test
    void aGuestStaticMakesAResource() {
        Guest guest = instantiate(new HostCounters()).guestCounters();

        Tally tally = guest.tallyOpen(5L);

        assertEquals(Long.valueOf(105L), tally.get());
    }

    /** A guest static returning a plain value counts what the constructor and the static made. */
    @Test
    void aGuestStaticReturnsAPlainValue() {
        Guest guest = instantiate(new HostCounters()).guestCounters();

        guest.tally(1L);
        guest.tallyOpen(2L);

        assertEquals(Long.valueOf(2L), guest.tallyMade());
    }

    /** The guest traps on a 0 it is never passed, so an argument that never arrived shows up. */
    @Test
    void aGuestStaticTrapsOnAValueItIsNeverPassed() {
        Guest guest = instantiate(new HostCounters()).guestCounters();

        assertThrows(TrapException.class, () -> guest.tallyOpen(0L));
    }

    private static StaticFunctions instantiate(HostCounters counters) {
        return StaticFunctions.instantiate(new ComponentStore(), component, () -> counters);
    }

    /** The host side of {@code example:static-functions/host-counters}. */
    private static final class HostCounters implements Host {

        private final List<Long> opened = new ArrayList<>();
        private final List<HostCounter> made = new ArrayList<>();
        private long skew;
        private long count = 41;

        @Override
        public Counter counter(Long start) {
            return record(start);
        }

        @Override
        public Counter counterOpen(Long start) {
            opened.add(start);
            return record(start + skew);
        }

        @Override
        public Long counterMade() {
            return count;
        }

        private HostCounter record(long value) {
            HostCounter counter = new HostCounter(value);
            made.add(counter);
            return counter;
        }
    }

    /** One counter, recording what the guest does to it. */
    private static final class HostCounter implements Counter {

        private final long value;
        private boolean dropped;

        HostCounter(long value) {
            this.value = value;
        }

        @Override
        public Long get() {
            return value;
        }

        @Override
        public void drop() {
            dropped = true;
        }
    }
}
