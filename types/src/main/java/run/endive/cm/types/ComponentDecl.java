package run.endive.cm.types;

import java.util.Objects;

public final class ComponentDecl {

    private final ImportDecl importDecl;

    private final InstanceDecl instanceDecl;

    public static ComponentDecl of(ImportDecl importDecl) {
        return new ComponentDecl(importDecl, null);
    }

    public static ComponentDecl of(InstanceDecl instanceDecl) {
        return new ComponentDecl(null, instanceDecl);
    }

    private ComponentDecl(ImportDecl importDecl, InstanceDecl instanceDecl) {
        this.importDecl = importDecl;
        this.instanceDecl = instanceDecl;
    }

    public ImportDecl importDecl() {
        return importDecl;
    }

    public InstanceDecl instanceDecl() {
        return instanceDecl;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ComponentDecl)) {
            return false;
        }
        ComponentDecl that = (ComponentDecl) o;
        return Objects.equals(importDecl, that.importDecl)
                && Objects.equals(instanceDecl, that.instanceDecl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(importDecl, instanceDecl);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ComponentDecl{");
        if (importDecl != null) {
            sb.append("importDecl=").append(importDecl);
        } else if (instanceDecl != null) {
            sb.append("instanceDecl=").append(instanceDecl);
        }
        sb.append('}');
        return sb.toString();
    }
}
