package io.roastedroot.typewire.tools;

public class WitParseException extends RuntimeException {

    public WitParseException() {}

    public WitParseException(String message) {
        super(message);
    }

    public WitParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
