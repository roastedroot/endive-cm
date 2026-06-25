package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TypeSection extends Section {

    private final List<Type> types;

    private TypeSection(List<Type> types) {
        super(SectionId.TYPE);
        this.types = List.copyOf(types);
    }

    public List<Type> types() {
        return types;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Type> types = new ArrayList<>();

        public Builder() {}

        public Builder addType(Type type) {
            types.add(requireNonNull(type, "type"));
            return this;
        }

        public TypeSection build() {
            return new TypeSection(types);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypeSection)) {
            return false;
        }
        TypeSection that = (TypeSection) o;
        return Objects.equals(types, that.types);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(types);
    }

    @Override
    public String toString() {
        return "TypeSection{" + "types=" + types + '}';
    }
}
