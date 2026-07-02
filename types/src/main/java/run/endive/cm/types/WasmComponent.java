package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class WasmComponent {

    private final List<Section> sections;

    private WasmComponent(List<Section> sections) {
        this.sections = List.copyOf(sections);
    }

    public static WasmComponent.Builder builder() {
        return new WasmComponent.Builder();
    }

    public List<CustomSection> coreCustomSections() {
        return sections.stream()
                .filter(CustomSection.class::isInstance)
                .map(CustomSection.class::cast)
                .collect(Collectors.toList());
    }

    public List<CoreModuleSection> coreModuleSections() {
        return sections.stream()
                .filter(CoreModuleSection.class::isInstance)
                .map(CoreModuleSection.class::cast)
                .collect(Collectors.toList());
    }

    public List<CoreInstanceSection> coreInstanceSections() {
        return sections.stream()
                .filter(CoreInstanceSection.class::isInstance)
                .map(CoreInstanceSection.class::cast)
                .collect(Collectors.toList());
    }

    public List<CoreTypeSection> coreTypeSections() {
        return sections.stream()
                .filter(CoreTypeSection.class::isInstance)
                .map(CoreTypeSection.class::cast)
                .collect(Collectors.toList());
    }

    public List<ComponentSection> componentSections() {
        return sections.stream()
                .filter(ComponentSection.class::isInstance)
                .map(ComponentSection.class::cast)
                .collect(Collectors.toList());
    }

    public List<InstanceSection> instanceSections() {
        return sections.stream()
                .filter(InstanceSection.class::isInstance)
                .map(InstanceSection.class::cast)
                .collect(Collectors.toList());
    }

    public List<AliasSection> aliasSections() {
        return sections.stream()
                .filter(AliasSection.class::isInstance)
                .map(AliasSection.class::cast)
                .collect(Collectors.toList());
    }

    public List<CanonSection> canonSections() {
        return sections.stream()
                .filter(CanonSection.class::isInstance)
                .map(CanonSection.class::cast)
                .collect(Collectors.toList());
    }

    public List<TypeSection> typeSections() {
        return sections.stream()
                .filter(TypeSection.class::isInstance)
                .map(TypeSection.class::cast)
                .collect(Collectors.toList());
    }

    public List<ImportSection> importSections() {
        return sections.stream()
                .filter(ImportSection.class::isInstance)
                .map(ImportSection.class::cast)
                .collect(Collectors.toList());
    }

    public List<ExportSection> exportSections() {
        return sections.stream()
                .filter(ExportSection.class::isInstance)
                .map(ExportSection.class::cast)
                .collect(Collectors.toList());
    }

    public static final class Builder {

        private final List<Section> sections = new ArrayList<>();

        private Builder() {}

        public Builder addCoreCustomSection(CustomSection customSection) {
            sections.add(requireNonNull(customSection, "coreCustomSection"));
            return this;
        }

        public Builder addCoreModuleSection(CoreModuleSection coreModuleSection) {
            sections.add(requireNonNull(coreModuleSection, "coreModuleSection"));
            return this;
        }

        public Builder addCoreInstanceSection(CoreInstanceSection coreInstanceSection) {
            sections.add(requireNonNull(coreInstanceSection, "coreInstanceSection"));
            return this;
        }

        public Builder addCoreTypeSection(CoreTypeSection coreTypeSection) {
            sections.add(requireNonNull(coreTypeSection, "coreTypeSection"));
            return this;
        }

        public Builder addComponentSection(ComponentSection componentSection) {
            sections.add(requireNonNull(componentSection, "componentSection"));
            return this;
        }

        public Builder addInstanceSection(InstanceSection instanceSection) {
            sections.add(requireNonNull(instanceSection, "instanceSection"));
            return this;
        }

        public Builder addAliasSection(AliasSection aliasSection) {
            sections.add(requireNonNull(aliasSection, "aliasSection"));
            return this;
        }

        public Builder addCanonSection(CanonSection canonSection) {
            sections.add(requireNonNull(canonSection, "canonSection"));
            return this;
        }

        public Builder addTypeSection(TypeSection typeSection) {
            sections.add(requireNonNull(typeSection, "typeSection"));
            return this;
        }

        public Builder addImportSection(ImportSection importSection) {
            sections.add(requireNonNull(importSection, "importSection"));
            return this;
        }

        public Builder addExportSection(ExportSection exportSection) {
            sections.add(requireNonNull(exportSection, "exportSection"));
            return this;
        }

        public WasmComponent build() {
            return new WasmComponent(sections);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WasmComponent)) {
            return false;
        }
        WasmComponent that = (WasmComponent) o;
        return Objects.equals(sections, that.sections);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sections);
    }

    @Override
    public String toString() {
        return "WasmComponent{" + "sections=" + sections + '}';
    }
}
