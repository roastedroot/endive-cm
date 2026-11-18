package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.VariantValue;
import io.roastedroot.typewire.types.DefValType;
import io.roastedroot.typewire.types.EnumType;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.ValType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Binds a Java enum to a component {@code enum} whose labels are the constants' names in kebab
 * case, converting between a constant and the payload-less {@link VariantValue} the Canonical ABI
 * carries.
 */
public final class EnumHostTypeDescriptor extends HostTypeDescriptor {

    private final Class<?> hostType;

    private EnumHostTypeDescriptor(Class<?> hostType) {
        Objects.requireNonNull(hostType, "hostType");
        if (!Enum.class.isAssignableFrom(hostType)) {
            throw new IllegalArgumentException("hostType must be an enum");
        }
        this.hostType = hostType;
    }

    public static EnumHostTypeDescriptor forClass(Class<?> hostType) {
        return new EnumHostTypeDescriptor(hostType);
    }

    @Override
    boolean matches(Class<?> hostType) {
        return this.hostType == hostType;
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
        if (type.defValType().kind() != DefValType.Kind.ENUM) {
            return false;
        }
        List<String> labels = ((EnumType) type.defValType()).labels();

        Object[] constants = hostType.getEnumConstants();
        if (constants == null || constants.length != labels.size()) {
            return false;
        }

        return Arrays.stream(constants)
                .map(constant -> labelOf((Enum<?>) constant))
                .allMatch(labels::contains);
    }

    @Override
    Object toComponentValue(Object hostValue) {
        return VariantValue.of(labelOf((Enum<?>) hostValue), null);
    }

    @Override
    Object toHostValue(Object componentValue) {
        String label = ((VariantValue) componentValue).label();
        for (Object constant : hostType.getEnumConstants()) {
            if (labelOf((Enum<?>) constant).equals(label)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(
                "enum label \"" + label + "\" has no constant in " + hostType.getName());
    }

    @Override
    public String toString() {
        return hostType.getSimpleName();
    }

    static boolean supports(Class<?> hostType) {
        return Enum.class.isAssignableFrom(hostType);
    }

    private static String labelOf(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace("_", "-");
    }
}
