package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CoreInlineExportInstanceExpr extends CoreInstanceExpr {

    private final List<CoreInlineExport> inlineExports;

    private CoreInlineExportInstanceExpr(List<CoreInlineExport> inlineExports) {
        super(Kind.INLINE_EXPORT);
        this.inlineExports = List.copyOf(inlineExports);
    }

    public List<CoreInlineExport> inlineExports() {
        return inlineExports;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<CoreInlineExport> inlineExports = new ArrayList<>();

        private Builder() {}

        public Builder addInlineExport(CoreInlineExport inlineExport) {
            Objects.requireNonNull(inlineExport, "inlineExport");
            this.inlineExports.add(inlineExport);
            return this;
        }

        public CoreInlineExportInstanceExpr build() {
            return new CoreInlineExportInstanceExpr(inlineExports);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreInlineExportInstanceExpr)) {
            return false;
        }
        CoreInlineExportInstanceExpr that = (CoreInlineExportInstanceExpr) o;
        return Objects.equals(inlineExports, that.inlineExports);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(inlineExports);
    }

    @Override
    public String toString() {
        return "CoreInlineExportInstanceExpr{" + "inlineExports=" + inlineExports + '}';
    }
}
