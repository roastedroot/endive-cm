package endive.testing.exports.example.resulttypes.running;

import javax.annotation.processing.Generated;

/**
 * The error case of a WIT result declared by {@code example:result-types/running}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class RunErrorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final RunError error;

    public RunErrorException(RunError error) {
        super(String.valueOf(error));
        this.error = error;
    }

    /**
     * The error payload the failing side supplied.
     */
    public RunError error() {
        return error;
    }
}
