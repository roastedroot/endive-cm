package io.roastedroot.typewire.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.types.WasmComponent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.endive.tools.wasm.Wat2Wasm;
import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;

/**
 * A core module import is checked against its declared type at link time, which is the only chance
 * there is. The module arrives from the embedder, so no validation of the component ever saw it and
 * the import declaration is the only statement of what it has to be.
 */
public class CoreModuleImportTests {

    /** Imports {@code "m"} as a core module exporting {@code f : () -> i32}. */
    private static final String COMPONENT = "/core-module-import/cmi.wat";

    private static final String MATCHING = "/core-module-import/goodmod.wat";
    private static final String WRONG_SIGNATURE = "/core-module-import/wrongsig.wat";

    @Test
    public void aModuleThatSatisfiesTheDeclaredTypeLinks() {
        assertNotNull(instantiate(module(MATCHING)));
    }

    @Test
    public void aModuleMissingADeclaredExportIsRejected() {
        var thrown =
                assertThrows(
                        LinkageException.class, () -> instantiate(WasmModule.builder().build()));
        assertTrue(
                thrown.getMessage().contains("f"),
                "expected the missing export to be named, got: " + thrown.getMessage());
    }

    @Test
    public void aModuleWhoseExportHasTheWrongSignatureIsRejected() {
        assertThrows(LinkageException.class, () -> instantiate(module(WRONG_SIGNATURE)));
    }

    private static ComponentInstance instantiate(WasmModule suppliedModule) {
        return ComponentLinker.builder()
                .build()
                .instantiate(
                        new ComponentStore(), component(COMPONENT), Map.of("m", suppliedModule));
    }

    private static WasmComponent component(String resourcePath) {
        try (InputStream is = CoreModuleImportTests.class.getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Resource not found: " + resourcePath);
            return TestComponents.fromWat(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static WasmModule module(String resourcePath) {
        return Parser.parse(assemble(resourcePath));
    }

    /** Assembles the {@code .wat} beside this test, so the fixture stays the readable form. */
    private static byte[] assemble(String resourcePath) {
        try (InputStream is = CoreModuleImportTests.class.getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Resource not found: " + resourcePath);
            return Wat2Wasm.parse(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
