package io.roastedroot.typewire.runtime;

import run.endive.runtime.ImportFunction;
import run.endive.runtime.WasmFunctionHandle;
import run.endive.wasm.types.FunctionType;

public class CoreImportFunction implements CoreFunction<WasmFunctionHandle> {

    private final FunctionType funcType;
    private final WasmFunctionHandle importFunctionHandle;

    public CoreImportFunction(FunctionType funcType, WasmFunctionHandle importFunctionHandle) {
        this.funcType = funcType;
        this.importFunctionHandle = importFunctionHandle;
    }

    @Override
    public WasmFunctionHandle getFunctionInstance() {
        return importFunctionHandle;
    }

    @Override
    public FunctionType funcType() {
        return funcType;
    }

    @Override
    public ImportFunction importFunction(String moduleName, String functionName) {
        return new ImportFunction(moduleName, functionName, funcType, importFunctionHandle);
    }

    @Override
    public long[] apply(long... args) {
        return importFunctionHandle.apply(null, args);
    }
}
