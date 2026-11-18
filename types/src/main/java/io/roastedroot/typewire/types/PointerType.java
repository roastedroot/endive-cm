package io.roastedroot.typewire.types;

import run.endive.wasm.types.ValType;

/**
 * Endive's {@code Memory} is addressed with a plain {@code int} today, so only {@link
 * #I32} is currently in use; {@link #I64} exists so alignment/size computations won't
 * need reshaping once memory64 support is available.
 */
public enum PointerType {
    I32(4),
    I64(8);

    private final int size;

    PointerType(int size) {
        this.size = size;
    }

    public int size() {
        return size;
    }

    /** The core value type a pointer/length/index flattens to for this addressing width. */
    public ValType coreValType() {
        return this == I32 ? ValType.I32 : ValType.I64;
    }
}
