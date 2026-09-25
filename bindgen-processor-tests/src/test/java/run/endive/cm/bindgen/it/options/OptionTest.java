package run.endive.cm.bindgen.it.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.cm.bindgen.it.Components;
import run.endive.cm.bindgen.it.options.example.optiontypes.maybe.Host;
import run.endive.cm.runtime.Bindgen;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.runtime.TrapException;

/**
 * A WIT {@code option<T>} as a nullable {@code T}, crossing in both directions.
 *
 * <p>The guest echoes its argument through the host import and traps unless what comes back is a
 * none for a none and one more for a some, so a binding that lost either case fails rather than
 * passes on a call that merely happened.
 */
@Bindgen(world = "option-types")
public class OptionTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/option-types.wat"),
                        Components.text("/wit/option-types.wit"),
                        "option-types");
    }

    /** A none reaches the guest, reaches the host as {@code null}, and comes back as a none. */
    @Test
    void aNoneCrossesInBothDirections() {
        Echo echo = new Echo();

        Long answer = instantiate(echo).run(null);

        assertEquals(1, echo.seen.size());
        assertNull(echo.seen.get(0));
        assertEquals(7L, answer);
    }

    /** A some carries its payload the whole way round, and the guest answers with a none. */
    @Test
    void aSomeCrossesInBothDirections() {
        Echo echo = new Echo();

        Long answer = instantiate(echo).run(5L);

        assertEquals(List.of(5L), echo.seen);
        assertNull(answer);
    }

    /** The guest expects a none back, so a host answering some traps. */
    @Test
    void aNoneAnsweredWithSomeTraps() {
        OptionTypes bindings = instantiate(value -> 0L);

        assertThrows(TrapException.class, () -> bindings.run(null));
    }

    /** The guest expects one more than it sent, so a host answering none traps. */
    @Test
    void aSomeAnsweredWithNoneTraps() {
        OptionTypes bindings = instantiate(value -> null);

        assertThrows(TrapException.class, () -> bindings.run(5L));
    }

    private static OptionTypes instantiate(Host maybe) {
        return OptionTypes.instantiate(new ComponentStore(), component, () -> maybe);
    }

    /** Answers a none with a none and a some with one more, recording what it was handed. */
    private static final class Echo implements Host {

        private final List<Long> seen = new ArrayList<>();

        @Override
        public Long echo(Long value) {
            seen.add(value);
            return value == null ? null : value + 1;
        }
    }
}
