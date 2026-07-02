package run.endive.cm.types;

import java.util.Objects;

public final class CoreExportDecl {

    private final String name;
    private final CoreImportDesc coreImportDesc;

    public CoreExportDecl(String name, CoreImportDesc coreImportDesc) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(coreImportDesc, "coreImportDesc");
        this.name = name;
        this.coreImportDesc = coreImportDesc;
    }

    public String name() {
        return name;
    }

    public CoreImportDesc coreImportDesc() {
        return coreImportDesc;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private CoreImportDesc coreImportDesc;

        public Builder withName(String name) {
            Objects.requireNonNull(name, "name");
            this.name = name;
            return this;
        }

        public Builder withCoreImportDesc(CoreImportDesc coreImportDesc) {
            Objects.requireNonNull(coreImportDesc, "coreImportDesc");
            this.coreImportDesc = coreImportDesc;
            return this;
        }

        public CoreExportDecl build() {
            return new CoreExportDecl(name, coreImportDesc);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreExportDecl)) {
            return false;
        }
        CoreExportDecl that = (CoreExportDecl) o;
        return Objects.equals(name, that.name)
                && Objects.equals(coreImportDesc, that.coreImportDesc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, coreImportDesc);
    }

    @Override
    public String toString() {
        return "CoreExportDecl{"
                + "name='"
                + name
                + '\''
                + ", coreImportDesc="
                + coreImportDesc
                + '}';
    }
}
