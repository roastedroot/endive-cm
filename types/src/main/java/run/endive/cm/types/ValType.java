package run.endive.cm.types;

import java.util.Objects;

public final class ValType {

    private final int typeIdx;
    private final PrimValType primValType;
    private static final int UNDEFINED_INDEX = -1;

    private ValType(int typeIdx, PrimValType primValType) {
        if ((typeIdx == UNDEFINED_INDEX ? 0 : 1) + (primValType == null ? 0 : 1) != 1) {
            throw new IllegalArgumentException("only one of typeIdx or primValType should be set");
        }
        this.typeIdx = typeIdx;
        this.primValType = primValType;
    }

    public int typeIdx() {
        return typeIdx;
    }

    public PrimValType primValType() {
        return primValType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int typeIdx = UNDEFINED_INDEX;
        private PrimValType primValType;

        private Builder() {}

        public Builder withTypeIdx(int typeIdx) {
            this.typeIdx = typeIdx;
            return this;
        }

        public Builder withPrimValType(PrimValType primValType) {
            this.primValType = primValType;
            return this;
        }

        public ValType build() {
            return new ValType(typeIdx, primValType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ValType)) {
            return false;
        }
        ValType that = (ValType) o;
        return typeIdx == that.typeIdx && Objects.equals(primValType, that.primValType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeIdx, primValType);
    }

    @Override
    public String toString() {
        return "ValType{" + "typeIdx=" + typeIdx + ", primValType=" + primValType + '}';
    }
}
