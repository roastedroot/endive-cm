package io.roastedroot.typewire.bindgen.it.usetypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.usetypes.example.usetypes.logging.Host;
import io.roastedroot.typewire.bindgen.it.usetypes.example.usetypes.types.Entry;
import io.roastedroot.typewire.bindgen.it.usetypes.example.usetypes.types.Level;
import io.roastedroot.typewire.runtime.Bindgen;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.runtime.TrapException;

/**
 * A world whose interface uses types another interface declares, written for this test because
 * none of the bindgen! examples has a {@code use}.
 *
 * <p>The guest traps unless the entry and the levels it is handed hold what these tests say the
 * host handed over, so a passing call shows the used types crossed rather than only that a call
 * happened.
 */
@Bindgen(world = "use-types")
public class UseTypesTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/use-types.wat"),
                        Components.text("/wit/use-types.wit"),
                        "use-types");
    }

    /** A used enum and a used record reach the guest, and the enum comes back out of it. */
    @Test
    void usedTypesCrossInBothDirections() {
        Recorder recorder = new Recorder(new Entry(Level.ERROR, 42L));

        Level level = instantiate(recorder).run();

        assertSame(Level.ERROR, level);
    }

    /** An argument of a used type arrives as the case the guest named. */
    @Test
    void aUsedEnumArgumentArrivesAsItsCase() {
        Recorder recorder = new Recorder(new Entry(Level.ERROR, 42L));

        instantiate(recorder).run();

        assertEquals(List.of("warn: starting"), recorder.entries);
    }

    /** A used list alias arrives element by element, under the name the using interface gave it. */
    @Test
    void aRenamedListAliasArrivesWhole() {
        Recorder recorder = new Recorder(new Entry(Level.ERROR, 42L));

        instantiate(recorder).run();

        assertEquals(List.of(Level.INFO, Level.ERROR, Level.DEBUG), recorder.levels);
    }

    /** The guest traps on an entry it was not promised, so the one above really arrived. */
    @Test
    void anEntryTheGuestDoesNotExpectTraps() {
        Recorder recorder = new Recorder(new Entry(Level.ERROR, 41L));

        UseTypesWorld bindings = instantiate(recorder);

        assertThrows(TrapException.class, bindings::run);
    }

    private static UseTypesWorld instantiate(Recorder recorder) {
        return UseTypesWorld.instantiate(new ComponentStore(), component, () -> recorder);
    }

    /** The host side of {@code example:use-types/logging}. */
    private static final class Recorder implements Host {

        private final Entry latest;
        private final List<String> entries = new ArrayList<>();
        private final List<Level> levels = new ArrayList<>();

        Recorder(Entry latest) {
            this.latest = latest;
        }

        @Override
        public void log(Level level, String msg) {
            entries.add(level.name().toLowerCase() + ": " + msg);
        }

        @Override
        public Entry latest() {
            return latest;
        }

        @Override
        public Level worst(List<Level> given) {
            levels.addAll(given);
            return given.stream().max(Comparator.naturalOrder()).orElse(null);
        }
    }
}
