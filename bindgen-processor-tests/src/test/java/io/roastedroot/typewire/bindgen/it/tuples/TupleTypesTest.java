package io.roastedroot.typewire.bindgen.it.tuples;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.tuples.example.tuples.points.Host;
import io.roastedroot.typewire.runtime.Bindgen;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.runtime.Tuple2;
import io.roastedroot.typewire.types.WasmComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.runtime.TrapException;

/**
 * A tuple crossing as a parameter and as a result, in both call directions.
 *
 * <p>The guest traps unless it is handed {@code (7, 11)} and {@code 5}, and again unless the host
 * answers {@code (14, 22)}, so these pass only on values that arrived. Its {@code check-reverse}
 * traps unless the host answers {@code ([255, 2, 1], false)} to {@code ([1, 2, 255], true)}.
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
        TupleTypesWorld bindings = instantiate(new Recorder());

        assertThrows(TrapException.class, () -> bindings.shift(Tuple2.of(1L, 2L), 5L));
    }

    /** The guest checks what the host answered, which is the other direction of the same claim. */
    @Test
    void aTupleTheHostAnswersWithWronglyTraps() {
        TupleTypesWorld bindings = instantiate(new Recorder(Tuple2.of(0L, 0L)));

        assertThrows(TrapException.class, () -> bindings.shift(Tuple2.of(7L, 11L), 5L));
    }

    /** Each element of a tuple that converts at the boundary arrives converted. */
    @Test
    void aConvertedTupleArgumentReachesTheHost() {
        Recorder recorder = new Recorder();

        instantiate(recorder).checkReverse();

        assertEquals(1, recorder.chunks.size());
        assertArrayEquals(new byte[] {1, 2, (byte) 255}, recorder.chunks.get(0).f0());
        assertEquals(true, recorder.chunks.get(0).f1());
    }

    /** The guest checks the converted tuple in the host's answer. */
    @Test
    void aConvertedTupleTheHostAnswersWithWronglyTraps() {
        TupleTypesWorld bindings =
                instantiate(
                        new Recorder() {
                            @Override
                            public Tuple2<byte[], Boolean> reverse(Tuple2<byte[], Boolean> chunk) {
                                return Tuple2.of(chunk.f0(), chunk.f1());
                            }
                        });

        assertThrows(TrapException.class, bindings::checkReverse);
    }

    private static TupleTypesWorld instantiate(Host points) {
        return TupleTypesWorld.instantiate(new ComponentStore(), component, () -> points);
    }

    /** The host side of {@code example:tuples/points}. */
    private static class Recorder implements Host {

        private final List<Tuple2<String, Long>> points = new ArrayList<>();
        private final List<Tuple2<byte[], Boolean>> chunks = new ArrayList<>();
        private final Tuple2<Long, Long> described;

        Recorder() {
            this(Tuple2.of(14L, 22L));
        }

        Recorder(Tuple2<Long, Long> described) {
            this.described = described;
        }

        @Override
        public Tuple2<Long, Long> describe(Tuple2<String, Long> point) {
            points.add(point);
            return described;
        }

        @Override
        public Tuple2<byte[], Boolean> reverse(Tuple2<byte[], Boolean> chunk) {
            chunks.add(chunk);
            byte[] bytes = chunk.f0();
            byte[] reversed = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                reversed[i] = bytes[bytes.length - 1 - i];
            }
            return Tuple2.of(reversed, !chunk.f1());
        }
    }
}
