package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.CharValue;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.ValType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Binds a Java host type to one of the Component Model's primitive value types.
 *
 * <p>"Primitive" here describes the component side rather than the Java side, which is why
 * reference types like {@link String}, {@link BigInteger} and {@link CharValue} sit alongside the
 * boxed numerics. Some component primitives simply have no Java primitive that can carry them.
 * {@code u64} exceeds {@code long}, and {@code char} is any Unicode scalar value up to
 * {@code U+10FFFF}, which neither {@code char} nor {@link Character} can hold.
 */
public final class PrimitiveHostTypeDescriptor extends HostTypeDescriptor {

    private static final ValType BOOL_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.BOOL).build();
    private static final ValType S8_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.S8).build();
    private static final ValType U8_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.U8).build();
    private static final ValType S16_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.S16).build();
    private static final ValType U16_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.U16).build();
    private static final ValType S32_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.S32).build();
    private static final ValType U32_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.U32).build();
    private static final ValType S64_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.S64).build();
    private static final ValType U64_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.U64).build();
    private static final ValType F32_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.F32).build();
    private static final ValType F64_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.F64).build();
    private static final ValType CHAR_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.CHAR).build();
    private static final ValType STRING_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.STRING).build();
    private static final ValType ERROR_CONTEXT_VAL_TYPE =
            ValType.builder().withPrimValType(PrimValType.ERROR_CONTEXT).build();

    private static final PrimitiveHostTypeDescriptor BOOL_WRAPPER =
            new PrimitiveHostTypeDescriptor(Boolean.class, BOOL_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor BOOL_PRIMITIVE =
            new PrimitiveHostTypeDescriptor(Boolean.TYPE, BOOL_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor BYTE_WRAPPER =
            new PrimitiveHostTypeDescriptor(Byte.class, S8_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor BYTE_PRIMITIVE =
            new PrimitiveHostTypeDescriptor(Byte.TYPE, S8_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor SHORT_WRAPPER =
            new PrimitiveHostTypeDescriptor(Short.class, U8_VAL_TYPE, S16_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor SHORT_PRIMITIVE =
            new PrimitiveHostTypeDescriptor(Short.TYPE, U8_VAL_TYPE, S16_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor INT_WRAPPER =
            new PrimitiveHostTypeDescriptor(Integer.class, U16_VAL_TYPE, S32_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor INT_PRIMITIVE =
            new PrimitiveHostTypeDescriptor(Integer.TYPE, U16_VAL_TYPE, S32_VAL_TYPE);
    // Not u64, which lifts to a BigInteger because it exceeds long.
    private static final PrimitiveHostTypeDescriptor LONG_WRAPPER =
            new PrimitiveHostTypeDescriptor(
                    Long.class, U32_VAL_TYPE, S64_VAL_TYPE, ERROR_CONTEXT_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor LONG_PRIMITIVE =
            new PrimitiveHostTypeDescriptor(
                    Long.TYPE, U32_VAL_TYPE, S64_VAL_TYPE, ERROR_CONTEXT_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor BIG_INTEGER =
            new PrimitiveHostTypeDescriptor(BigInteger.class, U64_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor FLOAT_WRAPPER =
            new PrimitiveHostTypeDescriptor(Float.class, F32_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor FLOAT_PRIMITIVE =
            new PrimitiveHostTypeDescriptor(Float.TYPE, F32_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor DOUBLE_WRAPPER =
            new PrimitiveHostTypeDescriptor(Double.class, F64_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor DOUBLE_PRIMITIVE =
            new PrimitiveHostTypeDescriptor(Double.TYPE, F64_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor CHAR_VALUE =
            new PrimitiveHostTypeDescriptor(CharValue.class, CHAR_VAL_TYPE);
    private static final PrimitiveHostTypeDescriptor STRING =
            new PrimitiveHostTypeDescriptor(String.class, STRING_VAL_TYPE);

    private final Class<?> hostType;
    private final List<ValType> compatibleComponentTypes = new ArrayList<>();

    private PrimitiveHostTypeDescriptor(Class<?> hostType, ValType... compatibleComponentTypes) {
        Objects.requireNonNull(hostType, "type");
        Objects.requireNonNull(compatibleComponentTypes, "compatibleComponentType");
        this.hostType = hostType;
        this.compatibleComponentTypes.addAll(Arrays.asList(compatibleComponentTypes));
    }

    public static PrimitiveHostTypeDescriptor forClass(Class<?> hostType) {
        Objects.requireNonNull(hostType, "hostType");
        if (hostType == Boolean.class) {
            return BOOL_WRAPPER;
        } else if (hostType == Boolean.TYPE) {
            return BOOL_PRIMITIVE;
        } else if (hostType == Byte.class) {
            return BYTE_WRAPPER;
        } else if (hostType == Byte.TYPE) {
            return BYTE_PRIMITIVE;
        } else if (hostType == Short.class) {
            return SHORT_WRAPPER;
        } else if (hostType == Short.TYPE) {
            return SHORT_PRIMITIVE;
        } else if (hostType == Integer.class) {
            return INT_WRAPPER;
        } else if (hostType == Integer.TYPE) {
            return INT_PRIMITIVE;
        } else if (hostType == Long.class) {
            return LONG_WRAPPER;
        } else if (hostType == Long.TYPE) {
            return LONG_PRIMITIVE;
        } else if (hostType == BigInteger.class) {
            return BIG_INTEGER;
        } else if (hostType == Float.class) {
            return FLOAT_WRAPPER;
        } else if (hostType == Float.TYPE) {
            return FLOAT_PRIMITIVE;
        } else if (hostType == Double.class) {
            return DOUBLE_WRAPPER;
        } else if (hostType == Double.TYPE) {
            return DOUBLE_PRIMITIVE;
        } else if (hostType == CharValue.class) {
            return CHAR_VALUE;
        } else if (hostType == String.class) {
            return STRING;
        }
        throw new IllegalArgumentException("Unsupported primitive type: " + hostType);
    }

    static boolean supports(Class<?> hostType) {
        return hostType == Boolean.class
                || hostType == Boolean.TYPE
                || hostType == Byte.class
                || hostType == Byte.TYPE
                || hostType == Short.class
                || hostType == Short.TYPE
                || hostType == Integer.class
                || hostType == Integer.TYPE
                || hostType == Long.class
                || hostType == Long.TYPE
                || hostType == BigInteger.class
                || hostType == Float.class
                || hostType == Float.TYPE
                || hostType == Double.class
                || hostType == Double.TYPE
                || hostType == CharValue.class
                || hostType == String.class;
    }

    @Override
    boolean matches(Class<?> hostType) {
        return boxed(this.hostType) == boxed(hostType);
    }

    @Override
    public String toString() {
        return hostType.getSimpleName();
    }

    @Override
    boolean isCompatibleWith(ComponentInstance instance, ValType componentType) {
        return compatibleComponentTypes.contains(resolve(instance, componentType));
    }

    /**
     * A named primitive such as {@code type instant = u64} is defined in the type index space
     * rather than written inline, so it is resolved to the primitive it names.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#fundamental-value-types">Explainer.md, fundamental value types</a>
     */
    private static ValType resolve(ComponentInstance instance, ValType componentType) {
        if (componentType.primValType() != null) {
            return componentType;
        }
        Type type = instance.getType(componentType.typeIdx());
        if (type != null && type.defValType() instanceof PrimValType) {
            return ValType.builder().withPrimValType((PrimValType) type.defValType()).build();
        }
        return componentType;
    }
}
