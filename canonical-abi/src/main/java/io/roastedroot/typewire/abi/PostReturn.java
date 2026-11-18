package io.roastedroot.typewire.abi;

/**
 * A function specified through canon opts that may only be present in
 * canon lift when async is not present. Specifies a core function to be
 * called with the original return values after they have finished being
 * read, allowing memory to be deallocated and destructors to be called.
 * This immediate is always optional but, if present, is validated to
 * have parameters matching the callee's return type and empty results.
 */
@FunctionalInterface
public interface PostReturn {
    void apply(long... args);
}
