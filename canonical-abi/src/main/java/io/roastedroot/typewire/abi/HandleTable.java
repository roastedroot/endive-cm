package io.roastedroot.typewire.abi;

/**
 * A component instance's handle table.
 *
 * <p>The table is heterogeneous in the full Component Model. Alongside resource handles, it
 * is meant to eventually hold waitables, waitable sets, and error contexts. {@link #get} and
 * {@link #remove} accordingly return {@link Object}, and the lift paths much check the types
 * so that an index naming the wrong kind of element traps rather than being misread.
 */
public interface HandleTable {

    /**
     * Creates a resource handle and adds it to the table, returning its index.
     *
     * <p>The table constructs the handle rather than accepting one so that this module needs no
     * concrete {@link ResourceState} of its own.
     *
     * @param borrowScope the call to charge a borrowed handle to; {@code null} for an owning one
     * @throws run.endive.runtime.TrapException if the table is full
     */
    int add(ResourceTypeRef resourceType, int rep, boolean own, BorrowScope.Callee borrowScope);

    /**
     * The element at {@code index}, left in the table.
     *
     * @throws run.endive.runtime.TrapException if no element is present at {@code index}
     */
    Object get(int index);

    /**
     * Removes the element at {@code index} and returns it.
     *
     * @throws run.endive.runtime.TrapException if no element is present at {@code index}
     */
    Object remove(int index);
}
