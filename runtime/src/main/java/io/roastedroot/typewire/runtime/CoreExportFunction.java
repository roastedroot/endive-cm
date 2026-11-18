package io.roastedroot.typewire.runtime;

import run.endive.runtime.ExportFunction;
import run.endive.runtime.ImportFunction;
import run.endive.runtime.WasmFunctionHandle;
import run.endive.wasm.types.FunctionType;

public final class CoreExportFunction implements CoreFunction<ExportFunction> {

    private final FunctionType funcType;
    private final ExportFunction exportFunction;

    public CoreExportFunction(FunctionType funcType, ExportFunction exportFunction) {
        this.funcType = funcType;
        this.exportFunction = exportFunction;
    }

    @Override
    public ExportFunction getFunctionInstance() {
        return exportFunction;
    }

    @Override
    public FunctionType funcType() {
        return funcType;
    }

    @Override
    public ImportFunction importFunction(String moduleName, String functionName) {
        WasmFunctionHandle functionHandle = (instance, args) -> exportFunction.apply(args);
        return new ImportFunction(moduleName, functionName, funcType, functionHandle);
    }

    @Override
    public long[] apply(long... args) {
        return exportFunction.apply(args);
    }
}
