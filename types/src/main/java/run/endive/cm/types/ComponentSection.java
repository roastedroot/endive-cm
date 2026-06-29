package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

public final class ComponentSection extends Section {

    private final WasmComponent component;

    private ComponentSection(WasmComponent component) {
        super(SectionId.COMPONENT);
        this.component = requireNonNull(component, "component");
    }

    public WasmComponent component() {
        return component;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private WasmComponent component;

        private Builder() {}

        public Builder withComponent(WasmComponent component) {
            this.component = requireNonNull(component, "component");
            return this;
        }

        public ComponentSection build() {
            return new ComponentSection(component);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ComponentSection)) {
            return false;
        }
        ComponentSection that = (ComponentSection) o;
        return Objects.equals(component, that.component);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(component);
    }

    @Override
    public String toString() {
        return "ComponentSection{" + "component=" + component + '}';
    }
}
