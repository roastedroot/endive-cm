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

/**
 * Checks a component supplied by the host against the {@code (component ...)} type with which it is
 * declared.
 *
 * <p>This is the boundary no validator covers. Each case validates both sides separately, the
 * consumer and the component the host hands it being two independent binaries, and then asks the
 * linker to put them together.
 *
 * <p>The consumer is a fixture and the host's component is written inline, because what varies
 * between cases is almost always the component being supplied rather than the type holding it to
 * account.
 */
public class ComponentTypeMatchingTests {

    private static final String DECLARES_F = "/component-type/declared-export-and-func.wat";
    private static final String DECLARES_IMPORT =
            "/component-type/declared-component-with-import.wat";

    /** A component exporting {@code f : () -> u32}, and whatever else {@code extra} adds. */
    private static String exportsF(String result, String extra) {
        String core = "u64".equals(result) ? "i64" : "i32";
        return "(component "
                + "(core module $m (func (export \"g\") (result "
                + core
                + ") ("
                + core
                + ".const 7))) "
                + "(core instance $mi (instantiate $m)) "
                + "(func (export \"f\") (result "
                + result
                + ") (canon lift (core func $mi \"g\"))) "
                + extra
                + ")";
    }

    @Test
    public void aComponentMatchingItsDeclaredTypeLinks() {
        assertNotNull(link(DECLARES_F, exportsF("u32", "")));
    }

    /**
     * Component subtyping lets a subtype "export more ... than is declared by the supertype", so an
     * extra export is not a mismatch.
     */
    @Test
    public void aComponentExportingMoreThanDeclaredLinks() {
        String extra = "(func (export \"extra\") (result u32) (canon lift (core func $mi \"g\")))";
        assertNotNull(link(DECLARES_F, exportsF("u32", extra)));
    }

    @Test
    public void aComponentMissingADeclaredExportIsRejected() {
        var thrown = assertThrows(LinkageException.class, () -> link(DECLARES_F, "(component)"));
        assertTrue(
                thrown.getMessage().contains("\"f\""),
                "expected the missing export to be named, got: " + thrown.getMessage());
    }

    @Test
    public void aComponentWhoseExportHasTheWrongTypeIsRejected() {
        var thrown =
                assertThrows(LinkageException.class, () -> link(DECLARES_F, exportsF("u64", "")));
        assertTrue(
                thrown.getMessage().contains("\"f\""),
                "expected the mismatched export to be named, got: " + thrown.getMessage());
    }

    /**
     * The other direction. Imports are contravariant, so a component may import less than its type
     * declares but never more. Nothing would supply the extra one.
     */
    @Test
    public void aComponentImportingWhatItsTypeDoesNotDeclareIsRejected() {
        String importsDep =
                "(component (import \"dep\" (func (result u32))) "
                        + "(core module $m (func (export \"g\") (result i32) (i32.const 7))) "
                        + "(core instance $mi (instantiate $m)) "
                        + "(func (export \"f\") (result u32) (canon lift (core func $mi \"g\"))))";
        var thrown = assertThrows(LinkageException.class, () -> link(DECLARES_F, importsDep));
        assertTrue(
                thrown.getMessage().contains("\"dep\""),
                "expected the undeclared import to be named, got: " + thrown.getMessage());
    }

    @Test
    public void aComponentImportingWhatItsTypeDeclaresLinks() {
        String importsDep =
                "(component (import \"dep\" (func (result u32))) "
                        + "(core module $m (func (export \"g\") (result i32) (i32.const 7))) "
                        + "(core instance $mi (instantiate $m)) "
                        + "(func (export \"f\") (result u32) (canon lift (core func $mi \"g\"))))";
        assertNotNull(link(DECLARES_IMPORT, importsDep));
    }

    /** A declared import the component does not use is fine, since it may need less. */
    @Test
    public void aComponentIgnoringADeclaredImportLinks() {
        assertNotNull(link(DECLARES_IMPORT, exportsF("u32", "")));
    }

    @Test
    public void aComponentImportOfTheWrongTypeIsRejected() {
        String wrongDep =
                "(component (import \"dep\" (func (result u64))) "
                        + "(core module $m (func (export \"g\") (result i32) (i32.const 7))) "
                        + "(core instance $mi (instantiate $m)) "
                        + "(func (export \"f\") (result u32) (canon lift (core func $mi \"g\"))))";
        var thrown = assertThrows(LinkageException.class, () -> link(DECLARES_IMPORT, wrongDep));
        assertTrue(
                thrown.getMessage().contains("\"dep\""),
                "expected the mismatched import to be named, got: " + thrown.getMessage());
    }

    private static final String DECLARES_INSTANCE_IMPORT =
            "/component-type/declared-component-with-instance-import.wat";

    /** A component importing an instance {@code dep} with the given exports, and exporting f. */
    private static String importsInstance(String depExports) {
        return "(component (import \"dep\" (instance "
                + depExports
                + ")) "
                + "(core module $m (func (export \"g\") (result i32) (i32.const 7))) "
                + "(core instance $mi (instantiate $m)) "
                + "(func (export \"f\") (result u32) (canon lift (core func $mi \"g\"))))";
    }

    /** Asking for less than the declared type promises is allowed. */
    @Test
    public void aComponentImportingANarrowerInstanceThanDeclaredLinks() {
        assertNotNull(
                link(
                        DECLARES_INSTANCE_IMPORT,
                        importsInstance("(export \"a\" (func (result u32)))")));
    }

    @Test
    public void aComponentImportingExactlyTheDeclaredInstanceLinks() {
        assertNotNull(
                link(
                        DECLARES_INSTANCE_IMPORT,
                        importsInstance(
                                "(export \"a\" (func (result u32)))"
                                        + " (export \"b\" (func (result u32)))")));
    }

    /** Nothing will ever supply {@code c}, so asking for it is asking for more than declared. */
    @Test
    public void aComponentImportingAWiderInstanceThanDeclaredIsRejected() {
        var thrown =
                assertThrows(
                        LinkageException.class,
                        () ->
                                link(
                                        DECLARES_INSTANCE_IMPORT,
                                        importsInstance(
                                                "(export \"a\" (func (result u32)))"
                                                        + " (export \"b\" (func (result u32)))"
                                                        + " (export \"c\" (func (result u32)))")));
        assertTrue(
                thrown.getMessage().contains("\"c\""),
                "expected the unpromised export to be named, got: " + thrown.getMessage());
    }

    @Test
    public void aComponentImportingAnInstanceExportOfTheWrongTypeIsRejected() {
        var thrown =
                assertThrows(
                        LinkageException.class,
                        () ->
                                link(
                                        DECLARES_INSTANCE_IMPORT,
                                        importsInstance("(export \"a\" (func (result u64)))")));
        assertTrue(
                thrown.getMessage().contains("\"a\""),
                "expected the mismatched export to be named, got: " + thrown.getMessage());
    }

    private static final String DECLARES_REEXPORTED_RESOURCE =
            "/component-type/declared-reexported-resource.wat";

    /**
     * A resource related through an import and again through an export resolves to one identity
     * on each side.
     */
    @Test
    public void aResourceRelatedThroughBothAnImportAndAnExportMatches() {
        String provider =
                "(component "
                        + "(import \"t\" (type $t (sub resource))) "
                        + "(export \"u\" (type $t)) "
                        + "(core module $m (func (export \"g\") (param i32))) "
                        + "(core instance $mi (instantiate $m)) "
                        + "(func (export \"f\") (param \"x\" (own $t)) "
                        + "(canon lift (core func $mi \"g\"))))";
        assertNotNull(link(DECLARES_REEXPORTED_RESOURCE, provider));
    }

    private static final String DECLARES_TYPE = "/component-type/declared-type-export.wat";

    /** A component exporting {@code t}, a record, alongside {@code f : () -> u32}. */
    private static String exportsType(String recordType) {
        return "(component "
                + "(type $T "
                + recordType
                + ") "
                + "(export \"t\" (type $T)) "
                + "(core module $m (func (export \"g\") (result i32) (i32.const 7))) "
                + "(core instance $mi (instantiate $m)) "
                + "(func (export \"f\") (result u32) (canon lift (core func $mi \"g\"))))";
    }

    /**
     * A {@code type} export is a name the rest of a component type can use, so what it resolves to
     * has to agree structurally like anything else.
     */
    @Test
    public void aComponentWhoseTypeExportMatchesLinks() {
        assertNotNull(link(DECLARES_TYPE, exportsType("(record (field \"a\" u32))")));
    }

    @Test
    public void aComponentWhoseTypeExportHasTheWrongFieldTypeIsRejected() {
        var thrown =
                assertThrows(
                        LinkageException.class,
                        () -> link(DECLARES_TYPE, exportsType("(record (field \"a\" u64))")));
        assertTrue(
                thrown.getMessage().contains("\"t\""),
                "expected the mismatched type to be named, got: " + thrown.getMessage());
    }

    @Test
    public void aComponentWhoseTypeExportHasTheWrongFieldNameIsRejected() {
        assertThrows(
                LinkageException.class,
                () -> link(DECLARES_TYPE, exportsType("(record (field \"b\" u32))")));
    }

    /**
     * Types are compared as written rather than despecialised, so a tuple and the record it shares
     * a layout with are different types, and one does not satisfy an import asking for the other.
     */
    @Test
    public void aComponentWhoseTypeExportIsADifferentShapeIsRejected() {
        assertThrows(LinkageException.class, () -> link(DECLARES_TYPE, exportsType("(tuple u32)")));
    }

    /** An alias extends the type index space, so the type after it sits one index further along. */
    @Test
    public void aTypeDefinedAfterAnAliasIsFoundAtItsRealIndex() {
        String provider =
                "(component "
                        + "(type $X u8) "
                        + "(instance $i (export \"x\" (type $X))) "
                        + "(alias export $i \"x\" (type $A)) "
                        + "(type $T (record (field \"a\" u32))) "
                        + "(export \"t\" (type $T)) "
                        + "(core module $m (func (export \"g\") (result i32) (i32.const 7))) "
                        + "(core instance $mi (instantiate $m)) "
                        + "(func (export \"f\") (result u32) (canon lift (core func $mi \"g\"))))";
        assertNotNull(link(DECLARES_TYPE, provider));
    }

    /** An alias into an instance the pass has read is followed to what that instance exports. */
    @Test
    public void aTypeReachedThroughAnExportAliasIsCompared() {
        assertNotNull(link(DECLARES_TYPE, exportsAliasedType("(record (field \"a\" u32))")));
        var thrown =
                assertThrows(
                        LinkageException.class,
                        () ->
                                link(
                                        DECLARES_TYPE,
                                        exportsAliasedType("(record (field \"a\" u64))")));
        assertTrue(
                thrown.getMessage().contains("\"t\""),
                "expected the mismatched type to be named, got: " + thrown.getMessage());
    }

    /** Exports {@code t} as an alias of the type an inline instance exports. */
    private static String exportsAliasedType(String recordType) {
        return "(component "
                + "(type $R "
                + recordType
                + ") "
                + "(instance $i (export \"x\" (type $R))) "
                + "(alias export $i \"x\" (type $A)) "
                + "(export \"t\" (type $A)) "
                + "(core module $m (func (export \"g\") (result i32) (i32.const 7))) "
                + "(core instance $mi (instantiate $m)) "
                + "(func (export \"f\") (result u32) (canon lift (core func $mi \"g\"))))";
    }

    /** The same for the function index space. */
    @Test
    public void aFunctionReachedThroughAnExportAliasIsCompared() {
        String provider =
                "(component "
                        + "(core module $m (func (export \"g\") (result i32) (i32.const 7))) "
                        + "(core instance $mi (instantiate $m)) "
                        + "(func $g (result u32) (canon lift (core func $mi \"g\"))) "
                        + "(instance $i (export \"g\" (func $g))) "
                        + "(alias export $i \"g\" (func $h)) "
                        + "(export \"f\" (func $h)))";
        assertNotNull(link(DECLARES_F, provider));
    }

    private static final String DECLARES_RESOURCE = "/component-type/declared-resource-export.wat";

    private static final String LIFTS =
            "(core module $m (func (export \"g\") (result i32) (i32.const 1))) "
                    + "(core instance $mi (instantiate $m)) ";

    /**
     * A resource declared by a component type has no identity until something instantiates it, so
     * the two sides are related by the {@code type} export they share a name on, and the handle in
     * {@code make} is compared through that.
     */
    @Test
    public void aComponentWhoseResourceExportMatchesLinks() {
        String provider =
                "(component (type $R (resource (rep i32))) (export $R2 \"r\" (type $R)) "
                        + LIFTS
                        + "(func (export \"make\") (result (own $R2)) "
                        + "(canon lift (core func $mi \"g\"))))";
        assertNotNull(link(DECLARES_RESOURCE, provider));
    }

    /**
     * The case no validator can reach. This component is perfectly valid on its own, and wrong only
     * against the type under which it is supplied. It exports one resource as {@code r} and hands
     * back a handle to a different one.
     */
    @Test
    public void aComponentReturningADifferentResourceIsRejected() {
        String provider =
                "(component (type $R1 (resource (rep i32))) (type $R2 (resource (rep i32))) "
                        + "(export $E1 \"r\" (type $R1)) (export $E2 \"other\" (type $R2)) "
                        + LIFTS
                        + "(func (export \"make\") (result (own $E2)) "
                        + "(canon lift (core func $mi \"g\"))))";
        var thrown = assertThrows(LinkageException.class, () -> link(DECLARES_RESOURCE, provider));
        assertTrue(
                thrown.getMessage().contains("make"),
                "expected the mismatched export to be named, got: " + thrown.getMessage());
    }

    /** A plain type cannot stand in where the component type declares a resource. */
    @Test
    public void aComponentExportingAPlainTypeWhereAResourceIsDeclaredIsRejected() {
        String provider =
                "(component (type $T u32) (export \"r\" (type $T)) "
                        + LIFTS
                        + "(func (export \"make\") (result u32) "
                        + "(canon lift (core func $mi \"g\"))))";
        assertThrows(LinkageException.class, () -> link(DECLARES_RESOURCE, provider));
    }

    private static final String DECLARES_TWO_RESOURCES =
            "/component-type/declared-two-resources.wat";

    /**
     * Two resource declarations in a component type are two variables, not an assertion that they
     * differ, so one resource may satisfy both. A validator accepts this, so refusing it here would
     * reject a working component.
     */
    @Test
    public void oneResourceMaySatisfyTwoDeclaredResourceExports() {
        String provider =
                "(component (type $R (resource (rep i32))) "
                        + "(export \"r\" (type $R)) (export \"s\" (type $R)))";
        assertNotNull(link(DECLARES_TWO_RESOURCES, provider));
    }

    private static final String DECLARES_TIED_RESOURCES =
            "/component-type/declared-tied-resources.wat";

    /** The direction that <em>is</em> required: an {@code eq} bound says these are one type. */
    @Test
    public void twoDifferentResourcesCannotSatisfyOneTiedDeclaration() {
        String provider =
                "(component (type $R1 (resource (rep i32))) (type $R2 (resource (rep i32))) "
                        + "(export \"r\" (type $R1)) (export \"s\" (type $R2)))";
        var thrown =
                assertThrows(LinkageException.class, () -> link(DECLARES_TIED_RESOURCES, provider));
        assertTrue(
                thrown.getMessage().contains("\"s\""),
                "expected the tied export to be named, got: " + thrown.getMessage());
    }

    /** The same declaration, satisfied by one resource under both names. */
    @Test
    public void oneResourceSatisfiesATiedDeclaration() {
        String provider =
                "(component (type $R (resource (rep i32))) "
                        + "(export \"r\" (type $R)) (export \"s\" (type $R)))";
        assertNotNull(link(DECLARES_TIED_RESOURCES, provider));
    }

    private static final String DECLARES_INSTANCE = "/component-type/declared-instance-export.wat";

    /** A component exporting an instance that groups {@code extra} under the name {@code i}. */
    private static String exportsInstance(String result, String extra) {
        String core = "u64".equals(result) ? "i64" : "i32";
        return "(component "
                + "(core module $m (func (export \"g\") (result "
                + core
                + ") ("
                + core
                + ".const 7))) "
                + "(core instance $mi (instantiate $m)) "
                + "(func $f (result "
                + result
                + ") (canon lift (core func $mi \"g\"))) "
                + "(instance $i (export \"f\" (func $f))"
                + extra
                + ") "
                + "(export \"i\" (instance $i)))";
    }

    /**
     * An instance nested inside a component type is the covariant half again, one level down: the
     * instance must export everything named, and may export more.
     */
    @Test
    public void aComponentWhoseInstanceExportMatchesLinks() {
        assertNotNull(link(DECLARES_INSTANCE, exportsInstance("u32", "")));
    }

    @Test
    public void aNestedInstanceExportingMoreLinks() {
        assertNotNull(link(DECLARES_INSTANCE, exportsInstance("u32", " (export \"g\" (func $f))")));
    }

    @Test
    public void aNestedInstanceMissingTheDeclaredExportIsRejected() {
        String provider =
                "(component "
                        + "(core module $m (func (export \"g\") (result i32) (i32.const 7))) "
                        + "(core instance $mi (instantiate $m)) "
                        + "(func $f (result u32) (canon lift (core func $mi \"g\"))) "
                        + "(instance $i (export \"other\" (func $f))) "
                        + "(export \"i\" (instance $i)))";
        var thrown = assertThrows(LinkageException.class, () -> link(DECLARES_INSTANCE, provider));
        assertTrue(
                thrown.getMessage().contains("\"f\""),
                "expected the missing export to be named, got: " + thrown.getMessage());
    }

    @Test
    public void aNestedInstanceExportOfTheWrongTypeIsRejected() {
        var thrown =
                assertThrows(
                        LinkageException.class,
                        () -> link(DECLARES_INSTANCE, exportsInstance("u64", "")));
        assertTrue(
                thrown.getMessage().contains("\"f\""),
                "expected the mismatched export to be named, got: " + thrown.getMessage());
    }

    private static final String DECLARES_NESTED_COMPONENT =
            "/component-type/declared-nested-component.wat";

    /**
     * A component reached two levels down - the declared type names an instance, which names a
     * component. A root component may not export a component directly, but nothing stops it
     * exporting an instance that holds one.
     */
    private static String exportsComponentInInstance(String leafExports) {
        return "(component (component $Leaf "
                + "(core module $m (func (export \"g\") (result i32) (i32.const 7))) "
                + "(core instance $mi (instantiate $m)) "
                + leafExports
                + ") "
                + "(instance $i (export \"c\" (component $Leaf))) "
                + "(export \"i\" (instance $i)))";
    }

    @Test
    public void aComponentNestedInsideAnInstanceExportMatchesLinks() {
        String leaf = "(func (export \"f\") (result u32) (canon lift (core func $mi \"g\")))";
        assertNotNull(link(DECLARES_NESTED_COMPONENT, exportsComponentInInstance(leaf)));
    }

    @Test
    public void aNestedComponentMissingADeclaredExportIsRejected() {
        String leaf = "(func (export \"other\") (result u32) (canon lift (core func $mi \"g\")))";
        var thrown =
                assertThrows(
                        LinkageException.class,
                        () -> link(DECLARES_NESTED_COMPONENT, exportsComponentInInstance(leaf)));
        assertTrue(
                thrown.getMessage().contains("\"f\""),
                "expected the missing export to be named, got: " + thrown.getMessage());
    }

    @Test
    public void aNestedComponentWithAnUndeclaredImportIsRejected() {
        String leaf =
                "(import \"dep\" (func)) "
                        + "(func (export \"f\") (result u32) (canon lift (core func $mi \"g\")))";
        var thrown =
                assertThrows(
                        LinkageException.class,
                        () -> link(DECLARES_NESTED_COMPONENT, exportsComponentInInstance(leaf)));
        assertTrue(
                thrown.getMessage().contains("\"dep\""),
                "expected the undeclared import to be named, got: " + thrown.getMessage());
    }

    /** Instantiates {@code consumer}, with the host exporting {@code componentWat} by name. */
    private static ComponentInstance link(String consumerResource, String componentWat) {
        var store = new ComponentStore();
        var host = ComponentInstance.builder(store);
        host.addExport(
                "a-component",
                new ComponentClosure(TestComponents.parse(Wat2Wasm.parse(componentWat)), null));
        return ComponentLinker.builder()
                .build()
                .instantiate(store, consumer(consumerResource), Map.of("host", host.build()));
    }

    private static WasmComponent consumer(String resourcePath) {
        try (InputStream is = ComponentTypeMatchingTests.class.getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Resource not found: " + resourcePath);
            return TestComponents.fromWat(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
