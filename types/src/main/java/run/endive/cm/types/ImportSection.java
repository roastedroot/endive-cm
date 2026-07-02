package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ImportSection extends Section {

    private final List<Import> imports;

    private ImportSection(List<Import> imports) {
        super(SectionId.IMPORT);
        this.imports = List.copyOf(imports);
    }

    public List<Import> imports() {
        return imports;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Import> imports = new ArrayList<>();

        public Builder addImport(Import imprt) {
            Objects.requireNonNull(imprt);
            imports.add(imprt);
            return this;
        }

        public ImportSection build() {
            return new ImportSection(imports);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ImportSection)) {
            return false;
        }
        ImportSection that = (ImportSection) o;
        return Objects.equals(imports, that.imports);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(imports);
    }

    @Override
    public String toString() {
        return "ImportSection{" + "imports=" + imports + '}';
    }
}
