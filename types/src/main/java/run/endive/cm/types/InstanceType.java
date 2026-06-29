package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InstanceType {

    private final List<InstanceDecl> instanceDecls;

    private InstanceType(List<InstanceDecl> instanceDecls) {
        this.instanceDecls = List.copyOf(instanceDecls);
    }

    public List<InstanceDecl> getInstanceDecls() {
        return instanceDecls;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<InstanceDecl> instanceDecls = new ArrayList<>();

        public Builder addInstanceDecl(InstanceDecl instanceDecl) {
            instanceDecls.add(instanceDecl);
            return this;
        }

        public InstanceType build() {
            return new InstanceType(instanceDecls);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InstanceType)) {
            return false;
        }
        InstanceType that = (InstanceType) o;
        return Objects.equals(instanceDecls, that.instanceDecls);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(instanceDecls);
    }

    @Override
    public String toString() {
        return "InstanceType{" + "instanceDecls=" + instanceDecls + '}';
    }
}
