package io.roastedroot.typewire.bindgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.typewire.parser.ComponentParser;
import io.roastedroot.typewire.tools.WitParser;
import io.roastedroot.typewire.types.ComponentType;
import io.roastedroot.typewire.types.Export;
import io.roastedroot.typewire.types.ExportSection;
import io.roastedroot.typewire.types.Section;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.TypeSection;
import io.roastedroot.typewire.types.WasmComponent;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The parser has to accept what the WIT encoder produces, since that pairing is the whole route
 * from WIT text to something bindgen can read.
 *
 * <p>These fix the shape the generator walks. A WIT package encodes as a component whose exports
 * name the package's items, and a world arrives wrapped, as an exported component type holding one
 * export that names the world under its fully qualified id.
 */
public class WitBinaryTests {

    private static final String HELLO_WORLD =
            "package my:project;\n"
                    + "world hello-world {\n"
                    + "  import name: func() -> string;\n"
                    + "  export greet: func();\n"
                    + "}\n";

    @Test
    public void anEncodedPackageParses() {
        assertNotNull(parse(HELLO_WORLD));
    }

    /** Every top-level item of the package is exported under its WIT name. */
    @Test
    public void aWorldIsExportedUnderItsName() {
        List<String> names =
                exports(parse(HELLO_WORLD)).stream().map(Export::name).collect(Collectors.toList());

        assertEquals(List.of("hello-world"), names);
    }

    /**
     * The world's own imports and exports sit two component types down, one for the package item
     * and one for the world.
     */
    @Test
    public void aWorldCarriesItsImportsAndExports() {
        WasmComponent component = parse(HELLO_WORLD);
        Export item = exports(component).get(0);

        Type wrapper = types(component).get((int) item.sortIdx().idx());
        assertNotNull(wrapper.componentType(), "the package item is a component type");

        ComponentType world = null;
        String qualifiedName = null;
        for (var decl : wrapper.componentType().getComponentDecls()) {
            var instanceDecl = decl.instanceDecl();
            if (instanceDecl == null) {
                continue;
            }
            if (instanceDecl.type() != null && instanceDecl.type().componentType() != null) {
                world = instanceDecl.type().componentType();
            } else if (instanceDecl.exportDecl() != null) {
                qualifiedName = instanceDecl.exportDecl().name();
            }
        }

        assertEquals("my:project/hello-world", qualifiedName);
        assertNotNull(world, "the wrapper holds the world as a component type");

        var imported = names(world, true);
        var exported = names(world, false);
        assertEquals(List.of("name"), imported);
        assertEquals(List.of("greet"), exported);
    }

    /** A function type is reachable from the declaration naming it, by index into the same scope. */
    @Test
    public void anImportedFunctionTypeResolvesWithinTheWorld() {
        WasmComponent component = parse(HELLO_WORLD);
        ComponentType world = worldOf(component);

        List<Type> declared =
                world.getComponentDecls().stream()
                        .filter(d -> d.instanceDecl() != null && d.instanceDecl().type() != null)
                        .map(d -> d.instanceDecl().type())
                        .collect(Collectors.toList());

        var name =
                world.getComponentDecls().stream()
                        .filter(d -> d.importDecl() != null)
                        .findFirst()
                        .orElseThrow();

        Type nameType = declared.get((int) name.importDecl().externDesc().typeIdx());
        assertNotNull(nameType.funcType());
        assertTrue(nameType.funcType().params().isEmpty());
        assertEquals("STRING", nameType.funcType().result().primValType().kind().name());
    }

    private static ComponentType worldOf(WasmComponent component) {
        Type wrapper = types(component).get((int) exports(component).get(0).sortIdx().idx());
        return wrapper.componentType().getComponentDecls().stream()
                .map(d -> d.instanceDecl())
                .filter(d -> d != null && d.type() != null && d.type().componentType() != null)
                .map(d -> d.type().componentType())
                .findFirst()
                .orElseThrow();
    }

    private static List<String> names(ComponentType world, boolean imported) {
        return world.getComponentDecls().stream()
                .map(
                        d ->
                                imported
                                        ? (d.importDecl() == null ? null : d.importDecl().name())
                                        : (d.instanceDecl() == null
                                                        || d.instanceDecl().exportDecl() == null
                                                ? null
                                                : d.instanceDecl().exportDecl().name()))
                .filter(n -> n != null)
                .collect(Collectors.toList());
    }

    private static List<Export> exports(WasmComponent component) {
        for (Section section : component.sections()) {
            if (section instanceof ExportSection) {
                return ((ExportSection) section).exports();
            }
        }
        throw new AssertionError("no export section");
    }

    private static List<Type> types(WasmComponent component) {
        for (Section section : component.sections()) {
            if (section instanceof TypeSection) {
                return ((TypeSection) section).types();
            }
        }
        throw new AssertionError("no type section");
    }

    private static WasmComponent parse(String wit) {
        byte[] encoded = WitParser.encode(wit);
        return ComponentParser.builder()
                .withValidation(true)
                .withValidator(WitValidator.INSTANCE)
                .build()
                .parse(() -> new ByteArrayInputStream(encoded));
    }
}
