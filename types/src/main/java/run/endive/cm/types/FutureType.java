package run.endive.cm.types;

import java.util.Objects;

public final class FutureType extends DefValType {

    private final ValType elementType;

    private FutureType(ValType elementType) {
        super(ID.FUTURE);
        this.elementType = elementType;
    }

    public ValType elementType() {
        return elementType;
    }

    public boolean hasElementType() {
        return elementType != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ValType elementType;

        private Builder() {}

        public Builder withElementType(ValType elementType) {
            this.elementType = elementType;
            return this;
        }

        public FutureType build() {
            return new FutureType(elementType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FutureType)) {
            return false;
        }
        FutureType that = (FutureType) o;
        return Objects.equals(elementType, that.elementType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(elementType);
    }

    @Override
    public String toString() {
        return "FutureType{" + "elementType=" + elementType + '}';
    }
}
