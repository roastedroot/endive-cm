package io.roastedroot.typewire.abi;

/**
 * A callback function specified through canon opts that may only be present
 * in canon lift when the async option has also been set. Providing this option
 * selects the "stackless" async ABI. The stackless async ABI allows core wasm
 * to repeatedly return to an event loop to receive events (delivered to the
 * callback), thereby clearing the native stack for the benefit of the wasm
 * runtime while waiting in the event loop.
 */
@FunctionalInterface
public interface Callback {

    boolean apply(int ctx, int event, int payload);
}
