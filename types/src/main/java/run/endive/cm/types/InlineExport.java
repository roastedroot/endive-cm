package run.endive.cm.types;

import java.util.Objects;

public final class InlineExport {

    private final String name;
    private final SortIdx sortIdx;

    private InlineExport(String name, SortIdx sortIdx) {
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

        public InlineExport build() {
            return new InlineExport(name, sortIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InlineExport)) {
            return false;
        }
        InlineExport that = (InlineExport) o;
        return Objects.equals(name, that.name) && Objects.equals(sortIdx, that.sortIdx);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sortIdx);
    }

    @Override
    public String toString() {
        return "InlineExport{" + "name=" + name + ", sortIdx=" + sortIdx + '}';
    }
}
