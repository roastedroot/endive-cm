package io.roastedroot.typewire.abi;

import static org.assertj.core.api.Assertions.assertThat;

import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.LabelValType;
import io.roastedroot.typewire.types.ListType;
import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.RecordType;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.TypeResolver;
import io.roastedroot.typewire.types.ValType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.Memory;
import run.endive.wasm.types.MemoryLimits;

/** Values that skip the label-keyed map and the element-by-element list on their way to memory. */
class CanonicalAbiDirectValueTests {

    private static final RecordType POINT =
            RecordType.builder()
                    .addField(field("tag", PrimValType.U8))
                    .addField(field("x", PrimValType.F64))
                    .addField(field("id", PrimValType.U32))
                    .build();

    private static final ListType BYTES =
            ListType.builder().withElementType(prim(PrimValType.U8)).build();

    /** Names {@link #POINT} as type 0 and {@link #BYTES} as type 1. */
    private static final TypeResolver RESOLVER = index -> Type.of(index == 0 ? POINT : BYTES);

    private static final AbiHelper ABI = new AbiHelper(RESOLVER);

    private static ValType prim(PrimValType t) {
        return ValType.builder().withPrimValType(t).build();
    }

    private static LabelValType field(String label, PrimValType t) {
        return LabelValType.builder().withLabel(label).withValType(prim(t)).build();
    }

    private static LiftLowerContext newContext() {
        int[] bumpPtr = {256};
        Memory memory = new ByteArrayMemory(new MemoryLimits(1));
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

    private static Map<String, Object> pointMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tag", (short) 200);
        value.put("x", 1.5);
        value.put("id", 4_000_000_000L);
        return value;
    }

    /** The record {@link #pointMap} holds, read by position. */
    private static final class Point implements RecordValue {
        @Override
        public Object field(int index) {
            switch (index) {
                case 0:
                    return (short) 200;
                case 1:
                    return 1.5;
                case 2:
                    return 4_000_000_000L;
                default:
                    throw new IndexOutOfBoundsException();
            }
        }
    }

    /** The same record, writing itself at the offsets its layout fixes. */
    private static final class FlatPoint implements FlatRecordValue {
        int stores;

        @Override
        public Object field(int index) {
            return new Point().field(index);
        }

        @Override
        public void store(Memory memory, int pointer) {
            stores++;
            memory.writeByte(pointer, (byte) 200);
            FlatRecordValue.storeF64(memory, pointer + 8, 1.5);
            memory.writeI32(pointer + 16, (int) 4_000_000_000L);
        }
    }

    private static byte[] bytesAt(LiftLowerContext ctx, int pointer, int length) {
        return ctx.memory().readBytes(pointer, length);
    }

    @Test
    void storesARecordValueAsTheMapWouldBe() {
        var byMap = newContext();
        var byPosition = newContext();

        ABI.store(byMap, pointMap(), POINT, 0);
        ABI.store(byPosition, new Point(), POINT, 0);

        assertThat(bytesAt(byPosition, 0, 24)).isEqualTo(bytesAt(byMap, 0, 24));
        assertThat(ABI.load(byPosition, 0, POINT)).isEqualTo(pointMap());
    }

    @Test
    void letsAFlatRecordWriteItself() {
        var byMap = newContext();
        var flat = newContext();
        var point = new FlatPoint();

        ABI.store(byMap, pointMap(), POINT, 0);
        ABI.store(flat, point, POINT, 0);

        assertThat(point.stores).isEqualTo(1);
        assertThat(bytesAt(flat, 0, 24)).isEqualTo(bytesAt(byMap, 0, 24));
    }

    @Test
    void storesAListOfFlatRecords() {
        var ctx = newContext();
        var points = ListType.builder().withElementType(typeAt(0)).build();
        var first = new FlatPoint();
        var second = new FlatPoint();

        ABI.store(ctx, List.of(first, second), points, 0);

        assertThat(first.stores).isEqualTo(1);
        assertThat(second.stores).isEqualTo(1);
        assertThat(ABI.load(ctx, 0, points)).isEqualTo(List.of(pointMap(), pointMap()));
    }

    @Test
    void lowersARecordValueFlat() {
        var ctx = newContext();
        var types = List.of(prim(PrimValType.U8), prim(PrimValType.F64), prim(PrimValType.U32));
        long[] byMap = ABI.lowerFlatParams(ctx, List.of((short) 200, 1.5, 4_000_000_000L), types);
        long[] byRecord =
                ABI.lowerFlatParams(newContext(), List.of(new Point()), List.of(typeAt(0)));
        assertThat(byRecord).isEqualTo(byMap);
    }

    @Test
    void storesAByteArrayInOneWrite() {
        var ctx = newContext();
        byte[] bytes = {1, 2, (byte) 255, 0};

        ABI.store(ctx, bytes, BYTES, 0);

        int begin = ctx.memory().readInt(0);
        assertThat(ctx.memory().readInt(4)).isEqualTo(4);
        assertThat(bytesAt(ctx, begin, 4)).isEqualTo(bytes);
    }

    @Test
    void storesAByteArrayAsTheEquivalentListWouldBe() {
        var asArray = newContext();
        var asList = newContext();

        ABI.store(asArray, new byte[] {9, (byte) 200}, BYTES, 0);
        ABI.store(asList, List.of((short) 9, (short) 200), BYTES, 0);

        assertThat(ABI.load(asArray, 0, BYTES)).isEqualTo(ABI.load(asList, 0, BYTES));
    }

    @Test
    void liftsABytesListBackedByAnArray() {
        var ctx = newContext();
        ABI.store(ctx, new byte[] {7, (byte) 128}, BYTES, 0);

        Object lifted = ABI.load(ctx, 0, BYTES);

        assertThat(lifted).isInstanceOf(ByteList.class);
        assertThat(lifted).isEqualTo(List.of((short) 7, (short) 128));
        assertThat(ByteList.toBytes(lifted)).isEqualTo(new byte[] {7, (byte) 128});
    }

    @Test
    void lowersAByteArrayFlat() {
        var ctx = newContext();
        long[] flat = ABI.lowerFlatParams(ctx, List.of(new byte[] {4, 5, 6}), List.of(typeAt(1)));

        assertThat(flat[1]).isEqualTo(3L);
        assertThat(bytesAt(ctx, (int) flat[0], 3)).isEqualTo(new byte[] {4, 5, 6});
    }

    @Test
    void convertsAnyListOfNumbersToBytes() {
        assertThat(ByteList.toBytes(List.of((short) 1, (short) 255)))
                .isEqualTo(new byte[] {1, (byte) 255});
    }

    private static ValType typeAt(int index) {
        return ValType.builder().withTypeIdx(index).build();
    }
}
