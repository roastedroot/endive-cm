package run.endive.cm.types;

import java.util.Objects;

public final class SortIdx {

    private final Sort sort;
    private final long idx;
    private static final int UNDEFINED_IDX = -1;

    private SortIdx(Sort sort, long idx) {
        Objects.requireNonNull(sort, "sort");
        if (idx == UNDEFINED_IDX) {
            throw new IllegalArgumentException("idx is required");
        }
        this.sort = sort;
        this.idx = idx;
    }

    public Sort sort() {
        return sort;
    }

    public long idx() {
        return idx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Sort sort;
        private long idx = UNDEFINED_IDX;

        public Builder withSort(Sort sort) {
            this.sort = sort;
            return this;
        }

        public Builder withIdx(long idx) {
            this.idx = idx;
            return this;
        }

        public SortIdx build() {
            return new SortIdx(sort, idx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SortIdx)) {
            return false;
        }
        SortIdx sortIdx = (SortIdx) o;
        return idx == sortIdx.idx && Objects.equals(sort, sortIdx.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sort, idx);
    }

    @Override
    public String toString() {
        return "SortIdx{" + "sort=" + sort + ", idx=" + idx + '}';
    }
}
