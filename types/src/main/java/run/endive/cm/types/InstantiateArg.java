package run.endive.cm.types;

import java.util.Objects;

public final class InstantiateArg {

    private final String name;
    private final SortIdx sortIdx;

    private InstantiateArg(String name, SortIdx sortIdx) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sortIdx, "sortIdx");
        this.name = name;
        this.sortIdx = sortIdx;
    }

    public String name() {
        return name;
    }

    public SortIdx sortIdx() {
        return sortIdx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private SortIdx sortIdx;

        private Builder() {}

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withSortIdx(SortIdx sortIdx) {
            this.sortIdx = sortIdx;
            return this;
        }

        public InstantiateArg build() {
            return new InstantiateArg(name, sortIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InstantiateArg)) {
            return false;
        }
        InstantiateArg that = (InstantiateArg) o;
        return Objects.equals(name, that.name) && Objects.equals(sortIdx, that.sortIdx);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sortIdx);
    }

    @Override
    public String toString() {
        return "InstantiateArg{" + "name=" + name + ", sortIdx=" + sortIdx + '}';
    }
}
