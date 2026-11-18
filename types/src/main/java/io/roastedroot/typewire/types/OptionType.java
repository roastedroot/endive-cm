package io.roastedroot.typewire.types;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

public final class OptionType extends DefValType implements Specialized<VariantType> {

    private final ValType valType;

    private OptionType(ValType valType) {
        super(Kind.OPTION);
        this.valType = requireNonNull(valType, "valType");
    }

    public ValType valType() {
        return valType;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public VariantType despecialize() {
        return VariantType.builder()
                .addCase(Case.builder().withLabel("none").build())
                .addCase(Case.builder().withLabel("some").withValType(valType).build())
                .build();
    }

    public static final class Builder {
        private ValType valType;

        private Builder() {}

        public Builder withValType(ValType valType) {
            this.valType = valType;
            return this;
        }

        public OptionType build() {
            return new OptionType(valType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OptionType)) {
            return false;
        }
        OptionType that = (OptionType) o;
        return Objects.equals(valType, that.valType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(valType);
    }

    @Override
    public String toString() {
        return "OptionType{" + "valType=" + valType + '}';
    }
}
