package run.endive.cm.types;

import java.util.Objects;
import run.endive.wasm.types.CompType;
import run.endive.wasm.types.RecType;
import run.endive.wasm.types.SubType;

public final class CoreType {
    private final RecType recType;
    private final SubType subType;
    private final CompType compType;
    private final ModuleType moduleType;

    private CoreType(RecType recType, SubType subType, CompType compType, ModuleType moduleType) {
        requireExactlyOneNonNull(recType, subType, compType, moduleType);
        this.recType = recType;
        this.subType = subType;
        this.compType = compType;
        this.moduleType = moduleType;
    }

    private static void requireExactlyOneNonNull(Object a, Object b, Object c, Object d) {
        if ((a == null ? 0 : 1) + (b == null ? 0 : 1) + (c == null ? 0 : 1) + (d == null ? 0 : 1)
                != 1) {
            throw new IllegalArgumentException("Exactly one field must be filled");
        }
    }

    public RecType recType() {
        return recType;
    }

    public SubType subType() {
        return subType;
    }

    public CompType compType() {
        return compType;
    }

    public ModuleType moduleType() {
        return moduleType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private RecType recType;
        private SubType subType;
        private CompType compType;
        private ModuleType moduleType;

        private Builder() {}

        public Builder withRecType(RecType recType) {
            this.recType = recType;
            return this;
        }

        public Builder withSubType(SubType subType) {
            this.subType = subType;
            return this;
        }

        public Builder withCompType(CompType compType) {
            this.compType = compType;
            return this;
        }

        public Builder withModuleType(ModuleType moduleType) {
            this.moduleType = moduleType;
            return this;
        }

        public CoreType build() {
            return new CoreType(recType, subType, compType, moduleType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreType)) {
            return false;
        }
        CoreType coreType = (CoreType) o;
        return Objects.equals(recType, coreType.recType)
                && Objects.equals(subType, coreType.subType)
                && Objects.equals(compType, coreType.compType)
                && Objects.equals(moduleType, coreType.moduleType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recType, subType, compType, moduleType);
    }

    @Override
    public String toString() {
        return "CoreType{"
                + "recType="
                + recType
                + ", subType="
                + subType
                + ", compType="
                + compType
                + ", moduleType="
                + moduleType
                + '}';
    }
}
