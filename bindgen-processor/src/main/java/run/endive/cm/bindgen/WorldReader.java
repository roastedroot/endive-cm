package run.endive.cm.bindgen;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import run.endive.cm.parser.ComponentParser;
import run.endive.cm.tools.WitParser;
import run.endive.cm.types.Alias;
import run.endive.cm.types.ComponentDecl;
import run.endive.cm.types.ComponentType;
import run.endive.cm.types.DefValType;
import run.endive.cm.types.Export;
import run.endive.cm.types.ExportAlias;
import run.endive.cm.types.ExportSection;
import run.endive.cm.types.ExternDesc;
import run.endive.cm.types.ImportDecl;
import run.endive.cm.types.InstanceDecl;
import run.endive.cm.types.InstanceType;
import run.endive.cm.types.OuterAlias;
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
        return read(() -> WitParser.encode(wit), world);
    }

    /**
     * @param wit a WIT file, or a package directory whose dependencies sit under {@code deps}
     * @param world the world to read, or empty when the package declares exactly one
     */
    static WitWorld read(Path wit, String world) {
        return read(() -> WitParser.encode(wit), world);
    }

    private static WitWorld read(Supplier<byte[]> encoder, String world) {
        byte[] encoded;
        try {
            encoded = encoder.get();
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
     * One walk of the world's declarations, filling every list. The type and instance index spaces
     * grow as the walk goes, so an index in a declaration is resolved against what came before it.
     */
    private static WitWorld build(String name, String qualifiedName, ComponentType world) {
        WitScope declared = new WitScope();
        List<WitInterface> instances = new ArrayList<>();
        List<WitFunction> importedFunctions = new ArrayList<>();
        List<WitInterface> importedInterfaces = new ArrayList<>();
        List<WitFunction> exportedFunctions = new ArrayList<>();
        List<WitInterface> exportedInterfaces = new ArrayList<>();

        for (ComponentDecl decl : world.getComponentDecls()) {
            ImportDecl importDecl = decl.importDecl();
            if (importDecl != null) {
                if (importDecl.externDesc().kind() == ExternDesc.Kind.TYPE) {
                    declareWorldType(name, declared, importDecl.name(), importDecl.externDesc());
                    continue;
                }
                collect(
                        declared,
                        instances,
                        importDecl.name(),
                        importDecl.externDesc(),
                        importedFunctions,
                        importedInterfaces,
                        false);
                continue;
            }
            InstanceDecl instanceDecl = decl.instanceDecl();
            if (instanceDecl == null) {
                continue;
            }
            if (instanceDecl.kind() == InstanceDecl.Kind.TYPE) {
                declared.add(instanceDecl.type());
            } else if (instanceDecl.kind() == InstanceDecl.Kind.ALIAS) {
                alias(declared, instances, instanceDecl.alias());
            } else if (instanceDecl.exportDecl() != null) {
                collect(
                        declared,
                        instances,
                        instanceDecl.exportDecl().name(),
                        instanceDecl.exportDecl().externDesc(),
                        exportedFunctions,
                        exportedInterfaces,
                        true);
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

    /**
     * A world reaches a function directly and an interface as an instance, both ways round. An
     * interface also takes the next slot in the instance index space, which is how a later alias
     * names it.
     */
    private static void collect(
            WitScope declared,
            List<WitInterface> instances,
            String name,
            ExternDesc desc,
            List<WitFunction> functions,
            List<WitInterface> interfaces,
            boolean exported) {
        if (desc.kind() == ExternDesc.Kind.INSTANCE) {
            WitInterface read = readInterface(name, instanceTypeAt(declared, desc, name), declared);
            if (exported) {
                requireOwnTypes(read);
            }
            instances.add(read);
            interfaces.add(read);
        } else {
            functions.add(function(declared, name, desc));
        }
    }

    /**
     * A world's {@code use} arrives as an alias of a type an imported interface exports. It takes a
     * slot in the world's type index space and stays declared by that interface.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#alias-definitions">Explainer.md, alias definitions</a>
     */
    private static void alias(WitScope declared, List<WitInterface> instances, Alias alias) {
        if (alias.kind() != Alias.Kind.EXPORT || alias.sort().kind() != Sort.Kind.TYPE) {
            throw new BindgenException(
                    "a world alias of kind "
                            + alias.kind().name().toLowerCase()
                            + " is not yet supported");
        }
        ExportAlias export = (ExportAlias) alias;
        int instance = (int) export.instanceIdx();
        if (instance < 0 || instance >= instances.size()) {
            throw new BindgenException(
                    "type \"" + export.name() + "\" is aliased from an instance never declared");
        }
        WitScope from = instances.get(instance).scope();
        int index = from.indexOf(export.name());
        if (index < 0) {
            throw new BindgenException(
                    "\""
                            + instances.get(instance).name()
                            + "\" exports no type named \""
                            + export.name()
                            + "\"");
        }
        declared.alias(from, index);
    }

    /**
     * A type a world names arrives as an imported type. One bound to an alias is a {@code use},
     * which the world can name like any type the interface declares. One the world declares in its
     * own right has no Java package to be generated into, so it is refused.
     */
    private static void declareWorldType(
            String world, WitScope declared, String name, ExternDesc desc) {
        TypeBound bound = desc.typeBound();
        if (bound != null && bound.kind() == TypeBound.Kind.EQ) {
            int index = (int) bound.typeIdx();
            if (declared.isUsed(index)) {
                requireUsable(declared, index, "world \"" + world + "\"", name, false);
                declared.alias(declared, index, name);
                return;
            }
        }
        throw new BindgenException(
                "world \""
                        + world
                        + "\" declares type \""
                        + name
                        + "\" in its own right, which is not yet supported");
    }

    /**
     * The guest side of an interface names its types through its own package, so an exported
     * interface using a type from elsewhere is refused rather than generated wrongly.
     */
    private static void requireOwnTypes(WitInterface exported) {
        WitScope scope = exported.scope();
        for (int i = 0; i < scope.size(); i++) {
            if (scope.isUsed(i)) {
                throw new BindgenException(
                        "exported interface \""
                                + exported.name()
                                + "\" uses types from elsewhere, which is not yet supported");
            }
        }
    }

    /**
     * An interface's functions are the functions its instance type exports.
     *
     * <p>A resource is exported as a type rather than defined as one, and that export grows the
     * type index space just as a definition does, so it has to be counted or every index after it
     * names the wrong type.
     *
     * @param world the enclosing world's type index space, which an {@code alias outer} reaches
     */
    private static WitInterface readInterface(String name, InstanceType type, WitScope world) {
        WitScope scope = new WitScope();
        scope.withOwner(Names.simpleName(name));
        List<WitFunction> functions = new ArrayList<>();
        List<WitType> types = new ArrayList<>();
        Map<String, ResourceFunctions> resources = new LinkedHashMap<>();

        for (InstanceDecl decl : type.getInstanceDecls()) {
            if (decl.kind() == InstanceDecl.Kind.TYPE) {
                scope.add(decl.type());
                continue;
            }
            if (decl.kind() == InstanceDecl.Kind.ALIAS) {
                aliasOuter(name, scope, world, decl.alias());
                continue;
            }
            if (decl.exportDecl() == null) {
                continue;
            }
            String exportName = decl.exportDecl().name();
            ExternDesc desc = decl.exportDecl().externDesc();
            if (desc.kind() == ExternDesc.Kind.TYPE) {
                declareType(name, scope, types, resources, exportName, desc);
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
     * An interface's {@code use} arrives as an {@code alias outer} reaching the enclosing world,
     * which aliased the type from the interface declaring it.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#alias-definitions">Explainer.md, alias definitions</a>
     */
    private static void aliasOuter(String name, WitScope scope, WitScope world, Alias alias) {
        if (alias.kind() != Alias.Kind.OUTER
                || alias.sort().kind() != Sort.Kind.TYPE
                || ((OuterAlias) alias).count() != 1) {
            throw new BindgenException(
                    "interface \""
                            + name
                            + "\" holds an alias other than a type from its world, which is not"
                            + " yet supported");
        }
        scope.alias(world, (int) ((OuterAlias) alias).index());
    }

    /**
     * A type an interface exports takes an index of its own, whether it names a resource or a type
     * defined just above it, so both have to be recorded or every index after them is wrong.
     *
     * <p>A {@code sub} bound is a resource, which has no structure to read. An {@code eq} bound
     * names a type the interface defined, and that is where a record or an enum gets its name. An
     * {@code eq} bound naming an alias is a {@code use}, and the type stays declared elsewhere.
     */
    private static void declareType(
            String iface,
            WitScope scope,
            List<WitType> types,
            Map<String, ResourceFunctions> resources,
            String exportName,
            ExternDesc desc) {
        TypeBound bound = desc.typeBound();
        if (bound == null || bound.kind() != TypeBound.Kind.EQ) {
            int index = scope.add(null, exportName);
            resources.computeIfAbsent(exportName, name -> new ResourceFunctions(name, index));
            return;
        }
        int boundIndex = (int) bound.typeIdx();
        if (scope.isUsed(boundIndex)) {
            requireUsable(scope, boundIndex, "interface \"" + iface + "\"", exportName, true);
            scope.alias(scope, boundIndex, exportName);
            return;
        }
        Type named = scope.at(boundIndex);
        scope.add(named, exportName);
        if (named != null && named.defValType() != null) {
            types.add(new WitType(exportName, named.defValType()));
        }
    }

    /**
     * A used {@code result} generates its exception into the declaring interface's package, which
     * is not wired yet, so it is refused by name. A used resource converts through the declaring
     * interface's {@code Handles}, which only an interface's wiring reaches, so a world using one is
     * refused too.
     *
     * @param resources whether {@code user} may use a resource
     */
    private static void requireUsable(
            WitScope scope, int index, String user, String name, boolean resources) {
        Type used = scope.at(index);
        String declaredBy = scope.declaringScope(index).owner();
        if (used == null && !resources) {
            throw new BindgenException(
                    user
                            + " uses resource \""
                            + name
                            + "\" from \""
                            + declaredBy
                            + "\", which is not yet supported");
        }
        if (used != null
                && used.defValType() != null
                && used.defValType().kind() == DefValType.Kind.RESULT) {
            throw new BindgenException(
                    user
                            + " uses result type \""
                            + name
                            + "\" from \""
                            + declaredBy
                            + "\", which is not yet supported");
        }
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
                methods.add(member(exportName, function));
            } else if (exportName.startsWith("[static]")) {
                statics.add(member(exportName, function));
            } else {
                throw new BindgenException(
                        "\""
                                + exportName
                                + "\" is neither a constructor, a method nor a static function,"
                                + " which is not yet supported");
            }
        }

        /** {@code [method]file.get-name} names the method {@code get-name} of {@code file}. */
        private WitFunction member(String exportName, WitFunction function) {
            String memberName = exportName.substring(exportName.indexOf('.') + 1);
            return new WitFunction(name, memberName, function.type(), function.scope());
        }

        WitResource toResource() {
            return new WitResource(name, typeIndex, constructor, methods, statics);
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
