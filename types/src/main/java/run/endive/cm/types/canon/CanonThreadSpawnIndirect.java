package run.endive.cm.types.canon;

import java.util.Objects;

public final class CanonThreadSpawnIndirect extends Canon {

    private final boolean shared;
    private final long funcTypeIdx;
    private final long tableIdx;

    private CanonThreadSpawnIndirect(boolean shared, long funcTypeIdx, long tableIdx) {
        super(Kind.THREAD_SPAWN_INDIRECT);
        this.shared = shared;
        this.funcTypeIdx = funcTypeIdx;
        this.tableIdx = tableIdx;
    }

    public boolean isShared() {
        return shared;
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
        private boolean shared;
        private long funcTypeIdx;
        private long tableIdx;

        public Builder withShared(boolean shared) {
            this.shared = shared;
            return this;
        }

        public Builder withFuncTypeIdx(long funcTypeIdx) {
            this.funcTypeIdx = funcTypeIdx;
            return this;
        }

        public Builder withTableIdx(long tableIdx) {
            this.tableIdx = tableIdx;
            return this;
        }

        public CanonThreadSpawnIndirect build() {
            return new CanonThreadSpawnIndirect(shared, funcTypeIdx, tableIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonThreadSpawnIndirect)) {
            return false;
        }
        CanonThreadSpawnIndirect that = (CanonThreadSpawnIndirect) o;
        return shared == that.shared
                && funcTypeIdx == that.funcTypeIdx
                && tableIdx == that.tableIdx;
    }

    @Override
    public int hashCode() {
        return Objects.hash(shared, funcTypeIdx, tableIdx);
    }

    @Override
    public String toString() {
        return "CanonThreadSpawnIndirect{"
                + "shared="
                + shared
                + ", funcTypeIdx="
                + funcTypeIdx
                + ", tableIdx="
                + tableIdx
                + '}';
    }
}
