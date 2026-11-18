package io.roastedroot.typewire.types;

/**
 * A type index space, which resolves the indices written against it.
 *
 * <p>Resolving hands back a {@link ResolvedType}: a self-contained graph that no longer refers to
 * any index space. That is what lets a type cross from the component that defined it into one that
 * merely uses it. A record aliased out of another component still names <em>its</em> field types
 * and <em>its</em> resource types, and the numbers it was written with mean something else here.
 * Resolving where the type was defined, once, settles that before the number can be read anywhere
 * it would mean the wrong thing.
 *
 * <p>{@link #of} is the base case, where everything resolves in one place.
 */
public interface TypeSpace {

    /** The fully resolved type that {@code valType} names in this space. */
    ResolvedType resolve(ValType valType);

    /**
     * The runtime identity of the resource type at {@code typeIdx}, which is what an {@code own} or
     * {@code borrow} written against this space denotes.
     */
    ResourceTypeId resourceType(int typeIdx);

    /**
     * A space backed by a plain index lookup, in which every type resolves locally.
     *
     * <p>Correct only where nothing was written elsewhere, such as a test's hand-built table, and
     * only for questions that do not turn on resource identity, such as layout and flattening. A
     * space that can receive types from outside has to resolve each of its slots in the space that
     * slot came from instead.
     */
    static TypeSpace of(TypeResolver resolver) {
        return new TypeSpace() {

            @Override
            public ResolvedType resolve(ValType valType) {
                return ResolvedType.of(resolver.resolveDefValType(valType), this);
            }

            @Override
            public ResourceTypeId resourceType(int typeIdx) {
                // A plain resolver holds declarations, not runtime identities. A handle type
                // resolved here still lays out correctly, and only asking which resource it names
                // fails, and it fails where it is asked rather than silently naming the wrong
                // one.
                return null;
            }
        };
    }
}
