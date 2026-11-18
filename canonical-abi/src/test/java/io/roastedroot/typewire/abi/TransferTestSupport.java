package io.roastedroot.typewire.abi;

import static org.assertj.core.api.Assertions.assertThat;

import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.PointerType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.TypeResolver;
import io.roastedroot.typewire.types.ValType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.Memory;
import run.endive.wasm.types.MemoryLimits;

/**
 * Shared scaffolding for the transfer tests. Provides a type table, bump-allocating contexts, and
 * the differential assertions that hold the transfer path to the lift/lower pair it replaces.
 */
final class TransferTestSupport {

    /** Where the bump allocator starts, leaving room below for values written at fixed offsets. */
    static final int HEAP_BASE = 512;

    private TransferTestSupport() {}

    /** A mutable type table, so tests can build types that reference each other by index. */
    static final class Types implements TypeResolver {

        private final List<Type> types = new ArrayList<>();

        @Override
        public Type getType(int index) {
            return types.get(index);
        }

        /** Registers {@code t} and returns a {@link ValType} referring to it. */
        ValType add(DefValType t) {
            types.add(Type.of(t));
            return ValType.builder().withTypeIdx(types.size() - 1).build();
        }
    }

    static ValType prim(PrimValType t) {
        return ValType.builder().withPrimValType(t).build();
    }

    static LiftLowerContext newContext(TypeResolver resolver, StringEncoding encoding) {
        Memory memory = new ByteArrayMemory(new MemoryLimits(4));
        int[] bumpPtr = {HEAP_BASE};
        Realloc realloc =
                (oldPtr, oldSize, align, newSize) -> {
                    int ptr = DefValType.alignTo(bumpPtr[0], align);
                    bumpPtr[0] = ptr + newSize;
                    return ptr;
                };
        return LiftLowerContext.builder()
                .withMemory(memory)
                .withPtrType(PointerType.I32)
                .withStringEncoding(encoding)
                .withRealloc(realloc)
                .build();
    }

    static LiftLowerContext newContext(TypeResolver resolver) {
        return newContext(resolver, StringEncoding.UTF8);
    }

    /**
     * Assert that transferring a value produces the same result as lifting it out of the source and
     * lowering it into an identically configured destination.
     *
     * <p>Byte equality holds here because the source is written into a zeroed memory, so the
     * padding the transfer path copies is zero, which is also what the destination allocator hands
     * back.
     */
    static void assertTransferMatchesLiftLower(
            Types types,
            StringEncoding srcEncoding,
            StringEncoding dstEncoding,
            Object v,
            DefValType t) {
        var src = newContext(types, srcEncoding);
        var dst = newContext(types, dstEncoding);
        var reference = newContext(types, dstEncoding);

        new AbiHelper(types).store(src, v, t, 0);
        new AbiHelper(types).transfer(src, dst, 0, 0, t);
        new AbiHelper(types).store(reference, new AbiHelper(types).load(src, 0, t), t, 0);

        assertThat(new AbiHelper(types).load(dst, 0, t))
                .as("value read back from the destination")
                .isEqualTo(new AbiHelper(types).load(reference, 0, t));
        assertMemoryEquals(dst.memory(), reference.memory());
    }

    static void assertTransferMatchesLiftLower(Types types, Object v, DefValType t) {
        assertTransferMatchesLiftLower(types, StringEncoding.UTF8, StringEncoding.UTF8, v, t);
    }

    /** Asserts the compiled plan and the interpreted transfer leave the destination identical. */
    static void assertCompiledPlanMatchesInterpreted(
            Types types,
            StringEncoding srcEncoding,
            StringEncoding dstEncoding,
            Object v,
            DefValType t) {
        var src = newContext(types, srcEncoding);
        var interpreted = newContext(types, dstEncoding);
        var compiled = newContext(types, dstEncoding);

        new AbiHelper(types).store(src, v, t, 0);
        new AbiHelper(types).transfer(src, interpreted, 0, 0, t);
        TransferPlan.compile(PointerType.I32, new AbiHelper(types).resolve(t))
                .run(src, compiled, 0, 0);

        assertMemoryEquals(compiled.memory(), interpreted.memory());
    }

    static void assertMemoryEquals(Memory actual, Memory expected) {
        int size = Memory.bytes(Math.min(actual.pages(), expected.pages()));
        assertThat(actual.readBytes(0, size))
                .as("destination memory bytes")
                .isEqualTo(expected.readBytes(0, size));
    }

    static Map<String, Object> record(Object... labelsAndValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < labelsAndValues.length; i += 2) {
            m.put((String) labelsAndValues[i], labelsAndValues[i + 1]);
        }
        return m;
    }

    static Map<String, Boolean> flags(List<String> labels, String... set) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (String label : labels) {
            m.put(label, false);
        }
        for (String label : set) {
            m.put(label, true);
        }
        return m;
    }
}
