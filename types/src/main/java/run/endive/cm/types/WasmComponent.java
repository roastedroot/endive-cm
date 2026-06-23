package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WasmComponent {

    private final List<CustomSection> customSections;
    private final List<CoreTypeSection> coreTypeSections;

    private WasmComponent(
            List<CustomSection> customSections, List<CoreTypeSection> coreTypeSections) {
        this.customSections = List.copyOf(customSections);
        this.coreTypeSections = List.copyOf(coreTypeSections);
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

    public static final class Builder {

        private final List<CustomSection> customSections = new ArrayList<>();
        private final List<CoreTypeSection> coreTypeSections = new ArrayList<>();

        private Builder() {}

        public Builder addCoreCustomSection(CustomSection customSection) {
            customSections.add(requireNonNull(customSection, "coreCustomSection"));
            return this;
        }

        public Builder addCoreTypeSection(CoreTypeSection coreTypeSection) {
            coreTypeSections.add(requireNonNull(coreTypeSection, "coreTypeSection"));
            return this;
        }

        public WasmComponent build() {
            return new WasmComponent(customSections, coreTypeSections);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WasmComponent)) {
            return false;
        }
        WasmComponent that = (WasmComponent) o;
        return Objects.equals(coreTypeSections, that.coreTypeSections);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(coreTypeSections);
    }

    @Override
    public String toString() {
        return "WasmComponent{" + "coreTypeSections=" + coreTypeSections + '}';
    }
}
