package io.roastedroot.typewire.types;

import java.util.Objects;

public final class InstanceDecl {

    private final Kind kind;

    private final CoreType coreType;

    private final Type type;

    private final Alias alias;

    private final ExportDecl exportDecl;

    public static InstanceDecl of(CoreType coreType) {
        return new InstanceDecl(Kind.CORE_TYPE, coreType, null, null, null);
    }

    public static InstanceDecl of(Type type) {
        return new InstanceDecl(Kind.TYPE, null, type, null, null);
    }

    public static InstanceDecl of(Alias alias) {
        return new InstanceDecl(Kind.ALIAS, null, null, alias, null);
    }

    public static InstanceDecl of(ExportDecl exportDecl) {
        return new InstanceDecl(Kind.EXPORT_DECL, null, null, null, exportDecl);
    }

    public enum Kind {
        CORE_TYPE,
        TYPE,
        ALIAS,
        EXPORT_DECL,
    }

    private InstanceDecl(
            Kind kind, CoreType coreType, Type type, Alias alias, ExportDecl exportDecl) {
        this.kind = kind;
        this.coreType = coreType;
        this.type = type;
        this.alias = alias;
        this.exportDecl = exportDecl;
    }

    public Kind kind() {
        return kind;
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InstanceDecl that = (InstanceDecl) o;
        return kind == that.kind
                && Objects.equals(coreType, that.coreType)
                && Objects.equals(type, that.type)
                && Objects.equals(alias, that.alias)
                && Objects.equals(exportDecl, that.exportDecl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, coreType, type, alias, exportDecl);
    }

    @Override
    public String toString() {
        return "InstanceDecl{"
                + "kind="
                + kind
                + ", coreType="
                + coreType
                + ", type="
                + type
                + ", alias="
                + alias
                + ", exportDecl="
                + exportDecl
                + '}';
    }
}
