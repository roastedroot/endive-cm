package run.endive.cm.types;

import java.util.Objects;

public final class StreamType extends DefValType {

    private final ValType elementType;

    private StreamType(ValType elementType) {
        super(Kind.STREAM);
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

        public StreamType build() {
            return new StreamType(elementType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StreamType)) {
            return false;
        }
        StreamType that = (StreamType) o;
        return Objects.equals(elementType, that.elementType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(elementType);
    }

    @Override
    public String toString() {
        return "StreamType{" + "elementType=" + elementType + '}';
    }
}
