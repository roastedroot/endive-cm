package endive.testing.exports.example.varianttypes.replies;

import java.util.Objects;
import javax.annotation.processing.Generated;
import run.endive.cm.abi.VariantValue;

/**
 * The WIT variant {@code reply}, declared by {@code example:variant-types/replies}. A value is one of the nested case classes, told apart with {@code instanceof}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public abstract class Reply {

    private Reply() {
    }

    /**
     * This case as the ABI carries it.
     */
    public abstract VariantValue toComponent();

    /**
     * The case a lifted value names.
     */
    public static Reply fromComponent(Object value) {
        VariantValue variant = (VariantValue) value;
        switch(variant.label()) {
            case "silence":
                return new Silence();
            case "text":
                return new Text((String) variant.value());
            default:
                throw new IllegalArgumentException("unknown reply: " + variant.label());
        }
    }

    /**
     * The case {@code silence}.
     */
    public static final class Silence extends Reply {

        @Override
        public VariantValue toComponent() {
            return VariantValue.of("silence", null);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Silence;
        }

        @Override
        public int hashCode() {
            return "silence".hashCode();
        }

        @Override
        public String toString() {
            return "silence";
        }
    }

    /**
     * The case {@code text}.
     */
    public static final class Text extends Reply {

        private final String value;

        public Text(String value) {
            this.value = value;
        }

        /**
         * The payload this case carries.
         */
        public String value() {
            return value;
        }

        @Override
        public VariantValue toComponent() {
            return VariantValue.of("text", value);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Text)) {
                return false;
            }
            return Objects.equals(value, ((Text) o).value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return "text(" + value + ")";
        }
    }
}
