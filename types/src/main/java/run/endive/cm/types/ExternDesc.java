package run.endive.cm.types;

import java.util.Objects;

public final class ExternDesc {

    public enum Kind {
        CORE_MODULE,
        FUNC,
        VALUE,
        TYPE,
        COMPONENT,
        INSTANCE
    }

    private final Kind kind;
    private final long typeIdx;
    private final TypeBound typeBound;
    private final ValueBound valueBound;
    private static final long UNDEFINED_IDX = -1L;

    private ExternDesc(Kind kind, long typeIdx, TypeBound typeBound, ValueBound valueBound) {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.TYPE) {
            Objects.requireNonNull(typeBound, "typeBound");
        } else if (kind == Kind.VALUE) {
            Objects.requireNonNull(valueBound, "valueBound");
        } else if (typeIdx == UNDEFINED_IDX) {
            throw new IllegalArgumentException(
                    "extern desc type index must be defined for all kinds other than typebound and"
                            + " valuebound");
        }

        this.kind = kind;
        this.typeIdx = typeIdx;
        this.typeBound = typeBound;
        this.valueBound = valueBound;
    }

    public Kind kind() {
        return kind;
    }

    public boolean hasTypeIdx() {
        return typeIdx != UNDEFINED_IDX;
    }

    public long typeIdx() {
        return typeIdx;
    }

    public TypeBound typeBound() {
        return typeBound;
    }

    public ValueBound valueBound() {
        return valueBound;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Kind kind;
        private long typeIdx = UNDEFINED_IDX;
        private TypeBound typeBound;
        private ValueBound valueBound;

        private Builder() {}

        public Builder withKind(Kind kind) {
            this.kind = kind;
            return this;
        }

        public Builder withTypeIdx(long typeIdx) {
            this.typeIdx = typeIdx;
            return this;
        }

        public Builder withTypeBound(TypeBound typeBound) {
            this.typeBound = typeBound;
            return this;
        }

        public Builder withValueBound(ValueBound valueBound) {
            this.valueBound = valueBound;
            return this;
        }

        public ExternDesc build() {
            return new ExternDesc(kind, typeIdx, typeBound, valueBound);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExternDesc)) {
            return false;
        }
        ExternDesc that = (ExternDesc) o;
        return kind == that.kind
                && typeIdx == that.typeIdx
                && Objects.equals(typeBound, that.typeBound)
                && Objects.equals(valueBound, that.valueBound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, typeIdx, typeBound, valueBound);
    }

    @Override
    public String toString() {
        return "ExternDesc{"
                + "kind="
                + kind
                + ", typeIdx="
                + typeIdx
                + ", typeBound="
                + typeBound
                + ", valueBound="
                + valueBound
                + '}';
    }
}
