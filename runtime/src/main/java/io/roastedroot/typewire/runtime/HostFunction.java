package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.types.FuncType;

/**
 * Builds a function the embedder supplies, for satisfying an import declared as a bare function
 * rather than as one export of an instance.
 *
 * <p>A function still belongs to an instance, which is what a call into it enters, so one is built
 * to hold it even though nothing is exported from it. Use {@link HostInstance} instead when the
 * import names an instance, so that several functions can share a type index space.
 */
public final class HostFunction {

    private HostFunction() {}

    /**
     * @param funcType the type an importer will be matched against, which may name only primitives
     *     because the instance holding this function declares no types
     */
    public static ComponentFunction of(
            ComponentStore store, FuncType funcType, ComponentFunctionCall call) {
        var owner = ComponentInstance.builder(store);
        ComponentFunction function = HostInstance.function(owner, funcType, call);
        owner.build();
        return function;
    }
}
