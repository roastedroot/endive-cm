package run.endive.cm.types;

import java.util.List;
import java.util.Objects;

public final class CanonLower extends Canon {

    private final SortIdx funcIdx;
    private final List<CanonOpt> opts;

    private CanonLower(SortIdx funcIdx, List<CanonOpt> opts) {
        super(Kind.LOWER);
        this.funcIdx = Objects.requireNonNull(funcIdx, "funcIdx");
        this.opts = List.copyOf(opts);
    }

    public SortIdx funcIdx() {
        return funcIdx;
    }

    public List<CanonOpt> opts() {
        return opts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private SortIdx funcIdx;
        private List<CanonOpt> opts = List.of();

        private Builder() {}

        public Builder withFuncIdx(SortIdx funcIdx) {
            this.funcIdx = funcIdx;
            return this;
        }

        public Builder withOpts(List<CanonOpt> opts) {
            this.opts = opts;
            return this;
        }

        public CanonLower build() {
            return new CanonLower(funcIdx, opts);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonLower)) {
            return false;
        }
        CanonLower that = (CanonLower) o;
        return Objects.equals(funcIdx, that.funcIdx) && Objects.equals(opts, that.opts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(funcIdx, opts);
    }

    @Override
    public String toString() {
        return "CanonLower{" + "funcIdx=" + funcIdx + ", opts=" + opts + '}';
    }
}
