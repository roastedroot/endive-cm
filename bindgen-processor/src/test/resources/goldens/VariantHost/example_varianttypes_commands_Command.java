package endive.testing.example.varianttypes.commands;

import java.util.Objects;
import javax.annotation.processing.Generated;
import run.endive.cm.abi.VariantValue;

/**
 * The WIT variant {@code command}, declared by {@code example:variant-types/commands}. A value is one of the nested case classes, told apart with {@code instanceof}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public abstract class Command {

    private Command() {
    }

    /**
     * This case as the ABI carries it.
     */
    public abstract VariantValue toComponent();

    /**
     * The case a lifted value names.
     */
    public static Command fromComponent(Object value) {
        VariantValue variant = (VariantValue) value;
        switch(variant.label()) {
            case "stop":
                return new Stop();
            case "jump":
                return new Jump((Long) variant.value());
            case "speak":
                return new Speak((String) variant.value());
            default:
                throw new IllegalArgumentException("unknown command: " + variant.label());
        }
    }

    /**
     * The case {@code stop}.
     */
    public static final class Stop extends Command {

        @Override
        public VariantValue toComponent() {
            return VariantValue.of("stop", null);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Stop;
        }

        @Override
        public int hashCode() {
            return "stop".hashCode();
        }

        @Override
        public String toString() {
            return "stop";
        }
    }

    /**
     * The case {@code jump}.
     */
    public static final class Jump extends Command {

        private final Long value;

        public Jump(Long value) {
            this.value = value;
        }

        /**
         * The payload this case carries.
         */
        public Long value() {
            return value;
        }

        @Override
        public VariantValue toComponent() {
            return VariantValue.of("jump", value);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Jump)) {
                return false;
            }
            return Objects.equals(value, ((Jump) o).value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return "jump(" + value + ")";
        }
    }

    /**
     * The case {@code speak}.
     */
    public static final class Speak extends Command {

        private final String value;

        public Speak(String value) {
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
            return VariantValue.of("speak", value);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Speak)) {
                return false;
            }
            return Objects.equals(value, ((Speak) o).value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return "speak(" + value + ")";
        }
    }
}
