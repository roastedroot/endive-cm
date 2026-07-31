package run.endive.cm.types.canon;

import java.util.List;
import java.util.Objects;

public final class CanonResource extends Canon {

    private final long typeIdx;

    private static final List<Kind> VALID_KINDS =
            List.of(Kind.RESOURCE_NEW, Kind.RESOURCE_DROP, Kind.RESOURCE_REP);

    private CanonResource(Kind kind, long typeIdx) {
        super(kind);
        this.typeIdx = typeIdx;
    }

    public long typeIdx() {
        return typeIdx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Kind kind;
        private long typeIdx;

        public Builder withKind(Kind kind) {
            if (!VALID_KINDS.contains(kind)) {
                throw new IllegalArgumentException("Invalid kind for canon resource: " + kind);
            }
            this.kind = kind;
            return this;
        }

        public Builder withTypeIdx(long typeIdx) {
            this.typeIdx = typeIdx;
            return this;
        }

        public CanonResource build() {
            return new CanonResource(kind, typeIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonResource)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        CanonResource that = (CanonResource) o;
        return typeIdx == that.typeIdx;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), typeIdx);
    }

    @Override
    public String toString() {
        return "CanonResource{" + "kind=" + kind() + ", typeIdx=" + typeIdx + '}';
    }
}
