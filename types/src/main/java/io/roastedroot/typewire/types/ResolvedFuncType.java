package io.roastedroot.typewire.types;

import java.util.ArrayList;
import java.util.List;

/**
 * A component function type with its parameter and result types already resolved.
 *
 * <p>Stands to {@link FuncType} as {@link ResolvedType} stands to {@link DefValType}: the
 * declaration says what it means only alongside the index space in which it was written, and this
 * is what it means. Comparing two of them, or lifting and lowering through one, needs no space.
 */
public final class ResolvedFuncType {

    private final FuncType node;
    private final List<Param> params;
    private final ResolvedType result;

    private ResolvedFuncType(FuncType node, List<Param> params, ResolvedType result) {
        this.node = node;
        this.params = params;
        this.result = result;
    }

    /** Resolves {@code funcType}, whose parameter and result indices count in {@code space}. */
    public static ResolvedFuncType of(FuncType funcType, TypeSpace space) {
        if (funcType == null) {
            return null;
        }
        List<Param> params = new ArrayList<>(funcType.params().size());
        for (LabelValType p : funcType.params()) {
            params.add(new Param(p.label(), space.resolve(p.valType())));
        }
        return new ResolvedFuncType(
                funcType,
                List.copyOf(params),
                funcType.hasResult() ? space.resolve(funcType.result()) : null);
    }

    /** The parsed declaration this resolves, for diagnostics. */
    public FuncType node() {
        return node;
    }

    public List<Param> params() {
        return params;
    }

    public boolean hasResult() {
        return result != null;
    }

    /** The result type, or {@code null} for a function that returns nothing. */
    public ResolvedType result() {
        return result;
    }

    public boolean isAsync() {
        return node.isAsync();
    }

    @Override
    public String toString() {
        return "ResolvedFuncType{" + node + '}';
    }

    /** One named parameter of a resolved function type. */
    public static final class Param {

        private final String label;
        private final ResolvedType type;

        private Param(String label, ResolvedType type) {
            this.label = label;
            this.type = type;
        }

        public String label() {
            return label;
        }

        public ResolvedType type() {
            return type;
        }
    }
}
