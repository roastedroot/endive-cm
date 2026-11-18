package io.roastedroot.typewire.abi;

/**
 * The borrow scope of a resource handle, used for tracking state when a resource is lent across
 * a component boundary. A borrow must never outlive the call that created it.
 */
public interface BorrowScope {

    /**
     * The borrow state of an export call's callee, which is meant to be
     * (<a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/Concurrency.md#concepts">tracked by a Task</a>).
     * Borrows lowered into the call are counted here and must all be dropped before returning.
     */
    interface Callee extends BorrowScope {

        /** Counts a borrow lowered into the export call. */
        void borrow();

        /** Counts a borrowed handle dropped during the export call. */
        void unborrow();

        /** How many borrows lowered into this export call are still undropped. */
        int numBorrows();
    }

    /**
     * The lending state of an import call's caller, which is meant to be
     * <a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/Concurrency.md#subtasks-and-supertasks">tracked by a Subtask</a>.
     * Handles lent to the import call are counted here and must be released once it resolves.
     */
    interface Caller extends BorrowScope {

        /**
         * Records that {@code handle} was lent to the import call, holding it lent until {@link
         * #releaseLenders}.
         */
        void addLender(ResourceState handle);

        /** Releases every handle lent to the import call once it has resolved. */
        void releaseLenders();
    }
}
