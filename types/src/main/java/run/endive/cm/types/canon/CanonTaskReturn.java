package run.endive.cm.types.canon;

import java.util.List;
import java.util.Objects;
import run.endive.cm.types.ValType;

public final class CanonTaskReturn extends Canon {

    private final ValType result;
    private final List<CanonOpt> opts;

    private CanonTaskReturn(ValType result, List<CanonOpt> opts) {
        super(Kind.TASK_RETURN);
        this.result = result;
        this.opts = List.copyOf(opts);
    }

    public ValType result() {
        return result;
    }

    public boolean hasResult() {
        return result != null;
    }

    public List<CanonOpt> opts() {
        return opts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ValType result;
        private List<CanonOpt> opts = List.of();

        private Builder() {}

        public Builder withResult(ValType result) {
            this.result = result;
            return this;
        }

        public Builder withOpts(List<CanonOpt> opts) {
            this.opts = opts;
            return this;
        }

        public CanonTaskReturn build() {
            return new CanonTaskReturn(result, opts);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonTaskReturn)) {
            return false;
        }
        CanonTaskReturn that = (CanonTaskReturn) o;
        return Objects.equals(result, that.result) && Objects.equals(opts, that.opts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, opts);
    }

    @Override
    public String toString() {
        return "CanonTaskReturn{" + "result=" + result + ", opts=" + opts + '}';
    }
}
