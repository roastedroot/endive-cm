package run.endive.cm.types.canon;

import java.util.List;
import java.util.Objects;

public final class CanonStreamCancel extends Canon {

    private static final List<Kind> VALID_KINDS =
            List.of(Kind.STREAM_CANCEL_READ, Kind.STREAM_CANCEL_WRITE);

    private final long typeIdx;
    private final boolean async;

    private CanonStreamCancel(Kind kind, long typeIdx, boolean async) {
        super(kind);
        this.typeIdx = typeIdx;
        this.async = async;
    }

    public long typeIdx() {
        return typeIdx;
    }

    public boolean isAsync() {
        return async;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Kind kind;
        private long typeIdx;
        private boolean async;

        public Builder withKind(Kind kind) {
            if (!VALID_KINDS.contains(kind)) {
                throw new IllegalArgumentException("Invalid kind for canon stream cancel: " + kind);
            }
            this.kind = kind;
            return this;
        }

        public Builder withTypeIdx(long typeIdx) {
            this.typeIdx = typeIdx;
            return this;
        }

        public Builder withAsync(boolean async) {
            this.async = async;
            return this;
        }

        public CanonStreamCancel build() {
            return new CanonStreamCancel(kind, typeIdx, async);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonStreamCancel)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        CanonStreamCancel that = (CanonStreamCancel) o;
        return typeIdx == that.typeIdx && async == that.async;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), typeIdx, async);
    }

    @Override
    public String toString() {
        return "CanonStreamCancel{"
                + "kind="
                + kind()
                + ", typeIdx="
                + typeIdx
                + ", async="
                + async
                + '}';
    }
}
