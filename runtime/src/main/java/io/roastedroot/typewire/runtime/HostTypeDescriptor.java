package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.types.ValType;

/**
 * Binds a Java type to the component value types it can carry, converting where the Java shape
 * differs from what the Canonical ABI carries.
 */
public abstract class HostTypeDescriptor {

    abstract boolean matches(Class<?> hostType);

    abstract boolean isCompatibleWith(ComponentInstance instance, ValType componentType);

    /** The value the Canonical ABI carries for {@code hostValue}. */
    Object toComponentValue(Object hostValue) {
        return hostValue;
    }

    /** The inverse of {@link #toComponentValue}. */
    Object toHostValue(Object componentValue) {
        return componentValue;
    }

    /** The wrapper class for a primitive {@code type}, otherwise {@code type} itself. */
    static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }
}
