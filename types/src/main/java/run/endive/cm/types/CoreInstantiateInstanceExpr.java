package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CoreInstantiateInstanceExpr extends CoreInstanceExpr {

    private final long moduleIdx;
    private final List<CoreInstantiateArg> instantiateArgs;
    private static final long UNDEFINED_IDX = -1;

    private CoreInstantiateInstanceExpr(long moduleIdx, List<CoreInstantiateArg> instantiateArgs) {
        super(Kind.INSTANTIATE);
        if (moduleIdx == UNDEFINED_IDX) {
            throw new IllegalArgumentException("moduleIdx is required");
        }
        this.moduleIdx = moduleIdx;
        this.instantiateArgs = List.copyOf(instantiateArgs);
    }

    public long moduleIdx() {
        return moduleIdx;
    }

    public List<CoreInstantiateArg> instantiateArgs() {
        return instantiateArgs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private long moduleIdx = UNDEFINED_IDX;
        private final List<CoreInstantiateArg> instantiateArgs = new ArrayList<>();

        private Builder() {}

        public Builder withModuleIdx(long moduleIdx) {
            this.moduleIdx = moduleIdx;
            return this;
        }

        public Builder addInstantiateArg(CoreInstantiateArg instantiateArg) {
            Objects.requireNonNull(instantiateArg, "instantiateArg");
            this.instantiateArgs.add(instantiateArg);
            return this;
        }

        public CoreInstantiateInstanceExpr build() {
            return new CoreInstantiateInstanceExpr(moduleIdx, instantiateArgs);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreInstantiateInstanceExpr)) {
            return false;
        }
        CoreInstantiateInstanceExpr that = (CoreInstantiateInstanceExpr) o;
        return moduleIdx == that.moduleIdx && Objects.equals(instantiateArgs, that.instantiateArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleIdx, instantiateArgs);
    }

    @Override
    public String toString() {
        return "CoreInstantiateInstanceExpr{"
                + "moduleIdx="
                + moduleIdx
                + ", instantiateArgs="
                + instantiateArgs
                + '}';
    }
}
