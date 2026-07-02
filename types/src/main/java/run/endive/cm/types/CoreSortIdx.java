package run.endive.cm.types;

import java.util.Objects;

public final class CoreSortIdx {

    private final CoreSort sort;
    private final long idx;
    private static final int UNDEFINED_IDX = -1;

    private CoreSortIdx(CoreSort sort, long idx) {
        Objects.requireNonNull(sort, "sort");
        if (idx == UNDEFINED_IDX) {
            throw new IllegalArgumentException("idx is required");
        }
        this.sort = sort;
        this.idx = idx;
    }

    public CoreSort sort() {
        return sort;
    }

    public long idx() {
        return idx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private CoreSort sort;
        private long idx = UNDEFINED_IDX;

        public Builder withSort(CoreSort sort) {
            this.sort = sort;
            return this;
        }

        public Builder withIdx(long idx) {
            this.idx = idx;
            return this;
        }

        public CoreSortIdx build() {
            return new CoreSortIdx(sort, idx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreSortIdx)) {
            return false;
        }
        CoreSortIdx sortIdx = (CoreSortIdx) o;
        return idx == sortIdx.idx && Objects.equals(sort, sortIdx.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sort, idx);
    }

    @Override
    public String toString() {
        return "CoreSortIdx{" + "sort=" + sort + ", idx=" + idx + '}';
    }
}
