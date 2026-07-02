package run.endive.cm.types;

import java.util.Objects;
import run.endive.wasm.types.ExternalType;

public final class CoreFunctionImportDesc extends CoreImportDesc {

    private final long typeIndex;
    private static final long UNDEFINED_INDEX = -1;

    private CoreFunctionImportDesc(long typeIndex) {
        super(ExternalType.FUNCTION);
        if (typeIndex == UNDEFINED_INDEX) {
            throw new IllegalArgumentException("typeIndex must be defined");
        }
        this.typeIndex = typeIndex;
    }

    public long typeIndex() {
        return typeIndex;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long typeIndex = UNDEFINED_INDEX;

        public Builder withTypeIndex(long typeIndex) {
            this.typeIndex = typeIndex;
            return this;
        }

        public CoreFunctionImportDesc build() {
            return new CoreFunctionImportDesc(typeIndex);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreFunctionImportDesc)) {
            return false;
        }
        CoreFunctionImportDesc that = (CoreFunctionImportDesc) o;
        return typeIndex == that.typeIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(typeIndex);
    }

    public String toString() {
        return "CoreFunctionImportDesc{" + "typeIndex=" + typeIndex + '}';
    }
}
