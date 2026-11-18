package io.roastedroot.typewire.abi;

/**
 * A function specified via canon opts that is used in the Canonical ABI both
 * to allocate (passing 0 for the first two parameters) and reallocate. If the
 * Canonical ABI needs realloc, validation requires this option to be present
 * (there is no default).
 */
@FunctionalInterface
public interface Realloc {

    int apply(int oldPtr, int oldSize, int align, int newSize);
}
