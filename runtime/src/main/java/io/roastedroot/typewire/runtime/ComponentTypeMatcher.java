package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.types.Alias;
import io.roastedroot.typewire.types.AliasSection;
import io.roastedroot.typewire.types.CanonSection;
import io.roastedroot.typewire.types.ComponentDecl;
import io.roastedroot.typewire.types.ComponentSection;
import io.roastedroot.typewire.types.ComponentType;
import io.roastedroot.typewire.types.Export;
import io.roastedroot.typewire.types.ExportAlias;
import io.roastedroot.typewire.types.ExportDecl;
import io.roastedroot.typewire.types.ExportSection;
import io.roastedroot.typewire.types.ExternDesc;
import io.roastedroot.typewire.types.FuncType;
import io.roastedroot.typewire.types.Import;
import io.roastedroot.typewire.types.ImportDecl;
import io.roastedroot.typewire.types.ImportSection;
import io.roastedroot.typewire.types.InlineExport;
import io.roastedroot.typewire.types.InlineExportInstanceExpr;
import io.roastedroot.typewire.types.Instance;
import io.roastedroot.typewire.types.InstanceDecl;
import io.roastedroot.typewire.types.InstanceSection;
import io.roastedroot.typewire.types.InstanceType;
import io.roastedroot.typewire.types.ResolvedFuncType;
import io.roastedroot.typewire.types.ResolvedType;
import io.roastedroot.typewire.types.ResourceTypeId;
import io.roastedroot.typewire.types.Section;
import io.roastedroot.typewire.types.Sort;
import io.roastedroot.typewire.types.SortIdx;
import io.roastedroot.typewire.types.Type;
import io.roastedroot.typewire.types.TypeBound;
import io.roastedroot.typewire.types.TypeResolver;
import io.roastedroot.typewire.types.TypeSection;
import io.roastedroot.typewire.types.TypeSpace;
import io.roastedroot.typewire.types.ValType;
import io.roastedroot.typewire.types.WasmComponent;
import io.roastedroot.typewire.types.canon.CanonLift;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks an uninstantiated component against a {@code (component ...)} type under which it is
 * supplied.
 *
 * <p>A validator sees one binary at a time, so it settles this wherever a component instantiates a
 * child it defines. A component arriving from the host has never been compared against the
 * declaration to which it is held, and that comparison is made here.
 *
 * <p>A resource has no identity before instantiation. Each side introduces a stand-in per
 * declaration, and a matching type export relates one side's stand-in to the other's, which is what
 * lets handles be compared.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#type-definitions">Explainer.md, instance and component subtyping</a>
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Linking.md">Linking.md, why an imported child is checked like an inline one</a>
 */
final class ComponentTypeMatcher {

    private ComponentTypeMatcher() {}

    /**
     * Checks that {@code actual} can stand in for the component type {@code declared}.
     *
     * @param what how to name the thing being checked in a failure
     * @throws LinkageException if the component does not conform
     */
    static void requireSubtype(ComponentType declared, WasmComponent actual, String what) {
        matchSides(Side.ofComponentType(declared), Side.ofComponent(actual), what);
    }

    /**
     * Walks one side's declarations against the other's, in the order they were written.
     *
     * <p>Exports are covariant and imports are contravariant, so a nested instance or component
     * under an import is compared with its sides swapped. Everything else is compared for equality
     * in the same orientation, which keeps every resource relation recorded on {@code expected}.
     */
    private static void matchSides(Side expected, Side supplied, String what) {
        for (Decl decl : expected.decls) {
            if (decl.isImport) {
                Extern need = supplied.imports.get(decl.name);
                if (need == null) {
                    continue;
                }
                requireMatch(
                        decl.extern,
                        expected,
                        need,
                        supplied,
                        true,
                        what,
                        "import \"" + decl.name + "\"");
            } else {
                Extern got = supplied.exports.get(decl.name);
                if (got == null) {
                    throw new LinkageException(what + " is missing export \"" + decl.name + "\"");
                }
                requireMatch(
                        decl.extern,
                        expected,
                        got,
                        supplied,
                        false,
                        what,
                        "export \"" + decl.name + "\"");
            }
        }

        for (Map.Entry<String, Extern> entry : supplied.imports.entrySet()) {
            if (!expected.imports.containsKey(entry.getKey())) {
                throw new LinkageException(
                        what
                                + " imports \""
                                + entry.getKey()
                                + "\", which its declared component type does not provide");
            }
        }
    }

    /**
     * Compares one extern against another. Only instance and component types are subtypes. For an
     * export {@code actual} has to be a subtype of {@code expected}; for an import, which is
     * {@code contravariant}, it is the other way round.
     */
    private static void requireMatch(
            Extern expected,
            Side expectedSide,
            Extern actual,
            Side actualSide,
            boolean contravariant,
            String what,
            String which) {
        if (expected.kind != actual.kind) {
            throw new LinkageException(
                    what
                            + " declares "
                            + which
                            + " as "
                            + describe(expected.kind)
                            + " but it is "
                            + describe(actual.kind));
        }
        switch (expected.kind) {
            case FUNC:
                requireFuncMatch(
                        expected.funcType, expectedSide, actual.funcType, actualSide, what, which);
                return;
            case TYPE:
                requireTypeMatch(expected, expectedSide, actual, actualSide, what, which);
                return;
            case INSTANCE:
            case COMPONENT:
                if (expected.nested == null || actual.nested == null) {
                    throw new UnsupportedOperationException(
                            "comparing "
                                    + which
                                    + " of a component type is not yet supported ("
                                    + what
                                    + "): it is built in a way this pass does not follow");
                }
                if (contravariant) {
                    matchSides(actual.nested, expected.nested, what + ", " + which);
                } else {
                    matchSides(expected.nested, actual.nested, what + ", " + which);
                }
                return;
            default:
                throw new UnsupportedOperationException(
                        "comparing "
                                + describe(expected.kind)
                                + " inside a component type is not yet supported ("
                                + what
                                + ", "
                                + which
                                + ")");
        }
    }

    private static void requireFuncMatch(
            FuncType expected,
            Side expectedSide,
            FuncType actual,
            Side actualSide,
            String what,
            String which) {
        ResolvedFuncType e = resolve(expected, expectedSide, what, which);
        ResolvedFuncType a = resolve(actual, actualSide, what, which);
        if (!TypeMatcher.funcTypesMatch(e, a, true)) {
            throw new LinkageException(
                    what + " does not match on " + which + " - expected " + e + ", got " + a);
        }
    }

    /**
     * Compares the two definitions a {@code type} extern names, relating them where they are
     * resources.
     */
    private static void requireTypeMatch(
            Extern expected,
            Side expectedSide,
            Extern actual,
            Side actualSide,
            String what,
            String which) {
        boolean expectedIsResource = expected.token != null;
        boolean actualIsResource = actual.token != null;
        if (expectedIsResource != actualIsResource) {
            throw new LinkageException(
                    what
                            + " does not match on "
                            + which
                            + " - expected "
                            + (expectedIsResource ? "a resource type" : "a plain type")
                            + ", got "
                            + (actualIsResource ? "a resource type" : "a plain type"));
        }
        if (expectedIsResource) {
            expectedSide.relate(expected.token, actual.token, what, which);
            return;
        }
        requireResolvable(expected.type, what, which);
        requireResolvable(actual.type, what, which);
        if (expected.type.defValType() != null && actual.type.defValType() != null) {
            ResolvedType e = ResolvedType.of(expected.type.defValType(), expectedSide.space());
            ResolvedType a = ResolvedType.of(actual.type.defValType(), actualSide.space());
            requireHandlesResolved(e, what, which);
            requireHandlesResolved(a, what, which);
            if (!TypeMatcher.valTypesMatch(e, a)) {
                throw new LinkageException(
                        what + " does not match on " + which + " - expected " + e + ", got " + a);
            }
            return;
        }
        if (expected.type.funcType() != null && actual.type.funcType() != null) {
            requireFuncMatch(
                    expected.type.funcType(),
                    expectedSide,
                    actual.type.funcType(),
                    actualSide,
                    what,
                    which);
            return;
        }
        if (expected.type.instanceType() != null || expected.type.componentType() != null) {
            throw new UnsupportedOperationException(
                    "comparing "
                            + expected.type.simpleName()
                            + " types inside a component type is not yet supported ("
                            + what
                            + ", "
                            + which
                            + ")");
        }
        throw new LinkageException(
                what
                        + " does not match on "
                        + which
                        + " - expected "
                        + expected.type.simpleName()
                        + ", got "
                        + actual.type.simpleName());
    }

    /** Refuses a type this pass could not follow to a definition. */
    private static void requireResolvable(Type type, String what, String which) {
        if (type == null) {
            throw new UnsupportedOperationException(
                    "comparing "
                            + which
                            + " of a component type is not yet supported ("
                            + what
                            + "): it names a type aliased from outside the declaration, which"
                            + " this pass does not follow");
        }
    }

    /** Resolves a function type against the space it was written in. */
    private static ResolvedFuncType resolve(
            FuncType funcType, Side side, String what, String which) {
        if (funcType == null) {
            throw new LinkageException(what + " has a " + which + " whose type is not derivable");
        }
        ResolvedFuncType resolved = ResolvedFuncType.of(funcType, side.space());
        for (ResolvedFuncType.Param param : resolved.params()) {
            requireHandlesResolved(param.type(), what, which);
        }
        if (resolved.hasResult()) {
            requireHandlesResolved(resolved.result(), what, which);
        }
        return resolved;
    }

    /**
     * Refuses a handle that resolved to no resource, which would otherwise compare equal to any
     * other such handle.
     */
    private static void requireHandlesResolved(ResolvedType type, String what, String which) {
        if (hasUnresolvedHandle(type)) {
            throw new UnsupportedOperationException(
                    "comparing "
                            + which
                            + " of a component type is not yet supported ("
                            + what
                            + "): it names a resource that no type export of the component type"
                            + " introduces, so there is nothing to compare it against");
        }
    }

    private static boolean hasUnresolvedHandle(ResolvedType type) {
        if (type == null) {
            return false;
        }
        switch (type.kind()) {
            case OWN:
            case BORROW:
                return type.resourceType() == null;
            case RECORD:
            case TUPLE:
                for (ResolvedType.Field field : type.fields()) {
                    if (hasUnresolvedHandle(field.type())) {
                        return true;
                    }
                }
                return false;
            case VARIANT:
            case OPTION:
            case RESULT:
                for (ResolvedType.Case c : type.cases()) {
                    if (c.hasType() && hasUnresolvedHandle(c.type())) {
                        return true;
                    }
                }
                return false;
            case LIST:
            case SIZED_LIST:
            case MAP:
            case STREAM:
            case FUTURE:
                return hasUnresolvedHandle(type.element());
            default:
                return false;
        }
    }

    private static String describe(ExternDesc.Kind kind) {
        switch (kind) {
            case CORE_MODULE:
                return "a core module";
            case FUNC:
                return "a function";
            case VALUE:
                return "a value";
            case TYPE:
                return "a type";
            case COMPONENT:
                return "a component";
            case INSTANCE:
                return "an instance";
            default:
                return kind.toString();
        }
    }

    /**
     * Stands for a resource type that does not exist yet, distinct from every other by identity
     * alone.
     */
    private static final class AbstractResource implements ResourceTypeId {

        private final String label;

        private AbstractResource(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** What a name stands for on either side of the comparison. */
    private static final class Extern {

        private final ExternDesc.Kind kind;
        private final FuncType funcType;

        /** For a {@code type} extern, the definition it names. */
        private final Type type;

        /** For a {@code type} extern naming a resource, its stand-in. */
        private final ResourceTypeId token;

        /** For an {@code instance} or {@code component} extern, the same reduction applied. */
        private final Side nested;

        private Extern(
                ExternDesc.Kind kind,
                FuncType funcType,
                Type type,
                ResourceTypeId token,
                Side nested) {
            this.kind = kind;
            this.funcType = funcType;
            this.type = type;
            this.token = token;
            this.nested = nested;
        }

        private static Extern func(FuncType funcType) {
            return new Extern(ExternDesc.Kind.FUNC, funcType, null, null, null);
        }

        private static Extern type(Type type, ResourceTypeId token) {
            return new Extern(ExternDesc.Kind.TYPE, null, type, token, null);
        }

        private static Extern nested(ExternDesc.Kind kind, Side nested) {
            return new Extern(kind, null, null, null, nested);
        }

        private static Extern of(ExternDesc.Kind kind) {
            return new Extern(kind, null, null, null, null);
        }
    }

    /** One declaration of a component type, in the order it was written. */
    private static final class Decl {

        private final String name;
        private final boolean isImport;
        private final Extern extern;

        private Decl(String name, boolean isImport, Extern extern) {
            this.name = name;
            this.isImport = isImport;
            this.extern = extern;
        }
    }

    /**
     * One side of the comparison, reduced to what it imports and exports by name. Each side numbers
     * its own index spaces, so reducing both to names is what makes the numbers stop mattering.
     */
    private static final class Side implements TypeResolver {

        /** The type index space. A {@code null} slot is one this pass cannot derive. */
        private List<Type> types = new ArrayList<>();

        /** Stand-ins, parallel to {@link #types}; {@code null} where a slot is not a resource. */
        private List<ResourceTypeId> tokens = new ArrayList<>();

        /** The component function index space, which only imports and {@code canon lift} fill. */
        private List<FuncType> funcs = new ArrayList<>();

        /** The instance index space. A {@code null} slot is one this pass cannot derive. */
        private List<Side> instances = new ArrayList<>();

        /** The component index space, filled by nested definitions and component imports. */
        private List<Side> components = new ArrayList<>();

        private final Map<String, Extern> imports = new LinkedHashMap<>();
        private final Map<String, Extern> exports = new LinkedHashMap<>();

        /** Declarations in written order; empty for a side read from a real component. */
        private final List<Decl> decls = new ArrayList<>();

        /** This side's stand-ins, mapped onto the ones they were matched against. */
        private Map<ResourceTypeId, ResourceTypeId> substitution = new IdentityHashMap<>();

        private TypeSpace space;

        private Side() {}

        /**
         * A side whose names index into {@code scope}'s spaces rather than its own, as an instance
         * written inline in a component does.
         */
        private Side(Side scope) {
            types = scope.types;
            tokens = scope.tokens;
            funcs = scope.funcs;
            instances = scope.instances;
            components = scope.components;
            substitution = scope.substitution;
        }

        /**
         * Records that this side's {@code mine} is the other side's {@code theirs}. One stand-in
         * cannot come to mean two types. The converse is allowed, so one resource may stand in for
         * two separate declarations.
         */
        private void relate(ResourceTypeId mine, ResourceTypeId theirs, String what, String which) {
            ResourceTypeId already = substitution.get(mine);
            if (already != null && already != theirs) {
                throw new LinkageException(
                        what
                                + " does not match on "
                                + which
                                + " - its component type ties this to another export naming a"
                                + " different resource type");
            }
            substitution.put(mine, theirs);
        }

        @Override
        public Type getType(int index) {
            Type type = index >= 0 && index < types.size() ? types.get(index) : null;
            if (type == null) {
                throw new LinkageException(
                        "type index " + index + " is not derivable in a component type comparison");
            }
            return type;
        }

        /** The index space as something resolvable, reading the relation as it stands. */
        private TypeSpace space() {
            if (space == null) {
                space =
                        new TypeSpace() {

                            @Override
                            public ResolvedType resolve(ValType valType) {
                                return ResolvedType.of(resolveDefValType(valType), this);
                            }

                            @Override
                            public ResourceTypeId resourceType(int typeIdx) {
                                ResourceTypeId token =
                                        typeIdx >= 0 && typeIdx < tokens.size()
                                                ? tokens.get(typeIdx)
                                                : null;
                                ResourceTypeId mapped = substitution.get(token);
                                return mapped != null ? mapped : token;
                            }
                        };
            }
            return space;
        }

        /** Adds a slot to the type index space, with the stand-in it carries if it is one. */
        private void addType(Type type, ResourceTypeId token) {
            types.add(type);
            tokens.add(token);
        }

        /** Reads the declarations of a {@code (component ...)} type. */
        private static Side ofComponentType(ComponentType componentType) {
            Side side = new Side();
            for (ComponentDecl decl : componentType.getComponentDecls()) {
                ImportDecl importDecl = decl.importDecl();
                if (importDecl != null) {
                    Extern extern = side.bind(importDecl.externDesc(), importDecl.name());
                    side.imports.put(importDecl.name(), extern);
                    side.decls.add(new Decl(importDecl.name(), true, extern));
                    continue;
                }
                InstanceDecl instanceDecl = decl.instanceDecl();
                if (instanceDecl != null) {
                    side.addInstanceDecl(instanceDecl);
                }
            }
            return side;
        }

        /**
         * Reads the declarations of an {@code (instance ...)} type, which numbers its own spaces.
         */
        private static Side ofInstanceType(InstanceType instanceType) {
            Side side = new Side();
            for (InstanceDecl decl : instanceType.getInstanceDecls()) {
                side.addInstanceDecl(decl);
            }
            return side;
        }

        /** One declaration of an instance type, which a component type may also contain. */
        private void addInstanceDecl(InstanceDecl decl) {
            switch (decl.kind()) {
                case TYPE:
                    addType(decl.type(), null);
                    break;
                case EXPORT_DECL:
                    ExportDecl exportDecl = decl.exportDecl();
                    Extern extern = bind(exportDecl.externDesc(), exportDecl.name());
                    exports.put(exportDecl.name(), extern);
                    decls.add(new Decl(exportDecl.name(), false, extern));
                    break;
                case CORE_TYPE:
                    break;
                case ALIAS:
                default:
                    addType(null, null);
                    break;
            }
        }

        /** Reads the sections of a real component, in the order its index spaces are filled. */
        private static Side ofComponent(WasmComponent component) {
            Side side = new Side();
            for (Section section : component.sections()) {
                if (section instanceof TypeSection) {
                    for (Type type : ((TypeSection) section).types()) {
                        side.addType(
                                type,
                                type.resourceType() == null
                                        ? null
                                        : new AbstractResource("resource#" + side.types.size()));
                    }
                } else if (section instanceof ImportSection) {
                    for (Import imp : ((ImportSection) section).imports()) {
                        side.imports.put(imp.name(), side.bind(imp.externDesc(), imp.name()));
                    }
                } else if (section instanceof ComponentSection) {
                    side.components.add(ofComponent(((ComponentSection) section).component()));
                } else if (section instanceof InstanceSection) {
                    for (Instance instance : ((InstanceSection) section).instances()) {
                        // An instance built by `instantiate` is left underivable.
                        side.instances.add(
                                instance.expr() instanceof InlineExportInstanceExpr
                                        ? side.ofInlineExports(
                                                ((InlineExportInstanceExpr) instance.expr())
                                                        .inlineExports())
                                        : null);
                    }
                } else if (section instanceof CanonSection) {
                    for (var canon : ((CanonSection) section).canons()) {
                        if (canon instanceof CanonLift) {
                            side.funcs.add(side.funcTypeAt((int) ((CanonLift) canon).typeIdx()));
                        }
                    }
                } else if (section instanceof AliasSection) {
                    for (Alias alias : ((AliasSection) section).aliases()) {
                        side.addAlias(alias);
                    }
                } else if (section instanceof ExportSection) {
                    for (Export export : ((ExportSection) section).exports()) {
                        // An export names a preceding definition before adding one of its own.
                        Extern extern = side.externOf(export);
                        side.exports.put(export.name(), extern);
                        side.bindExport(extern);
                    }
                }
            }
            return side;
        }

        /**
         * An alias extends the index space of its sort, so it is counted even when what it reaches
         * cannot be derived. An export alias into an instance read here is followed; an outer alias
         * is left underivable.
         */
        private void addAlias(Alias alias) {
            Sort sort = alias.sort();
            if (sort.kind() == Sort.Kind.CORE || sort.kind() == Sort.Kind.VALUE) {
                // Neither space is tracked here.
                return;
            }
            ExternDesc.Kind kind = externKindOf(sort);
            Extern reached = null;
            if (alias instanceof ExportAlias) {
                ExportAlias exportAlias = (ExportAlias) alias;
                int index = (int) exportAlias.instanceIdx();
                Side instance =
                        index >= 0 && index < instances.size() ? instances.get(index) : null;
                if (instance != null) {
                    reached = instance.exports.get(exportAlias.name());
                }
            }
            bindExport(reached != null && reached.kind == kind ? reached : Extern.of(kind));
        }

        /** Records what an extern description denotes, filling the index space it extends. */
        private Extern bind(ExternDesc desc, String name) {
            switch (desc.kind()) {
                case FUNC:
                    FuncType funcType = funcTypeAt((int) desc.typeIdx());
                    funcs.add(funcType);
                    return Extern.func(funcType);
                case TYPE:
                    TypeBound bound = desc.typeBound();
                    if (bound != null && bound.kind() == TypeBound.Kind.EQ) {
                        int index = (int) bound.typeIdx();
                        Type named = typeAt(index);
                        ResourceTypeId token = tokenAt(index);
                        addType(named, token);
                        return Extern.type(named, token);
                    }
                    ResourceTypeId fresh = new AbstractResource("resource \"" + name + "\"");
                    addType(null, fresh);
                    return Extern.type(null, fresh);
                case INSTANCE:
                    Type instanceSlot = typeAt((int) desc.typeIdx());
                    InstanceType instanceType =
                            instanceSlot == null ? null : instanceSlot.instanceType();
                    Side nestedInstance =
                            instanceType == null ? null : ofInstanceType(instanceType);
                    instances.add(nestedInstance);
                    return Extern.nested(ExternDesc.Kind.INSTANCE, nestedInstance);
                case COMPONENT:
                    Type componentSlot = typeAt((int) desc.typeIdx());
                    ComponentType componentType =
                            componentSlot == null ? null : componentSlot.componentType();
                    Side nestedComponent =
                            componentType == null ? null : ofComponentType(componentType);
                    components.add(nestedComponent);
                    return Extern.nested(ExternDesc.Kind.COMPONENT, nestedComponent);
                default:
                    return Extern.of(desc.kind());
            }
        }

        /** Extends the index space an export adds to, which an export always does. */
        private void bindExport(Extern extern) {
            switch (extern.kind) {
                case FUNC:
                    funcs.add(extern.funcType);
                    break;
                case TYPE:
                    addType(extern.type, extern.token);
                    break;
                case INSTANCE:
                    instances.add(extern.nested);
                    break;
                case COMPONENT:
                    components.add(extern.nested);
                    break;
                default:
                    break;
            }
        }

        /** The names an inline-export instance groups, read against this side's spaces. */
        private Side ofInlineExports(List<InlineExport> inlineExports) {
            Side side = new Side(this);
            for (InlineExport inlineExport : inlineExports) {
                side.exports.put(inlineExport.name(), externOfSortIdx(inlineExport.sortIdx()));
            }
            return side;
        }

        /** What an export names, from its ascribed type if it has one and its definition if not. */
        private Extern externOf(Export export) {
            ExternDesc desc = export.externDesc();
            if (desc != null) {
                switch (desc.kind()) {
                    case FUNC:
                        return Extern.func(funcTypeAt((int) desc.typeIdx()));
                    case TYPE:
                        TypeBound bound = desc.typeBound();
                        if (bound != null && bound.kind() == TypeBound.Kind.EQ) {
                            int index = (int) bound.typeIdx();
                            return Extern.type(typeAt(index), tokenAt(index));
                        }
                        return Extern.type(null, new AbstractResource("resource#" + types.size()));
                    default:
                        return Extern.of(desc.kind());
                }
            }
            return externOfSortIdx(export.sortIdx());
        }

        /** What a {@code sortidx} names, looked up in the index space for its sort. */
        private Extern externOfSortIdx(SortIdx sortIdx) {
            Sort sort = sortIdx.sort();
            int index = (int) sortIdx.idx();
            if (sort.kind() == Sort.Kind.FUNC) {
                return Extern.func(index >= 0 && index < funcs.size() ? funcs.get(index) : null);
            }
            if (sort.kind() == Sort.Kind.TYPE) {
                return Extern.type(typeAt(index), tokenAt(index));
            }
            if (sort.kind() == Sort.Kind.INSTANCE) {
                return Extern.nested(
                        ExternDesc.Kind.INSTANCE,
                        index >= 0 && index < instances.size() ? instances.get(index) : null);
            }
            if (sort.kind() == Sort.Kind.COMPONENT) {
                return Extern.nested(
                        ExternDesc.Kind.COMPONENT,
                        index >= 0 && index < components.size() ? components.get(index) : null);
            }
            return Extern.of(externKindOf(sort));
        }

        private static ExternDesc.Kind externKindOf(Sort sort) {
            switch (sort.kind()) {
                case FUNC:
                    return ExternDesc.Kind.FUNC;
                case TYPE:
                    return ExternDesc.Kind.TYPE;
                case COMPONENT:
                    return ExternDesc.Kind.COMPONENT;
                case INSTANCE:
                    return ExternDesc.Kind.INSTANCE;
                case VALUE:
                    return ExternDesc.Kind.VALUE;
                case CORE:
                default:
                    return ExternDesc.Kind.CORE_MODULE;
            }
        }

        private Type typeAt(int index) {
            return index >= 0 && index < types.size() ? types.get(index) : null;
        }

        private ResourceTypeId tokenAt(int index) {
            return index >= 0 && index < tokens.size() ? tokens.get(index) : null;
        }

        private FuncType funcTypeAt(int index) {
            Type type = typeAt(index);
            return type == null ? null : type.funcType();
        }
    }
}
