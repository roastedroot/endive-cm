package io.roastedroot.typewire.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DefValTypeTest {

    private static ValType prim(PrimValType t) {
        return ValType.builder().withPrimValType(t).build();
    }

    private static ValType typeIdx(int idx) {
        return ValType.builder().withTypeIdx(idx).build();
    }

    private static final TypeResolver stubResolver = index -> null;

    /** Grounds a type against the local index space, which is what gives it a layout. */
    private static ResolvedType g(DefValType t) {
        return ResolvedType.of(t, TypeSpace.of(stubResolver));
    }

    private static final run.endive.wasm.types.ValType CORE_I32 = run.endive.wasm.types.ValType.I32;
    private static final run.endive.wasm.types.ValType CORE_I64 = run.endive.wasm.types.ValType.I64;
    private static final run.endive.wasm.types.ValType CORE_F32 = run.endive.wasm.types.ValType.F32;
    private static final run.endive.wasm.types.ValType CORE_F64 = run.endive.wasm.types.ValType.F64;

    @Test
    void primitiveAlignmentAndSize() {
        assertEquals(1, g(PrimValType.BOOL).alignment(PointerType.I32));
        assertEquals(1, g(PrimValType.BOOL).elementSize(PointerType.I32));

        assertEquals(1, g(PrimValType.U8).alignment(PointerType.I32));
        assertEquals(1, g(PrimValType.U8).elementSize(PointerType.I32));

        assertEquals(2, g(PrimValType.S16).alignment(PointerType.I32));
        assertEquals(2, g(PrimValType.S16).elementSize(PointerType.I32));

        assertEquals(4, g(PrimValType.U32).alignment(PointerType.I32));
        assertEquals(4, g(PrimValType.U32).elementSize(PointerType.I32));

        assertEquals(8, g(PrimValType.S64).alignment(PointerType.I32));
        assertEquals(8, g(PrimValType.S64).elementSize(PointerType.I32));

        assertEquals(4, g(PrimValType.F32).alignment(PointerType.I32));
        assertEquals(4, g(PrimValType.F32).elementSize(PointerType.I32));

        assertEquals(8, g(PrimValType.F64).alignment(PointerType.I32));
        assertEquals(8, g(PrimValType.F64).elementSize(PointerType.I32));

        assertEquals(4, g(PrimValType.CHAR).alignment(PointerType.I32));
        assertEquals(4, g(PrimValType.CHAR).elementSize(PointerType.I32));

        assertEquals(4, g(PrimValType.ERROR_CONTEXT).alignment(PointerType.I32));
        assertEquals(4, g(PrimValType.ERROR_CONTEXT).elementSize(PointerType.I32));
    }

    @Test
    void stringIsPointerLengthPair() {
        assertEquals(4, g(PrimValType.STRING).alignment(PointerType.I32));
        assertEquals(8, g(PrimValType.STRING).elementSize(PointerType.I32));
    }

    @Test
    void unboundedListIsPointerLengthPairRegardlessOfElementType() {
        var list = ListType.builder().withElementType(prim(PrimValType.U64)).build();
        assertEquals(4, g(list).alignment(PointerType.I32));
        assertEquals(8, g(list).elementSize(PointerType.I32));
    }

    @Test
    void fixedSizeListScalesWithElementType() {
        var list =
                ListType.builder().withElementType(prim(PrimValType.U32)).withFixedSize(3).build();
        assertEquals(4, g(list).alignment(PointerType.I32));
        assertEquals(12, g(list).elementSize(PointerType.I32));
    }

    @Test
    void recordAlignsToWidestFieldAndPacksWithPadding() {
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
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .build();
        assertEquals(4, g(record).alignment(PointerType.I32));
        assertEquals(8, g(record).elementSize(PointerType.I32));
    }

    @Test
    void variantSizesDiscriminantPlusWidestCasePayload() {
        // [0]=discriminant, pad to 4, [4..8)=payload -> size 8, align 4
        var variant =
                VariantType.builder()
                        .addCase(
                                Case.builder()
                                        .withLabel("a")
                                        .withValType(prim(PrimValType.U8))
                                        .build())
                        .addCase(
                                Case.builder()
                                        .withLabel("b")
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .build();
        assertEquals(4, g(variant).alignment(PointerType.I32));
        assertEquals(8, g(variant).elementSize(PointerType.I32));
    }

    @Test
    void variantDiscriminantWidensWithCaseCount() {
        // 256 payload-less cases -> u8 discriminant (align/size 1)
        assertEquals(1, g(variantWithNCases(256)).alignment(PointerType.I32));
        assertEquals(1, g(variantWithNCases(256)).elementSize(PointerType.I32));

        // 257 payload-less cases -> u16 discriminant (align/size 2)
        assertEquals(2, g(variantWithNCases(257)).alignment(PointerType.I32));
        assertEquals(2, g(variantWithNCases(257)).elementSize(PointerType.I32));
    }

    @Test
    void flagsSizeGrowsWithLabelCount() {
        assertEquals(1, g(flags(4)).alignment(PointerType.I32));
        assertEquals(1, g(flags(4)).elementSize(PointerType.I32));

        assertEquals(2, g(flags(10)).alignment(PointerType.I32));
        assertEquals(2, g(flags(10)).elementSize(PointerType.I32));

        assertEquals(4, g(flags(20)).alignment(PointerType.I32));
        assertEquals(4, g(flags(20)).elementSize(PointerType.I32));
    }

    @Test
    void handleTypesAreFourBytes() {
        var own = OwnType.builder().withTypeIdx(0).build();
        var borrow = BorrowType.builder().withTypeIdx(0).build();
        assertEquals(4, g(own).alignment(PointerType.I32));
        assertEquals(4, g(own).elementSize(PointerType.I32));
        assertEquals(4, g(borrow).alignment(PointerType.I32));
        assertEquals(4, g(borrow).elementSize(PointerType.I32));
    }

    @Test
    void streamAndFutureAreFourBytes() {
        var stream = StreamType.builder().withElementType(prim(PrimValType.U8)).build();
        var future = FutureType.builder().withElementType(prim(PrimValType.U8)).build();
        assertEquals(4, g(stream).alignment(PointerType.I32));
        assertEquals(4, g(stream).elementSize(PointerType.I32));
        assertEquals(4, g(future).alignment(PointerType.I32));
        assertEquals(4, g(future).elementSize(PointerType.I32));
    }

    @Test
    void mapIsTreatedAsUnboundedList() {
        var map =
                MapType.builder()
                        .withKeyType(prim(PrimValType.STRING))
                        .withValueType(prim(PrimValType.U64))
                        .build();
        assertEquals(4, g(map).alignment(PointerType.I32));
        assertEquals(8, g(map).elementSize(PointerType.I32));
    }

    @Test
    void tupleDespecializesToRecordOfIndexedFields() {
        var tuple =
                TupleType.builder()
                        .addElementType(prim(PrimValType.U8))
                        .addElementType(prim(PrimValType.U32))
                        .build();
        var record = tuple.despecialize();
        assertEquals(
                List.of("0", "1"),
                record.fields().stream().map(LabelValType::label).collect(Collectors.toList()));
        assertEquals(4, g(tuple).alignment(PointerType.I32));
        assertEquals(8, g(tuple).elementSize(PointerType.I32));
    }

    @Test
    void enumDespecializesToVariantOfPayloadlessCases() {
        var enumType =
                EnumType.builder().addLabel("red").addLabel("green").addLabel("blue").build();
        var variant = enumType.despecialize();
        assertEquals(
                List.of("red", "green", "blue"),
                variant.cases().stream().map(Case::label).collect(Collectors.toList()));
        assertEquals(1, g(enumType).alignment(PointerType.I32));
        assertEquals(1, g(enumType).elementSize(PointerType.I32));
    }

    @Test
    void optionDespecializesToNoneSomeVariant() {
        var option = OptionType.builder().withValType(prim(PrimValType.U32)).build();
        var variant = option.despecialize();
        assertEquals(
                List.of("none", "some"),
                variant.cases().stream().map(Case::label).collect(Collectors.toList()));
        assertEquals(4, g(option).alignment(PointerType.I32));
        assertEquals(8, g(option).elementSize(PointerType.I32));
    }

    @Test
    void resultDespecializesToOkErrorVariant() {
        var result = ResultType.builder().withOk(prim(PrimValType.U32)).build();
        var variant = result.despecialize();
        assertEquals(
                List.of("ok", "error"),
                variant.cases().stream().map(Case::label).collect(Collectors.toList()));
        assertTrue(variant.cases().get(0).hasValType());
        assertFalse(variant.cases().get(1).hasValType());
    }

    @Test
    void unresolvedTypeIndexFailsWhenTheTypeIsGrounded() {
        TypeResolver empty = index -> null;
        // Grounding follows every index up front, so an unresolvable one is reported when the
        // type is built rather than when some later query happens to need it. That holds even
        // for an unbounded list, whose alignment and size never consult its element type.
        var fixed = ListType.builder().withElementType(typeIdx(0)).withFixedSize(1).build();
        var unbounded = ListType.builder().withElementType(typeIdx(0)).build();
        assertThrows(
                IllegalArgumentException.class, () -> ResolvedType.of(fixed, TypeSpace.of(empty)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ResolvedType.of(unbounded, TypeSpace.of(empty)));
    }

    @Test
    void alignToRoundsUpToAlignmentBoundary() {
        assertEquals(0, DefValType.alignTo(0, 4));
        assertEquals(4, DefValType.alignTo(1, 4));
        assertEquals(4, DefValType.alignTo(4, 4));
        assertEquals(8, DefValType.alignTo(5, 8));
    }

    @Test
    void primitivesFlattenToTheExpectedCoreType() {
        assertEquals(List.of(CORE_I32), g(PrimValType.BOOL).flatten(PointerType.I32));
        assertEquals(List.of(CORE_I32), g(PrimValType.U32).flatten(PointerType.I32));
        assertEquals(List.of(CORE_I64), g(PrimValType.U64).flatten(PointerType.I32));
        assertEquals(List.of(CORE_I64), g(PrimValType.S64).flatten(PointerType.I32));
        assertEquals(List.of(CORE_F32), g(PrimValType.F32).flatten(PointerType.I32));
        assertEquals(List.of(CORE_F64), g(PrimValType.F64).flatten(PointerType.I32));
        assertEquals(List.of(CORE_I32), g(PrimValType.CHAR).flatten(PointerType.I32));
        assertEquals(List.of(CORE_I32, CORE_I32), g(PrimValType.STRING).flatten(PointerType.I32));
    }

    @Test
    void listsFlattenAccordingToFixedOrUnboundedSize() {
        var unbounded = ListType.builder().withElementType(prim(PrimValType.U64)).build();
        assertEquals(List.of(CORE_I32, CORE_I32), g(unbounded).flatten(PointerType.I32));

        var fixed =
                ListType.builder().withElementType(prim(PrimValType.U8)).withFixedSize(3).build();
        assertEquals(List.of(CORE_I32, CORE_I32, CORE_I32), g(fixed).flatten(PointerType.I32));
    }

    @Test
    void recordFlattensToConcatenatedFieldFlattenings() {
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
        assertEquals(List.of(CORE_I32, CORE_F64), g(record).flatten(PointerType.I32));
    }

    @Test
    void variantFlattenJoinsMismatchedCasePayloadTypes() {
        // discriminant (i32) + joined payload slot
        var i32AndF32 =
                VariantType.builder()
                        .addCase(
                                Case.builder()
                                        .withLabel("a")
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .addCase(
                                Case.builder()
                                        .withLabel("b")
                                        .withValType(prim(PrimValType.F32))
                                        .build())
                        .build();
        // i32/f32 join -> i32
        assertEquals(List.of(CORE_I32, CORE_I32), g(i32AndF32).flatten(PointerType.I32));

        var i32AndF64 =
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
        // mismatched, neither i32/f32 -> i64
        assertEquals(List.of(CORE_I32, CORE_I64), g(i32AndF64).flatten(PointerType.I32));
    }

    @Test
    void flagsHandlesOwnBorrowStreamFutureFlattenToI32() {
        assertEquals(List.of(CORE_I32), g(flags(4)).flatten(PointerType.I32));
        assertEquals(
                List.of(CORE_I32),
                g(OwnType.builder().withTypeIdx(0).build()).flatten(PointerType.I32));
        assertEquals(
                List.of(CORE_I32),
                g(BorrowType.builder().withTypeIdx(0).build()).flatten(PointerType.I32));
        assertEquals(
                List.of(CORE_I32),
                g(StreamType.builder().withElementType(prim(PrimValType.U8)).build())
                        .flatten(PointerType.I32));
        assertEquals(
                List.of(CORE_I32),
                g(FutureType.builder().withElementType(prim(PrimValType.U8)).build())
                        .flatten(PointerType.I32));
    }

    @Test
    void specializedTypesFlattenViaDespecializedForm() {
        var tuple =
                TupleType.builder()
                        .addElementType(prim(PrimValType.U8))
                        .addElementType(prim(PrimValType.U32))
                        .build();
        assertEquals(List.of(CORE_I32, CORE_I32), g(tuple).flatten(PointerType.I32));

        var map =
                MapType.builder()
                        .withKeyType(prim(PrimValType.STRING))
                        .withValueType(prim(PrimValType.U64))
                        .build();
        assertEquals(List.of(CORE_I32, CORE_I32), g(map).flatten(PointerType.I32));
    }

    private static FlagsType flags(int n) {
        var builder = FlagsType.builder();
        IntStream.range(0, n).forEach(i -> builder.addLabel("f" + i));
        return builder.build();
    }

    private static VariantType variantWithNCases(int n) {
        var builder = VariantType.builder();
        IntStream.range(0, n)
                .forEach(i -> builder.addCase(Case.builder().withLabel("c" + i).build()));
        return builder.build();
    }
}
