package run.endive.cm.types;

import java.util.Objects;

public final class CoreInlineExport {

    private final String name;
    private final CoreSortIdx sortIdx;

    private CoreInlineExport(String name, CoreSortIdx sortIdx) {
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

        public CoreInlineExport build() {
            return new CoreInlineExport(name, sortIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreInlineExport)) {
            return false;
        }
        CoreInlineExport that = (CoreInlineExport) o;
        return Objects.equals(name, that.name) && Objects.equals(sortIdx, that.sortIdx);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sortIdx);
    }

    @Override
    public String toString() {
        return "CoreInlineExport{" + "name=" + name + ", sortIdx=" + sortIdx + '}';
    }
}
