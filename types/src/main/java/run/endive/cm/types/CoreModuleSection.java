package run.endive.cm.types;

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
}
