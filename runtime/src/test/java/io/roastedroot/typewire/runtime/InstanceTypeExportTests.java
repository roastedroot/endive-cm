package io.roastedroot.typewire.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.types.FuncType;
import io.roastedroot.typewire.types.PrimValType;
import io.roastedroot.typewire.types.ValType;
import io.roastedroot.typewire.types.WasmComponent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.endive.tools.wasm.Wat2Wasm;

/**
 * The {@code instance} and {@code component} exports an instance type declares are checked against
 * what the provider supplies under those names, not merely confirmed to be of the right sort.
 *
 * <p>For an instance the enclosing declaration's check is applied again one level down, so the
 * provider may export more and in any order.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#type-definitions">Explainer.md, instance subtyping</a>
 */
public class InstanceTypeExportTests {

    private static final String MATCHING = "/nested-instance-type/matching.wat";
    private static final String MISSING_EXPORT = "/nested-instance-type/missing-export.wat";
    private static final String WRONG_SIGNATURE = "/nested-instance-type/wrong-signature.wat";

    @Test
    public void aNestedInstanceThatSatisfiesItsDeclaredTypeLinks() {
        assertNotNull(instantiate(MATCHING));
    }

    /** Until the declared type was consulted, being an instance was the whole of the check. */
    @Test
    public void aNestedInstanceMissingADeclaredExportIsRejected() {
        var thrown = assertThrows(LinkageException.class, () -> instantiate(MISSING_EXPORT));
        assertTrue(
                thrown.getMessage().contains("return-five"),
                "expected the missing export to be named, got: " + thrown.getMessage());
    }

    @Test
    public void aNestedInstanceExportOfTheWrongTypeIsRejected() {
        var thrown = assertThrows(LinkageException.class, () -> instantiate(WRONG_SIGNATURE));
        assertTrue(
                thrown.getMessage().contains("return-four"),
                "expected the mismatched export to be named, got: " + thrown.getMessage());
    }

    /**
     * A {@code (component)} type names no exports and no imports, and a component may only import
     * less than its type. The host's {@code a-component} imports a function, so it cannot satisfy
     * an empty type.
     */
    @Test
    public void aComponentWithImportsCannotSatisfyAnEmptyComponentType() {
        var thrown =
                assertThrows(
                        LinkageException.class,
                        () -> instantiate("/component-type/empty-type-rejects-imports.wat"));
        assertTrue(
                thrown.getMessage().contains("a-component"),
                "expected the export to be named, got: " + thrown.getMessage());
    }

    /**
     * A non-empty component type, where the exported component's type comes from the declaration
     * under which the provider imported it.
     */
    @Test
    public void aComponentTypeDeclaredByAnImportLinks() {
        assertNotNull(instantiate("/component-type/declared-via-import.wat"));
    }

    /**
     * The same, where the component is defined and exported in one place, so its type exists only
     * as whatever its definition implies.
     */
    @Test
    public void aComponentTypeInferredFromADefinitionLinks() {
        assertNotNull(instantiate("/component-type/inferred-from-definition.wat"));
    }

    private static ComponentInstance instantiate(String resourcePath) {
        var store = new ComponentStore();
        return ComponentLinker.builder()
                .build()
                .instantiate(store, component(resourcePath), Map.of("host", host(store)));
    }

    /**
     * The provider every case imports. It holds one nested instance exporting {@code return-four}
     * and one component that imports a function.
     */
    private static ComponentInstance host(ComponentStore store) {
        var builder = ComponentInstance.builder(store);
        builder.addExport("nested", nested(store));
        builder.addExport(
                "a-component",
                new ComponentClosure(
                        TestComponents.parse(Wat2Wasm.parse("(component (import \"x\" (func)))")),
                        null));
        return builder.build();
    }

    /** An instance exporting {@code return-four : () -> u32}, and nothing else. */
    private static ComponentInstance nested(ComponentStore store) {
        var builder = ComponentInstance.builder(store);
        FuncType funcType =
                FuncType.builder()
                        .withResult(ValType.builder().withPrimValType(PrimValType.U32).build())
                        .build();
        builder.addExport(
                "return-four",
                ComponentFunctionInstance.builder()
                        .withInstance(builder.instance())
                        .withTypeResolver(builder.instance())
                        .withFuncType(funcType)
                        .withCall(args -> new Object[] {4L})
                        .withHostProvided(true)
                        .build());
        return builder.build();
    }

    private static WasmComponent component(String resourcePath) {
        try (InputStream is = InstanceTypeExportTests.class.getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Resource not found: " + resourcePath);
            return TestComponents.fromWat(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
