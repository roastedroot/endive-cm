package io.roastedroot.typewire.abi;

import static io.roastedroot.typewire.abi.TransferTestSupport.newContext;
import static io.roastedroot.typewire.abi.TransferTestSupport.prim;
import static org.assertj.core.api.Assertions.assertThat;

import io.roastedroot.typewire.types.Case;
import io.roastedroot.typewire.types.FlagsType;
import io.roastedroot.typewire.types.FuncType;
import io.roastedroot.typewire.types.LabelValType;
import io.roastedroot.typewire.types.ListType;
import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.RecordType;
import io.roastedroot.typewire.types.ValType;
import io.roastedroot.typewire.types.VariantType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.runtime.ByteArrayMemory;
import run.endive.wasm.types.MemoryLimits;

class CanonicalAbiIdentityTransferTests {

    private static LiftLowerContext memoryless(TransferTestSupport.Types types) {
        return LiftLowerContext.builder().build();
    }

    private static LabelValType param(String label, ValType t) {
        return LabelValType.builder().withLabel(label).withValType(t).build();
    }

    private static FuncType func(ValType result, ValType... params) {
        var builder = FuncType.builder();
        for (int i = 0; i < params.length; i++) {
            builder.addParam(param("p" + i, params[i]));
        }
        if (result != null) {
            builder.withResult(result);
        }
        return builder.build();
    }

    private static ValType flags(TransferTestSupport.Types types, int labelCount) {
        var builder = FlagsType.builder();
        for (int i = 0; i < labelCount; i++) {
            builder.addLabel("f" + i);
        }
        return types.add(builder.build());
    }

    // --- accepted -----------------------------------------------------------------------

    /**
     * Each entry is a function the predicate must accept, paired with flat core arguments in the
     * canonical (zero-extended) form that the Canonical ABI produces.
     */
    private static Map<String, Supplier<Accepted>> accepted() {
        Map<String, Supplier<Accepted>> cases = new LinkedHashMap<>();
        cases.put(
                "u32 -> u32",
                () -> {
                    var types = new TransferTestSupport.Types();
                    return new Accepted(
                            types,
                            func(prim(PrimValType.U32), prim(PrimValType.U32)),
                            new long[] {0xFFFFFFFFL});
                });
        cases.put(
                "s32, s64 -> s64",
                () -> {
                    var types = new TransferTestSupport.Types();
                    return new Accepted(
                            types,
                            func(
                                    prim(PrimValType.S64),
                                    prim(PrimValType.S32),
                                    prim(PrimValType.S64)),
                            new long[] {0xFFFFFFFFL, Long.MIN_VALUE});
                });
        cases.put(
                "f32, f64 -> f64",
                () -> {
                    var types = new TransferTestSupport.Types();
                    return new Accepted(
                            types,
                            func(
                                    prim(PrimValType.F64),
                                    prim(PrimValType.F32),
                                    prim(PrimValType.F64)),
                            new long[] {
                                Integer.toUnsignedLong(Float.floatToRawIntBits(-1.5f)),
                                Double.doubleToRawLongBits(2.5)
                            });
                });
        cases.put(
                "record of wide integers",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var record =
                            RecordType.builder()
                                    .addField(param("a", prim(PrimValType.U32)))
                                    .addField(param("b", prim(PrimValType.U64)))
                                    .build();
                    return new Accepted(types, func(null, types.add(record)), new long[] {7L, -1L});
                });
        cases.put(
                "fixed-size list of u32",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var list =
                            ListType.builder()
                                    .withElementType(prim(PrimValType.U32))
                                    .withFixedSize(3)
                                    .build();
                    return new Accepted(
                            types, func(null, types.add(list)), new long[] {1L, 2L, 3L});
                });
        cases.put(
                "flags filling the whole i32 slot",
                () -> {
                    var types = new TransferTestSupport.Types();
                    return new Accepted(
                            types, func(null, flags(types, 32)), new long[] {0xFFFFFFFFL});
                });
        cases.put(
                "no params, no result",
                () -> new Accepted(new TransferTestSupport.Types(), func(null), new long[0]));
        return cases;
    }

    static List<Arguments> acceptedCases() {
        var out = new ArrayList<Arguments>();
        accepted().forEach((name, supplier) -> out.add(Arguments.of(name, supplier.get())));
        return out;
    }

    /** A function the predicate should accept, with canonical flat arguments for it. */
    static final class Accepted {
        final TransferTestSupport.Types types;
        final FuncType ft;
        final long[] args;

        Accepted(TransferTestSupport.Types types, FuncType ft, long[] args) {
            this.types = types;
            this.ft = ft;
            this.args = args;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedCases")
    void acceptsFunctionsWhoseValuesNeedNoWork(String name, Accepted sample) {
        assertThat(
                        new AbiHelper(sample.types)
                                .isIdentityTransfer(
                                        memoryless(sample.types),
                                        memoryless(sample.types),
                                        sample.ft))
                .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedCases")
    void acceptedFunctionsReallyTransferUnchanged(String name, Accepted sample) {
        var caller = memoryless(sample.types);
        var callee = memoryless(sample.types);

        long[] transferred =
                new AbiHelper(sample.types)
                        .compile(caller, callee, sample.ft)
                        .transferParams(sample.args);

        assertThat(transferred).isEqualTo(sample.args);
    }

    // --- rejected -----------------------------------------------------------------------

    @Test
    void rejectsTypesWhoseFlatSlotsNeedNormalizing() {
        var types = new TransferTestSupport.Types();
        var caller = memoryless(types);
        var callee = memoryless(types);
        List<PrimValType> needWork =
                List.of(
                        PrimValType.BOOL,
                        PrimValType.CHAR,
                        PrimValType.U8,
                        PrimValType.U16,
                        PrimValType.S8,
                        PrimValType.S16);

        for (PrimValType t : needWork) {
            assertThat(new AbiHelper(types).isIdentityTransfer(caller, callee, func(null, prim(t))))
                    .as("%s needs masking, sign extension or validation", t.kind())
                    .isFalse();
        }
    }

    @Test
    void rejectsSparseFlagsButAcceptsAFullSlot() {
        // In memory 8 labels fill their byte and copy verbatim. Flattened, they sit in an
        // i32 with 24 slack bits that lifting discards.
        var types = new TransferTestSupport.Types();
        var caller = memoryless(types);
        var callee = memoryless(types);

        assertThat(
                        new AbiHelper(types)
                                .isIdentityTransfer(caller, callee, func(null, flags(types, 5))))
                .isFalse();
        assertThat(
                        new AbiHelper(types)
                                .isIdentityTransfer(caller, callee, func(null, flags(types, 8))))
                .as("bitwise-copyable in memory, but not flat-identity")
                .isFalse();
        assertThat(
                        new AbiHelper(types)
                                .isIdentityTransfer(caller, callee, func(null, flags(types, 32))))
                .isTrue();
    }

    @Test
    void rejectsVariantsBecauseTheDiscriminantMustBeValidated() {
        var types = new TransferTestSupport.Types();
        var variant = VariantType.builder().addCase(Case.builder().withLabel("x").build()).build();
        var ft = func(null, types.add(variant));
        var caller = memoryless(types);
        var callee = memoryless(types);

        assertThat(new AbiHelper(types).canTransfer(caller, callee, ft)).isTrue();
        assertThat(new AbiHelper(types).isIdentityTransfer(caller, callee, ft)).isFalse();
    }

    @Test
    void rejectsAnythingThatReachesMemory() {
        var types = new TransferTestSupport.Types();
        var caller = newContext(types);
        var callee = newContext(types);
        var list = types.add(ListType.builder().withElementType(prim(PrimValType.U32)).build());

        assertThat(
                        new AbiHelper(types)
                                .isIdentityTransfer(
                                        caller, callee, func(null, prim(PrimValType.STRING))))
                .isFalse();
        assertThat(new AbiHelper(types).isIdentityTransfer(caller, callee, func(null, list)))
                .isFalse();
    }

    @Test
    void rejectsSpilledParamsAndResults() {
        var types = new TransferTestSupport.Types();
        var caller = newContext(types);
        var callee = newContext(types);

        ValType[] manyParams = new ValType[CanonicalAbi.MAX_FLAT_PARAMS + 1];
        for (int i = 0; i < manyParams.length; i++) {
            manyParams[i] = prim(PrimValType.U32);
        }
        assertThat(new AbiHelper(types).isIdentityTransfer(caller, callee, func(null, manyParams)))
                .as("parameters spill into memory")
                .isFalse();

        // Each field is flat-identity on its own, but two slots is one more than a result
        // may carry, so it spills.
        var wide =
                RecordType.builder()
                        .addField(param("a", prim(PrimValType.U32)))
                        .addField(param("b", prim(PrimValType.U32)))
                        .build();
        assertThat(new AbiHelper(types).isIdentityTransfer(caller, callee, func(types.add(wide))))
                .as("result spills into memory")
                .isFalse();
    }

    @Test
    void rejectsWhateverCanTransferRejects() {
        var types = new TransferTestSupport.Types();
        var ft = func(prim(PrimValType.U32), prim(PrimValType.U32));
        var async =
                LiftLowerContext.builder()
                        .withMemory(new ByteArrayMemory(new MemoryLimits(1)))
                        .withAsync(true)
                        .build();
        var wide = LiftLowerContext.builder().withPtrType(PointerType.I64).build();

        assertThat(new AbiHelper(types).isIdentityTransfer(memoryless(types), async, ft)).isFalse();
        assertThat(new AbiHelper(types).isIdentityTransfer(memoryless(types), wide, ft)).isFalse();
    }

    @Test
    void identityHoldsOnlyToTheWidthOfAnI32Slot() {
        var types = new TransferTestSupport.Types();
        var ft = func(null, prim(PrimValType.U32));
        var transfer = new AbiHelper(types).compile(memoryless(types), memoryless(types), ft);

        long signExtended = -1L; // what InterpreterMachine pushes for i32.const -1
        long[] transferred = transfer.transferParams(new long[] {signExtended});

        assertThat(transferred[0]).isNotEqualTo(signExtended);
        assertThat((int) transferred[0]).isEqualTo((int) signExtended);
    }
}
