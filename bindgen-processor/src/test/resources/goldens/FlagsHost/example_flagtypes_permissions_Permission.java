package endive.testing.example.flagtypes.permissions;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

/**
 * The WIT flags {@code permission}, declared by {@code example:flag-types/permissions}.
 */
@Generated("run.endive.cm.bindgen.BindgenProcessor")
public final class Permission {

    /**
     * One flag of {@code permission}.
     */
    public enum Flag {

        READ("read"), WRITE("write"), EXEC("exec");

        private final String label;

        Flag(String label) {
            this.label = label;
        }
    }

    private final EnumSet<Flag> flags;

    private Permission(EnumSet<Flag> flags) {
        this.flags = flags;
    }

    /**
     * The value with exactly {@code flags} set.
     */
    public static Permission of(Flag... flags) {
        EnumSet<Flag> set = EnumSet.noneOf(Flag.class);
        for (Flag flag : flags) {
            set.add(flag);
        }
        return new Permission(set);
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
    public static Permission fromComponent(Object value) {
        Map<?, ?> carried = (Map<?, ?>) value;
        EnumSet<Flag> set = EnumSet.noneOf(Flag.class);
        for (Flag flag : Flag.values()) {
            if (Boolean.TRUE.equals(carried.get(flag.label))) {
                set.add(flag);
            }
        }
        return new Permission(set);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Permission && flags.equals(((Permission) other).flags);
    }

    @Override
    public int hashCode() {
        return flags.hashCode();
    }

    @Override
    public String toString() {
        return "Permission" + flags;
    }
}
