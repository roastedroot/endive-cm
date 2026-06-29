package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

public final class LabelValType {

    private final String label;
    private final ValType valType;

    private LabelValType(String label, ValType valType) {
        this.label = requireNonNull(label, "label");
        this.valType = requireNonNull(valType, "valType");
    }

    public String label() {
        return label;
    }

    public ValType valType() {
        return valType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String label;
        private ValType valType;

        private Builder() {}

        public Builder withLabel(String label) {
            this.label = label;
            return this;
        }

        public Builder withValType(ValType valType) {
            this.valType = valType;
            return this;
        }

        public LabelValType build() {
            return new LabelValType(label, valType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LabelValType)) {
            return false;
        }
        LabelValType that = (LabelValType) o;
        return Objects.equals(label, that.label) && Objects.equals(valType, that.valType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, valType);
    }

    @Override
    public String toString() {
        return "LabelValType{" + "label='" + label + '\'' + ", valType=" + valType + '}';
    }
}
