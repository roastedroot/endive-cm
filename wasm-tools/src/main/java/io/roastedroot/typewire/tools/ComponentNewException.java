package io.roastedroot.typewire.tools;

public class ComponentNewException extends RuntimeException {

    public ComponentNewException(String message) {
        super(message);
    }

    public ComponentNewException(String message, Throwable cause) {
        super(message, cause);
    }
}
