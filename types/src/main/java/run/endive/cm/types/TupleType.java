package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TupleType extends DefValType {
    private final List<ValType> elementTypes;

    private TupleType(List<ValType> elementTypes) {
        super(Kind.TUPLE);
        this.elementTypes = List.copyOf(elementTypes);
    }

    public List<ValType> elementTypes() {
        return elementTypes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<ValType> elementTypes = new ArrayList<>();

        private Builder() {}

        public Builder addElementType(ValType valType) {
            elementTypes.add(requireNonNull(valType, "valType"));
            return this;
        }

        public TupleType build() {
            return new TupleType(elementTypes);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TupleType)) {
            return false;
        }
        TupleType that = (TupleType) o;
        return Objects.equals(elementTypes, that.elementTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(elementTypes);
    }

    @Override
    public String toString() {
        return "TupleType{" + "elementTypes=" + elementTypes + '}';
    }
}
