package run.endive.cm.types;

import java.util.Objects;

public final class OwnType extends DefValType {

    private final int typeIdx;

    private OwnType(int typeIdx) {
        super(Kind.OWN);
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

        public OwnType build() {
            return new OwnType(typeIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OwnType)) {
            return false;
        }
        OwnType that = (OwnType) o;
        return typeIdx == that.typeIdx;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(typeIdx);
    }

    @Override
    public String toString() {
        return "OwnType{" + "typeIdx=" + typeIdx + '}';
    }
}
