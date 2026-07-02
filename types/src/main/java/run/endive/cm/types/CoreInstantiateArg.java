package run.endive.cm.types;

import java.util.Objects;

public final class CoreInstantiateArg {

    private final String name;
    private final CoreSortIdx sortIdx;

    private CoreInstantiateArg(String name, CoreSortIdx sortIdx) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sortIdx, "sortIdx");
        this.name = name;
        this.sortIdx = sortIdx;
    }

    public String name() {
        return name;
    }

    public CoreSortIdx sortIdx() {
        return sortIdx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private CoreSortIdx sortIdx;

        private Builder() {}

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withSortIdx(CoreSortIdx sortIdx) {
            this.sortIdx = sortIdx;
            return this;
        }

        public CoreInstantiateArg build() {
            return new CoreInstantiateArg(name, sortIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreInstantiateArg)) {
            return false;
        }
        CoreInstantiateArg that = (CoreInstantiateArg) o;
        return Objects.equals(name, that.name) && Objects.equals(sortIdx, that.sortIdx);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sortIdx);
    }

    @Override
    public String toString() {
        return "CoreInstantiateArg{" + "name=" + name + ", sortIdx=" + sortIdx + '}';
    }
}
