package io.roastedroot.typewire.abi;

import static org.assertj.core.api.Assertions.assertThat;

import io.roastedroot.typewire.types.BorrowType;
import io.roastedroot.typewire.types.Case;
import io.roastedroot.typewire.types.EnumType;
import io.roastedroot.typewire.types.LabelValType;
import io.roastedroot.typewire.types.OptionType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.RecordType;
import io.roastedroot.typewire.types.ResultType;
import io.roastedroot.typewire.types.StreamType;
import io.roastedroot.typewire.types.TupleType;
import io.roastedroot.typewire.types.TypeResolver;
import io.roastedroot.typewire.types.TypeSpace;
import io.roastedroot.typewire.types.ValType;
import org.junit.jupiter.api.Test;

class CanonicalAbiTests {

    private static ValType prim(PrimValType t) {
        return ValType.builder().withPrimValType(t).build();
    }

    private static final TypeResolver stubResolver = index -> null;

    @Test
    void tupleDespecializesToRecordOfIndexedFields() {
        var tuple =
                TupleType.builder()
                        .addElementType(prim(PrimValType.U8))
                        .addElementType(prim(PrimValType.U32))
                        .build();
        var record = tuple.despecialize();
        assertThat(record.fields()).extracting(LabelValType::label).containsExactly("0", "1");
    }

    @Test
    void enumDespecializesToVariantOfPayloadlessCases() {
        var enumType =
                EnumType.builder().addLabel("red").addLabel("green").addLabel("blue").build();
        var variant = enumType.despecialize();
        assertThat(variant.cases()).extracting(Case::label).containsExactly("red", "green", "blue");
        assertThat(variant.cases()).allMatch(c -> !c.hasValType());
    }

    @Test
    void optionDespecializesToNoneSomeVariant() {
        var option = OptionType.builder().withValType(prim(PrimValType.U32)).build();
        var variant = option.despecialize();
        assertThat(variant.cases()).extracting(Case::label).containsExactly("none", "some");
    }

    @Test
    void resultDespecializesToOkErrorVariant() {
        var result = ResultType.builder().withOk(prim(PrimValType.U32)).build();
        var variant = result.despecialize();
        assertThat(variant.cases()).extracting(Case::label).containsExactly("ok", "error");
        assertThat(variant.cases().get(0).hasValType()).isTrue();
        assertThat(variant.cases().get(1).hasValType()).isFalse();
    }

    @Test
    void containsBorrowFindsTopLevelBorrow() {
        var borrow = BorrowType.builder().withTypeIdx(0).build();
        assertThat(CanonicalAbi.containsBorrow(TypeSpace.of(stubResolver), borrow)).isTrue();
        assertThat(CanonicalAbi.containsBorrow(TypeSpace.of(stubResolver), PrimValType.U32))
                .isFalse();
    }

    @Test
    void containsBorrowIsFalseForRecordOfPrimitiveFields() {
        var record =
                RecordType.builder()
                        .addField(
                                LabelValType.builder()
                                        .withLabel("a")
                                        .withValType(prim(PrimValType.U32))
                                        .build())
                        .build();
        assertThat(CanonicalAbi.containsBorrow(TypeSpace.of(stubResolver), record)).isFalse();
    }

    @Test
    void containsAsyncValueFindsNestedStreamOrFuture() {
        var stream = StreamType.builder().withElementType(prim(PrimValType.U8)).build();
        assertThat(CanonicalAbi.containsAsyncValue(TypeSpace.of(stubResolver), stream)).isTrue();
        assertThat(CanonicalAbi.containsAsyncValue(TypeSpace.of(stubResolver), PrimValType.U32))
                .isFalse();
    }
}
