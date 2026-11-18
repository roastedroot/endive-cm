package io.roastedroot.typewire.types;

import java.util.Objects;

public final class CoreAlias {

    private final CoreSort sort;
    private final long count;
    private final long index;

    private CoreAlias(CoreSort sort, long count, long index) {
        Objects.requireNonNull(sort, "sort");
        this.sort = sort;
        this.count = count;
        this.index = index;
    }

    public CoreSort sort() {
        return sort;
    }

    public long count() {
        return count;
    }

    public long index() {
        return index;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private CoreSort sort;
        private long count;
        private long index;

        private Builder() {}

        public Builder withSort(CoreSort sort) {
            this.sort = sort;
            return this;
        }

        public Builder withCount(long count) {
            this.count = count;
            return this;
        }

        public Builder withIndex(long index) {
            this.index = index;
            return this;
        }

        public CoreAlias build() {
            return new CoreAlias(sort, count, index);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreAlias)) {
            return false;
        }
        CoreAlias that = (CoreAlias) o;
        return count == that.count && index == that.index && Objects.equals(sort, that.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sort, count, index);
    }

    @Override
    public String toString() {
        return "CoreAlias{" + "sort=" + sort + ", count=" + count + ", index=" + index + '}';
    }
}
