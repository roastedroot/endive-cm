package run.endive.cm.bindgen;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import run.endive.cm.types.BorrowType;
import run.endive.cm.types.DefValType;
import run.endive.cm.types.OwnType;
import run.endive.cm.types.Type;
import run.endive.cm.types.ValType;

/**
 * Builds the host side of an interface a world imports, as statements inside {@code instantiate}.
 *
 * <p>An interface is built through a local rather than in one chained expression, because a type
 * has to be declared into the instance before a function type can name it, and a resource before
 * an {@code own} or a {@code borrow} can.
 */
final class HostWiring {

    private final GeneratedUnit unit;
    private final WitTypes types;
    private final FunctionBindings bindings;

    /** Every type local {@code instantiate} declares, so that no two share a name. */
    private final Set<String> typeLocals = new HashSet<>();

    /** The local holding each interface's {@code Handles}, by the scope of that interface. */
    private final Map<WitScope, String> handles = new IdentityHashMap<>();

    /**
     * The wiring converts through bindings of its own, since the {@code Handles} it reaches are
     * locals of {@code instantiate} and nothing else in the world class may name them.
     */
    HostWiring(GeneratedUnit unit) {
        this.unit = unit;
        this.bindings = FunctionBindings.forUnit(unit);
        this.types = bindings.types();
        types.withHandles(
                scope -> handles.containsKey(scope) ? new NameExpr(handles.get(scope)) : null);
    }

    /** The types an interface declares, which a host instance has to be told about. */
    static Map<Integer, Type> declaredTypes(WitInterface iface) {
        Map<Integer, Type> found = new LinkedHashMap<>();
        WitScope scope = iface.scope();
        for (int i = 0; i < scope.size(); i++) {
            Type declared = scope.at(i);
            if (isDeclared(declared)) {
                found.put(i, declared);
            }
        }
        return found;
    }

    /**
     * Appends the statements building a host instance for {@code imported} and registering it
     * under the name the linker matches.
     *
     * @param name the name by which the world's members reach {@code imported}
     * @param values the local collecting what satisfies each import
     */
    void addTo(BlockStmt body, WitInterface imported, String name, String values) {
        Locals locals = new Locals(imported, name);

        if (imported.needsHost()) {
            body.addStatement(
                    AstBuilders.declare(
                            AstBuilders.type(imported.scope().javaPackage() + ".Host"),
                            locals.host,
                            AstBuilders.call(new NameExpr("imports"), locals.accessor)));
        }
        body.addStatement(
                AstBuilders.declare(
                        unit.use(QualifiedTypes.HOST_INSTANCE, "Builder"),
                        locals.builder,
                        AstBuilders.call(
                                unit.useName(QualifiedTypes.HOST_INSTANCE),
                                "builder",
                                new NameExpr("store"))));

        declareResources(body, imported, locals);
        declareTypes(body, imported, locals);

        Map<WitFunction, String> hostMethods = InterfaceGenerator.hostMethodNames(imported);
        for (WitResource resource : imported.resources()) {
            addResourceFunctions(body, resource, hostMethods, locals);
        }
        for (WitFunction function : imported.functions()) {
            addFunction(
                    body,
                    function.name(),
                    function,
                    locals,
                    bindings.importLambda(new NameExpr(locals.host), function, 0));
        }
        body.addStatement(
                AstBuilders.call(
                        new NameExpr(values),
                        "put",
                        AstBuilders.text(imported.name()),
                        AstBuilders.call(locals.builder(), "build")));
    }

    /**
     * Declares every resource the interface declares, through the interface's {@code Handles},
     * and every resource it uses, through the {@code Handles} of the interface declaring that one.
     * Each is exported under the name the interface gives it.
     *
     * <p>A host instance is matched against the importer structurally rather than index by
     * index, so its resources are declared ahead of everything else. That way every {@code own}
     * and {@code borrow} names a resource that already exists, wherever the WIT declared it.
     */
    private void declareResources(BlockStmt body, WitInterface imported, Locals locals) {
        WitScope scope = imported.scope();
        if (!imported.resources().isEmpty()) {
            String javaType = scope.javaPackage() + "." + InterfaceGenerator.HANDLES;
            String local = Names.free(locals.host + InterfaceGenerator.HANDLES, typeLocals);
            typeLocals.add(local);
            body.addStatement(
                    AstBuilders.declare(
                            AstBuilders.type(javaType),
                            local,
                            AstBuilders.construct(AstBuilders.type(javaType), locals.builder())));
            handles.put(scope, local);

            for (WitResource resource : imported.resources()) {
                String handle =
                        declareResource(
                                body,
                                locals,
                                resource.name(),
                                AstBuilders.call(
                                        new NameExpr(local),
                                        Names.resourceTypeGetter(resource.name())));
                locals.resourceIn(scope).put(resource.typeIndex(), handle);
                addResource(body, locals, resource.name(), handle);
            }
        }

        for (int i = 0; i < scope.size(); i++) {
            if (scope.at(i) == null && scope.isUsed(i) && scope.nameAt(i) != null) {
                useResource(body, imported, locals, i);
            }
        }

        for (int i = 0; i < scope.size(); i++) {
            Type slot = scope.at(i);
            DefValType defined = slot == null ? null : slot.defValType();
            if (defined instanceof OwnType) {
                declareHandleType(locals, i, ((OwnType) defined).typeIdx(), "own");
            } else if (defined instanceof BorrowType) {
                declareHandleType(locals, i, ((BorrowType) defined).typeIdx(), "borrow");
            }
        }
    }

    /**
     * A used resource keeps the runtime type the declaring interface brought into existence,
     * which is what the importer's {@code eq} bound on it requires. The declaring interface is
     * imported ahead of any interface using it, so its {@code Handles} is already in reach.
     */
    private void useResource(BlockStmt body, WitInterface imported, Locals locals, int index) {
        WitScope scope = imported.scope();
        String name = scope.nameAt(index);
        WitScope declaring = scope.declaringScope(index);
        int declaredAt = scope.declaringIndex(index);
        String handle = locals.resourceIn(declaring).get(declaredAt);
        if (handle == null) {
            String declaringHandles = handles.get(declaring);
            if (declaringHandles == null) {
                throw new BindgenException(
                        "interface \""
                                + imported.name()
                                + "\" uses resource \""
                                + name
                                + "\" from \""
                                + declaring.owner()
                                + "\", whose bindings are not built ahead of it");
            }
            Expression declared =
                    AstBuilders.call(
                            new NameExpr(declaringHandles),
                            Names.resourceTypeGetter(declaring.nameAt(declaredAt)));
            handle =
                    declareResource(
                            body,
                            locals,
                            name,
                            AstBuilders.call(locals.builder(), "useResource", declared));
            locals.resourceIn(declaring).put(declaredAt, handle);
        }
        addResource(body, locals, name, handle);
    }

    /** Declares the local holding a resource type, and gives back its name. */
    private String declareResource(BlockStmt body, Locals locals, String name, Expression value) {
        String handle = Names.free(locals.host + Names.type(name), typeLocals);
        typeLocals.add(handle);
        body.addStatement(
                AstBuilders.declare(unit.use(QualifiedTypes.HOST_RESOURCE), handle, value));
        return handle;
    }

    private void addResource(BlockStmt body, Locals locals, String name, String handle) {
        body.addStatement(
                AstBuilders.call(
                        locals.builder(),
                        "addResource",
                        AstBuilders.text(name),
                        new NameExpr(handle)));
    }

    /**
     * Records the {@code own} or {@code borrow} at {@code index} as the one the resource it names
     * brought with it into this instance.
     */
    private void declareHandleType(Locals locals, int index, int resource, String kind) {
        WitScope scope = locals.scope;
        String handle =
                locals.resourceIn(scope.declaringScope(resource))
                        .get(scope.declaringIndex(resource));
        if (handle != null) {
            locals.declared.put(index, AstBuilders.call(new NameExpr(handle), kind));
        }
    }

    /**
     * Declares each type into the instance, since a function type names one by index. A
     * named type is exported as well, since a component using it aliases it from the instance.
     */
    private void declareTypes(BlockStmt body, WitInterface imported, Locals locals) {
        WitScope scope = imported.scope();
        for (Map.Entry<Integer, Type> entry : declaredTypes(imported).entrySet()) {
            String preferred = typeName(scope, entry.getValue(), entry.getKey());
            String local =
                    declare(
                            body,
                            scope,
                            entry.getKey(),
                            locals.host + Names.type(preferred),
                            locals);
            locals.declared.put(entry.getKey(), new NameExpr(local));
            String name = scope.nameAt(entry.getKey());
            if (name != null) {
                body.addStatement(
                        AstBuilders.call(
                                locals.builder(),
                                "addType",
                                AstBuilders.text(name),
                                new NameExpr(local)));
            }
        }
    }

    /**
     * Declares the type at {@code index} unless it was declared already, and gives back the local
     * holding it.
     *
     * <p>A type used from another interface is declared into this instance too, since the host
     * instance has a type index space of its own. Its definition is numbered by the interface that
     * declared it, so whatever it refers to is declared first, against that interface's space.
     *
     * @param local the preferred name for the local, or {@code null} to derive one from the
     *     declaring scope
     */
    private String declare(BlockStmt body, WitScope scope, int index, String local, Locals locals) {
        Map<Integer, Expression> declared = locals.declaredIn(scope);
        if (scope.isUsed(index)) {
            String used =
                    declare(
                            body,
                            scope.declaringScope(index),
                            scope.declaringIndex(index),
                            local,
                            locals);
            declared.put(index, new NameExpr(used));
            return used;
        }
        String existing = locals.byType.get(scope.at(index));
        if (existing != null) {
            declared.put(index, new NameExpr(existing));
            return existing;
        }
        DefValType defined = scope.at(index).defValType();
        for (ValType reference : WitTypes.references(defined)) {
            if (reference.primValType() == null && isDeclared(scope.at(reference.typeIdx()))) {
                declare(body, scope, reference.typeIdx(), null, locals);
            }
        }
        String name =
                Names.free(
                        local != null
                                ? local
                                : locals.host
                                        + Names.type(scope.owner())
                                        + Names.type(typeName(scope, scope.at(index), index)),
                        typeLocals);
        typeLocals.add(name);
        body.addStatement(
                AstBuilders.declare(
                        unit.use(QualifiedTypes.VAL_TYPE),
                        name,
                        AstBuilders.call(
                                locals.builder(),
                                "declareType",
                                types.defValType(defined, scope, declared))));
        locals.byType.put(scope.at(index), name);
        declared.put(index, new NameExpr(name));
        return name;
    }

    /**
     * A constructor and a static are reached on the {@code Host}, and a method on the object its
     * borrowed receiver stands for. Every handle crossing converts through the interface's
     * {@code Handles}, so none of them needs wiring of its own.
     */
    private void addResourceFunctions(
            BlockStmt body,
            WitResource resource,
            Map<WitFunction, String> hostMethods,
            Locals locals) {
        Expression host = new NameExpr(locals.host);
        if (resource.constructor() != null) {
            WitFunction constructor = resource.constructor();
            addFunction(
                    body,
                    "[constructor]" + resource.name(),
                    constructor,
                    locals,
                    bindings.importLambda(host, hostMethods.get(constructor), constructor, 0));
        }
        for (WitFunction function : resource.statics()) {
            addFunction(
                    body,
                    "[static]" + resource.name() + "." + function.name(),
                    function,
                    locals,
                    bindings.importLambda(host, hostMethods.get(function), function, 0));
        }
        for (WitFunction method : resource.methods()) {
            Expression receiver =
                    types.fromComponent(
                            bindings.argument(0),
                            method.type().params().get(0).valType(),
                            method.scope());
            addFunction(
                    body,
                    "[method]" + resource.name() + "." + method.name(),
                    method,
                    locals,
                    bindings.importLambda(receiver, method, 1));
        }
    }

    private void addFunction(
            BlockStmt body, String name, WitFunction function, Locals locals, Expression lambda) {
        body.addStatement(
                AstBuilders.call(
                        locals.builder(),
                        "addFunction",
                        AstBuilders.text(name),
                        bindings.funcType(function, locals.declared),
                        lambda));
    }

    private static boolean isDeclared(Type type) {
        return type != null && type.defValType() != null && WitTypes.isDeclared(type.defValType());
    }

    /** Only the export declaring a type says what it is called, so an unnamed one gets an index. */
    private static String typeName(WitScope scope, Type type, int index) {
        for (int i = 0; i < scope.size(); i++) {
            if (scope.at(i) == type && scope.nameAt(i) != null) {
                return scope.nameAt(i);
            }
        }
        return "type-" + index;
    }

    /**
     * The locals one interface's wiring is written in terms of, all prefixed by the interface so
     * that two of them may declare a type or a resource of one name.
     */
    private static final class Locals {

        /**
         * The names {@code instantiate} and the lambdas inside it use already, which an
         * interface's locals give way to.
         */
        private static final Set<String> TAKEN =
                Set.of("store", "component", "imports", "values", "args");

        /** The accessor on {@code Imports} reaching the interface's {@code Host}. */
        private final String accessor;

        private final String host;
        private final String builder;
        private final WitScope scope;
        private final Map<Integer, Expression> declared = new LinkedHashMap<>();

        /**
         * The local holding each resource this instance declares or uses, by the scope declaring
         * the resource and its index there.
         */
        private final Map<WitScope, Map<Integer, String>> resources = new IdentityHashMap<>();

        /** What types used from other interfaces were declared as, by the scope declaring them. */
        private final Map<WitScope, Map<Integer, Expression>> elsewhere = new IdentityHashMap<>();

        /** One local per type, however many indices name it. */
        private final Map<Type, String> byType = new IdentityHashMap<>();

        Locals(WitInterface imported, String name) {
            this.accessor = Names.member(name);
            this.host = Names.free(accessor, TAKEN);
            this.builder = host + "Builder";
            this.scope = imported.scope();
        }

        /** The types {@code declaring} numbers, as what declared them, keyed by its indices. */
        Map<Integer, Expression> declaredIn(WitScope declaring) {
            return declaring == scope
                    ? declared
                    : elsewhere.computeIfAbsent(declaring, s -> new LinkedHashMap<>());
        }

        /** The locals holding the resources {@code declaring} declares, keyed by its indices. */
        Map<Integer, String> resourceIn(WitScope declaring) {
            return resources.computeIfAbsent(declaring, s -> new LinkedHashMap<>());
        }

        Expression builder() {
            return new NameExpr(builder);
        }
    }
}
