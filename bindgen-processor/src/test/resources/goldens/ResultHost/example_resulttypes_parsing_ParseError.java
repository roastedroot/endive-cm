package endive.testing.example.resulttypes.parsing;

import javax.annotation.processing.Generated;
import run.endive.cm.abi.VariantValue;

/**
 * The WIT enum {@code parse-error}, declared by {@code example:result-types/parsing}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public enum ParseError {

    EMPTY("empty"), OVERFLOW("overflow");

    private final String label;

    ParseError(String label) {
        this.label = label;
    }

    /**
     * This case as the ABI carries it, which is a variant with no payload.
     */
    public VariantValue toComponent() {
        return VariantValue.of(label, null);
    }

    /**
     * The case a lifted value names.
     */
    public static ParseError fromComponent(Object value) {
        String label = ((VariantValue) value).label();
        for (ParseError candidate : values()) {
            if (candidate.label.equals(label)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown parse-error: " + label);
    }
}
