package run.endive.cm.types;

public final class ListType extends DefValType {

    private final ValType elementType;
    private final int size;
    private static final int UNDEFINED_SIZE = -1;

    private ListType(ValType elementType, int fixedSize) {
        super(Kind.LIST);
        this.elementType = elementType;
        this.size = fixedSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ValType elementType() {
        return elementType;
    }

    public boolean isFixedSize() {
        return size != UNDEFINED_SIZE;
    }

    public int size() {
        return size;
    }

    public static final class Builder {
        private ValType elementType;
        private int size = UNDEFINED_SIZE;

        private Builder() {}

        public Builder withElementType(ValType elementType) {
            this.elementType = elementType;
            return this;
        }

        public Builder withFixedSize(int fixedSize) {
            this.size = fixedSize;
            return this;
        }

        public ListType build() {
            return new ListType(elementType, size);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ListType)) {
            return false;
        }
        ListType that = (ListType) o;
        return size == that.size && java.util.Objects.equals(elementType, that.elementType);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(elementType, size);
    }

    @Override
    public String toString() {
        return "ListType{" + "elementType=" + elementType + ", size=" + size + '}';
    }
}
