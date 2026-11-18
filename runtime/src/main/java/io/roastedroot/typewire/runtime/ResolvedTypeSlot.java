package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.ResourceTypeRef;
import io.roastedroot.typewire.types.ResolvedFuncType;
import io.roastedroot.typewire.types.ResolvedType;
import io.roastedroot.typewire.types.Type;

/**
 * One numbered slot of a type index space, with its declaration already resolved.
 *
 * <p>A component's index space and the little scope an {@code instance} type declaration builds up
 * are different things that answer the same question, and comparing a declaration against what an
 * instance provides means holding one of each. What they have in common is the declaration as
 * parsed, the runtime identity if it names a resource type, and the resolved forms that make the
 * two comparable without either side needing to know the other's index space.
 */
interface ResolvedTypeSlot {

    /** The declaration this slot holds, for diagnostics and for the sorts with no resolved form. */
    Type type();

    /** The resource type this slot names, or {@code null} if it holds an ordinary type. */
    ResourceTypeRef resourceType();

    /** The resolved value type, or {@code null} if this slot holds something else. */
    ResolvedType value();

    /** The resolved function type, or {@code null} if this slot holds something else. */
    ResolvedFuncType func();
}
