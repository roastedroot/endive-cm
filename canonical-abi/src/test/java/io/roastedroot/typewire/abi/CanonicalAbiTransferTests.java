package io.roastedroot.typewire.abi;

import static io.roastedroot.typewire.abi.TransferTestSupport.assertCompiledPlanMatchesInterpreted;
import static io.roastedroot.typewire.abi.TransferTestSupport.assertTransferMatchesLiftLower;
import static io.roastedroot.typewire.abi.TransferTestSupport.flags;
import static io.roastedroot.typewire.abi.TransferTestSupport.newContext;
import static io.roastedroot.typewire.abi.TransferTestSupport.prim;
import static io.roastedroot.typewire.abi.TransferTestSupport.record;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.roastedroot.typewire.types.Case;
import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.FlagsType;
import io.roastedroot.typewire.types.LabelValType;
import io.roastedroot.typewire.types.ListType;
import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.RecordType;
import io.roastedroot.typewire.types.ResolvedType;
import io.roastedroot.typewire.types.TupleType;
import io.roastedroot.typewire.types.TypeSpace;
import io.roastedroot.typewire.types.VariantType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.runtime.TrapException;

class CanonicalAbiTransferTests {

    private static final List<String> FIVE_LABELS = List.of("a", "b", "c", "d", "e");
    private static final List<String> EIGHT_LABELS =
            List.of("a", "b", "c", "d", "e", "f", "g", "h");

    @Test
    void transfersIntegers() {
        var types = new TransferTestSupport.Types();
        assertTransferMatchesLiftLower(types, (short) 200, PrimValType.U8);
        assertTransferMatchesLiftLower(types, 60000, PrimValType.U16);
        assertTransferMatchesLiftLower(types, 4_000_000_000L, PrimValType.U32);
        assertTransferMatchesLiftLower(
                types, new BigInteger("18446744073709551615"), PrimValType.U64);
        assertTransferMatchesLiftLower(types, (byte) -100, PrimValType.S8);
        assertTransferMatchesLiftLower(types, (short) -30000, PrimValType.S16);
        assertTransferMatchesLiftLower(types, -2_000_000_000, PrimValType.S32);
        assertTransferMatchesLiftLower(types, Long.MIN_VALUE, PrimValType.S64);
    }

    @Test
    void transfersFloats() {
        var types = new TransferTestSupport.Types();
        assertTransferMatchesLiftLower(types, 3.5f, PrimValType.F32);
        assertTransferMatchesLiftLower(types, -0.0f, PrimValType.F32);
        assertTransferMatchesLiftLower(types, Float.POSITIVE_INFINITY, PrimValType.F32);
        assertTransferMatchesLiftLower(types, 1.0e300d, PrimValType.F64);
        assertTransferMatchesLiftLower(types, -0.0d, PrimValType.F64);
    }

    @Test
    void transfersBoolChar() {
        var types = new TransferTestSupport.Types();
        assertTransferMatchesLiftLower(types, true, PrimValType.BOOL);
        assertTransferMatchesLiftLower(types, false, PrimValType.BOOL);
        assertTransferMatchesLiftLower(types, CharValue.of('x'), PrimValType.CHAR);
        assertTransferMatchesLiftLower(types, CharValue.of(0x1F600), PrimValType.CHAR);
    }

    @Test
    void transfersString() {
        var types = new TransferTestSupport.Types();
        assertTransferMatchesLiftLower(types, "", PrimValType.STRING);
        assertTransferMatchesLiftLower(types, "hello", PrimValType.STRING);
        assertTransferMatchesLiftLower(types, "café 😀", PrimValType.STRING);
    }

    @Test
    void transfersRecordOfMixedWidths() {
        var types = new TransferTestSupport.Types();
        var t =
                RecordType.builder()
                        .addField(field("a", prim(PrimValType.U8)))
                        .addField(field("b", prim(PrimValType.U32)))
                        .addField(field("c", prim(PrimValType.S16)))
                        .build();
        assertTransferMatchesLiftLower(
                types, record("a", (short) 7, "b", 123456L, "c", (short) -9), t);
    }

    @Test
    void transfersRecordContainingAString() {
        var types = new TransferTestSupport.Types();
        var t =
                RecordType.builder()
                        .addField(field("n", prim(PrimValType.U32)))
                        .addField(field("s", prim(PrimValType.STRING)))
                        .addField(field("m", prim(PrimValType.U32)))
                        .build();
        assertTransferMatchesLiftLower(types, record("n", 1L, "s", "embedded", "m", 2L), t);
    }

    @Test
    void transfersUnboundedListOfPrimitives() {
        var types = new TransferTestSupport.Types();
        var t = ListType.builder().withElementType(prim(PrimValType.U32)).build();
        assertTransferMatchesLiftLower(types, List.of(1L, 2L, 3L, 4L), t);
    }

    @Test
    void transfersUnboundedListOfStrings() {
        var types = new TransferTestSupport.Types();
        var t = ListType.builder().withElementType(prim(PrimValType.STRING)).build();
        assertTransferMatchesLiftLower(types, List.of("one", "two", "three"), t);
    }

    @Test
    void transfersFixedSizeList() {
        var types = new TransferTestSupport.Types();
        var t = ListType.builder().withElementType(prim(PrimValType.U16)).withFixedSize(3).build();
        assertTransferMatchesLiftLower(types, List.of(1, 2, 3), t);
    }

    @Test
    void transfersNestedList() {
        var types = new TransferTestSupport.Types();
        var inner = types.add(ListType.builder().withElementType(prim(PrimValType.U8)).build());
        var t = ListType.builder().withElementType(inner).build();
        assertTransferMatchesLiftLower(
                types, List.of(List.of((short) 1, (short) 2), List.of((short) 3)), t);
    }

    @Test
    void transfersVariantWithAndWithoutPayload() {
        var types = new TransferTestSupport.Types();
        var t =
                VariantType.builder()
                        .addCase(Case.builder().withLabel("none").build())
                        .addCase(
                                Case.builder()
                                        .withLabel("num")
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .addCase(
                                Case.builder()
                                        .withLabel("text")
                                        .withValType(prim(PrimValType.STRING))
                                        .build())
                        .build();
        assertTransferMatchesLiftLower(types, VariantValue.of("none", null), t);
        assertTransferMatchesLiftLower(types, VariantValue.of("num", 42L), t);
        assertTransferMatchesLiftLower(types, VariantValue.of("text", "payload"), t);
    }

    @Test
    void transfersFlags() {
        var types = new TransferTestSupport.Types();
        var sparse = flagsType(FIVE_LABELS);
        assertTransferMatchesLiftLower(types, flags(FIVE_LABELS, "a", "c"), sparse);

        var dense = flagsType(EIGHT_LABELS);
        assertTransferMatchesLiftLower(types, flags(EIGHT_LABELS, "b", "h"), dense);
    }

    @Test
    void transfersTupleThroughDespecialization() {
        var types = new TransferTestSupport.Types();
        var t =
                TupleType.builder()
                        .addElementType(prim(PrimValType.U32))
                        .addElementType(prim(PrimValType.STRING))
                        .build();
        assertTransferMatchesLiftLower(types, record("0", 5L, "1", "tuple"), t);
    }

    // --- normalizations the transfer path must reproduce -------------------------------

    @Test
    void normalizesNonZeroBoolByteToOne() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        src.memory().writeByte(0, (byte) 0x02);

        new AbiHelper(types).transfer(src, dst, 0, 0, PrimValType.BOOL);

        assertThat(dst.memory().read(0)).isEqualTo((byte) 1);
        assertThat(new AbiHelper(types).load(dst, 0, PrimValType.BOOL)).isEqualTo(true);
    }

    @Test
    void dropsFlagBitsAboveTheLabelCount() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        var t = flagsType(FIVE_LABELS);
        // Five labels occupy one byte and the top three bits are slack that lifting discards.
        src.memory().writeByte(0, (byte) 0xFF);

        new AbiHelper(types).transfer(src, dst, 0, 0, t);

        assertThat(dst.memory().read(0)).isEqualTo((byte) 0x1F);
    }

    @Test
    void keepsAllFlagBitsWhenLabelsFillTheWidth() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        src.memory().writeByte(0, (byte) 0xFF);

        new AbiHelper(types).transfer(src, dst, 0, 0, flagsType(EIGHT_LABELS));

        assertThat(dst.memory().read(0)).isEqualTo((byte) 0xFF);
    }

    @Test
    void preservesNonCanonicalNanPayloads() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        // A signaling NaN with a payload the load/store path would canonicalize away.
        int nonCanonicalBits = 0x7f800001;
        src.memory().writeI32(0, nonCanonicalBits);

        new AbiHelper(types).transfer(src, dst, 0, 0, PrimValType.F32);

        assertThat(dst.memory().readInt(0)).isEqualTo(nonCanonicalBits);
        // The lift/lower path, by contrast, replaces it with the canonical NaN.
        var canonical = newContext(types);
        new AbiHelper(types)
                .store(
                        canonical,
                        new AbiHelper(types).load(src, 0, PrimValType.F32),
                        PrimValType.F32,
                        0);
        assertThat(canonical.memory().readInt(0)).isEqualTo(0x7fc00000);
    }

    @Test
    void transferCarriesSourcePaddingWhileLiftLowerDoesNot() {
        var types = new TransferTestSupport.Types();
        var t =
                RecordType.builder()
                        .addField(field("a", prim(PrimValType.U8)))
                        .addField(field("b", prim(PrimValType.U32)))
                        .build();
        var src = newContext(types);
        var dst = newContext(types);
        new AbiHelper(types).store(src, record("a", (short) 1, "b", 2L), t, 0);
        // Dirty the three padding bytes between the u8 and the u32.
        src.memory().writeByte(1, (byte) 0xAA);
        src.memory().writeByte(2, (byte) 0xBB);
        src.memory().writeByte(3, (byte) 0xCC);

        new AbiHelper(types).transfer(src, dst, 0, 0, t);

        // The fields agree, which is all the Canonical ABI defines
        assertThat(new AbiHelper(types).load(dst, 0, t))
                .isEqualTo(new AbiHelper(types).load(src, 0, t));
        // The padding rode along because coalescing copies the whole record at once.
        assertThat(dst.memory().read(1)).isEqualTo((byte) 0xAA);
    }

    // --- trap parity -------------------------------------------------------------------

    @Test
    void trapsOnSurrogateChar() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        src.memory().writeI32(0, 0xD800);

        assertThatThrownBy(() -> new AbiHelper(types).transfer(src, dst, 0, 0, PrimValType.CHAR))
                .isInstanceOf(TrapException.class);
        assertThatThrownBy(() -> new AbiHelper(types).load(src, 0, PrimValType.CHAR))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void trapsOnCharAboveMaximumScalarValue() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        src.memory().writeI32(0, 0x110000);

        assertThatThrownBy(() -> new AbiHelper(types).transfer(src, dst, 0, 0, PrimValType.CHAR))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void trapsOnOutOfRangeVariantDiscriminant() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        var t =
                VariantType.builder()
                        .addCase(Case.builder().withLabel("a").build())
                        .addCase(Case.builder().withLabel("b").build())
                        .build();
        src.memory().writeByte(0, (byte) 7);

        assertThatThrownBy(() -> new AbiHelper(types).transfer(src, dst, 0, 0, t))
                .isInstanceOf(TrapException.class);
        assertThatThrownBy(() -> new AbiHelper(types).load(src, 0, t))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void trapsOnMisalignedListPointer() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        var t = ListType.builder().withElementType(prim(PrimValType.U32)).build();
        src.memory().writeI32(0, 101); // not 4-byte aligned
        src.memory().writeI32(4, 1);

        assertThatThrownBy(() -> new AbiHelper(types).transfer(src, dst, 0, 0, t))
                .isInstanceOf(TrapException.class);
        assertThatThrownBy(() -> new AbiHelper(types).load(src, 0, t))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void trapsOnListByteLengthAboveTheMaximum() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        var t = ListType.builder().withElementType(prim(PrimValType.U32)).build();
        src.memory().writeI32(0, 0);
        src.memory().writeI32(4, (1 << 28)); // 2^28 elements of 4 bytes each

        assertThatThrownBy(() -> new AbiHelper(types).transfer(src, dst, 0, 0, t))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void trapsOnListLengthThatOverflowsASignedInt() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        var t = ListType.builder().withElementType(prim(PrimValType.U8)).build();
        src.memory().writeI32(0, 0);
        src.memory().writeI32(4, 0x80000000); // 2^31 elements, negative as a signed int

        assertThatThrownBy(() -> new AbiHelper(types).transfer(src, dst, 0, 0, t))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void trapsOnInvalidUtf8() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst = newContext(types);
        src.memory().writeI32(0, 100);
        src.memory().writeI32(4, 1);
        src.memory().writeByte(100, (byte) 0xFF);

        assertThatThrownBy(() -> new AbiHelper(types).transfer(src, dst, 0, 0, PrimValType.STRING))
                .isInstanceOf(TrapException.class);
        assertThatThrownBy(() -> new AbiHelper(types).load(src, 0, PrimValType.STRING))
                .isInstanceOf(TrapException.class);
    }

    @Test
    void rejectsMismatchedPointerWidths() {
        var types = new TransferTestSupport.Types();
        var src = newContext(types);
        var dst =
                LiftLowerContext.builder()
                        .withMemory(newContext(types).memory())
                        .withPtrType(PointerType.I64)
                        .build();

        assertThatThrownBy(() -> new AbiHelper(types).transfer(src, dst, 0, 0, PrimValType.U32))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- compiled plan parity ----------------------------------------------------------

    @Test
    void compiledPlanMatchesInterpretedTransfer() {
        var types = new TransferTestSupport.Types();
        var t =
                RecordType.builder()
                        .addField(field("a", prim(PrimValType.U8)))
                        .addField(field("b", prim(PrimValType.U32)))
                        .addField(field("s", prim(PrimValType.STRING)))
                        .addField(field("f", prim(PrimValType.BOOL)))
                        .build();
        assertCompiledPlanMatchesInterpreted(
                types,
                StringEncoding.UTF8,
                StringEncoding.UTF8,
                record("a", (short) 3, "b", 9L, "s", "plan", "f", true),
                t);
    }

    @Test
    void compiledPlanCoalescesAdjacentFieldsAcrossPadding() {
        var types = new TransferTestSupport.Types();
        var t =
                RecordType.builder()
                        .addField(field("a", prim(PrimValType.U8)))
                        .addField(field("b", prim(PrimValType.U32)))
                        .addField(field("c", prim(PrimValType.S16)))
                        .build();

        var plan = TransferPlan.compile(PointerType.I32, ResolvedType.of(t, TypeSpace.of(types)));

        assertThat(plan.stepCount())
                .as("three integer fields and their padding should collapse to one copy")
                .isEqualTo(1);
    }

    @Test
    void compiledPlanBreaksCopyRunsAtFieldsNeedingWork() {
        var types = new TransferTestSupport.Types();
        var t =
                RecordType.builder()
                        .addField(field("a", prim(PrimValType.U32)))
                        .addField(field("b", prim(PrimValType.U32)))
                        .addField(field("s", prim(PrimValType.STRING)))
                        .addField(field("c", prim(PrimValType.U32)))
                        .build();

        var plan = TransferPlan.compile(PointerType.I32, ResolvedType.of(t, TypeSpace.of(types)));

        // a+b coalesce, the string is its own step, then c.
        assertThat(plan.stepCount()).isEqualTo(3);
    }

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @MethodSource("corpusAcrossEncodings")
    void everySampleAgreesAcrossBothPathsAndEncodings(
            String name, StringEncoding srcEncoding, StringEncoding dstEncoding, Sample sample) {
        assertTransferMatchesLiftLower(
                sample.types, srcEncoding, dstEncoding, sample.value, sample.type);
        assertCompiledPlanMatchesInterpreted(
                sample.types, srcEncoding, dstEncoding, sample.value, sample.type);
    }

    static List<Arguments> corpusAcrossEncodings() {
        var encodings =
                List.of(StringEncoding.UTF8, StringEncoding.UTF16, StringEncoding.LATIN1_UTF16);
        var out = new ArrayList<Arguments>();
        for (Map.Entry<String, Supplier<Sample>> entry : corpus().entrySet()) {
            for (StringEncoding src : encodings) {
                for (StringEncoding dst : encodings) {
                    out.add(Arguments.of(entry.getKey(), src, dst, entry.getValue().get()));
                }
            }
        }
        return out;
    }

    /** A type paired with a value of it, plus the type table the type resolves against. */
    static final class Sample {
        final TransferTestSupport.Types types;
        final DefValType type;
        final Object value;

        Sample(TransferTestSupport.Types types, DefValType type, Object value) {
            this.types = types;
            this.type = type;
            this.value = value;
        }
    }

    private static Map<String, Supplier<Sample>> corpus() {
        Map<String, Supplier<Sample>> corpus = new LinkedHashMap<>();
        corpus.put(
                "record of integers",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var t =
                            RecordType.builder()
                                    .addField(field("a", prim(PrimValType.U8)))
                                    .addField(field("b", prim(PrimValType.U64)))
                                    .addField(field("c", prim(PrimValType.S16)))
                                    .build();
                    return new Sample(
                            types,
                            t,
                            record(
                                    "a",
                                    (short) 250,
                                    "b",
                                    BigInteger.valueOf(1234567890123L),
                                    "c",
                                    (short) -1));
                });
        corpus.put(
                "record with a string between integers",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var t =
                            RecordType.builder()
                                    .addField(field("n", prim(PrimValType.U32)))
                                    .addField(field("s", prim(PrimValType.STRING)))
                                    .addField(field("m", prim(PrimValType.U32)))
                                    .build();
                    return new Sample(types, t, record("n", 1L, "s", "café 😀", "m", 2L));
                });
        corpus.put(
                "record with bool, char and flags",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var t =
                            RecordType.builder()
                                    .addField(field("b", prim(PrimValType.BOOL)))
                                    .addField(field("c", prim(PrimValType.CHAR)))
                                    .addField(field("f", types.add(flagsType(FIVE_LABELS))))
                                    .build();
                    return new Sample(
                            types,
                            t,
                            record(
                                    "b",
                                    true,
                                    "c",
                                    CharValue.of(0x1F600),
                                    "f",
                                    flags(FIVE_LABELS, "b", "e")));
                });
        corpus.put(
                "list of strings",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var t = ListType.builder().withElementType(prim(PrimValType.STRING)).build();
                    return new Sample(types, t, List.of("", "日本語", "plain"));
                });
        corpus.put(
                "list of records",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var element =
                            RecordType.builder()
                                    .addField(field("k", prim(PrimValType.U16)))
                                    .addField(field("v", prim(PrimValType.STRING)))
                                    .build();
                    var t = ListType.builder().withElementType(types.add(element)).build();
                    return new Sample(
                            types,
                            t,
                            List.of(record("k", 1, "v", "one"), record("k", 2, "v", "två")));
                });
        corpus.put(
                "list of floats",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var t = ListType.builder().withElementType(prim(PrimValType.F64)).build();
                    return new Sample(types, t, List.of(1.5d, -2.25d, 0.0d));
                });
        corpus.put(
                "variant carrying a string",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var t =
                            VariantType.builder()
                                    .addCase(Case.builder().withLabel("none").build())
                                    .addCase(
                                            Case.builder()
                                                    .withLabel("text")
                                                    .withValType(prim(PrimValType.STRING))
                                                    .build())
                                    .build();
                    return new Sample(types, t, VariantValue.of("text", "variant é"));
                });
        corpus.put(
                "variant selecting the empty case",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var t =
                            VariantType.builder()
                                    .addCase(Case.builder().withLabel("none").build())
                                    .addCase(
                                            Case.builder()
                                                    .withLabel("text")
                                                    .withValType(prim(PrimValType.STRING))
                                                    .build())
                                    .build();
                    return new Sample(types, t, VariantValue.of("none", null));
                });
        corpus.put(
                "fixed-size list of records with strings",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var element =
                            RecordType.builder()
                                    .addField(field("s", prim(PrimValType.STRING)))
                                    .addField(field("n", prim(PrimValType.U32)))
                                    .build();
                    var t =
                            ListType.builder()
                                    .withElementType(types.add(element))
                                    .withFixedSize(2)
                                    .build();
                    return new Sample(
                            types,
                            t,
                            List.of(record("s", "a", "n", 1L), record("s", "ø", "n", 2L)));
                });
        corpus.put(
                "nested record",
                () -> {
                    var types = new TransferTestSupport.Types();
                    var inner =
                            RecordType.builder()
                                    .addField(field("x", prim(PrimValType.U32)))
                                    .addField(field("y", prim(PrimValType.U32)))
                                    .build();
                    var t =
                            RecordType.builder()
                                    .addField(field("head", prim(PrimValType.U8)))
                                    .addField(field("inner", types.add(inner)))
                                    .addField(field("tail", prim(PrimValType.S64)))
                                    .build();
                    return new Sample(
                            types,
                            t,
                            record(
                                    "head",
                                    (short) 9,
                                    "inner",
                                    record("x", 4L, "y", 5L),
                                    "tail",
                                    -77L));
                });
        return corpus;
    }

    static LabelValType field(String label, io.roastedroot.typewire.types.ValType t) {
        return LabelValType.builder().withLabel(label).withValType(t).build();
    }

    static DefValType flagsType(List<String> labels) {
        var builder = FlagsType.builder();
        for (String label : labels) {
            builder.addLabel(label);
        }
        return builder.build();
    }
}
