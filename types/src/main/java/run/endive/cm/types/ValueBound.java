package run.endive.cm.types;

import java.util.Objects;

public final class ValueBound {

    public enum Kind {
        EQ,
        VAL
    }

    private final Kind kind;
    private final long valueIdx;
    private final ValType valType;

    private ValueBound(Kind kind, long valueIdx, ValType valType) {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.VAL) {
            Objects.requireNonNull(valType, "valType");
        }
        this.kind = kind;
        this.valueIdx = valueIdx;
        this.valType = valType;
    }

    public Kind kind() {
        return kind;
    }

    public long valueIdx() {
        return valueIdx;
    }

    public ValType valType() {
        return valType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Kind kind;
        private long valueIdx;
        private ValType valType;

        private Builder() {}

        public Builder withKind(Kind kind) {
            this.kind = kind;
            return this;
        }

        public Builder withValueIdx(long valueIdx) {
            this.valueIdx = valueIdx;
            return this;
        }

        public Builder withValType(ValType valType) {
            this.valType = valType;
            return this;
        }

        public ValueBound build() {
            return new ValueBound(kind, valueIdx, valType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ValueBound)) {
            return false;
        }
        ValueBound that = (ValueBound) o;
        return kind == that.kind
                && valueIdx == that.valueIdx
                && Objects.equals(valType, that.valType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, valueIdx, valType);
    }

    @Override
    public String toString() {
        return "ValueBound{"
                + "kind="
                + kind
                + ", valueIdx="
                + valueIdx
                + ", valType="
                + valType
                + '}';
    }
}
