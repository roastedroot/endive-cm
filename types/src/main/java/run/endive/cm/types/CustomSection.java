package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

public final class CustomSection extends Section {

    private final run.endive.wasm.types.CustomSection customSection;

    private CustomSection(run.endive.wasm.types.CustomSection customSection) {
        super(SectionId.CUSTOM);
        this.customSection = customSection;
    }

    public run.endive.wasm.types.CustomSection customSection() {
        return customSection;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private run.endive.wasm.types.CustomSection customSection;

        private Builder() {}

        public CustomSection build() {
            return new CustomSection(customSection);
        }

        public Builder withCustomSection(run.endive.wasm.types.CustomSection customSection) {
            this.customSection = requireNonNull(customSection, "customSection");
            return this;
        }
    }
}
