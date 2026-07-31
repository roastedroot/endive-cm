package run.endive.cm.types.canon;

import java.util.List;
import java.util.Objects;

public final class CanonErrorContext extends Canon {

    private static final List<Kind> VALID_KINDS =
            List.of(Kind.ERROR_CONTEXT_NEW, Kind.ERROR_CONTEXT_DEBUG_MESSAGE);

    private final List<CanonOpt> opts;

    private CanonErrorContext(Kind kind, List<CanonOpt> opts) {
        super(kind);
        this.opts = List.copyOf(opts);
    }

    public List<CanonOpt> opts() {
        return opts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Kind kind;
        private List<CanonOpt> opts = List.of();

        public Builder withKind(Kind kind) {
            if (!VALID_KINDS.contains(kind)) {
                throw new IllegalArgumentException("Invalid kind for canon error-context: " + kind);
            }
            this.kind = kind;
            return this;
        }

        public Builder withOpts(List<CanonOpt> opts) {
            this.opts = opts;
            return this;
        }

        public CanonErrorContext build() {
            return new CanonErrorContext(kind, opts);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonErrorContext)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        CanonErrorContext that = (CanonErrorContext) o;
        return Objects.equals(opts, that.opts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), opts);
    }

    @Override
    public String toString() {
        return "CanonErrorContext{" + "kind=" + kind() + ", opts=" + opts + '}';
    }
}
