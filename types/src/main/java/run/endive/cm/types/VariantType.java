package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VariantType extends DefValType {

    private final List<Case> cases;

    private VariantType(List<Case> cases) {
        super(Kind.VARIANT);
        this.cases = List.copyOf(cases);
    }

    public List<Case> cases() {
        return cases;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Case> cases = new ArrayList<>();

        private Builder() {}

        public Builder addCase(Case variantCase) {
            cases.add(requireNonNull(variantCase, "variantCase"));
            return this;
        }

        public VariantType build() {
            return new VariantType(cases);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VariantType)) {
            return false;
        }
        VariantType that = (VariantType) o;
        return Objects.equals(cases, that.cases);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(cases);
    }

    @Override
    public String toString() {
        return "VariantType{" + "cases=" + cases + '}';
    }
}
