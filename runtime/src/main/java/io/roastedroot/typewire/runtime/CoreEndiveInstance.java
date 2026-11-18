package io.roastedroot.typewire.runtime;

import run.endive.runtime.Instance;

public final class CoreEndiveInstance extends CoreModuleInstance {

    private final Instance instance;

    public CoreEndiveInstance(Instance instance) {
        this.instance = instance;
    }

    public Instance getModuleInstance() {
        return instance;
    }
}
