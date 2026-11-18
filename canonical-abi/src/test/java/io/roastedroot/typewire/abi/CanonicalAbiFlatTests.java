package io.roastedroot.typewire.abi;

import static org.assertj.core.api.Assertions.assertThat;

import io.roastedroot.typewire.types.Case;
import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.FlagsType;
import io.roastedroot.typewire.types.FuncType;
import io.roastedroot.typewire.types.LabelValType;
import io.roastedroot.typewire.types.ListType;
import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.RecordType;
import io.roastedroot.typewire.types.TypeResolver;
import io.roastedroot.typewire.types.ValType;
import io.roastedroot.typewire.types.VariantType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.Memory;
import run.endive.wasm.types.MemoryLimits;

class CanonicalAbiFlatTests {

    private static final TypeResolver STUB_RESOLVER = index -> null;

    private static final AbiHelper ABI = new AbiHelper(STUB_RESOLVER);

    private static final run.endive.wasm.types.ValType CORE_I32 = run.endive.wasm.types.ValType.I32;
    private static final run.endive.wasm.types.ValType CORE_I64 = run.endive.wasm.types.ValType.I64;

    private static ValType prim(PrimValType t) {
        return ValType.builder().withPrimValType(t).build();
    }

    private static LiftLowerContext newContext() {
        return newContext(STUB_RESOLVER);
    }

    private static LiftLowerContext newContext(TypeResolver resolver) {
        Memory memory = new ByteArrayMemory(new MemoryLimits(1));
        int[] bumpPtr = {100};
        Realloc realloc =
                (oldPtr, oldSize, align, newSize) -> {
                    int ptr = DefValType.alignTo(bumpPtr[0], align);
                    bumpPtr[0] = ptr + newSize;
                    return ptr;
                };
        return LiftLowerContext.builder()
                .withMemory(memory)
                .withPtrType(PointerType.I32)
                .withStringEncoding(StringEncoding.UTF8)
                .withRealloc(realloc)
                .build();
    }

    private static Object roundTrip(LiftLowerContext ctx, Object v, DefValType t) {
        var resolved = ABI.resolve(t);
        long[] flat = CanonicalAbi.lowerFlat(ctx, v, resolved);
        return CanonicalAbi.liftFlat(ctx, new CanonicalAbi.CoreValues(flat), resolved);
    }

    @Test
    void roundTripsACharAboveTheBasicMultilingualPlane() {
        var ctx = newContext();
        assertThat(roundTrip(ctx, CharValue.of(0x1F370), PrimValType.CHAR))
                .isEqualTo(CharValue.of(0x1F370));
    }

    @Test
    void flattenFuncTypeWithinLimitsPassesThroughUnchanged() {
        var ft =
                FuncType.builder()
                        .addParam(
                                LabelValType.builder()
                                        .withLabel("a")
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .withResult(prim(PrimValType.U32))
                        .build();
        var flat =
                ABI.flattenFuncType(
                        LiftLowerContext.builder().withPtrType(PointerType.I32).build(),
                        ft,
                        Direction.LIFT);
        assertThat(flat.params()).containsExactly(CORE_I32);
        assertThat(flat.returns()).containsExactly(CORE_I32);
    }

    @Test
    void flattenFuncTypeCollapsesTooManyParamsToASinglePointer() {
        var builder = FuncType.builder();
        for (int i = 0; i < CanonicalAbi.MAX_FLAT_PARAMS + 1; i++) {
            builder.addParam(
                    LabelValType.builder()
                            .withLabel("p" + i)
                            .withValType(prim(PrimValType.U32))
                            .build());
        }
        var flat =
                ABI.flattenFuncType(
                        LiftLowerContext.builder().withPtrType(PointerType.I32).build(),
                        builder.build(),
                        Direction.LIFT);
        assertThat(flat.params()).containsExactly(CORE_I32);
    }

    @Test
    void flattenFuncTypeTooManyResultsOnLiftUsesPointerResult() {
        // A result type that flattens to more than MAX_FLAT_RESULTS (1) core values.
        var wideRecord =
                RecordType.builder()
                        .addField(
                                LabelValType.builder()
                                        .withLabel("a")
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .addField(
                                LabelValType.builder()
                                        .withLabel("b")
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .build();
        var ft = FuncType.builder().withResult(ValType.builder().withTypeIdx(0).build()).build();
        TypeResolver wideResolver = index -> io.roastedroot.typewire.types.Type.of(wideRecord);
        var ctx = LiftLowerContext.builder().withPtrType(PointerType.I32).build();
        var abi = new AbiHelper(wideResolver);
        var flatLift = abi.flattenFuncType(ctx, ft, Direction.LIFT);
        assertThat(flatLift.returns()).containsExactly(CORE_I32);

        var flatLower = abi.flattenFuncType(ctx, ft, Direction.LOWER);
        assertThat(flatLower.returns()).isEmpty();
        assertThat(flatLower.params()).containsExactly(CORE_I32); // out-param pointer
    }

    @Test
    void roundTripsPrimitivesThroughFlatValues() {
        var ctx = newContext();
        assertThat(roundTrip(ctx, true, PrimValType.BOOL)).isEqualTo(true);
        // Integers lift to the Java wrapper the host binds to each component type.
        assertThat(roundTrip(ctx, 200L, PrimValType.U8)).isEqualTo((short) 200);
        assertThat(roundTrip(ctx, 4_000_000_000L, PrimValType.U32)).isEqualTo(4_000_000_000L);
        assertThat(roundTrip(ctx, -1L, PrimValType.U64))
                .isEqualTo(new BigInteger(Long.toUnsignedString(-1L)));
        assertThat(roundTrip(ctx, -100L, PrimValType.S8)).isEqualTo((byte) -100);
        assertThat(roundTrip(ctx, Long.MIN_VALUE, PrimValType.S64)).isEqualTo(Long.MIN_VALUE);
        assertThat(roundTrip(ctx, 3.5f, PrimValType.F32)).isEqualTo(3.5f);
        assertThat(roundTrip(ctx, 3.5, PrimValType.F64)).isEqualTo(3.5);
        assertThat(roundTrip(ctx, CharValue.of('A'), PrimValType.CHAR))
                .isEqualTo(CharValue.of('A'));
    }

    @Test
    void roundTripsStringThroughFlatValues() {
        var ctx = newContext();
        assertThat(roundTrip(ctx, "hello, 😀", PrimValType.STRING)).isEqualTo("hello, 😀");
    }

    @Test
    void roundTripsFixedAndUnboundedListsThroughFlatValues() {
        var ctx = newContext();
        var fixed =
                ListType.builder().withElementType(prim(PrimValType.U32)).withFixedSize(3).build();
        assertThat(roundTrip(ctx, List.of(1L, 2L, 3L), fixed)).isEqualTo(List.of(1L, 2L, 3L));

        var unbounded = ListType.builder().withElementType(prim(PrimValType.U32)).build();
        assertThat(roundTrip(ctx, List.of(10L, 20L, 30L), unbounded))
                .isEqualTo(List.of(10L, 20L, 30L));
    }

    @Test
    void roundTripsRecordThroughFlatValues() {
        var ctx = newContext();
        var record =
                RecordType.builder()
                        .addField(
                                LabelValType.builder()
                                        .withLabel("a")
                                        .withValType(prim(PrimValType.U8))
                                        .build())
                        .addField(
                                LabelValType.builder()
                                        .withLabel("b")
                                        .withValType(prim(PrimValType.F64))
                                        .build())
                        .build();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("a", 7L);
        value.put("b", 2.5);
        // The u8 field lifts to a Short, matching the host binding for u8.
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("a", (short) 7);
        expected.put("b", 2.5);
        assertThat(roundTrip(ctx, value, record)).isEqualTo(expected);
    }

    @Test
    void roundTripsFlagsThroughFlatValues() {
        var ctx = newContext();
        var flags = FlagsType.builder().addLabel("read").addLabel("write").build();
        Map<String, Boolean> value = new LinkedHashMap<>();
        value.put("read", true);
        value.put("write", false);
        assertThat(roundTrip(ctx, value, flags)).isEqualTo(value);
    }

    @Test
    void variantWithMismatchedCaseTypesCoercesThroughJoinedSlot() {
        var ctx = newContext();
        // join(i32, f64) is neither (i32,f32) nor (f32,i32), so the shared slot is i64.
        var variant =
                VariantType.builder()
                        .addCase(
                                Case.builder()
                                        .withLabel("a")
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .addCase(
                                Case.builder()
                                        .withLabel("b")
                                        .withValType(prim(PrimValType.F64))
                                        .build())
                        .build();
        assertThat(ABI.resolve(variant).flatten(PointerType.I32))
                .containsExactly(CORE_I32, CORE_I64);

        assertThat(roundTrip(ctx, VariantValue.of("a", 4_000_000_000L), variant))
                .isEqualTo(VariantValue.of("a", 4_000_000_000L));
        assertThat(roundTrip(ctx, VariantValue.of("b", 2.5), variant))
                .isEqualTo(VariantValue.of("b", 2.5));
    }

    @Test
    void variantNoPayloadCasePadsUnusedSlotWithZero() {
        var ctx = newContext();
        var variant =
                VariantType.builder()
                        .addCase(Case.builder().withLabel("none").build())
                        .addCase(
                                Case.builder()
                                        .withLabel("some")
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .build();
        long[] lowered =
                CanonicalAbi.lowerFlat(ctx, VariantValue.of("none", null), ABI.resolve(variant));
        assertThat(lowered).containsExactly(0L, 0L);

        assertThat(roundTrip(ctx, VariantValue.of("none", null), variant))
                .isEqualTo(VariantValue.of("none", null));
        assertThat(roundTrip(ctx, VariantValue.of("some", 42L), variant))
                .isEqualTo(VariantValue.of("some", 42L));
    }

    @Test
    void flatParamsRoundTripDirectlyWhenWithinMaxFlat() {
        var ctx = newContext();
        List<ValType> ts = List.of(prim(PrimValType.U32), prim(PrimValType.F64));
        List<Object> vs = List.of(42L, 3.5);

        long[] flat = ABI.lowerFlatParams(ctx, vs, ts);
        // u32 -> [i32], f64 -> [f64]: passed directly as two core values, no spill pointer.
        assertThat(flat).hasSize(2);

        var lifted = ABI.liftFlatParams(ctx, flat, ts);
        assertThat(lifted).containsExactly(42L, 3.5);
    }

    @Test
    void flatParamsSpillThroughMemoryWhenOverMaxFlat() {
        var ctx = newContext();
        // MAX_FLAT_PARAMS + 1 single-slot params exceed the flat limit and spill to memory.
        List<ValType> ts = new ArrayList<>();
        List<Object> vs = new ArrayList<>();
        for (int i = 0; i < CanonicalAbi.MAX_FLAT_PARAMS + 1; i++) {
            ts.add(prim(PrimValType.U32));
            vs.add((long) i);
        }

        long[] flat = ABI.lowerFlatParams(ctx, vs, ts);
        assertThat(flat).hasSize(1); // just the freshly-allocated spill pointer

        var lifted = ABI.liftFlatParams(ctx, flat, ts);
        assertThat(lifted).isEqualTo(vs);
    }

    @Test
    void lowerFlatResultsSpillsIntoCallerOutParamBuffer() {
        // A single result whose record type flattens to [i32, i32] exceeds MAX_FLAT_RESULTS
        // (1) and so spills to memory.
        var wideRecord =
                RecordType.builder()
                        .addField(
                                LabelValType.builder()
                                        .withLabel("a")
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .addField(
                                LabelValType.builder()
                                        .withLabel("b")
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .build();
        TypeResolver wideResolver = index -> io.roastedroot.typewire.types.Type.of(wideRecord);
        var ctx = newContext(wideResolver);
        var abi = new AbiHelper(wideResolver);
        List<ValType> ts = List.of(ValType.builder().withTypeIdx(0).build());
        Map<String, Object> resultValue = new LinkedHashMap<>();
        resultValue.put("a", 7L);
        resultValue.put("b", 9L);
        List<Object> vs = List.of(resultValue);

        int bufPtr = 100;
        long[] flat = abi.lowerFlatResults(ctx, vs, ts, new long[] {bufPtr});
        // With an out-param, the pointer is not returned among the flat values.
        assertThat(flat).isEmpty();

        var lifted = abi.liftFlatResults(ctx, new long[] {bufPtr}, ts);
        assertThat(lifted).containsExactly(resultValue);
    }
}
