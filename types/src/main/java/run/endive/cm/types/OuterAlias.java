package run.endive.cm.types;

import java.util.Objects;

public final class OuterAlias extends Alias {

    private final long count;
    private final long index;

    private OuterAlias(Sort sort, long count, long index) {
        super(Kind.OUTER, sort);
        this.count = count;
        this.index = index;
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

        private Sort sort;
        private long count;
        private long index;

        private Builder() {}

        public Builder withSort(Sort sort) {
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

        public OuterAlias build() {
            return new OuterAlias(sort, count, index);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OuterAlias)) {
            return false;
        }
        OuterAlias that = (OuterAlias) o;
        return count == that.count && index == that.index && Objects.equals(sort(), that.sort());
    }

    @Override
    public int hashCode() {
        return Objects.hash(sort(), count, index);
    }

    @Override
    public String toString() {
        return "OuterAlias{" + "sort=" + sort() + ", count=" + count + ", index=" + index + '}';
    }
}
