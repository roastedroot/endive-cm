package run.endive.cm.types;

import java.util.Objects;
import run.endive.wasm.types.ExternalType;
import run.endive.wasm.types.MutabilityType;
import run.endive.wasm.types.ValType;

public final class CoreGlobalImportDesc extends CoreImportDesc {

    private final MutabilityType mutabilityType;
    private final ValType valType;

    private CoreGlobalImportDesc(MutabilityType mutabilityType, ValType valType) {
        super(ExternalType.GLOBAL);
        this.mutabilityType = Objects.requireNonNull(mutabilityType, "mutabilityType");
        this.valType = Objects.requireNonNull(valType, "type");
    }

    public MutabilityType mutabilityType() {
        return mutabilityType;
    }

    public ValType valType() {
        return valType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private MutabilityType mutabilityType;
        private ValType valType;

        public Builder withMutabilityType(MutabilityType mutabilityType) {
            this.mutabilityType = mutabilityType;
            return this;
        }

        public Builder withValType(ValType valType) {
            this.valType = valType;
            return this;
        }

        public CoreGlobalImportDesc build() {
            return new CoreGlobalImportDesc(mutabilityType, valType);
        }
    }

    @Override
    public String toString() {
        return "CoreGlobalImportDesc{"
                + "mutabilityType="
                + mutabilityType
                + ", valType="
                + valType
                + '}';
    }
}
