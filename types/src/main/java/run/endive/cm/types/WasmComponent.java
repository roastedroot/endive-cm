package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WasmComponent {

    private final List<CustomSection> customSections;
    private final List<CoreTypeSection> coreTypeSections;
    private final List<ComponentSection> componentSections;
    private final List<TypeSection> typeSections;

    private WasmComponent(
            List<CustomSection> customSections,
            List<CoreTypeSection> coreTypeSections,
            List<ComponentSection> componentSections,
            List<TypeSection> typeSections) {
        this.customSections = List.copyOf(customSections);
        this.coreTypeSections = List.copyOf(coreTypeSections);
        this.componentSections = componentSections;
        this.typeSections = List.copyOf(typeSections);
    }

    public static WasmComponent.Builder builder() {
        return new WasmComponent.Builder();
    }

    public List<CustomSection> coreCustomSections() {
        return customSections;
    }

    public List<CoreTypeSection> coreTypeSections() {
        return coreTypeSections;
    }

    public List<ComponentSection> componentSections() {
        return componentSections;
    }

    public List<TypeSection> typeSections() {
        return typeSections;
    }

    public static final class Builder {

        private final List<CustomSection> customSections = new ArrayList<>();
        private final List<CoreTypeSection> coreTypeSections = new ArrayList<>();
        private final List<ComponentSection> componentSections = new ArrayList<>();
        private final List<TypeSection> typeSections = new ArrayList<>();

        private Builder() {}

        public Builder addCoreCustomSection(CustomSection customSection) {
            customSections.add(requireNonNull(customSection, "coreCustomSection"));
            return this;
        }

        public Builder addCoreTypeSection(CoreTypeSection coreTypeSection) {
            coreTypeSections.add(requireNonNull(coreTypeSection, "coreTypeSection"));
            return this;
        }

        public Builder addComponentSection(ComponentSection componentSection) {
            componentSections.add(requireNonNull(componentSection, "componentSection"));
            return this;
        }

        public Builder addTypeSection(TypeSection typeSection) {
            typeSections.add(requireNonNull(typeSection, "typeSection"));
            return this;
        }

        public WasmComponent build() {
            return new WasmComponent(
                    customSections, coreTypeSections, componentSections, typeSections);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WasmComponent)) {
            return false;
        }
        WasmComponent that = (WasmComponent) o;
        return Objects.equals(customSections, that.customSections)
                && Objects.equals(coreTypeSections, that.coreTypeSections)
                && Objects.equals(componentSections, that.componentSections)
                && Objects.equals(typeSections, that.typeSections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customSections, coreTypeSections, componentSections, typeSections);
    }

    @Override
    public String toString() {
        return "WasmComponent{"
                + "coreTypeSections="
                + coreTypeSections
                + ", componentSections="
                + componentSections
                + ", typeSections="
                + typeSections
                + '}';
    }
}
