package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.types.ValType;

/**
 * The descriptor for a function that returns nothing, which is a component type in its own right
 * rather than the absence of one, because {@link ComponentFunction#typed} requires a result
 * descriptor, and this is what a caller passes when there is no result to describe.
 */
public final class VoidHostTypeDescriptor extends HostTypeDescriptor {

    private static final VoidHostTypeDescriptor INSTANCE = new VoidHostTypeDescriptor();

    private VoidHostTypeDescriptor() {}

    public static VoidHostTypeDescriptor instance() {
        return INSTANCE;
    }

    @Override
    boolean matches(Class<?> hostType) {
        return hostType == null;
    }

    @Override
    public String toString() {
        return "void";
    }

    @Override
    boolean isCompatibleWith(ComponentInstance instance, ValType componentType) {
        return componentType == null;
    }
}
