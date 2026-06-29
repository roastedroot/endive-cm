package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EnumType extends DefValType {

    private final List<String> labels;

    private EnumType(List<String> labels) {
        super(Kind.ENUM);
        this.labels = List.copyOf(labels);
    }

    public List<String> labels() {
        return labels;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> labels = new ArrayList<>();

        private Builder() {}

        public Builder addLabel(String label) {
            labels.add(requireNonNull(label, "label"));
            return this;
        }

        public EnumType build() {
            return new EnumType(labels);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EnumType)) {
            return false;
        }
        EnumType that = (EnumType) o;
        return Objects.equals(labels, that.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(labels);
    }

    @Override
    public String toString() {
        return "EnumType{" + "labels=" + labels + '}';
    }
}
