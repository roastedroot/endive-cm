package run.endive.cm.bindgen;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.endive.cm.types.DefValType;
import run.endive.cm.types.Type;

/**
 * Builds the host side of an interface a world imports, as statements inside {@code instantiate}.
 *
 * <p>An interface is built through a local rather than in one chained expression, because a type
 * has to be declared into the instance before a function type can name it, and a resource before
 * an {@code own} or a {@code borrow} can.
 */
final class HostWiring {

    /** The name of the parameter carrying a dropped resource's representation. */
    private static final String REP = "rep";

    private final GeneratedUnit unit;
    private final WitTypes types;
    private final FunctionBindings bindings;

    HostWiring(GeneratedUnit unit, FunctionBindings bindings) {
        this.unit = unit;
        this.types = bindings.types();
        this.bindings = bindings;
    }

    /** The compound types an interface declares, which a host instance has to be told about. */
    static Map<Integer, Type> compoundTypes(WitInterface iface) {
        Map<Integer, Type> found = new LinkedHashMap<>();
        WitScope scope = iface.scope();
        for (int i = 0; i < scope.size(); i++) {
            Type declared = scope.at(i);
            if (declared != null && declared.defValType() != null && isCompound(declared)) {
                found.put(i, declared);
            }
        }
        return found;
    }

    /**
     * Appends the statements building a host instance for {@code imported} and registering it
     * under the name the linker matches.
     *
     * @param values the local collecting what satisfies each import
     */
    void addTo(BlockStmt body, WitInterface imported, String values) {
        Locals locals = new Locals(imported);

        body.addStatement(
                AstBuilders.declare(
                        AstBuilders.type(imported.scope().javaPackage() + ".Host"),
                        locals.host,
                        AstBuilders.call(new NameExpr("imports"), locals.host)));
        body.addStatement(
                AstBuilders.declare(
                        unit.use(QualifiedTypes.HOST_INSTANCE, "Builder"),
                        locals.builder,
                        AstBuilders.call(
                                unit.useName(QualifiedTypes.HOST_INSTANCE),
                                "builder",
                                new NameExpr("store"))));

        declareTypes(body, imported, locals);
        for (WitResource resource : imported.resources()) {
            addResource(body, imported, resource, locals);
        }
        for (WitFunction function : imported.functions()) {
            body.addStatement(
                    AstBuilders.call(
                            locals.builder(),
                            "addFunction",
                            AstBuilders.text(function.name()),
                            bindings.funcType(function, 0, null, locals.declared),
                            bindings.importLambda(new NameExpr(locals.host), function, 0)));
        }
        body.addStatement(
                AstBuilders.call(
                        new NameExpr(values),
                        "put",
                        AstBuilders.text(imported.name()),
                        AstBuilders.call(locals.builder(), "build")));
    }

    /** Declares each compound type into the instance, since a function type names one by index. */
    private void declareTypes(BlockStmt body, WitInterface imported, Locals locals) {
        Map<Type, String> byType = new IdentityHashMap<>();
        for (Map.Entry<Integer, Type> entry : compoundTypes(imported).entrySet()) {
            String existing = byType.get(entry.getValue());
            if (existing != null) {
                locals.declared.put(entry.getKey(), existing);
                continue;
            }
            String local =
                    locals.host + Names.type(typeName(imported, entry.getValue(), entry.getKey()));
            byType.put(entry.getValue(), local);
            locals.declared.put(entry.getKey(), local);
            body.addStatement(
                    AstBuilders.declare(
                            unit.use(QualifiedTypes.VAL_TYPE),
                            local,
                            AstBuilders.call(
                                    locals.builder(),
                                    "declareType",
                                    types.defValType(
                                            entry.getValue().defValType(),
                                            imported.scope(),
                                            locals.declared))));
        }
    }

    /**
     * A handle carries an integer rather than an object, so a table maps one to the other and the
     * destructor hands the object to {@code drop} before forgetting it.
     */
    private void addResource(
            BlockStmt body, WitInterface imported, WitResource resource, Locals locals) {
        String implementation = imported.scope().javaPackage() + "." + Names.type(resource.name());
        String table = locals.table(resource);
        String handle = locals.handle(resource);

        body.addStatement(
                AstBuilders.declare(
                        AstBuilders.generic(
                                unit.use(QualifiedTypes.HOST_RESOURCE_TABLE),
                                AstBuilders.type(implementation)),
                        table,
                        AstBuilders.construct(
                                AstBuilders.diamond(
                                        unit.use(QualifiedTypes.HOST_RESOURCE_TABLE)))));
        Expression destructor =
                AstBuilders.lambda(
                        REP,
                        AstBuilders.call(
                                new NameExpr(table),
                                "drop",
                                new NameExpr(REP),
                                AstBuilders.methodReference(
                                        AstBuilders.name(implementation), "drop")));
        body.addStatement(
                AstBuilders.declare(
                        unit.use(QualifiedTypes.HOST_RESOURCE),
                        handle,
                        AstBuilders.call(locals.builder(), "declareResource", destructor)));
        body.addStatement(
                AstBuilders.call(
                        locals.builder(),
                        "addResource",
                        AstBuilders.text(resource.name()),
                        new NameExpr(handle)));

        if (resource.constructor() != null) {
            addConstructor(body, resource, locals);
        }
        for (WitFunction method : resource.methods()) {
            Expression receiver =
                    AstBuilders.call(
                            new NameExpr(table),
                            "get",
                            AstBuilders.cast(
                                    unit.use(QualifiedTypes.RESOURCE_VALUE), bindings.argument(0)));
            body.addStatement(
                    AstBuilders.call(
                            locals.builder(),
                            "addFunction",
                            AstBuilders.text("[method]" + resource.name() + "." + method.name()),
                            bindings.funcType(
                                    method,
                                    1,
                                    AstBuilders.call(new NameExpr(handle), "borrow"),
                                    locals.declared),
                            bindings.importLambda(receiver, method, 1)));
        }
    }

    private void addConstructor(BlockStmt body, WitResource resource, Locals locals) {
        WitFunction constructor = resource.constructor();
        String handle = locals.handle(resource);
        Expression made =
                AstBuilders.call(
                        new NameExpr(locals.host),
                        Names.member(resource.name()),
                        bindings.lambdaArguments(constructor, 0));
        Expression owned =
                AstBuilders.call(
                        unit.useName(QualifiedTypes.RESOURCE_VALUE),
                        "owned",
                        AstBuilders.call(new NameExpr(handle), "type"),
                        AstBuilders.call(new NameExpr(locals.table(resource)), "add", made));
        body.addStatement(
                AstBuilders.call(
                        locals.builder(),
                        "addFunction",
                        AstBuilders.text("[constructor]" + resource.name()),
                        bindings.funcType(
                                constructor,
                                0,
                                AstBuilders.call(new NameExpr(handle), "own"),
                                locals.declared),
                        bindings.lambda(AstBuilders.objects(List.of(owned)))));
    }

    private static boolean isCompound(Type type) {
        DefValType.Kind kind = type.defValType().kind();
        return kind == DefValType.Kind.LIST || kind == DefValType.Kind.ENUM;
    }

    /** Only the export declaring a type says what it is called, so an unnamed one gets an index. */
    private static String typeName(WitInterface imported, Type type, int index) {
        WitScope scope = imported.scope();
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

        private final String host;
        private final String builder;
        private final Map<Integer, String> declared = new LinkedHashMap<>();

        Locals(WitInterface imported) {
            this.host = Names.member(imported.simpleName());
            this.builder = host + "Builder";
        }

        Expression builder() {
            return new NameExpr(builder);
        }

        String table(WitResource resource) {
            return handle(resource) + "Table";
        }

        String handle(WitResource resource) {
            return host + Names.type(resource.name());
        }
    }
}
