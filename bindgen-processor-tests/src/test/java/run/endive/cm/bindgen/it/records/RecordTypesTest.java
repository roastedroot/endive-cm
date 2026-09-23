package run.endive.cm.bindgen.it.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.cm.abi.CharValue;
import run.endive.cm.bindgen.it.Components;
import run.endive.cm.bindgen.it.records.example.records.types.Host;
import run.endive.cm.bindgen.it.records.example.records.types.Person;
import run.endive.cm.bindgen.it.records.example.records.types.Point;
import run.endive.cm.bindgen.it.records.exports.example.records.shapes.Span;
import run.endive.cm.runtime.Bindgen;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.runtime.TrapException;

/**
 * A world declaring records, written for this test because none of the bindgen! examples declares
 * one.
 *
 * <p>The guest traps unless every field of the person it is handed holds what these tests say the
 * host handed over, so a passing call shows the values crossed rather than only that a call
 * happened. A {@code u64} arrives as a {@link BigInteger} and a {@code char} as a {@link
 * CharValue}, so both are given values no {@code Long} or Java {@code char} could carry.
 */
@Bindgen(world = "record-types")
public class RecordTypesTest {

    private static WasmComponent component;

    /** Beyond what a signed long holds, which is why a {@code u64} is not one. */
    private static final BigInteger ID = new BigInteger("9223372036854775817");

    /** U+1F980, which takes a surrogate pair in Java and so does not fit a {@code char}. */
    private static final CharValue INITIAL = CharValue.of(0x1F980);

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/record-types.wat"),
                        Components.text("/wit/record-types.wit"),
                        "record-types");
    }

    /** Every field of the record the host hands over reaches the guest. */
    @Test
    void aRecordReachesTheGuestWithEveryFieldIntact() {
        RecordTypes bindings = instantiate(resident());

        assertEquals(20L, bindings.check());
    }

    /** The trap above is live, so a field the guest does not expect is refused. */
    @Test
    void aFieldTheGuestDoesNotExpectTraps() {
        Person other =
                new Person("eve", true, ID, INITIAL, List.of("hot", "new"), new Point(7L, 11L));

        RecordTypes bindings = instantiate(other);

        assertThrows(TrapException.class, bindings::check);
    }

    /** A nested record is a field like any other, so it crosses with the one holding it. */
    @Test
    void aNestedRecordCrossesWithTheOneHoldingIt() {
        Person other =
                new Person("ada", true, ID, INITIAL, List.of("hot", "new"), new Point(7L, 12L));

        RecordTypes bindings = instantiate(other);

        assertThrows(TrapException.class, bindings::check);
    }

    /** A record travels the other way as an argument and comes back as a result. */
    @Test
    void aRecordCrossesIntoTheGuestAndBack() {
        RecordTypes bindings = instantiate(resident());

        Span widened = bindings.shapes().widen(new Span(2L, 5L), 3L);

        assertEquals(new Span(2L, 8L), widened);
    }

    /** The guest traps on a span it was not promised, so the argument above really arrived. */
    @Test
    void aSpanTheGuestDoesNotExpectTraps() {
        RecordTypes bindings = instantiate(resident());

        assertThrows(TrapException.class, () -> bindings.shapes().widen(new Span(9L, 5L), 3L));
    }

    /** A generated record compares by value, which is what makes it usable as a result. */
    @Test
    void aGeneratedRecordComparesByValue() {
        assertEquals(new Point(1L, 2L), new Point(1L, 2L));
        assertEquals(new Point(1L, 2L).hashCode(), new Point(1L, 2L).hashCode());
        assertNotEquals(new Point(1L, 2L), new Point(2L, 1L));
        assertEquals("Point{x=1, y=2}", new Point(1L, 2L).toString());
    }

    private static Person resident() {
        return new Person("ada", true, ID, INITIAL, List.of("hot", "new"), new Point(7L, 11L));
    }

    private static RecordTypes instantiate(Person person) {
        Host host = () -> person;
        return RecordTypes.instantiate(new ComponentStore(), component, () -> host);
    }
}
