package endive.testing.exports.example.flagtypes.runner;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

/**
 * The WIT flags {@code mode}, declared by {@code example:flag-types/runner}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class Mode {

    /**
     * One flag of {@code mode}.
     */
    public enum Flag {

        READ("read"), WRITE("write"), EXEC("exec");

        private final String label;

        Flag(String label) {
            this.label = label;
        }
    }

    private final EnumSet<Flag> flags;

    private Mode(EnumSet<Flag> flags) {
        this.flags = flags;
    }

    /**
     * The value with exactly {@code flags} set.
     */
    public static Mode of(Flag... flags) {
        EnumSet<Flag> set = EnumSet.noneOf(Flag.class);
        for (Flag flag : flags) {
            set.add(flag);
        }
        return new Mode(set);
    }

    /**
     * Whether {@code flag} is set.
     */
    public boolean has(Flag flag) {
        return flags.contains(flag);
    }

    /**
     * The flags that are set.
     */
    public Set<Flag> flags() {
        return EnumSet.copyOf(flags);
    }

    /**
     * These flags as the ABI carries them, which is a map per label.
     */
    public Map<String, Boolean> toComponent() {
        Map<String, Boolean> value = new LinkedHashMap<>();
        for (Flag flag : Flag.values()) {
            value.put(flag.label, flags.contains(flag));
        }
        return value;
    }

    /**
     * The flags a lifted value sets.
     */
    public static Mode fromComponent(Object value) {
        Map<?, ?> carried = (Map<?, ?>) value;
        EnumSet<Flag> set = EnumSet.noneOf(Flag.class);
        for (Flag flag : Flag.values()) {
            if (Boolean.TRUE.equals(carried.get(flag.label))) {
                set.add(flag);
            }
        }
        return new Mode(set);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Mode && flags.equals(((Mode) other).flags);
    }

    @Override
    public int hashCode() {
        return flags.hashCode();
    }

    @Override
    public String toString() {
        return "Mode" + flags;
    }
}
