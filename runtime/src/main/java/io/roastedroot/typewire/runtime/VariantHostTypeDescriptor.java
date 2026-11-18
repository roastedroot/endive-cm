package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.VariantValue;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.ValType;

/**
 * Binds {@link VariantValue} to {@code variant} and to the three shorthands that despecialize into
 * one. Those are {@code enum} (cases with no payload), {@code option} (cases {@code none} and
 * {@code some}) and {@code result} (cases {@code ok} and {@code error}).
 *
 * <p>Only the shape is checked. Whether the value names a case the type actually declares is
 * settled when it is lowered, which is also where a payload is matched against that case.
 */
public final class VariantHostTypeDescriptor extends HostTypeDescriptor {

    private static final VariantHostTypeDescriptor INSTANCE = new VariantHostTypeDescriptor();

    private VariantHostTypeDescriptor() {}

    public static VariantHostTypeDescriptor instance() {
        return INSTANCE;
    }

    @Override
    boolean matches(Class<?> hostType) {
        return supports(hostType);
    }

    @Override
    boolean isCompatibleWith(ComponentInstance instance, ValType componentType) {
        if (componentType.primValType() != null) {
            return false;
        }
        Type type = instance.getType(componentType.typeIdx());
        if (type == null || type.defValType() == null) {
            return false;
        }
        switch (type.defValType().kind()) {
            case VARIANT:
            case ENUM:
            case OPTION:
            case RESULT:
                return true;
            default:
                return false;
        }
    }

    static boolean supports(Class<?> hostType) {
        return VariantValue.class.isAssignableFrom(hostType);
    }
}
