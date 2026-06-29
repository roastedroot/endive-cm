package run.endive.cm.types;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RecordType extends DefValType {

    private final List<LabelValType> fields;

    private RecordType(List<LabelValType> fields) {
        super(Kind.RECORD);
        this.fields = List.copyOf(fields);
    }

    public List<LabelValType> fields() {
        return fields;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<LabelValType> fields = new ArrayList<>();

        private Builder() {}

        public Builder addField(LabelValType field) {
            fields.add(requireNonNull(field, "field"));
            return this;
        }

        public RecordType build() {
            return new RecordType(fields);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RecordType)) {
            return false;
        }
        RecordType that = (RecordType) o;
        return Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fields);
    }

    @Override
    public String toString() {
        return "RecordType{" + "fields=" + fields + '}';
    }
}
