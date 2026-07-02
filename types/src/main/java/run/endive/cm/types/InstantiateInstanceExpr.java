package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InstantiateInstanceExpr extends InstanceExpr {

    private final long componentIdx;
    private final List<InstantiateArg> instantiateArgs;
    private static final long UNDEFINED_IDX = -1;

    private InstantiateInstanceExpr(long componentIdx, List<InstantiateArg> instantiateArgs) {
        super(Kind.INSTANTIATE);
        if (componentIdx == UNDEFINED_IDX) {
            throw new IllegalArgumentException("componentIdx is required");
        }
        this.componentIdx = componentIdx;
        this.instantiateArgs = List.copyOf(instantiateArgs);
    }

    public long componentIdx() {
        return componentIdx;
    }

    public List<InstantiateArg> instantiateArgs() {
        return instantiateArgs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private long componentIdx = UNDEFINED_IDX;
        private final List<InstantiateArg> instantiateArgs = new ArrayList<>();

        private Builder() {}

        public Builder withComponentIdx(long componentIdx) {
            this.componentIdx = componentIdx;
            return this;
        }

        public Builder addInstantiateArg(InstantiateArg instantiateArg) {
            Objects.requireNonNull(instantiateArg, "instantiateArg");
            this.instantiateArgs.add(instantiateArg);
            return this;
        }

        public InstantiateInstanceExpr build() {
            return new InstantiateInstanceExpr(componentIdx, instantiateArgs);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InstantiateInstanceExpr)) {
            return false;
        }
        InstantiateInstanceExpr that = (InstantiateInstanceExpr) o;
        return componentIdx == that.componentIdx
                && Objects.equals(instantiateArgs, that.instantiateArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentIdx, instantiateArgs);
    }

    @Override
    public String toString() {
        return "InstantiateInstanceExpr{"
                + "componentIdx="
                + componentIdx
                + ", instantiateArgs="
                + instantiateArgs
                + '}';
    }
}
