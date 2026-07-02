package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InlineExportInstanceExpr extends InstanceExpr {

    private final List<InlineExport> inlineExports;

    private InlineExportInstanceExpr(List<InlineExport> inlineExports) {
        super(Kind.INLINE_EXPORT);
        this.inlineExports = List.copyOf(inlineExports);
    }

    public List<InlineExport> inlineExports() {
        return inlineExports;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<InlineExport> inlineExports = new ArrayList<>();

        private Builder() {}

        public Builder addInlineExport(InlineExport inlineExport) {
            Objects.requireNonNull(inlineExport, "inlineExport");
            this.inlineExports.add(inlineExport);
            return this;
        }

        public InlineExportInstanceExpr build() {
            return new InlineExportInstanceExpr(inlineExports);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InlineExportInstanceExpr)) {
            return false;
        }
        InlineExportInstanceExpr that = (InlineExportInstanceExpr) o;
        return Objects.equals(inlineExports, that.inlineExports);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(inlineExports);
    }

    @Override
    public String toString() {
        return "InlineExportInstanceExpr{" + "inlineExports=" + inlineExports + '}';
    }
}
