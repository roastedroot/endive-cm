package io.roastedroot.typewire.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.abi.CharValue;
import io.roastedroot.typewire.types.EnumType;
import io.roastedroot.typewire.types.FlagsType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.ValType;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ComponentFunctionTests {

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

    private static final ValType ENUM_VAL_TYPE = ValType.builder().withTypeIdx(0).build();

    private static final ValType FLAGS_VAL_TYPE = ValType.builder().withTypeIdx(1).build();

    private static final List<ValType> checkedValTypes =
            List.of(
                    BOOL_VAL_TYPE,
                    S8_VAL_TYPE,
                    U8_VAL_TYPE,
                    S16_VAL_TYPE,
                    U16_VAL_TYPE,
                    S32_VAL_TYPE,
                    U32_VAL_TYPE,
                    S64_VAL_TYPE,
                    U64_VAL_TYPE,
                    F32_VAL_TYPE,
                    F64_VAL_TYPE,
                    CHAR_VAL_TYPE,
                    STRING_VAL_TYPE,
                    ERROR_CONTEXT_VAL_TYPE,
                    ENUM_VAL_TYPE,
                    FLAGS_VAL_TYPE);

    private static final ComponentInstance.Builder builder =
            ComponentInstance.builder(new ComponentStore());

    private static final ComponentInstance instance = builder.instance();

    @BeforeAll
    static void setup() {
        var enumType =
                EnumType.builder()
                        .addLabel("foo")
                        .addLabel("bar")
                        .addLabel("baz-kebab-case")
                        .build();
        builder.addType(Type.of(enumType));

        var flagsType =
                FlagsType.builder()
                        .addLabel("foo")
                        .addLabel("bar")
                        .addLabel("baz-kebab-case")
                        .build();
        builder.addType(Type.of(flagsType));
    }

    @Test
    void testBooleanCompatibility() {
        checkedValTypes.stream()
                .collect(Collectors.partitioningBy(t -> t == BOOL_VAL_TYPE))
                .forEach(
                        (compatible, types) -> {
                            if (compatible) {
                                for (ValType t : types) {
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Boolean.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, boolean.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Boolean.class),
                                                    t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            boolean.class),
                                                    t));
                                }
                            } else {
                                for (ValType t : types) {
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Boolean.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, boolean.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Boolean.class),
                                                    t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            boolean.class),
                                                    t));
                                }
                            }
                        });
    }

    @Test
    void testByteCompatibility() {
        checkedValTypes.stream()
                .collect(Collectors.partitioningBy(t -> t == S8_VAL_TYPE))
                .forEach(
                        (compatible, types) -> {
                            if (compatible) {
                                for (ValType t : types) {
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Byte.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, byte.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Byte.class),
                                                    t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            byte.class),
                                                    t));
                                }
                            } else {
                                for (ValType t : types) {
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Byte.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, byte.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Byte.class),
                                                    t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            byte.class),
                                                    t));
                                }
                            }
                        });
    }

    @Test
    void testShortCompatibility() {
        checkedValTypes.stream()
                .collect(
                        Collectors.partitioningBy(
                                t -> List.of(U8_VAL_TYPE, S16_VAL_TYPE).contains(t)))
                .forEach(
                        (compatible, types) -> {
                            if (compatible) {
                                for (ValType t : types) {
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Short.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, short.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Short.class),
                                                    t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            short.class),
                                                    t));
                                }
                            } else {
                                for (ValType t : types) {
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Short.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, short.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Short.class),
                                                    t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            short.class),
                                                    t));
                                }
                            }
                        });
    }

    @Test
    void testIntCompatibility() {
        checkedValTypes.stream()
                .collect(
                        Collectors.partitioningBy(
                                t -> List.of(U16_VAL_TYPE, S32_VAL_TYPE).contains(t)))
                .forEach(
                        (compatible, types) -> {
                            if (compatible) {
                                for (ValType t : types) {
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Integer.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, int.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Integer.class),
                                                    t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(int.class),
                                                    t));
                                }
                            } else {
                                for (ValType t : types) {
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Integer.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, int.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Integer.class),
                                                    t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(int.class),
                                                    t));
                                }
                            }
                        });
    }

    @Test
    void testLongCompatibility() {
        checkedValTypes.stream()
                .collect(
                        Collectors.partitioningBy(
                                t ->
                                        List.of(U32_VAL_TYPE, S64_VAL_TYPE, ERROR_CONTEXT_VAL_TYPE)
                                                .contains(t)))
                .forEach(
                        (compatible, types) -> {
                            if (compatible) {
                                for (ValType t : types) {
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Long.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, long.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Long.class),
                                                    t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            long.class),
                                                    t));
                                }
                            } else {
                                for (ValType t : types) {
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Long.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, long.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Long.class),
                                                    t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            long.class),
                                                    t));
                                }
                            }
                        });
    }

    @Test
    void testBigIntegerCompatibility() {
        checkedValTypes.stream()
                .collect(Collectors.partitioningBy(t -> t == U64_VAL_TYPE))
                .forEach(
                        (compatible, types) -> {
                            if (compatible) {
                                for (ValType t : types) {
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, BigInteger.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            BigInteger.class),
                                                    t));
                                }
                            } else {
                                for (ValType t : types) {
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, BigInteger.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            BigInteger.class),
                                                    t));
                                }
                            }
                        });
    }

    @Test
    void testFloatCompatibility() {
        checkedValTypes.stream()
                .collect(Collectors.partitioningBy(t -> t == F32_VAL_TYPE))
                .forEach(
                        (compatible, types) -> {
                            if (compatible) {
                                for (ValType t : types) {
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Float.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, float.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Float.class),
                                                    t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            float.class),
                                                    t));
                                }
                            } else {
                                for (ValType t : types) {
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Float.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, float.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Float.class),
                                                    t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            float.class),
                                                    t));
                                }
                            }
                        });
    }

    @Test
    void testDoubleCompatibility() {
        checkedValTypes.stream()
                .collect(Collectors.partitioningBy(t -> t == F64_VAL_TYPE))
                .forEach(
                        (compatible, types) -> {
                            if (compatible) {
                                for (ValType t : types) {
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Double.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, double.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Double.class),
                                                    t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            double.class),
                                                    t));
                                }
                            } else {
                                for (ValType t : types) {
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, Double.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, double.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            Double.class),
                                                    t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            double.class),
                                                    t));
                                }
                            }
                        });
    }

    @Test
    void testCharCompatibility() {
        // A component char binds to CharValue rather than char or Character, both being 16
        // bits and cannot hold a scalar value above U+FFFF.
        checkedValTypes.stream()
                .collect(Collectors.partitioningBy(t -> t == CHAR_VAL_TYPE))
                .forEach(
                        (compatible, types) -> {
                            for (ValType t : types) {
                                assertEquals(
                                        compatible,
                                        ComponentFunctionInstance.isTypeCompatible(
                                                instance, CharValue.class, t));
                                assertEquals(
                                        compatible,
                                        ComponentFunctionInstance.isTypeCompatible(
                                                instance,
                                                PrimitiveHostTypeDescriptor.forClass(
                                                        CharValue.class),
                                                t));
                            }
                        });
    }

    @Test
    void charAndCharacterAreNoLongerBoundToAComponentChar() {
        // Rejected outright rather than silently truncating anything above the BMP.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ComponentFunctionInstance.isTypeCompatible(
                                instance, Character.class, CHAR_VAL_TYPE));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ComponentFunctionInstance.isTypeCompatible(
                                instance, char.class, CHAR_VAL_TYPE));
    }

    @Test
    void testStringCompatibility() {
        checkedValTypes.stream()
                .collect(Collectors.partitioningBy(t -> t == STRING_VAL_TYPE))
                .forEach(
                        (compatible, types) -> {
                            if (compatible) {
                                for (ValType t : types) {
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, String.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            String.class),
                                                    t));
                                }
                            } else {
                                for (ValType t : types) {
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, String.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    PrimitiveHostTypeDescriptor.forClass(
                                                            String.class),
                                                    t));
                                }
                            }
                        });
    }

    /** A Java enum carries a component {@code enum}, and not {@code flags}, which is a set. */
    @Test
    void testEnumCompatibility() {
        checkedValTypes.stream()
                .collect(Collectors.partitioningBy(t -> t == ENUM_VAL_TYPE))
                .forEach(
                        (compatible, types) -> {
                            if (compatible) {
                                for (ValType t : types) {
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, TestEnum1.class, t));
                                    assertTrue(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    EnumHostTypeDescriptor.forClass(
                                                            TestEnum1.class),
                                                    t));
                                }
                            } else {
                                for (ValType t : types) {
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance, TestEnum1.class, t));
                                    assertFalse(
                                            ComponentFunctionInstance.isTypeCompatible(
                                                    instance,
                                                    EnumHostTypeDescriptor.forClass(
                                                            TestEnum1.class),
                                                    t));
                                }
                            }
                        });
    }

    @Test
    void testEnumCompatibilityWithExtraCase() {
        checkedValTypes.forEach(
                t -> {
                    assertFalse(
                            ComponentFunctionInstance.isTypeCompatible(
                                    instance, TestEnum2.class, t));
                    assertFalse(
                            ComponentFunctionInstance.isTypeCompatible(
                                    instance, EnumHostTypeDescriptor.forClass(TestEnum2.class), t));
                });
    }

    @Test
    void testEnumCompatibilityMissingCase() {
        checkedValTypes.forEach(
                t -> {
                    assertFalse(
                            ComponentFunctionInstance.isTypeCompatible(
                                    instance, TestEnum3.class, t));
                    assertFalse(
                            ComponentFunctionInstance.isTypeCompatible(
                                    instance, EnumHostTypeDescriptor.forClass(TestEnum3.class), t));
                });
    }
}
