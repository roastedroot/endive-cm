package run.endive.cm.types.canon;

import java.util.List;
import java.util.Objects;

public final class CanonFuture extends Canon {

    private static final List<Kind> VALID_KINDS =
            List.of(Kind.FUTURE_NEW, Kind.FUTURE_DROP_READABLE, Kind.FUTURE_DROP_WRITABLE);

    private final long typeIdx;

    private CanonFuture(Kind kind, long typeIdx) {
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
                throw new IllegalArgumentException("Invalid kind for canon future: " + kind);
            }
            this.kind = kind;
            return this;
        }

        public Builder withTypeIdx(long typeIdx) {
            this.typeIdx = typeIdx;
            return this;
        }

        public CanonFuture build() {
            return new CanonFuture(kind, typeIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonFuture)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        CanonFuture that = (CanonFuture) o;
        return typeIdx == that.typeIdx;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), typeIdx);
    }

    @Override
    public String toString() {
        return "CanonFuture{" + "kind=" + kind() + ", typeIdx=" + typeIdx + '}';
    }
}
