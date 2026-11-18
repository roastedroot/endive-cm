package io.roastedroot.typewire.abi;

/**
 * The runtime identity of a resource type, as lifting and lowering see it.
 *
 * <p>Resource types in the Component Model are <em>generative</em>: each instantiation of a
 * component that declares {@code (type (resource ...))} brings a distinct resource type into
 * existence, even though every instantiation shares the same AST produced by parsing the binary.
 * The Canonical ABI guards on that distinction. A handle created with one instantiation's resource
 * type must not be usable against another's so they are compared by reference identity.
 *
 * @see io.roastedroot.typewire.types.ResourceType the static declaration this is an instantiation of
 */
public interface ResourceTypeRef extends io.roastedroot.typewire.types.ResourceTypeId {

    /**
     * The handle table owned by the component instance that declared this resource type,
     * {@code null} if this is a reference to a host-defined resource.
     *
     * <p>Lowering a {@code borrow} type compares this against the context's own table to recognize
     * a {@code borrow} being handed back to the component that implements the resource.
     */
    HandleTable handleTable();

    /**
     * A description of the resource type, distinguished by where it is implemented, which is the
     * only thing disambiguating two otherwise identical resource types to a reader.
     */
    default String describe() {
        return handleTable() == null ? "host-defined resource" : "guest-defined resource";
    }

    /** The error text for reporting a handle used against the wrong resource type. */
    static String mismatch(ResourceTypeRef expected, ResourceTypeRef found) {
        String want = expected.describe();
        if (found == null) {
            return "expected " + want + " but found a value that is not a resource";
        }
        String got = found.describe();
        return "expected " + want + " but found " + (want.equals(got) ? "a different " : "") + got;
    }
}
