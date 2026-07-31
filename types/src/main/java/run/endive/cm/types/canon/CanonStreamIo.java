package run.endive.cm.types.canon;

import java.util.List;
import java.util.Objects;

public final class CanonStreamIo extends Canon {

    private static final List<Kind> VALID_KINDS = List.of(Kind.STREAM_READ, Kind.STREAM_WRITE);

    private final long typeIdx;
    private final List<CanonOpt> opts;

    private CanonStreamIo(Kind kind, long typeIdx, List<CanonOpt> opts) {
        super(kind);
        this.typeIdx = typeIdx;
        this.opts = List.copyOf(opts);
    }

    public long typeIdx() {
        return typeIdx;
    }

    public List<CanonOpt> opts() {
        return opts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Kind kind;
        private long typeIdx;
        private List<CanonOpt> opts = List.of();

        public Builder withKind(Kind kind) {
            if (!VALID_KINDS.contains(kind)) {
                throw new IllegalArgumentException("Invalid kind for canon stream io: " + kind);
            }
            this.kind = kind;
            return this;
        }

        public Builder withTypeIdx(long typeIdx) {
            this.typeIdx = typeIdx;
            return this;
        }

        public Builder withOpts(List<CanonOpt> opts) {
            this.opts = opts;
            return this;
        }

        public CanonStreamIo build() {
            return new CanonStreamIo(kind, typeIdx, opts);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonStreamIo)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        CanonStreamIo that = (CanonStreamIo) o;
        return typeIdx == that.typeIdx && Objects.equals(opts, that.opts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), typeIdx, opts);
    }

    @Override
    public String toString() {
        return "CanonStreamIo{"
                + "kind="
                + kind()
                + ", typeIdx="
                + typeIdx
                + ", opts="
                + opts
                + '}';
    }
}
