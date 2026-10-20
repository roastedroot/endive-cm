package run.endive.cm.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.PrimValType;
import run.endive.cm.types.ValType;
import run.endive.cm.types.WasmComponent;
import run.endive.tools.wasm.Wat2Wasm;

/**
 * The host may satisfy an imported interface with another version of it, as long as semantic
 * versioning calls the two compatible. Toolchains pin the WASI version a guest was built against,
 * so this is what lets one host serve guests built against older or newer patch releases.
 *
 * <p>A compatible name only chooses the instance. The instance is still checked against the
 * imported type, which is what makes accepting either direction safe.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#-canonical-interface-name">Explainer.md, canonical interface name</a>
 */
public class CompatibleVersionImportTests {

    @Test
    public void aNewerPatchSatisfiesTheImport() {
        var store = new ComponentStore();

        ComponentInstance guest =
                link(
                        store,
                        "test:pkg/iface@0.2.6",
                        Map.of("test:pkg/iface@0.2.12", host(store, 12)));

        assertArrayEquals(new Object[] {12L}, guest.export("run").apply());
    }

    @Test
    public void anOlderPatchSatisfiesTheImport() {
        var store = new ComponentStore();

        ComponentInstance guest =
                link(
                        store,
                        "test:pkg/iface@0.2.12",
                        Map.of("test:pkg/iface@0.2.6", host(store, 6)));

        assertArrayEquals(new Object[] {6L}, guest.export("run").apply());
    }

    /** From 1.0.0 on, a minor release is compatible as well. */
    @Test
    public void aNewerMinorSatisfiesAStableImport() {
        var store = new ComponentStore();

        ComponentInstance guest =
                link(store, "test:pkg/iface@1.0.0", Map.of("test:pkg/iface@1.4.2", host(store, 1)));

        assertArrayEquals(new Object[] {1L}, guest.export("run").apply());
    }

    @Test
    public void theExactNameIsPreferred() {
        var store = new ComponentStore();

        ComponentInstance guest =
                link(
                        store,
                        "test:pkg/iface@0.2.6",
                        Map.of(
                                "test:pkg/iface@0.2.12", host(store, 12),
                                "test:pkg/iface@0.2.6", host(store, 6)));

        assertArrayEquals(new Object[] {6L}, guest.export("run").apply());
    }

    @Test
    public void theHighestCompatibleVersionIsChosen() {
        var store = new ComponentStore();

        ComponentInstance guest =
                link(
                        store,
                        "test:pkg/iface@0.2.6",
                        Map.of(
                                "test:pkg/iface@0.2.9", host(store, 9),
                                "test:pkg/iface@0.2.12", host(store, 12),
                                "test:pkg/iface@0.2.10", host(store, 10),
                                "test:pkg/iface@0.3.0", host(store, 30)));

        assertArrayEquals(new Object[] {12L}, guest.export("run").apply());
    }

    @ParameterizedTest
    @CsvSource({
        "test:pkg/iface@0.3.0, test:pkg/iface@0.2.12",
        "test:pkg/iface@0.2.12, test:pkg/iface@0.3.0",
        "test:pkg/iface@1.0.0, test:pkg/iface@2.0.0",
        "test:pkg/iface@0.0.1, test:pkg/iface@0.0.2",
        "test:pkg/iface@0.2.0-rc.1, test:pkg/iface@0.2.12",
        "test:pkg/iface@0.2.6, test:pkg/iface@0.2.7-rc.1",
        "test:pkg/iface, test:pkg/iface@0.2.12",
        "test:pkg/iface@0.2.6, test:pkg/iface",
        "test:pkg/iface@0.2.6, test:pkg/other@0.2.12"
    })
    public void anIncompatibleNameIsRefused(String imported, String supplied) {
        var store = new ComponentStore();

        var thrown =
                assertThrows(
                        LinkageException.class,
                        () -> link(store, imported, Map.of(supplied, host(store, 1))));
        assertTrue(
                thrown.getMessage().contains(imported),
                "expected the import to be named, got: " + thrown.getMessage());
    }

    @Test
    public void aCompatibleInstanceMissingAFunctionIsRejected() {
        var store = new ComponentStore();
        ComponentInstance host =
                HostInstance.builder(store)
                        .addFunction("other", returning(PrimValType.U32), args -> new Object[] {0L})
                        .build();

        var thrown =
                assertThrows(
                        LinkageException.class,
                        () ->
                                link(
                                        store,
                                        "test:pkg/iface@0.2.12",
                                        Map.of("test:pkg/iface@0.2.6", host)));
        assertNamesBoth(thrown, "answer");
    }

    @Test
    public void aCompatibleInstanceWithAMistypedFunctionIsRejected() {
        var store = new ComponentStore();
        ComponentInstance host =
                HostInstance.builder(store)
                        .addFunction(
                                "answer", returning(PrimValType.STRING), args -> new Object[] {""})
                        .build();

        var thrown =
                assertThrows(
                        LinkageException.class,
                        () ->
                                link(
                                        store,
                                        "test:pkg/iface@0.2.12",
                                        Map.of("test:pkg/iface@0.2.6", host)));
        assertNamesBoth(thrown, "answer");
    }

    @Test
    public void aCompatibleNameSupplyingTheWrongSortIsRejected() {
        var store = new ComponentStore();
        ComponentFunction function =
                HostFunction.of(store, returning(PrimValType.U32), args -> new Object[] {0L});

        var thrown =
                assertThrows(
                        LinkageException.class,
                        () ->
                                link(
                                        store,
                                        "test:pkg/iface@0.2.12",
                                        Map.of("test:pkg/iface@0.2.6", function)));
        assertNamesBoth(thrown, "expected instance");
    }

    /** An instance type only says what the importer needs, so a newer host may offer more. */
    @Test
    public void aCompatibleInstanceExportingMoreThanDeclaredLinks() {
        var store = new ComponentStore();
        ComponentInstance host =
                HostInstance.builder(store)
                        .addFunction(
                                "answer", returning(PrimValType.U32), args -> new Object[] {42L})
                        .addFunction(
                                "added-later",
                                returning(PrimValType.U32),
                                args -> new Object[] {0L})
                        .build();

        ComponentInstance guest =
                link(store, "test:pkg/iface@0.2.6", Map.of("test:pkg/iface@0.2.12", host));

        assertArrayEquals(new Object[] {42L}, guest.export("run").apply());
    }

    @Test
    public void ambiguousCompatibleVersionsAreRejected() {
        var store = new ComponentStore();

        var thrown =
                assertThrows(
                        LinkageException.class,
                        () ->
                                link(
                                        store,
                                        "test:pkg/iface@0.2.6",
                                        Map.of(
                                                "test:pkg/iface@0.2.12+a", host(store, 1),
                                                "test:pkg/iface@0.2.12+b", host(store, 2))));
        assertTrue(
                thrown.getMessage().contains("ambiguous"),
                "expected the ambiguity to be named, got: " + thrown.getMessage());
    }

    /**
     * Only the host is matched leniently. Inside a component the validator already requires the
     * names an instantiation supplies to be the names imported.
     */
    @Test
    public void onlyTheTopOfAStoreMatchesCompatibleVersions() {
        var store = new ComponentStore();
        Map<String, Object> supplied = Map.of("test:pkg/iface@0.2.12", host(store, 12));
        var top = ComponentInstance.builder(store);
        top.addImports(supplied);
        var nested = ComponentInstance.builder(store).withParent(top.instance());
        nested.addImports(supplied);

        assertEquals(
                "test:pkg/iface@0.2.12",
                top.instance().resolveImport("test:pkg/iface@0.2.6").getKey());
        assertNull(nested.instance().resolveImport("test:pkg/iface@0.2.6"));
    }

    /** Links a guest that imports {@code imported} and exports {@code run}, which calls it. */
    private static ComponentInstance link(
            ComponentStore store, String imported, Map<String, Object> imports) {
        return ComponentLinker.builder().build().instantiate(store, guest(imported), imports);
    }

    private static WasmComponent guest(String imported) {
        return TestComponents.parse(
                Wat2Wasm.parse(
                        "(component\n"
                                + "  (import \""
                                + imported
                                + "\" (instance $iface\n"
                                + "    (export \"answer\" (func (result u32)))\n"
                                + "  ))\n"
                                + "  (alias export $iface \"answer\" (func $answer))\n"
                                + "  (core func $lowered (canon lower (func $answer)))\n"
                                + "  (core module $m\n"
                                + "    (import \"iface\" \"answer\" (func $answer (result i32)))\n"
                                + "    (func (export \"run\") (result i32) (call $answer))\n"
                                + "  )\n"
                                + "  (core instance $i (instantiate $m\n"
                                + "    (with \"iface\" (instance (export \"answer\" (func"
                                + " $lowered))))\n"
                                + "  ))\n"
                                + "  (func (export \"run\") (result u32) (canon lift (core func $i"
                                + " \"run\")))\n"
                                + ")"));
    }

    /** A host instance whose {@code answer} returns {@code answer}. */
    private static ComponentInstance host(ComponentStore store, long answer) {
        return HostInstance.builder(store)
                .addFunction("answer", returning(PrimValType.U32), args -> new Object[] {answer})
                .build();
    }

    private static FuncType returning(PrimValType result) {
        return FuncType.builder()
                .withResult(ValType.builder().withPrimValType(result).build())
                .build();
    }

    private static void assertNamesBoth(LinkageException thrown, String reason) {
        String message = thrown.getMessage();
        assertTrue(
                message.contains("test:pkg/iface@0.2.12")
                        && message.contains("test:pkg/iface@0.2.6")
                        && message.contains(reason),
                "expected both names and the reason, got: " + message);
    }
}
