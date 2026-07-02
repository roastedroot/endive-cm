package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ExportSection extends Section {

    private final List<Export> exports;

    private ExportSection(List<Export> exports) {
        super(SectionId.EXPORT);
        this.exports = List.copyOf(exports);
    }

    public List<Export> exports() {
        return exports;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Export> exports = new ArrayList<>();

        public Builder addExport(Export export) {
            Objects.requireNonNull(export);
            exports.add(export);
            return this;
        }

        public ExportSection build() {
            return new ExportSection(exports);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExportSection)) {
            return false;
        }
        ExportSection that = (ExportSection) o;
        return Objects.equals(exports, that.exports);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(exports);
    }

    @Override
    public String toString() {
        return "ExportSection{" + "exports=" + exports + '}';
    }
}
