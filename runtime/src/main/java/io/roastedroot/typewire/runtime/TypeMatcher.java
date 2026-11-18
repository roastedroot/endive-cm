package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.ResolvedFuncType;
import io.roastedroot.typewire.types.ResolvedType;
import io.roastedroot.typewire.types.Type;
import java.util.List;

/**
 * Compares two component-level types written in different type index spaces.
 *
 * <p>A type is resolved as it enters an index space, against the space in which it was written, so
 * by the time two of them meet, comparing them is a structural walk. Handles are the exception. Two
 * resource types are the same type only when they are the same runtime type, so they compare by
 * identity.
 */
final class TypeMatcher {

    private TypeMatcher() {}

    /**
     * Whether a function offered by {@code actual} can stand in for the one {@code expected}
     * declares. Parameter labels and their order are part of a function type and must agree.
     *
     * @param matchParamNames pass {@code false} only for an embedder-supplied function, which has
     *     no parameter names of its own to compare
     */
    static boolean funcTypesMatch(
            ResolvedFuncType expected, ResolvedFuncType actual, boolean matchParamNames) {
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.isAsync() != actual.isAsync()) {
            return false;
        }
        List<ResolvedFuncType.Param> expectedParams = expected.params();
        List<ResolvedFuncType.Param> actualParams = actual.params();
        if (expectedParams.size() != actualParams.size()) {
            return false;
        }
        for (int i = 0; i < expectedParams.size(); i++) {
            ResolvedFuncType.Param e = expectedParams.get(i);
            ResolvedFuncType.Param a = actualParams.get(i);
            if (matchParamNames && !e.label().equals(a.label())) {
                return false;
            }
            if (!valTypesMatch(e.type(), a.type())) {
                return false;
            }
        }
        return expected.hasResult() == actual.hasResult()
                && (!expected.hasResult() || valTypesMatch(expected.result(), actual.result()));
    }

    /**
     * Compares resolved types structurally, without despecializing. A tuple and the record it
     * shares a layout with are different types, so {@link ResolvedType#node()} is what is walked.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/CanonicalABI.md#despecialization">CanonicalABI.md, despecialization</a>
     */
    static boolean valTypesMatch(ResolvedType expected, ResolvedType actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        return defValTypesMatch(expected, actual);
    }

    private static boolean defValTypesMatch(ResolvedType expected, ResolvedType actual) {
        DefValType e = expected.node();
        DefValType a = actual.node();
        if (e.kind() != a.kind()) {
            return false;
        }
        switch (e.kind()) {
            case BOOL:
            case S8:
            case U8:
            case S16:
            case U16:
            case S32:
            case U32:
            case S64:
            case U64:
            case F32:
            case F64:
            case CHAR:
            case STRING:
            case ERROR_CONTEXT:
                return true;
            case OWN:
            case BORROW:
                return expected.resourceType() == actual.resourceType();
            case RECORD:
            case TUPLE:
                return fieldsMatch(expected, actual);
            case VARIANT:
            case OPTION:
            case RESULT:
                return casesMatch(expected, actual);
            case FLAGS:
                return ((io.roastedroot.typewire.types.FlagsType) e)
                        .labels()
                        .equals(((io.roastedroot.typewire.types.FlagsType) a).labels());
            case ENUM:
                return ((io.roastedroot.typewire.types.EnumType) e)
                        .labels()
                        .equals(((io.roastedroot.typewire.types.EnumType) a).labels());
            case LIST:
            case SIZED_LIST:
                return expected.isFixedSizeList() == actual.isFixedSizeList()
                        && (!expected.isFixedSizeList()
                                || expected.fixedSize() == actual.fixedSize())
                        && valTypesMatch(expected.element(), actual.element());
            case MAP:
                // A map despecialises to a list of two-field entries, which both sides hold.
                return valTypesMatch(expected.element(), actual.element());
            case STREAM:
            case FUTURE:
                return (expected.element() == null) == (actual.element() == null)
                        && valTypesMatch(expected.element(), actual.element());
            default:
                throw new IllegalStateException("unhandled kind " + e.kind());
        }
    }

    /** Compares the fields a {@code record} or {@code tuple} resolved to, labels included. */
    private static boolean fieldsMatch(ResolvedType expected, ResolvedType actual) {
        List<ResolvedType.Field> e = expected.fields();
        List<ResolvedType.Field> a = actual.fields();
        if (e.size() != a.size()) {
            return false;
        }
        for (int i = 0; i < e.size(); i++) {
            if (!e.get(i).label().equals(a.get(i).label())
                    || !valTypesMatch(e.get(i).type(), a.get(i).type())) {
                return false;
            }
        }
        return true;
    }

    /** Compares the cases a {@code variant}, {@code option} or {@code result} resolved to. */
    private static boolean casesMatch(ResolvedType expected, ResolvedType actual) {
        List<ResolvedType.Case> e = expected.cases();
        List<ResolvedType.Case> a = actual.cases();
        if (e.size() != a.size()) {
            return false;
        }
        for (int i = 0; i < e.size(); i++) {
            ResolvedType.Case ec = e.get(i);
            ResolvedType.Case ac = a.get(i);
            if (!ec.label().equals(ac.label()) || ec.hasType() != ac.hasType()) {
                return false;
            }
            if (ec.hasType() && !valTypesMatch(ec.type(), ac.type())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two type definitions that are not necessarily value types, which is the form a
     * {@code (type (eq ...))} bound takes.
     *
     * <p>Instance and component types are not handled. They are compared by subtyping rather than
     * equality and have no resolved form here, so reaching this with one throws.
     *
     * @throws UnsupportedOperationException if either side is an instance or component type
     */
    static boolean slotsMatch(ResolvedTypeSlot expected, ResolvedTypeSlot actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        Type e = expected.type();
        Type a = actual.type();
        if (e.funcType() != null || a.funcType() != null) {
            return funcTypesMatch(expected.func(), actual.func(), true);
        }
        if (e.defValType() != null || a.defValType() != null) {
            return valTypesMatch(expected.value(), actual.value());
        }
        throw new UnsupportedOperationException(
                "subtyping between "
                        + e.simpleName()
                        + " and "
                        + a.simpleName()
                        + " types is not yet supported");
    }
}
