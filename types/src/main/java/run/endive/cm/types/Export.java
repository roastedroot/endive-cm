package run.endive.cm.types;

import java.util.Objects;

public final class Export {

    private final String name;
    private final SortIdx sortIdx;
    private final ExternDesc externDesc;

    private Export(String name, SortIdx sortIdx, ExternDesc externDesc) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sortIdx, "sortIdx");
        this.name = name;
        this.sortIdx = sortIdx;
        this.externDesc = externDesc;
    }

    public String name() {
        return name;
    }

    public SortIdx sortIdx() {
        return sortIdx;
    }

    public ExternDesc externDesc() {
        return externDesc;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private SortIdx sortIdx;
        private ExternDesc externDesc;

        private Builder() {}

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withSortIdx(SortIdx sortIdx) {
            this.sortIdx = sortIdx;
            return this;
        }

        public Builder withExternDesc(ExternDesc externDesc) {
            this.externDesc = externDesc;
            return this;
        }

        public Export build() {
            return new Export(name, sortIdx, externDesc);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Export)) {
            return false;
        }
        Export that = (Export) o;
        return Objects.equals(name, that.name)
                && Objects.equals(sortIdx, that.sortIdx)
                && Objects.equals(externDesc, that.externDesc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sortIdx, externDesc);
    }

    @Override
    public String toString() {
        return "ExportDecl{"
                + "name="
                + name
                + ", sortIdx="
                + sortIdx
                + ", externDesc="
                + externDesc
                + '}';
    }
}
