package run.endive.cm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import run.endive.cm.abi.ResourceValue;
import run.endive.cm.abi.VariantValue;
import run.endive.cm.corpus.ComponentCorpus;
import run.endive.cm.types.WasmComponent;

/**
 * The corpus's {@code types} library, which {@code wit-bindgen} built from {@code
 * rust/types/wit/types.wit}. It imports nothing, so it runs without WASI.
 */
public class TypesLibraryTests {

    private static WasmComponent component;

    private ComponentInstance types;

    @BeforeAll
    static void parse() {
        component = TestComponents.parse(ComponentCorpus.readBytes("types.rs.wasm"));
    }

    @BeforeEach
    void instantiate() {
        types =
                ComponentLinker.builder()
                        .build()
                        .instantiate(new ComponentStore(), component)
                        .exportedInstance("endive:corpus/types");
    }

    @Test
    public void aRecordGoesInAndComesBack() {
        assertEquals(Map.of("x", 4, "y", -2), call("translate", Map.of("x", 1, "y", 2), 3, -4));
    }

    @Test
    public void everyVariantCaseIsLowered() {
        assertEquals(BigInteger.valueOf(9), call("area", VariantValue.of("square", 3L)));
        assertEquals(
                BigInteger.valueOf(12),
                call("area", VariantValue.of("rectangle", Map.of("x", -3, "y", 4))));
        assertEquals(BigInteger.ZERO, call("area", VariantValue.of("dot", null)));
    }

    @Test
    public void aListOfVariantsComesBackAsAnOption() {
        VariantValue square = VariantValue.of("square", 2L);
        VariantValue rectangle = VariantValue.of("rectangle", Map.of("x", 3, "y", -4));

        assertEquals(
                VariantValue.of("some", rectangle),
                call("largest", List.of(square, rectangle, VariantValue.of("dot", null))));
        assertEquals(VariantValue.of("none", null), call("largest", List.of()));
    }

    @Test
    public void anEnumGoesInAndComesBack() {
        assertEquals(
                VariantValue.of("green", null), call("next-color", VariantValue.of("red", null)));
        assertEquals(
                VariantValue.of("red", null), call("next-color", VariantValue.of("blue", null)));
    }

    @Test
    public void flagsGoInAndComeBack() {
        assertEquals(
                Map.of("read", true, "write", false, "execute", true),
                call(
                        "toggle",
                        Map.of("read", true, "write", true),
                        Map.of("write", true, "execute", true)));
    }

    @Test
    public void anOptionCarriesAStringOrNothing() {
        assertEquals(VariantValue.of("some", "hello"), call("first-word", "  hello wide world"));
        assertEquals(VariantValue.of("none", null), call("first-word", "   "));
    }

    @Test
    public void aResultCarriesEitherCase() {
        assertEquals(VariantValue.of("ok", 3), call("checked-divide", 7, 2));
        assertEquals(VariantValue.of("error", "division by zero"), call("checked-divide", 1, 0));
        assertEquals(
                VariantValue.of("error", "overflow"),
                call("checked-divide", Integer.MIN_VALUE, -1));
    }

    @Test
    public void aListOfNumbersIsLowered() {
        assertEquals(-7L, call("sum", List.of(1L, 2L, -10L)));
        assertEquals(0L, call("sum", List.of()));
    }

    /** The list outgrows the guest's initial memory, so lowering it makes the guest grow it. */
    @Test
    public void aLargeListIsLowered() {
        List<Long> values = LongStream.rangeClosed(1, 200_000).boxed().collect(Collectors.toList());

        assertEquals(20_000_100_000L, call("sum", values));
    }

    @Test
    public void aListOfStringsGoesInAndComesBack() {
        assertEquals(
                List.of("three", "two", "one"), call("reverse", List.of("one", "two", "three")));
    }

    @Test
    public void aStringGoesInAndComesBack() {
        assertEquals("Hello, Wörld 🌍!", call("greet", "Wörld 🌍"));
    }

    @Test
    public void aTupleGoesInAndComesBack() {
        assertEquals(
                Map.of("0", 42L, "1", "answer"), call("swap", Map.of("0", "answer", "1", 42L)));
    }

    @Test
    public void aResourceKeepsItsStateBetweenMethodCalls() {
        ResourceValue counter = construct(5L);

        assertEquals(8L, increment(counter, 3L));
        assertEquals(10L, increment(counter, 2L));
    }

    @Test
    public void everyConstructionIsItsOwnResource() {
        ResourceValue first = construct(0L);
        ResourceValue second = construct(100L);

        assertEquals(1L, increment(first, 1L));
        assertEquals(101L, increment(second, 1L));
        assertEquals(2L, call("live-counters"));
    }

    /** Dropping runs the destructor the guest exported, which nothing else would run. */
    @Test
    public void droppingRunsTheGuestDestructor() {
        ResourceValue counter = construct(0L);
        assertEquals(1L, call("live-counters"));

        GuestResource.drop(counter);

        assertEquals(0L, call("live-counters"));
    }

    private ResourceValue construct(long initial) {
        return (ResourceValue) call("[constructor]counter", initial);
    }

    private Object increment(ResourceValue counter, long by) {
        return call("[method]counter.increment", counter, by);
    }

    private Object call(String name, Object... args) {
        Object[] results = types.export(name).apply(args);
        assertEquals(1, results.length);
        return results[0];
    }
}
