package run.endive.cm.types;

import java.util.Objects;

public final class Sort {

    public enum Kind {
        CORE,
        FUNC,
        VALUE,
        TYPE,
        COMPONENT,
        INSTANCE
    }

    private final Kind kind;
    private final CoreSort coreSort;

    private Sort(Kind kind, CoreSort coreSort) {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.CORE) {
            Objects.requireNonNull(coreSort, "coreSort");
        } else if (coreSort != null) {
            throw new IllegalArgumentException("coreSort may only be set for CORE sort");
        }
        this.kind = kind;
        this.coreSort = coreSort;
    }

    public Kind kind() {
        return kind;
    }

    public CoreSort coreSort() {
        return coreSort;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Kind kind;
        private CoreSort coreSort;

        private Builder() {}

        public Builder withKind(Kind kind) {
            this.kind = kind;
            return this;
        }

        public Builder withCoreSort(CoreSort coreSort) {
            this.coreSort = coreSort;
            return this;
        }

        public Sort build() {
            return new Sort(kind, coreSort);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Sort)) {
            return false;
        }
        Sort that = (Sort) o;
        return kind == that.kind && coreSort == that.coreSort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, coreSort);
    }

    @Override
    public String toString() {
        return "Sort{" + "kind=" + kind + ", coreSort=" + coreSort + '}';
    }
}
