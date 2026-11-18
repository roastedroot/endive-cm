package io.roastedroot.typewire.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WasmComponent {

    private final List<Section> sections;
    private final List<CustomSection> customSections;
    private final List<CoreModuleSection> coreModuleSections;
    private final List<CoreInstanceSection> coreInstanceSections;
    private final List<CoreTypeSection> coreTypeSections;
    private final List<ComponentSection> componentSections;
    private final List<InstanceSection> instanceSections;
    private final List<AliasSection> aliasSections;
    private final List<CanonSection> canonSections;
    private final List<TypeSection> typeSections;
    private final List<ImportSection> importSections;
    private final List<ExportSection> exportSections;

    private WasmComponent(List<Section> sections) {
        this.sections = List.copyOf(sections);

        var customSections = new ArrayList<CustomSection>();
        var coreModuleSections = new ArrayList<CoreModuleSection>();
        var coreInstanceSections = new ArrayList<CoreInstanceSection>();
        var coreTypeSections = new ArrayList<CoreTypeSection>();
        var componentSections = new ArrayList<ComponentSection>();
        var instanceSections = new ArrayList<InstanceSection>();
        var aliasSections = new ArrayList<AliasSection>();
        var canonSections = new ArrayList<CanonSection>();
        var typeSections = new ArrayList<TypeSection>();
        var importSections = new ArrayList<ImportSection>();
        var exportSections = new ArrayList<ExportSection>();

        for (Section s : this.sections) {
            if (s instanceof CustomSection) {
                customSections.add((CustomSection) s);
            } else if (s instanceof CoreModuleSection) {
                coreModuleSections.add((CoreModuleSection) s);
            } else if (s instanceof CoreInstanceSection) {
                coreInstanceSections.add((CoreInstanceSection) s);
            } else if (s instanceof CoreTypeSection) {
                coreTypeSections.add((CoreTypeSection) s);
            } else if (s instanceof ComponentSection) {
                componentSections.add((ComponentSection) s);
            } else if (s instanceof InstanceSection) {
                instanceSections.add((InstanceSection) s);
            } else if (s instanceof AliasSection) {
                aliasSections.add((AliasSection) s);
            } else if (s instanceof CanonSection) {
                canonSections.add((CanonSection) s);
            } else if (s instanceof TypeSection) {
                typeSections.add((TypeSection) s);
            } else if (s instanceof ImportSection) {
                importSections.add((ImportSection) s);
            } else if (s instanceof ExportSection) {
                exportSections.add((ExportSection) s);
            }
        }

        this.customSections = List.copyOf(customSections);
        this.coreModuleSections = List.copyOf(coreModuleSections);
        this.coreInstanceSections = List.copyOf(coreInstanceSections);
        this.coreTypeSections = List.copyOf(coreTypeSections);
        this.componentSections = List.copyOf(componentSections);
        this.instanceSections = List.copyOf(instanceSections);
        this.aliasSections = List.copyOf(aliasSections);
        this.canonSections = List.copyOf(canonSections);
        this.typeSections = List.copyOf(typeSections);
        this.importSections = List.copyOf(importSections);
        this.exportSections = List.copyOf(exportSections);
    }

    public static WasmComponent.Builder builder() {
        return new WasmComponent.Builder();
    }

    public List<Section> sections() {
        return sections;
    }

    public List<CustomSection> coreCustomSections() {
        return customSections;
    }

    public List<CoreModuleSection> coreModuleSections() {
        return coreModuleSections;
    }

    public List<CoreInstanceSection> coreInstanceSections() {
        return coreInstanceSections;
    }

    public List<CoreTypeSection> coreTypeSections() {
        return coreTypeSections;
    }

    public List<ComponentSection> componentSections() {
        return componentSections;
    }

    public List<InstanceSection> instanceSections() {
        return instanceSections;
    }

    public List<AliasSection> aliasSections() {
        return aliasSections;
    }

    public List<CanonSection> canonSections() {
        return canonSections;
    }

    public List<TypeSection> typeSections() {
        return typeSections;
    }

    public List<ImportSection> importSections() {
        return importSections;
    }

    public List<ExportSection> exportSections() {
        return exportSections;
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
