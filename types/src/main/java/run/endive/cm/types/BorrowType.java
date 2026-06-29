package run.endive.cm.types;

import java.util.Objects;

public final class BorrowType extends DefValType {

    private final int typeIdx;

    private BorrowType(int typeIdx) {
        super(Kind.BORROW);
        this.typeIdx = typeIdx;
    }

    public int typeIdx() {
        return typeIdx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int typeIdx;

        private Builder() {}

        public Builder withTypeIdx(int typeIdx) {
            this.typeIdx = typeIdx;
            return this;
        }

        public BorrowType build() {
            return new BorrowType(typeIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BorrowType)) {
            return false;
        }
        BorrowType that = (BorrowType) o;
        return typeIdx == that.typeIdx;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(typeIdx);
    }

    @Override
    public String toString() {
        return "BorrowType{" + "typeIdx=" + typeIdx + '}';
    }
}
