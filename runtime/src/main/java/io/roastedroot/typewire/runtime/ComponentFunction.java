package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.LiftLowerContext;
import io.roastedroot.typewire.types.FuncType;
import io.roastedroot.typewire.types.ResolvedFuncType;

public interface ComponentFunction {

    Object[] apply(Object... args);

    FuncType funcType();

    /**
     * This function's type with its parameter and result types resolved, against the space in which
     * the type was written. Matching an import against it needs no index space on either side.
     */
    ResolvedFuncType resolvedFuncType();

    boolean isLifted();

    LiftLowerContext context();

    CoreFunction<?> liftedFunction();

    /**
     * Whether this function is supplied by the embedder rather than defined by a component.
     *
     * <p>A host function is a piece of Java, and Java has no component-level parameter names to
     * offer, because the {@link FuncType} it is registered with is written to satisfy importers,
     * not a declaration the function itself makes. Two components may therefore legitimately import
     * the same host function under different parameter names, so a host function is matched on
     * structure alone and takes its names from whoever imports it. Functions a component defines
     * are matched strictly, names included.
     */
    default boolean hostProvided() {
        return false;
    }

    /**
     * The component instance a call to this function enters, or {@code null} if calling it enters
     * no component at all.
     *
     * <p>Used to decide whether entering is allowed right now. See
     * {@link ComponentInstance#mayEnter()}. A function the embedder supplied reports the instance
     * it was registered against, which is never mid-instantiation, so host imports are never
     * blocked.
     */
    default ComponentInstance definingInstance() {
        return null;
    }

    ComponentFunction typed(
            HostTypeDescriptor resultDescriptor, HostTypeDescriptor... paramDescriptors);
}
