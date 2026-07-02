package run.endive.cm.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CoreInstanceSection extends Section {

    private final List<CoreInstance> instances;

    private CoreInstanceSection(List<CoreInstance> instances) {
        super(SectionId.CORE_INSTANCE);
        this.instances = List.copyOf(instances);
    }

    public List<CoreInstance> instances() {
        return instances;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<CoreInstance> instances = new ArrayList<>();

        public Builder addInstance(CoreInstance instance) {
            Objects.requireNonNull(instance, "instance");
            this.instances.add(instance);
            return this;
        }

        public CoreInstanceSection build() {
            return new CoreInstanceSection(instances);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreInstanceSection)) {
            return false;
        }
        CoreInstanceSection that = (CoreInstanceSection) o;
        return Objects.equals(instances, that.instances);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(instances);
    }

    @Override
    public String toString() {
        return "CoreInstanceSection{" + "instances=" + instances + '}';
    }
}
