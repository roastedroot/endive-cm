package run.endive.cm.tools;

public class ComponentValidateException extends RuntimeException {
    public ComponentValidateException() {}

    public ComponentValidateException(String message) {
        super(message);
    }

    public ComponentValidateException(String message, Throwable cause) {
        super(message, cause);
    }
}
