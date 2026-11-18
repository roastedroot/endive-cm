package io.roastedroot.typewire.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Layout of grounded types, at the boundaries and through type indices.
 *
 * <p>{@link DefValTypeTest} covers the common shapes. This covers what only grounding reaches: the
 * widths a discriminant and a {@code flags} step through, the shorthands that expand into something
 * with a different layout than they look like, and types that are named by index rather than
 * written inline, which is the case that motivated grounding, since an index only means something
 * relative to a space.
 */
class ResolvedTypeLayoutTest {

    private final List<Type> types = new ArrayList<>();

    private final TypeResolver resolver = index -> types.get(index);

    /** Defines a type in the local space and returns the {@link ValType} naming it. */
    private ValType define(DefValType type) {
        types.add(Type.of(type));
        return ValType.builder().withTypeIdx(types.size() - 1).build();
    }

    private ResolvedType g(DefValType t) {
        return ResolvedType.of(t, TypeSpace.of(resolver));
    }

    private static ValType prim(PrimValType t) {
        return ValType.builder().withPrimValType(t).build();
    }

    private static LabelValType field(String label, ValType type) {
        return LabelValType.builder().withLabel(label).withValType(type).build();
    }

    private void assertLayout(DefValType type, int alignment, int elementSize) {
        ResolvedType grounded = g(type);
        assertEquals(alignment, grounded.alignment(PointerType.I32), "alignment");
        assertEquals(elementSize, grounded.elementSize(PointerType.I32), "elementSize");
    }

    @Test
    void discriminantWidensAtTwoHundredFiftySixAndSixtyFiveThousandFiveHundredThirtySix() {
        // A payload-free variant is exactly its discriminant, so its size is that width.
        assertLayout(variantWithCases(256), 1, 1);
        assertLayout(variantWithCases(257), 2, 2);
        assertLayout(variantWithCases(65536), 2, 2);
        assertLayout(variantWithCases(65537), 4, 4);
    }

    @Test
    void flagsWidenAtEightAndSixteenLabels() {
        assertLayout(flagsWithLabels(1), 1, 1);
        assertLayout(flagsWithLabels(8), 1, 1);
        assertLayout(flagsWithLabels(9), 2, 2);
        assertLayout(flagsWithLabels(16), 2, 2);
        assertLayout(flagsWithLabels(17), 4, 4);
        assertLayout(flagsWithLabels(33), 4, 4);
    }

    @Test
    void shorthandsLayOutAsWhatTheyStandFor() {
        assertLayout(
                TupleType.builder()
                        .addElementType(prim(PrimValType.U8))
                        .addElementType(prim(PrimValType.U64))
                        .build(),
                8,
                16);
        // enum -> a variant of payload-free cases, so just a discriminant.
        assertLayout(EnumType.builder().addLabel("a").addLabel("b").build(), 1, 1);
        assertLayout(OptionType.builder().withValType(prim(PrimValType.U32)).build(), 4, 8);
        // result<u32, string> -> variant whose widest payload is the string's (ptr, len) pair.
        assertLayout(
                ResultType.builder()
                        .withOk(prim(PrimValType.U32))
                        .withError(prim(PrimValType.STRING))
                        .build(),
                4,
                12);
        // result with neither payload is two payload-free cases.
        assertLayout(ResultType.builder().build(), 1, 1);
        // map<k, v> -> list<(k, v)>, and an unbounded list is a (ptr, len) pair whatever it holds.
        assertLayout(
                MapType.builder()
                        .withKeyType(prim(PrimValType.STRING))
                        .withValueType(prim(PrimValType.U64))
                        .build(),
                4,
                8);
    }

    @Test
    void mapGroundsToAListOfKeyValueRecords() {
        ResolvedType map =
                g(
                        MapType.builder()
                                .withKeyType(prim(PrimValType.STRING))
                                .withValueType(prim(PrimValType.U64))
                                .build());
        assertEquals(DefValType.Kind.LIST, map.kind());
        ResolvedType entry = map.element();
        assertEquals(DefValType.Kind.RECORD, entry.kind());
        assertEquals(
                List.of("0", "1"),
                List.of(entry.fields().get(0).label(), entry.fields().get(1).label()));
        assertEquals(DefValType.Kind.STRING, entry.fields().get(0).type().kind());
        assertEquals(DefValType.Kind.U64, entry.fields().get(1).type().kind());
    }

    @Test
    void fixedSizeListsScaleWithTheirElementWhileUnboundedOnesDoNot() {
        assertLayout(ListType.builder().withElementType(prim(PrimValType.U8)).build(), 4, 8);
        assertLayout(
                ListType.builder().withElementType(prim(PrimValType.U16)).withFixedSize(3).build(),
                2,
                6);
        assertLayout(
                ListType.builder().withElementType(prim(PrimValType.F64)).withFixedSize(0).build(),
                8,
                0);
    }

    @Test
    void variantFlattenJoinsCasePayloadsSlotwise() {
        // i32 against f32 widens to i32, and i64 against that widens to i64.
        ResolvedType variant =
                g(
                        VariantType.builder()
                                .addCase(payloadCase("i", prim(PrimValType.S32)))
                                .addCase(payloadCase("f", prim(PrimValType.F32)))
                                .addCase(payloadCase("l", prim(PrimValType.S64)))
                                .build());
        assertEquals(
                List.of(run.endive.wasm.types.ValType.I32, run.endive.wasm.types.ValType.I64),
                variant.flatten(PointerType.I32));
    }

    @Test
    void pointerWidthChangesTheLayoutOfEverythingBuiltOnPointers() {
        ResolvedType string = g(PrimValType.STRING);
        assertEquals(4, string.alignment(PointerType.I32));
        assertEquals(8, string.elementSize(PointerType.I32));
        assertEquals(8, string.alignment(PointerType.I64));
        assertEquals(16, string.elementSize(PointerType.I64));
    }

    @Test
    void aTypeNamedByIndexLaysOutTheSameAsOneWrittenInline() {
        // Reaching a type through an index has to produce the same type.
        DefValType inner =
                RecordType.builder()
                        .addField(field("x", prim(PrimValType.U8)))
                        .addField(field("y", prim(PrimValType.F64)))
                        .build();
        ValType named = define(inner);

        ResolvedType direct = g(inner);
        ResolvedType viaIndex = g(ListType.builder().withElementType(named).build()).element();

        assertEquals(direct.kind(), viaIndex.kind());
        assertEquals(direct.alignment(PointerType.I32), viaIndex.alignment(PointerType.I32));
        assertEquals(direct.elementSize(PointerType.I32), viaIndex.elementSize(PointerType.I32));
        assertEquals(direct.flatten(PointerType.I32), viaIndex.flatten(PointerType.I32));
    }

    @Test
    void groundingFollowsIndicesThroughNestedAggregates() {
        ValType inner =
                define(
                        RecordType.builder()
                                .addField(field("x", prim(PrimValType.U8)))
                                .addField(field("y", prim(PrimValType.F64)))
                                .build());
        ValType listOfInner = define(ListType.builder().withElementType(inner).build());

        ResolvedType outer =
                g(
                        RecordType.builder()
                                .addField(field("head", inner))
                                .addField(field("tail", listOfInner))
                                .build());

        ResolvedType head = outer.fields().get(0).type();
        ResolvedType tail = outer.fields().get(1).type();
        assertEquals(DefValType.Kind.RECORD, head.kind());
        assertEquals(DefValType.Kind.LIST, tail.kind());
        // The list's element resolved through a second index, and is the same shape as `head`.
        assertEquals(head.alignment(PointerType.I32), tail.element().alignment(PointerType.I32));
        assertEquals(
                head.elementSize(PointerType.I32), tail.element().elementSize(PointerType.I32));
    }

    @Test
    void layoutIsComputedOnceAndReused() {
        ResolvedType grounded =
                g(
                        RecordType.builder()
                                .addField(field("a", prim(PrimValType.U8)))
                                .addField(field("b", prim(PrimValType.STRING)))
                                .build());
        assertSame(grounded.flatten(PointerType.I32), grounded.flatten(PointerType.I32));
    }

    @Test
    void nodeReportsTheTypeAsWrittenAndKindReportsWhatItStandsFor() {
        TupleType tuple =
                TupleType.builder()
                        .addElementType(prim(PrimValType.U8))
                        .addElementType(prim(PrimValType.U8))
                        .build();
        ResolvedType grounded = g(tuple);
        assertSame(tuple, grounded.node());
        assertEquals(DefValType.Kind.RECORD, grounded.kind());
        assertEquals(
                List.of("0", "1"),
                List.of(grounded.fields().get(0).label(), grounded.fields().get(1).label()));
    }

    private static Case payloadCase(String label, ValType payload) {
        return Case.builder().withLabel(label).withValType(payload).build();
    }

    private static VariantType variantWithCases(int count) {
        var builder = VariantType.builder();
        for (int i = 0; i < count; i++) {
            builder.addCase(Case.builder().withLabel("c" + i).build());
        }
        return builder.build();
    }

    private static FlagsType flagsWithLabels(int count) {
        var builder = FlagsType.builder();
        for (int i = 0; i < count; i++) {
            builder.addLabel("f" + i);
        }
        return builder.build();
    }
}
