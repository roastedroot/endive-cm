package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CustomSection)) {
            return false;
        }
        CustomSection that = (CustomSection) o;
        return Objects.equals(customSection, that.customSection);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(customSection);
    }

    @Override
    public String toString() {
        return "CustomSection{" + "customSection=" + customSection + '}';
    }
}
