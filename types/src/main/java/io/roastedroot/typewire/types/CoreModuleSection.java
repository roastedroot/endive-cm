package io.roastedroot.typewire.types;

import java.util.Objects;
import run.endive.wasm.WasmModule;

public final class CoreModuleSection extends Section {

    private final WasmModule module;

    private CoreModuleSection(WasmModule module) {
        super(SectionId.CORE_MODULE);
        Objects.requireNonNull(module, "module");
        this.module = module;
    }

    public WasmModule module() {
        return module;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private WasmModule module;

        public Builder withModule(WasmModule module) {
            this.module = module;
            return this;
        }

        public CoreModuleSection build() {
            return new CoreModuleSection(module);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoreModuleSection)) {
            return false;
        }
        CoreModuleSection that = (CoreModuleSection) o;
        return Objects.equals(module, that.module);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(module);
    }

    @Override
    public String toString() {
        return "CoreModuleSection{" + "module=" + module + '}';
    }
}
