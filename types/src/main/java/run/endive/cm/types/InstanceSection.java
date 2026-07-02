package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InstanceSection extends Section {

    private final List<Instance> instances;

    private InstanceSection(List<Instance> instances) {
        super(SectionId.INSTANCE);
        this.instances = List.copyOf(instances);
    }

    public List<Instance> instances() {
        return instances;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Instance> instances = new ArrayList<>();

        public Builder addInstance(Instance instance) {
            Objects.requireNonNull(instance, "instance");
            this.instances.add(instance);
            return this;
        }

        public InstanceSection build() {
            return new InstanceSection(instances);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InstanceSection)) {
            return false;
        }
        InstanceSection that = (InstanceSection) o;
        return Objects.equals(instances, that.instances);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(instances);
    }

    @Override
    public String toString() {
        return "InstanceSection{" + "instances=" + instances + '}';
    }
}
