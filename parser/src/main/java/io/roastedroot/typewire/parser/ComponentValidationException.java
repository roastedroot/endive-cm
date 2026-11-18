package io.roastedroot.typewire.parser;

/**
 * A component was rejected as invalid.
 *
 * <p>Deliberately independent of how the rejection was reached. Some of it comes from running
 * {@code wasm-tools validate} over the binary, and some from checks this project makes itself
 * because they either go beyond what the spec calls invalid, or cover ground {@code wasm-tools}
 * does not. Callers care that the component is unusable, not which of those noticed, so both arrive
 * as this.
 */
public class ComponentValidationException extends RuntimeException {

    public ComponentValidationException(String message) {
        super(message);
    }

    public ComponentValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
