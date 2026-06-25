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
        this.recType = recType;
        this.subType = subType;
        this.compType = compType;
        this.moduleType = moduleType;
    }

    public static CoreType of(RecType recType) {
        return new CoreType(recType, null, null, null);
    }

    public static CoreType of(SubType subType) {
        return new CoreType(null, subType, null, null);
    }

    public static CoreType of(CompType compType) {
        return new CoreType(null, null, compType, null);
    }

    public static CoreType of(ModuleType moduleType) {
        return new CoreType(null, null, null, moduleType);
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
