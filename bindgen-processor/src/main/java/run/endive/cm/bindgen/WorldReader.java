package run.endive.cm.bindgen;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import run.endive.cm.parser.ComponentParser;
import run.endive.cm.tools.WitParser;
import run.endive.cm.types.ComponentDecl;
import run.endive.cm.types.ComponentType;
import run.endive.cm.types.Export;
import run.endive.cm.types.ExportSection;
import run.endive.cm.types.ExternDesc;
import run.endive.cm.types.ImportDecl;
import run.endive.cm.types.InstanceDecl;
import run.endive.cm.types.InstanceType;
import run.endive.cm.types.Section;
import run.endive.cm.types.Sort;
import run.endive.cm.types.Type;
import run.endive.cm.types.TypeBound;
import run.endive.cm.types.TypeSection;
import run.endive.cm.types.WasmComponent;

/**
 * Reads a world out of WIT text, by way of the binary encoding.
 *
 * <p>A WIT package encodes as a component whose exports name the package's items. A world arrives
 * wrapped twice over, as an exported component type holding one export that names the world under
 * its fully qualified id, so reaching the world's own declarations means stepping through both.
 *
 * <p>Each nesting level restarts its type numbering, so an index in a declaration counts the type
 * declarations preceding it in the same list rather than naming a slot in one flat space.
 */
final class WorldReader {

    private WorldReader() {}

    /**
     * @param world the world to read, or empty when the package declares exactly one
     */
    static WitWorld read(String wit, String world) {
        byte[] encoded;
        try {
            encoded = WitParser.encode(wit);
        } catch (RuntimeException e) {
            throw new BindgenException("WIT could not be encoded: " + e.getMessage(), e);
        }

        WasmComponent pkg =
                ComponentParser.builder()
                        .withValidation(true)
                        .withValidator(WitValidator.INSTANCE)
                        .build()
                        .parse(() -> new ByteArrayInputStream(encoded));

        Export item = packageItem(pkg, world);
        Type wrapper = typeSpace(pkg).get((int) item.sortIdx().idx());
        if (wrapper.componentType() == null) {
            throw new BindgenException("\"" + item.name() + "\" is not a world");
        }
        return readWorld(item.name(), wrapper.componentType());
    }

    /** The package's top-level item for {@code world}, or its only one when the name is empty. */
    private static Export packageItem(WasmComponent pkg, String world) {
        List<Export> exports = new ArrayList<>();
        for (Section section : pkg.sections()) {
            if (section instanceof ExportSection) {
                exports.addAll(((ExportSection) section).exports());
            }
        }
        if (world.isEmpty()) {
            if (exports.size() != 1) {
                throw new BindgenException(
                        "the package declares "
                                + exports.size()
                                + " items, so a world has to be named. Found "
                                + names(exports));
            }
            return exports.get(0);
        }
        return exports.stream()
                .filter(e -> e.name().equals(world))
                .findFirst()
                .orElseThrow(
                        () ->
                                new BindgenException(
                                        "world \""
                                                + world
                                                + "\" was not found. The package declares "
                                                + names(exports)));
    }

    /**
     * The wrapper holds the world as a type declaration and then exports it under its qualified id.
     */
    private static WitWorld readWorld(String name, ComponentType wrapper) {
        WitScope declared = new WitScope();
        String qualifiedName = null;
        ComponentType world = null;

        for (ComponentDecl decl : wrapper.getComponentDecls()) {
            InstanceDecl instanceDecl = decl.instanceDecl();
            if (instanceDecl == null) {
                continue;
            }
            if (instanceDecl.kind() == InstanceDecl.Kind.TYPE) {
                declared.add(instanceDecl.type());
            } else if (instanceDecl.exportDecl() != null) {
                ExternDesc desc = instanceDecl.exportDecl().externDesc();
                if (desc.kind() != ExternDesc.Kind.COMPONENT) {
                    throw new BindgenException(
                            "\"" + name + "\" is an interface rather than a world");
                }
                qualifiedName = instanceDecl.exportDecl().name();
                world = componentTypeAt(declared, desc, name);
            }
        }

        if (world == null) {
            throw new BindgenException("world \"" + name + "\" holds no declarations");
        }

        return build(name, qualifiedName, world);
    }

    /**
     * One walk of the world's declarations, filling every list. The type index space grows as the
     * walk goes, so an index in a declaration is resolved against what came before it.
     */
    private static WitWorld build(String name, String qualifiedName, ComponentType world) {
        WitScope declared = new WitScope();
        List<WitFunction> importedFunctions = new ArrayList<>();
        List<WitInterface> importedInterfaces = new ArrayList<>();
        List<WitFunction> exportedFunctions = new ArrayList<>();
        List<WitInterface> exportedInterfaces = new ArrayList<>();

        for (ComponentDecl decl : world.getComponentDecls()) {
            track(declared, decl);
            ImportDecl importDecl = decl.importDecl();
            if (importDecl != null) {
                collect(
                        declared,
                        importDecl.name(),
                        importDecl.externDesc(),
                        importedFunctions,
                        importedInterfaces);
                continue;
            }
            InstanceDecl instanceDecl = decl.instanceDecl();
            if (instanceDecl != null && instanceDecl.exportDecl() != null) {
                collect(
                        declared,
                        instanceDecl.exportDecl().name(),
                        instanceDecl.exportDecl().externDesc(),
                        exportedFunctions,
                        exportedInterfaces);
            }
        }
        return new WitWorld(
                name,
                qualifiedName,
                importedFunctions,
                importedInterfaces,
                exportedFunctions,
                exportedInterfaces);
    }

    /** A world reaches a function directly and an interface as an instance, both ways round. */
    private static void collect(
            WitScope declared,
            String name,
            ExternDesc desc,
            List<WitFunction> functions,
            List<WitInterface> interfaces) {
        if (desc.kind() == ExternDesc.Kind.INSTANCE) {
            interfaces.add(readInterface(name, instanceTypeAt(declared, desc, name)));
        } else {
            functions.add(function(declared, name, desc));
        }
    }

    /**
     * An interface's functions are the functions its instance type exports.
     *
     * <p>A resource is exported as a type rather than defined as one, and that export grows the
     * type index space just as a definition does, so it has to be counted or every index after it
     * names the wrong type.
     */
    private static WitInterface readInterface(String name, InstanceType type) {
        WitScope scope = new WitScope();
        scope.withOwner(simpleNameOf(name));
        List<WitFunction> functions = new ArrayList<>();
        List<WitType> types = new ArrayList<>();
        Map<String, ResourceFunctions> resources = new LinkedHashMap<>();

        for (InstanceDecl decl : type.getInstanceDecls()) {
            if (decl.kind() == InstanceDecl.Kind.TYPE) {
                scope.add(decl.type());
                continue;
            }
            if (decl.kind() == InstanceDecl.Kind.ALIAS) {
                throw new BindgenException(
                        "interface \""
                                + name
                                + "\" uses types from elsewhere, which is not yet supported");
            }
            if (decl.exportDecl() == null) {
                continue;
            }
            String exportName = decl.exportDecl().name();
            ExternDesc desc = decl.exportDecl().externDesc();
            if (desc.kind() == ExternDesc.Kind.TYPE) {
                declareType(scope, types, resources, exportName, desc);
                continue;
            }
            WitFunction function = function(scope, exportName, desc);
            ResourceFunctions owner = ownerOf(resources, exportName);
            if (owner == null) {
                functions.add(function);
            } else {
                owner.add(exportName, function);
            }
        }

        List<WitResource> read = new ArrayList<>();
        for (ResourceFunctions resource : resources.values()) {
            read.add(resource.toResource());
        }
        return new WitInterface(name, functions, read, types, scope);
    }

    /**
     * A type an interface exports takes an index of its own, whether it names a resource or a type
     * defined just above it, so both have to be recorded or every index after them is wrong.
     *
     * <p>A {@code sub} bound is a resource, which has no structure to read. An {@code eq} bound
     * names a type the interface defined, and that is where a record or an enum gets its name.
     */
    private static void declareType(
            WitScope scope,
            List<WitType> types,
            Map<String, ResourceFunctions> resources,
            String exportName,
            ExternDesc desc) {
        TypeBound bound = desc.typeBound();
        if (bound == null || bound.kind() != TypeBound.Kind.EQ) {
            int index = scope.add(null);
            resources.computeIfAbsent(exportName, name -> new ResourceFunctions(name, index));
            return;
        }
        Type named = scope.at((int) bound.typeIdx());
        scope.add(named, exportName);
        if (named != null && named.defValType() != null) {
            types.add(new WitType(exportName, named.defValType()));
        }
    }

    /** An interface's own name, with any package qualification dropped. */
    private static String simpleNameOf(String name) {
        int slash = name.lastIndexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
    }

    /** The resource owning a {@code [constructor]}, {@code [method]} or {@code [static]} name. */
    private static ResourceFunctions ownerOf(
            Map<String, ResourceFunctions> resources, String exportName) {
        int close = exportName.indexOf(']');
        if (!exportName.startsWith("[") || close < 0) {
            return null;
        }
        String target = exportName.substring(close + 1);
        int dot = target.indexOf('.');
        String resourceName = dot < 0 ? target : target.substring(0, dot);
        ResourceFunctions owner = resources.get(resourceName);
        if (owner == null) {
            throw new BindgenException(
                    "\""
                            + exportName
                            + "\" names resource \""
                            + resourceName
                            + "\", which was never declared");
        }
        return owner;
    }

    /** Gathers a resource's functions as they are met, since they arrive as separate exports. */
    private static final class ResourceFunctions {

        private final String name;
        private final int typeIndex;
        private WitFunction constructor;
        private final List<WitFunction> methods = new ArrayList<>();
        private final List<WitFunction> statics = new ArrayList<>();

        ResourceFunctions(String name, int typeIndex) {
            this.name = name;
            this.typeIndex = typeIndex;
        }

        void add(String exportName, WitFunction function) {
            if (exportName.startsWith("[constructor]")) {
                constructor = new WitFunction(name, function.type(), function.scope());
            } else if (exportName.startsWith("[method]")) {
                methods.add(
                        new WitFunction(memberName(exportName), function.type(), function.scope()));
            } else if (exportName.startsWith("[static]")) {
                statics.add(
                        new WitFunction(memberName(exportName), function.type(), function.scope()));
            } else {
                throw new BindgenException(
                        "\""
                                + exportName
                                + "\" is neither a constructor, a method nor a static function,"
                                + " which is not yet supported");
            }
        }

        /** {@code [method]file.get-name} names the method {@code get-name}. */
        private static String memberName(String exportName) {
            return exportName.substring(exportName.indexOf('.') + 1);
        }

        WitResource toResource() {
            return new WitResource(name, typeIndex, constructor, methods, statics);
        }
    }

    /**
     * Grows the type index space by whatever {@code decl} contributes to it. An alias would also
     * grow it, and silently mis-numbering every index after one is worse than refusing to read it.
     */
    private static void track(WitScope declared, ComponentDecl decl) {
        InstanceDecl instanceDecl = decl.instanceDecl();
        if (instanceDecl == null) {
            return;
        }
        if (instanceDecl.kind() == InstanceDecl.Kind.TYPE) {
            declared.add(instanceDecl.type());
        } else if (instanceDecl.kind() == InstanceDecl.Kind.ALIAS) {
            Sort sort = instanceDecl.alias().sort();
            if (sort != null && sort.kind() == Sort.Kind.TYPE) {
                throw new BindgenException(
                        "a world using types from an interface is not yet supported");
            }
        }
    }

    private static WitFunction function(WitScope declared, String name, ExternDesc desc) {
        if (desc.kind() != ExternDesc.Kind.FUNC) {
            throw new BindgenException(
                    "\""
                            + name
                            + "\" is "
                            + describe(desc.kind())
                            + ", and only functions are supported so far");
        }
        Type type = typeAt(declared, desc, name);
        if (type == null || type.funcType() == null) {
            throw new BindgenException("\"" + name + "\" does not name a function type");
        }
        return new WitFunction(name, type.funcType(), declared);
    }

    private static InstanceType instanceTypeAt(WitScope declared, ExternDesc desc, String name) {
        Type type = typeAt(declared, desc, name);
        if (type.instanceType() == null) {
            throw new BindgenException("\"" + name + "\" does not name an interface");
        }
        return type.instanceType();
    }

    private static ComponentType componentTypeAt(WitScope declared, ExternDesc desc, String name) {
        Type type = typeAt(declared, desc, name);
        if (type.componentType() == null) {
            throw new BindgenException("\"" + name + "\" does not name a component type");
        }
        return type.componentType();
    }

    private static Type typeAt(WitScope declared, ExternDesc desc, String name) {
        try {
            return declared.at((int) desc.typeIdx());
        } catch (BindgenException e) {
            throw new BindgenException("\"" + name + "\" names a type that was never declared", e);
        }
    }

    private static String describe(ExternDesc.Kind kind) {
        switch (kind) {
            case INSTANCE:
                return "an interface";
            case TYPE:
                return "a type";
            case VALUE:
                return "a value";
            case COMPONENT:
                return "a component";
            case CORE_MODULE:
                return "a core module";
            default:
                return "not a function";
        }
    }

    /**
     * The package component's type index space.
     *
     * <p>It grows both by the types the package defines and by the exports naming them, so an
     * exported world sits at a higher index than its position among the defined types. Walking the
     * sections in order is what puts an index on the type it actually names.
     */
    private static List<Type> typeSpace(WasmComponent pkg) {
        List<Type> space = new ArrayList<>();
        for (Section section : pkg.sections()) {
            if (section instanceof TypeSection) {
                space.addAll(((TypeSection) section).types());
            } else if (section instanceof ExportSection) {
                for (Export export : ((ExportSection) section).exports()) {
                    if (export.sortIdx().sort().kind() == Sort.Kind.TYPE) {
                        space.add(space.get((int) export.sortIdx().idx()));
                    }
                }
            }
        }
        return space;
    }

    private static String names(List<Export> exports) {
        return exports.stream().map(Export::name).collect(Collectors.joining(", ", "[", "]"));
    }
}
