package run.endive.cm.types.canon;

import java.util.Objects;

public final class CanonThreadSpawnRef extends Canon {

    private final boolean shared;
    private final long funcTypeIdx;

    private CanonThreadSpawnRef(boolean shared, long funcTypeIdx) {
        super(Kind.THREAD_SPAWN_REF);
        this.shared = shared;
        this.funcTypeIdx = funcTypeIdx;
    }

    public boolean isShared() {
        return shared;
    }

    public long funcTypeIdx() {
        return funcTypeIdx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean shared;
        private long funcTypeIdx;

        public Builder withShared(boolean shared) {
            this.shared = shared;
            return this;
        }

        public Builder withFuncTypeIdx(long funcTypeIdx) {
            this.funcTypeIdx = funcTypeIdx;
            return this;
        }

        public CanonThreadSpawnRef build() {
            return new CanonThreadSpawnRef(shared, funcTypeIdx);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CanonThreadSpawnRef)) {
            return false;
        }
        CanonThreadSpawnRef that = (CanonThreadSpawnRef) o;
        return shared == that.shared && funcTypeIdx == that.funcTypeIdx;
    }

    @Override
    public int hashCode() {
        return Objects.hash(shared, funcTypeIdx);
    }

    @Override
    public String toString() {
        return "CanonThreadSpawnRef{" + "shared=" + shared + ", funcTypeIdx=" + funcTypeIdx + '}';
    }
}
