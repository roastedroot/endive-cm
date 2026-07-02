package run.endive.cm.types;

import java.util.Objects;

public final class CanonResource extends Canon {

    private final long typeIdx;

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

    public static class Builder {
        private Kind kind;
        private long typeIdx;

        public Builder withKind(Kind kind) {
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
