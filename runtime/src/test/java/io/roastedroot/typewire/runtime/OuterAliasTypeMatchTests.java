package io.roastedroot.typewire.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.types.LabelValType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.RecordType;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.ValType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.endive.tools.wasm.Wat2Wasm;

/**
 * An imported instance type may bring in a type from the enclosing component with
 * {@code alias outer}.
 * This is what a WIT {@code use} of a record from another interface produces.
 * The record names its field's type by index, so the host's copy of it,
 * written in its own index space, is only equal once both are resolved.
 */
public class OuterAliasTypeMatchTests {

    private static final String COMPONENT =
            "(component\n"
                    + "  (type $a-ty (instance\n"
                    + "    (type $inner (record (field \"x\" u32)))\n"
                    + "    (export \"inner\" (type (eq $inner)))\n"
                    + "    (type (record (field \"i\" 1)))\n"
                    + "    (export \"outer\" (type (eq 2)))\n"
                    + "  ))\n"
                    + "  (import \"a\" (instance $a (type $a-ty)))\n"
                    + "  (alias export $a \"outer\" (type $outer))\n"
                    + "  (type $b-ty (instance\n"
                    + "    (alias outer 1 $outer (type))\n"
                    + "    (export \"outer\" (type (eq 0)))\n"
                    + "  ))\n"
                    + "  (import \"b\" (instance $b (type $b-ty)))\n"
                    + ")";

    /**
     * A provider declaring {@code inner} and {@code outer},
     * with {@code innerField} as inner's field.
     */
    private static ComponentInstance provider(
            ComponentStore store, PrimValType innerField, boolean exportInner) {
        var builder = HostInstance.builder(store);
        ValType inner =
                builder.declareType(
                        Type.of(
                                RecordType.builder()
                                        .addField(
                                                LabelValType.builder()
                                                        .withLabel("x")
                                                        .withValType(
                                                                ValType.builder()
                                                                        .withPrimValType(innerField)
                                                                        .build())
                                                        .build())
                                        .build()));
        if (exportInner) {
            builder.addType("inner", inner);
        }
        ValType outer =
                builder.declareType(
                        Type.of(
                                RecordType.builder()
                                        .addField(
                                                LabelValType.builder()
                                                        .withLabel("i")
                                                        .withValType(inner)
                                                        .build())
                                        .build()));
        return builder.addType("outer", outer).build();
    }

    /** Before the fix this failed with "Instance alias mismatch for 'b'". */
    @Test
    public void anOuterAliasedRecordMatchesAStructurallyEqualHostRecord() {
        var store = new ComponentStore();
        var component = TestComponents.parse(Wat2Wasm.parse(COMPONENT));
        ComponentInstance instance =
                ComponentLinker.builder()
                        .build()
                        .instantiate(
                                store,
                                component,
                                Map.of(
                                        "a", provider(store, PrimValType.U32, true),
                                        "b", provider(store, PrimValType.U32, false)));
        assertNotNull(instance);
    }

    @Test
    public void anOuterAliasedRecordStillRejectsADifferentHostRecord() {
        var store = new ComponentStore();
        var component = TestComponents.parse(Wat2Wasm.parse(COMPONENT));
        assertThrows(
                LinkageException.class,
                () ->
                        ComponentLinker.builder()
                                .build()
                                .instantiate(
                                        store,
                                        component,
                                        Map.of(
                                                "a",
                                                provider(store, PrimValType.U32, true),
                                                "b",
                                                provider(store, PrimValType.STRING, false))));
    }
}
