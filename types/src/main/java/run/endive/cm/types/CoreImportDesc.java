package run.endive.cm.types;

import run.endive.wasm.types.ExternalType;

public abstract class CoreImportDesc {

    private final ExternalType type;

    protected CoreImportDesc(ExternalType type) {
        this.type = type;
    }

    public ExternalType type() {
        return type;
    }
}
