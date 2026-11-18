package io.roastedroot.typewire.abi;

import run.endive.wasm.types.FunctionType;
import run.endive.wasm.types.ValType;

public final class BuiltinFunctionTypes {

    public static final FunctionType CANON_RESOURCE_NEW =
            FunctionType.of(
                    new run.endive.wasm.types.ValType[] {ValType.I32},
                    new run.endive.wasm.types.ValType[] {ValType.I32});

    public static final FunctionType CANON_RESOURCE_REP =
            FunctionType.of(
                    new run.endive.wasm.types.ValType[] {ValType.I32},
                    new run.endive.wasm.types.ValType[] {ValType.I32});

    public static final FunctionType CANON_RESOURCE_DROP =
            FunctionType.of(
                    new run.endive.wasm.types.ValType[] {ValType.I32},
                    new run.endive.wasm.types.ValType[0]);

    private BuiltinFunctionTypes() {}
}
