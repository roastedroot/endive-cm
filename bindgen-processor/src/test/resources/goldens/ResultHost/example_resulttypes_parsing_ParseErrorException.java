package endive.testing.example.resulttypes.parsing;

import javax.annotation.processing.Generated;

/**
 * The error case of a WIT result declared by {@code example:result-types/parsing}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class ParseErrorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ParseError error;

    public ParseErrorException(ParseError error) {
        super(String.valueOf(error));
        this.error = error;
    }

    /**
     * The error payload the failing side supplied.
     */
    public ParseError error() {
        return error;
    }
}
