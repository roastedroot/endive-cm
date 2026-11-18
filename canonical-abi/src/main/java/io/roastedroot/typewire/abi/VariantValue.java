package io.roastedroot.typewire.abi;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

/** A loaded/lowered variant value: the matched case's label and its payload (if any). */
public final class VariantValue {

    private final String label;
    private final Object value;

    private VariantValue(String label, Object value) {
        this.label = requireNonNull(label, "label");
        this.value = value;
    }

    public static VariantValue of(String label, Object value) {
        return new VariantValue(label, value);
    }

    public String label() {
        return label;
    }

    public Object value() {
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VariantValue)) {
            return false;
        }
        VariantValue that = (VariantValue) o;
        return Objects.equals(label, that.label) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, value);
    }

    @Override
    public String toString() {
        return "VariantValue{" + "label='" + label + '\'' + ", value=" + value + '}';
    }
}
