package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ComponentType {

    private final List<ComponentDecl> componentDecls;

    private ComponentType(List<ComponentDecl> componentDecls) {
        this.componentDecls = List.copyOf(componentDecls);
    }

    public List<ComponentDecl> getComponentDecls() {
        return componentDecls;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<ComponentDecl> componentDecls = new ArrayList<>();

        public Builder addComponentDecl(ComponentDecl componentDecl) {
            componentDecls.add(componentDecl);
            return this;
        }

        public ComponentType build() {
            return new ComponentType(componentDecls);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ComponentType)) {
            return false;
        }
        ComponentType that = (ComponentType) o;
        return Objects.equals(componentDecls, that.componentDecls);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(componentDecls);
    }

    @Override
    public String toString() {
        return "ComponentType{" + "componentDecls=" + componentDecls + '}';
    }
}
