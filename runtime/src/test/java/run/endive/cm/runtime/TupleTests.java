package run.endive.cm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The tuple classes generated bindings hand to an embedder, and the map the Canonical ABI carries
 * a tuple as.
 */
public class TupleTests {

    @Test
    public void aTupleIsCarriedAsAMapLabelledByPosition() {
        Map<String, Object> fields = Tuple3.of("a", 2L, true).toComponent();

        assertEquals(Map.of("0", "a", "1", 2L, "2", true), fields);
    }

    @Test
    public void aLiftedTupleIsCastToTheClassesItWasDeclaredWith() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("0", "a");
        fields.put("1", 2L);

        assertEquals(Tuple2.of("a", 2L), Tuple2.fromComponent(fields, String.class, Long.class));
    }

    @Test
    public void anElementOfAnotherClassIsRefused() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("0", "a");
        fields.put("1", "b");

        assertThrows(
                ClassCastException.class,
                () -> Tuple2.fromComponent(fields, String.class, Long.class));
    }

    @Test
    public void tuplesCompareByTheirElements() {
        assertEquals(Tuple2.of("a", 2L), Tuple2.of("a", 2L));
        assertEquals(Tuple2.of("a", 2L).hashCode(), Tuple2.of("a", 2L).hashCode());
        assertNotEquals(Tuple2.of("a", 2L), Tuple2.of("a", 3L));
        assertEquals("(a, 2)", Tuple2.of("a", 2L).toString());
    }
}
