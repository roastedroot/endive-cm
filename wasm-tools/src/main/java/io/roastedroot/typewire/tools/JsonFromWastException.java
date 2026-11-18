package io.roastedroot.typewire.tools;

public class JsonFromWastException extends RuntimeException {

    public JsonFromWastException() {}

    public JsonFromWastException(String message) {
        super(message);
    }

    public JsonFromWastException(String message, Throwable cause) {
        super(message, cause);
    }
}
