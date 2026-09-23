package run.endive.cm.bindgen.it.tuples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.cm.bindgen.it.Components;
import run.endive.cm.bindgen.it.tuples.example.tuples.points.Host;
import run.endive.cm.runtime.Bindgen;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.runtime.Tuple2;
import run.endive.cm.types.WasmComponent;
import run.endive.runtime.TrapException;

/**
 * A tuple crossing as a parameter and as a result, in both call directions.
 *
 * <p>The guest traps unless it is handed {@code (7, 11)} and {@code 5}, and again unless the host
 * answers {@code (14, 22)}, so these pass only on values that arrived.
 */
@Bindgen(world = "tuple-types")
public class TupleTypesTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/tuple-types.wat"),
                        Components.text("/wit/tuple-types.wit"),
                        "tuple-types");
    }

    /** A tuple of mixed element types reaches the embedder as the elements the guest wrote. */
    @Test
    void aTupleArgumentReachesTheHost() {
        Recorder recorder = new Recorder();

        instantiate(recorder).shift(Tuple2.of(7L, 11L), 5L);

        assertEquals(List.of(Tuple2.of("origin", 3L)), recorder.points);
    }

    @Test
    void aTupleResultComesBackFromTheGuest() {
        Tuple2<String, Long> shifted = instantiate(new Recorder()).shift(Tuple2.of(7L, 11L), 5L);

        assertEquals(Tuple2.of("shifted", 18L), shifted);
    }

    /** The guest checks its arguments, so a tuple it does not expect traps. */
    @Test
    void aTupleTheGuestDoesNotExpectTraps() {
        TupleTypes bindings = instantiate(new Recorder());

        assertThrows(TrapException.class, () -> bindings.shift(Tuple2.of(1L, 2L), 5L));
    }

    /** The guest checks what the host answered, which is the other direction of the same claim. */
    @Test
    void aTupleTheHostAnswersWithWronglyTraps() {
        TupleTypes bindings = instantiate(point -> Tuple2.of(0L, 0L));

        assertThrows(TrapException.class, () -> bindings.shift(Tuple2.of(7L, 11L), 5L));
    }

    private static TupleTypes instantiate(Host points) {
        return TupleTypes.instantiate(new ComponentStore(), component, () -> points);
    }

    /** The host side of {@code example:tuples/points}. */
    private static final class Recorder implements Host {

        private final List<Tuple2<String, Long>> points = new ArrayList<>();

        @Override
        public Tuple2<Long, Long> describe(Tuple2<String, Long> point) {
            points.add(point);
            return Tuple2.of(14L, 22L);
        }
    }
}
