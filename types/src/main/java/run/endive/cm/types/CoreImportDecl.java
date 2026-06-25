package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import run.endive.wasm.types.Import;

public final class CoreImportDecl {
    private final Import coreImport;

    private CoreImportDecl(Import coreImport) {
        this.coreImport = coreImport;
    }

    public Import coreImport() {
        return coreImport;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Import coreImport;

        private Builder() {}

        public Builder withCoreImport(Import coreImport) {
            this.coreImport = requireNonNull(coreImport, "coreImport");
            return this;
        }

        public CoreImportDecl build() {
            return new CoreImportDecl(coreImport);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreImportDecl)) {
            return false;
        }
        CoreImportDecl that = (CoreImportDecl) o;
        return Objects.equals(coreImport, that.coreImport);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(coreImport);
    }

    @Override
    public String toString() {
        return "CoreImportDecl{" + "coreImport=" + coreImport + '}';
    }
}
