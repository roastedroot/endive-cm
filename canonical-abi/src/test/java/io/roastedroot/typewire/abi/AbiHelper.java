package io.roastedroot.typewire.abi;

import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.FuncType;
import io.roastedroot.typewire.types.ResolvedFuncType;
import io.roastedroot.typewire.types.ResolvedType;
import io.roastedroot.typewire.types.TypeResolver;
import io.roastedroot.typewire.types.TypeSpace;
import io.roastedroot.typewire.types.ValType;
import java.util.ArrayList;
import java.util.List;
import run.endive.wasm.types.FunctionType;

/**
 * Drives the Canonical ABI from AST declarations, which are more natural to use in the tests,
 * rather than directly from resolved types.
 */
final class AbiHelper {

    private final TypeSpace space;

    AbiHelper(TypeResolver resolver) {
        this(TypeSpace.of(resolver));
    }

    AbiHelper(TypeSpace space) {
        this.space = space;
    }

    ResolvedType resolve(DefValType type) {
        return ResolvedType.of(type, space);
    }

    ResolvedFuncType resolve(FuncType funcType) {
        return ResolvedFuncType.of(funcType, space);
    }

    ResolvedType resolve(ValType valType) {
        return space.resolve(valType);
    }

    List<ResolvedType> resolveAll(List<ValType> types) {
        List<ResolvedType> resolved = new ArrayList<>(types.size());
        for (ValType t : types) {
            resolved.add(space.resolve(t));
        }
        return resolved;
    }

    Object load(LiftLowerContext context, int pointer, DefValType type) {
        return CanonicalAbi.load(context, pointer, resolve(type));
    }

    void store(LiftLowerContext context, Object value, DefValType type, int pointer) {
        CanonicalAbi.store(context, value, resolve(type), pointer);
    }

    void transfer(
            LiftLowerContext source,
            LiftLowerContext dest,
            int sourcePointer,
            int destPointer,
            DefValType type) {
        CanonicalAbi.transfer(source, dest, sourcePointer, destPointer, resolve(type));
    }

    FunctionType flattenFuncType(LiftLowerContext context, FuncType funcType, Direction direction) {
        return CanonicalAbi.flattenFuncType(context, resolve(funcType), direction);
    }

    List<Object> liftFlatParams(LiftLowerContext context, long[] flatArgs, List<ValType> types) {
        return CanonicalAbi.liftFlatParams(context, flatArgs, resolveAll(types));
    }

    List<Object> liftFlatResults(
            LiftLowerContext context, long[] flatResults, List<ValType> types) {
        return CanonicalAbi.liftFlatResults(context, flatResults, resolveAll(types));
    }

    long[] lowerFlatParams(LiftLowerContext context, List<?> values, List<ValType> types) {
        return CanonicalAbi.lowerFlatParams(context, values, resolveAll(types));
    }

    long[] lowerFlatResults(
            LiftLowerContext context, List<?> values, List<ValType> types, long[] outParam) {
        return CanonicalAbi.lowerFlatResults(context, values, resolveAll(types), outParam);
    }

    long[] transferFlatParams(
            LiftLowerContext source, LiftLowerContext dest, long[] flatArgs, List<ValType> types) {
        return CanonicalAbi.transferFlatParams(source, dest, flatArgs, resolveAll(types));
    }

    long[] transferFlatResults(
            LiftLowerContext source,
            LiftLowerContext dest,
            long[] flatResults,
            List<ValType> types,
            long[] outParam) {
        return CanonicalAbi.transferFlatResults(
                source, dest, flatResults, resolveAll(types), outParam);
    }

    boolean canTransfer(LiftLowerContext caller, LiftLowerContext callee, FuncType ft) {
        return ValueTransfer.canTransfer(caller, callee, resolve(ft));
    }

    boolean isIdentityTransfer(LiftLowerContext caller, LiftLowerContext callee, FuncType ft) {
        return ValueTransfer.isIdentityTransfer(caller, callee, resolve(ft));
    }

    ValueTransfer compile(LiftLowerContext caller, LiftLowerContext callee, FuncType ft) {
        return ValueTransfer.compile(caller, callee, resolve(ft));
    }
}
