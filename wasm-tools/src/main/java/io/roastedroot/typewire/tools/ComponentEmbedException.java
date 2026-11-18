package io.roastedroot.typewire.tools;

public class ComponentEmbedException extends RuntimeException {

    public ComponentEmbedException(String message) {
        super(message);
    }

    public ComponentEmbedException(String message, Throwable cause) {
        super(message, cause);
    }
}
