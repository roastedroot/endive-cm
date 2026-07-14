package run.endive.cm.runtime;

public final class LinkageException extends RuntimeException {

    public LinkageException(String message) {
        super(message);
    }

    public LinkageException(String message, Throwable cause) {
        super(message, cause);
    }
}
