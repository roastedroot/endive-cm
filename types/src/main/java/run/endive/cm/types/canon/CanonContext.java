package run.endive.cm.types.canon;

import java.util.List;
import java.util.Objects;
import run.endive.cm.types.ValType;

public final class CanonContext extends Canon {

    private static final List<Kind> VALID_KINDS = List.of(Kind.CONTEXT_GET, Kind.CONTEXT_SET);

    private final ValType valType;
    private final long index;

    private CanonContext(Kind kind, ValType valType, long index) {
        super(kind);
        this.valType = Objects.requireNonNull(valType, "valType");
        this.index = index;
    }

    public ValType valType() {
        return valType;
    }

    public long index() {
        return index;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Kind kind;
        private ValType valType;
        private long index;

        public Builder withKind(Kind kind) {
            if (!VALID_KINDS.contains(kind)) {
                throw new IllegalArgumentException("Invalid kind for canon context: " + kind);
            }
            this.kind = kind;
            return this;
        }

        public Builder withValType(ValType valType) {
            this.valType = valType;
            return this;
        }

        public Builder withIndex(long index) {
            this.index = index;
            return this;
        }

        public CanonContext build() {
            return new CanonContext(kind, valType, index);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonContext)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        CanonContext that = (CanonContext) o;
        return index == that.index && Objects.equals(valType, that.valType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), valType, index);
    }

    @Override
    public String toString() {
        return "CanonContext{"
                + "kind="
                + kind()
                + ", valType="
                + valType
                + ", index="
                + index
                + '}';
    }
}
