package run.endive.cm.types;

import java.util.Objects;
import run.endive.wasm.types.ExternalType;
import run.endive.wasm.types.MemoryLimits;

public final class CoreMemoryImportDesc extends CoreImportDesc {

    private final MemoryLimits limits;

    private CoreMemoryImportDesc(MemoryLimits limits) {
        super(ExternalType.MEMORY);
        this.limits = limits;
    }

    public MemoryLimits limits() {
        return limits;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private MemoryLimits limits;

        public Builder withLimits(MemoryLimits limits) {
            this.limits = limits;
            return this;
        }

        public CoreMemoryImportDesc build() {
            return new CoreMemoryImportDesc(limits);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreMemoryImportDesc)) {
            return false;
        }
        CoreMemoryImportDesc that = (CoreMemoryImportDesc) o;
        return Objects.equals(limits, that.limits);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(limits);
    }

    @Override
    public String toString() {
        return "CoreMemoryImportDesc{" + "limits=" + limits + '}';
    }
}
