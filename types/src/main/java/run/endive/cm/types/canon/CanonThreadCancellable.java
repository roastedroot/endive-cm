package run.endive.cm.types.canon;

import java.util.List;
import java.util.Objects;

public final class CanonThreadCancellable extends Canon {

    private static final List<Kind> VALID_KINDS =
            List.of(
                    Kind.THREAD_SUSPEND,
                    Kind.THREAD_YIELD,
                    Kind.THREAD_SUSPEND_THEN_RESUME,
                    Kind.THREAD_YIELD_THEN_RESUME,
                    Kind.THREAD_SUSPEND_THEN_PROMOTE,
                    Kind.THREAD_YIELD_THEN_PROMOTE);

    private final boolean cancellable;

    private CanonThreadCancellable(Kind kind, boolean cancellable) {
        super(kind);
        this.cancellable = cancellable;
    }

    public boolean isCancellable() {
        return cancellable;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Kind kind;
        private boolean cancellable;

        public Builder withKind(Kind kind) {
            if (!VALID_KINDS.contains(kind)) {
                throw new IllegalArgumentException(
                        "Invalid kind for canon thread cancellable op: " + kind);
            }
            this.kind = kind;
            return this;
        }

        public Builder withCancellable(boolean cancellable) {
            this.cancellable = cancellable;
            return this;
        }

        public CanonThreadCancellable build() {
            return new CanonThreadCancellable(kind, cancellable);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonThreadCancellable)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        CanonThreadCancellable that = (CanonThreadCancellable) o;
        return cancellable == that.cancellable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), cancellable);
    }

    @Override
    public String toString() {
        return "CanonThreadCancellable{" + "kind=" + kind() + ", cancellable=" + cancellable + '}';
    }
}
