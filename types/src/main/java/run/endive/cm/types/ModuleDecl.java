package run.endive.cm.types;

import java.util.Objects;

public final class ModuleDecl {

    private final CoreImportDecl importDecl;

    private final CoreAlias alias;

    private final CoreType type;

    private final CoreExportDecl exportDecl;

    private static void requireExactlyOneNonNull(Object a, Object b, Object c, Object d) {
        if ((a == null ? 0 : 1) + (b == null ? 0 : 1) + (c == null ? 0 : 1) + (d == null ? 0 : 1)
                != 1) {
            throw new IllegalArgumentException("Exactly one field must be filled");
        }
    }

    private ModuleDecl(
            CoreImportDecl importDecl, CoreAlias alias, CoreType type, CoreExportDecl exportDecl) {
        requireExactlyOneNonNull(importDecl, alias, type, exportDecl);
        this.importDecl = importDecl;
        this.alias = alias;
        this.type = type;
        this.exportDecl = exportDecl;
    }

    public CoreImportDecl importDecl() {
        return importDecl;
    }

    public CoreAlias alias() {
        return alias;
    }

    public CoreType type() {
        return type;
    }

    public CoreExportDecl exportDecl() {
        return exportDecl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private CoreImportDecl importDecl;

        private CoreAlias alias;

        private CoreType type;

        private CoreExportDecl exportDecl;

        public Builder() {}

        public Builder withImportDecl(CoreImportDecl importDecl) {
            this.importDecl = importDecl;
            return this;
        }

        public Builder withAlias(CoreAlias alias) {
            this.alias = alias;
            return this;
        }

        public Builder withType(CoreType type) {
            this.type = type;
            return this;
        }

        public Builder withExportDecl(CoreExportDecl exportDecl) {
            this.exportDecl = exportDecl;
            return this;
        }

        public ModuleDecl build() {
            return new ModuleDecl(importDecl, alias, type, exportDecl);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ModuleDecl)) return false;
        ModuleDecl that = (ModuleDecl) o;
        return Objects.equals(importDecl, that.importDecl) && Objects.equals(alias, that.alias) && Objects.equals(type, that.type) && Objects.equals(exportDecl, that.exportDecl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(importDecl, alias, type, exportDecl);
    }

    @Override
    public String toString() {
        return "ModuleDecl{" +
                "importDecl=" + importDecl +
                ", alias=" + alias +
                ", type=" + type +
                ", exportDecl=" + exportDecl +
                '}';
    }
}
