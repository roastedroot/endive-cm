package run.endive.cm.types.canon;

import java.util.Objects;

public final class CanonThreadNewIndirect extends Canon {

    private final long funcTypeIdx;
    private final long tableIdx;

    private CanonThreadNewIndirect(long funcTypeIdx, long tableIdx) {
        super(Kind.THREAD_NEW_INDIRECT);
        this.funcTypeIdx = funcTypeIdx;
        this.tableIdx = tableIdx;
    }

    public long funcTypeIdx() {
        return funcTypeIdx;
    }

    public long tableIdx() {
        return tableIdx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long funcTypeIdx;
        private long tableIdx;

        public Builder withFuncTypeIdx(long funcTypeIdx) {
            this.funcTypeIdx = funcTypeIdx;
            return this;
        }

        public Builder withTableIdx(long tableIdx) {
            this.tableIdx = tableIdx;
            return this;
        }

        public CanonThreadNewIndirect build() {
            return new CanonThreadNewIndirect(funcTypeIdx, tableIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonThreadNewIndirect)) {
            return false;
        }
        CanonThreadNewIndirect that = (CanonThreadNewIndirect) o;
        return funcTypeIdx == that.funcTypeIdx && tableIdx == that.tableIdx;
    }

    @Override
    public int hashCode() {
        return Objects.hash(funcTypeIdx, tableIdx);
    }

    @Override
    public String toString() {
        return "CanonThreadNewIndirect{"
                + "funcTypeIdx="
                + funcTypeIdx
                + ", tableIdx="
                + tableIdx
                + '}';
    }
}
