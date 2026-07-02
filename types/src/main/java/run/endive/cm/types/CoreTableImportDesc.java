package run.endive.cm.types;

import java.util.Objects;
import run.endive.wasm.types.ExternalType;
import run.endive.wasm.types.TableLimits;
import run.endive.wasm.types.ValType;

public final class CoreTableImportDesc extends CoreImportDesc {

    private final ValType entryType;
    private final TableLimits limits;

    private CoreTableImportDesc(ValType entryType, TableLimits limits) {
        super(ExternalType.TABLE);
        this.entryType = Objects.requireNonNull(entryType, "entryType");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public ValType entryType() {
        return entryType;
    }

    public TableLimits limits() {
        return limits;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ValType entryType;
        private TableLimits limits;

        public Builder withEntryType(ValType entryType) {
            this.entryType = Objects.requireNonNull(entryType, "entryType");
            return this;
        }

        public Builder withLimits(TableLimits limits) {
            this.limits = Objects.requireNonNull(limits, "limits");
            return this;
        }

        public CoreTableImportDesc build() {
            return new CoreTableImportDesc(entryType, limits);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreTableImportDesc)) {
            return false;
        }
        CoreTableImportDesc that = (CoreTableImportDesc) o;
        return Objects.equals(entryType, that.entryType) && Objects.equals(limits, that.limits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryType, limits);
    }

    @Override
    public String toString() {
        return "CoreTableImportDesc{" + "entryType=" + entryType + ", limits=" + limits + '}';
    }
}
