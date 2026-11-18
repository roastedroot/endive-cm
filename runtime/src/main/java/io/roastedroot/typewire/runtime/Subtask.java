package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.BorrowScope;
import io.roastedroot.typewire.abi.ResourceState;
import java.util.ArrayList;
import java.util.List;

/**
 * One invocation of a lowered component function, from the caller's side.
 *
 * <p>Its job here mirrors {@link Callee}. It remembers which of the caller's handles were lent to
 * the call as {@code borrow}s and keeps them marked as lent for its duration, so that the caller
 * cannot hand the underlying resource away, or destroy it, while the callee still holds a borrow.
 * The marks come off once the call resolves.
 */
public final class Subtask implements BorrowScope.Caller {

    private final List<ResourceState> lenders = new ArrayList<>();
    private boolean resolved;

    @Override
    public void addLender(ResourceState handle) {
        if (resolved) {
            throw new IllegalStateException("cannot lend to a subtask that has already resolved");
        }
        handle.lend();
        lenders.add(handle);
    }

    @Override
    public void releaseLenders() {
        if (resolved) {
            return;
        }
        resolved = true;
        for (ResourceState handle : lenders) {
            handle.returnLend();
        }
        lenders.clear();
    }
}
