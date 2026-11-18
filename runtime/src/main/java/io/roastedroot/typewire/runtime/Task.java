package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.BorrowScope;
import run.endive.runtime.TrapException;

/**
 * One invocation of a lifted component function, from the callee's side.
 *
 * <p>Its job here is to hold the borrows lowered into the call to account. Every {@code borrow}
 * handed to the callee is counted on the way in and decremented when the callee drops it. The call
 * is not allowed to return while any borrows remain.
 */
public final class Task implements BorrowScope.Callee {

    private int numBorrows;

    @Override
    public void borrow() {
        numBorrows++;
    }

    @Override
    public void unborrow() {
        numBorrows--;
    }

    @Override
    public int numBorrows() {
        return numBorrows;
    }

    /**
     * Checks that every borrow lowered into this call has since been dropped, throwing a
     * TrapException if not.
     */
    void requireBorrowsReleased() {
        if (numBorrows > 0) {
            throw new TrapException(
                    "cannot return from a call with " + numBorrows + " undropped borrow(s)");
        }
    }
}
