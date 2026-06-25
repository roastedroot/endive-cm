package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

public final class MapType extends DefValType {

    private final ValType keyType;
    private final ValType valueType;

    private MapType(ValType keyType, ValType valueType) {
        super(ID.MAP);
        this.keyType = requireNonNull(keyType, "keyType");
        this.valueType = requireNonNull(valueType, "valueType");
    }

    public ValType keyType() {
        return keyType;
    }

    public ValType valueType() {
        return valueType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ValType keyType;
        private ValType valueType;

        private Builder() {}

        public Builder withKeyType(ValType keyType) {
            this.keyType = keyType;
            return this;
        }

        public Builder withValueType(ValType valueType) {
            this.valueType = valueType;
            return this;
        }

        public MapType build() {
            return new MapType(keyType, valueType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MapType)) {
            return false;
        }
        MapType that = (MapType) o;
        return Objects.equals(keyType, that.keyType) && Objects.equals(valueType, that.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyType, valueType);
    }

    @Override
    public String toString() {
        return "MapType{" + "keyType=" + keyType + ", valueType=" + valueType + '}';
    }
}
