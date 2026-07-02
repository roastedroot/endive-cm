package run.endive.cm.types;

import java.util.List;
import java.util.Objects;

public final class CanonLift extends Canon {

    private final CoreSortIdx funcIdx;
    private final List<CanonOpt> opts;
    private final long typeIdx;
    private static final long UNDEFINED_IDX = -1;

    private CanonLift(CoreSortIdx funcIdx, List<CanonOpt> opts, long typeIdx) {
        super(Kind.LIFT);
        this.funcIdx = Objects.requireNonNull(funcIdx, "funcIdx");
        this.opts = List.copyOf(opts);
        if (typeIdx == UNDEFINED_IDX) {
            throw new IllegalArgumentException("typeIdx must be defined");
        }
        this.typeIdx = typeIdx;
    }

    public CoreSortIdx funcIdx() {
        return funcIdx;
    }

    public List<CanonOpt> opts() {
        return opts;
    }

    public long typeIdx() {
        return typeIdx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private CoreSortIdx funcIdx;
        private List<CanonOpt> opts = List.of();
        private long typeIdx = UNDEFINED_IDX;

        private Builder() {}

        public Builder withFuncIdx(CoreSortIdx funcIdx) {
            this.funcIdx = funcIdx;
            return this;
        }

        public Builder withOpts(List<CanonOpt> opts) {
            this.opts = opts;
            return this;
        }

        public Builder withTypeIdx(long typeIdx) {
            this.typeIdx = typeIdx;
            return this;
        }

        public CanonLift build() {
            return new CanonLift(funcIdx, opts, typeIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonLift)) {
            return false;
        }
        CanonLift that = (CanonLift) o;
        return typeIdx == that.typeIdx
                && Objects.equals(funcIdx, that.funcIdx)
                && Objects.equals(opts, that.opts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(funcIdx, opts, typeIdx);
    }

    @Override
    public String toString() {
        return "CanonLift{"
                + "funcIdx="
                + funcIdx
                + ", opts="
                + opts
                + ", typeIdx="
                + typeIdx
                + '}';
    }
}
