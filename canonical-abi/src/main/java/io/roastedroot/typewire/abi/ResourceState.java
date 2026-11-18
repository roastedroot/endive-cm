package io.roastedroot.typewire.abi;

/**
 * The state of a resource handle in a component instance's {@link HandleTable}.
 *
 * <p>A handle is either <em>owning</em>, indicating it has the sole right to the underlying
 * resource, which lifting transfers out of the table, or <em>borrowed</em>, indicating it is
 * valid only for the duration of the call that lowered it. The two counters below are what
 * make that distinction enforceable at runtime:
 *
 * <ul>
 *   <li>{@link #numLends} counts how many live {@code borrow} handles were lent <em>from</em>
 *       this handle. Canonical lifting of an {@code own} refuses to transfer ownership while
 *       it is non-zero, so a resource cannot be given away while a callee still holds a borrow
 *       of it.
 *   <li>{@link BorrowScope.Callee#numBorrows} on the {@link #borrowScope} counts borrows lowered
 *       <em>into</em> an export call, and must return to zero before that call may return.
 * </ul>
 */
public interface ResourceState {

    /** The resource type this handle contains, compared by reference identity. */
    ResourceTypeRef resourceType();

    /**
     * The resource's representation, an {@code i32} today, kept opaque from the component
     * holding the handle, which only ever names it by table index.
     */
    int rep();

    /** Whether this handle came from an {@code own} type rather than a {@code borrow}. */
    boolean own();

    /**
     * The scope for a borrowed handle. Will be {@code null} for an owning
     * one.
     */
    BorrowScope.Callee borrowScope();

    /** The number of live borrows that were lent by this handle. */
    int numLends();

    /** Records a borrow being lent by this handle. */
    void lend();

    /** Releases a borrow lent by this handle. */
    void returnLend();
}
