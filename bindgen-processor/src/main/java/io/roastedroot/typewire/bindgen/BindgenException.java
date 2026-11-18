package io.roastedroot.typewire.bindgen;

/** A WIT world the generator cannot read, reported against the annotation that asked for it. */
final class BindgenException extends RuntimeException {

    BindgenException(String message) {
        super(message);
    }

    BindgenException(String message, Throwable cause) {
        super(message, cause);
    }
}
