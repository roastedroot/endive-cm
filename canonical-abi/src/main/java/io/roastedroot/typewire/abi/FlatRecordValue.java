package io.roastedroot.typewire.abi;

import run.endive.runtime.Memory;

/**
 * A {@link RecordValue} that writes its own canonical layout to linear memory.
 *
 * <p>Only a record whose fields are all numbers, {@code bool},
 * or records of the same kind can be one.
 * Such a record needs no allocation, so storing it is a fixed set of writes at fixed offsets.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#storing">Storing</a>
 */
public interface FlatRecordValue extends RecordValue {

    /**
     * Writes this record at {@code pointer},
     * which the caller has already aligned and bounds checked.
     */
    void store(Memory memory, int pointer);

    /** Writes an {@code f32}, with NaN canonicalized as the ABI does. */
    static void storeF32(Memory memory, int pointer, float value) {
        memory.writeF32(pointer, CanonicalAbi.canonicalizeNan32(value));
    }

    /** Writes an {@code f64}, with NaN canonicalized as the ABI does. */
    static void storeF64(Memory memory, int pointer, double value) {
        memory.writeF64(pointer, CanonicalAbi.canonicalizeNan64(value));
    }
}
