package run.endive.cm.types.canon;

import java.util.List;
import java.util.Objects;

public final class CanonWaitableSetIo extends Canon {

    private static final List<Kind> VALID_KINDS =
            List.of(Kind.WAITABLE_SET_WAIT, Kind.WAITABLE_SET_POLL);

    private final boolean cancellable;
    private final long memIdx;

    private CanonWaitableSetIo(Kind kind, boolean cancellable, long memIdx) {
        super(kind);
        this.cancellable = cancellable;
        this.memIdx = memIdx;
    }

    public boolean isCancellable() {
        return cancellable;
    }

    public long memIdx() {
        return memIdx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Kind kind;
        private boolean cancellable;
        private long memIdx;

        public Builder withKind(Kind kind) {
            if (!VALID_KINDS.contains(kind)) {
                throw new IllegalArgumentException(
                        "Invalid kind for canon waitable-set io: " + kind);
            }
            this.kind = kind;
            return this;
        }

        public Builder withCancellable(boolean cancellable) {
            this.cancellable = cancellable;
            return this;
        }

        public Builder withMemIdx(long memIdx) {
            this.memIdx = memIdx;
            return this;
        }

        public CanonWaitableSetIo build() {
            return new CanonWaitableSetIo(kind, cancellable, memIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonWaitableSetIo)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        CanonWaitableSetIo that = (CanonWaitableSetIo) o;
        return cancellable == that.cancellable && memIdx == that.memIdx;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), cancellable, memIdx);
    }

    @Override
    public String toString() {
        return "CanonWaitableSetIo{"
                + "kind="
                + kind()
                + ", cancellable="
                + cancellable
                + ", memIdx="
                + memIdx
                + '}';
    }
}
