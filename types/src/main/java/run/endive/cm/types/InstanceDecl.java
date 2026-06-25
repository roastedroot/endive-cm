package run.endive.cm.types;

import java.util.Objects;

public final class InstanceDecl {

    private final CoreType coreType;

    private final Type type;

    private final Alias alias;

    private final ExportDecl exportDecl;

    public static InstanceDecl of(CoreType coreType) {
        return new InstanceDecl(coreType, null, null, null);
    }

    public static InstanceDecl of(Type type) {
        return new InstanceDecl(null, type, null, null);
    }

    public static InstanceDecl of(Alias alias) {
        return new InstanceDecl(null, null, alias, null);
    }

    public static InstanceDecl of(ExportDecl exportDecl) {
        return new InstanceDecl(null, null, null, exportDecl);
    }

    private InstanceDecl(CoreType coreType, Type type, Alias alias, ExportDecl exportDecl) {
        this.coreType = coreType;
        this.type = type;
        this.alias = alias;
        this.exportDecl = exportDecl;
    }

    public CoreType coreType() {
        return coreType;
    }

    public Type type() {
        return type;
    }

    public Alias alias() {
        return alias;
    }

    public ExportDecl exportDecl() {
        return exportDecl;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InstanceDecl)) {
            return false;
        }
        InstanceDecl that = (InstanceDecl) o;
        return Objects.equals(coreType, that.coreType)
                && Objects.equals(type, that.type)
                && Objects.equals(alias, that.alias)
                && Objects.equals(exportDecl, that.exportDecl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coreType, type, alias, exportDecl);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("InstanceDecl{");
        if (coreType != null) {
            sb.append("coreType=").append(coreType);
        } else if (type != null) {
            sb.append("type=").append(type);
        } else if (alias != null) {
            sb.append("alias=").append(alias);
        } else if (exportDecl != null) {
            sb.append("exportDecl=").append(exportDecl);
        }
        sb.append('}');
        return sb.toString();
    }
}
