package io.roastedroot.typewire.runtime;

import run.endive.runtime.ImportFunction;
import run.endive.wasm.types.FunctionType;

public interface CoreFunction<T> {

    T getFunctionInstance();

    FunctionType funcType();

    ImportFunction importFunction(String moduleName, String functionName);

    long[] apply(long... args);
}
