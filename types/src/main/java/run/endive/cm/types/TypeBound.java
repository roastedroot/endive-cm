package run.endive.cm.types;

import java.util.Objects;

public final class TypeBound {

    public enum Kind {
        EQ,
        SUB_RESOURCE
    }

    private final Kind kind;
    private final long typeIdx;

    private TypeBound(Kind kind, long typeIdx) {
        Objects.requireNonNull(kind, "kind");
        this.kind = kind;
        this.typeIdx = typeIdx;
    }

    public Kind kind() {
        return kind;
    }

    public long typeIdx() {
        return typeIdx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Kind kind;
        private long typeIdx;

        private Builder() {}

        public Builder withKind(Kind kind) {
            this.kind = kind;
            return this;
        }

        public Builder withTypeIdx(long typeIdx) {
            this.typeIdx = typeIdx;
            return this;
        }

        public TypeBound build() {
            return new TypeBound(kind, typeIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypeBound)) {
            return false;
        }
        TypeBound that = (TypeBound) o;
        return kind == that.kind && typeIdx == that.typeIdx;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, typeIdx);
    }

    @Override
    public String toString() {
        return "TypeBound{" + "kind=" + kind + ", typeIdx=" + typeIdx + '}';
    }
}
