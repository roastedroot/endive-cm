package endive.testing.exports.example.resulttypes.running;

import javax.annotation.processing.Generated;
import run.endive.cm.abi.VariantValue;

/**
 * The WIT enum {@code run-error}, declared by {@code example:result-types/running}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public enum RunError {

    REFUSED("refused");

    private final String label;

    RunError(String label) {
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
    public static RunError fromComponent(Object value) {
        String label = ((VariantValue) value).label();
        for (RunError candidate : values()) {
            if (candidate.label.equals(label)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown run-error: " + label);
    }
}
