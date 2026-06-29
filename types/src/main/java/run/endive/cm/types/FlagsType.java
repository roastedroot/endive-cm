package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FlagsType extends DefValType {

    private final List<String> labels;

    private FlagsType(List<String> labels) {
        super(Kind.FLAGS);
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

        public FlagsType build() {
            return new FlagsType(labels);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FlagsType)) {
            return false;
        }
        FlagsType that = (FlagsType) o;
        return Objects.equals(labels, that.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(labels);
    }

    @Override
    public String toString() {
        return "FlagsType{" + "labels=" + labels + '}';
    }
}
