package run.endive.cm.bindgen;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.type.WildcardType;
import java.util.ArrayList;
import java.util.List;
import run.endive.cm.types.EnumType;
import run.endive.cm.types.FlagsType;

/**
 * Generates the Java package mirroring one WIT interface, holding the interface itself plus
 * whatever types it declares.
 *
 * <p>Which way a call runs decides the shape. An imported interface becomes a {@code Host} the
 * embedder implements, and an exported one a {@code Guest} wrapping what the component exports.
 */
final class InterfaceGenerator {

    /** The nested enum a generated flags wrapper holds one constant of per label. */
    private static final String FLAG = "Flag";

    private final String generatedBy;

    InterfaceGenerator(String generatedBy) {
        this.generatedBy = generatedBy;
    }

    List<GeneratedUnit> sources(WitInterface iface, boolean exported) {
        List<GeneratedUnit> sources = new ArrayList<>();
        for (WitType declared : iface.types()) {
            // A kind with no Java source of its own is skipped here and refused where a function
            // names it, so declaring one an interface never uses costs nothing.
            switch (declared.kind()) {
                case ENUM:
                    sources.add(enumSource(iface, declared));
                    break;
                case FLAGS:
                    sources.add(flagsSource(iface, declared));
                    break;
                default:
                    break;
            }
        }
        for (WitResource resource : iface.resources()) {
            sources.add(
                    exported
                            ? guestResourceSource(iface, resource)
                            : hostResourceSource(iface, resource));
        }
        sources.add(exported ? guestSource(iface) : hostSource(iface));
        return sources;
    }

    /**
     * An enum carries the label the ABI knows it by, because the ABI despecializes an enum to a
     * variant and lifts it as a {@link run.endive.cm.abi.VariantValue} rather than as anything
     * nominal.
     */
    private GeneratedUnit enumSource(WitInterface iface, WitType declared) {
        String className = Names.type(declared.name());
        GeneratedUnit unit = unitFor(iface);

        EnumDeclaration type = unit.addEnum(className);
        type.setJavadocComment(
                "The WIT enum {@code "
                        + declared.name()
                        + "}, declared by {@code "
                        + iface.name()
                        + "}.");
        for (String label : ((EnumType) declared.defValType()).labels()) {
            type.addEnumConstant(constantOf(label)).addArgument(AstBuilders.text(label));
        }

        type.addField(
                AstBuilders.type("String"),
                "label",
                Modifier.Keyword.PRIVATE,
                Modifier.Keyword.FINAL);

        ConstructorDeclaration constructor = type.addConstructor();
        constructor.addParameter(AstBuilders.type("String"), "label");
        constructor
                .getBody()
                .addStatement(
                        AstBuilders.assign(AstBuilders.thisField("label"), new NameExpr("label")));

        BlockStmt lowered = new BlockStmt();
        lowered.addStatement(
                new ReturnStmt(
                        AstBuilders.call(
                                unit.useName(QualifiedTypes.VARIANT_VALUE),
                                "of",
                                new NameExpr("label"),
                                new NullLiteralExpr())));
        type.addMethod("toComponent", Modifier.Keyword.PUBLIC)
                .setType(unit.use(QualifiedTypes.VARIANT_VALUE))
                .setBody(lowered)
                .setJavadocComment(
                        "This case as the ABI carries it, which is a variant with no payload.");

        MethodDeclaration lifted =
                type.addMethod("fromComponent", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC)
                        .setType(AstBuilders.type(className));
        lifted.addParameter(AstBuilders.type("Object"), "value");
        lifted.setBody(matchLabel(unit, className, declared));
        lifted.setJavadocComment("The case a lifted value names.");

        return unit;
    }

    private BlockStmt matchLabel(GeneratedUnit unit, String className, WitType declared) {
        BlockStmt body = new BlockStmt();
        body.addStatement(
                AstBuilders.declare(
                        AstBuilders.type("String"),
                        "label",
                        AstBuilders.call(
                                AstBuilders.cast(
                                        unit.use(QualifiedTypes.VARIANT_VALUE),
                                        new NameExpr("value")),
                                "label")));

        BlockStmt matched = new BlockStmt();
        matched.addStatement(new ReturnStmt(new NameExpr("candidate")));
        BlockStmt loop = new BlockStmt();
        loop.addStatement(
                new IfStmt(
                        AstBuilders.call(
                                AstBuilders.field(new NameExpr("candidate"), "label"),
                                "equals",
                                new NameExpr("label")),
                        matched,
                        null));
        body.addStatement(
                new ForEachStmt(
                        new VariableDeclarationExpr(
                                new VariableDeclarator(AstBuilders.type(className), "candidate")),
                        AstBuilders.call(null, "values"),
                        loop));

        Expression message =
                new BinaryExpr(
                        AstBuilders.text("unknown " + declared.name() + ": "),
                        new NameExpr("label"),
                        BinaryExpr.Operator.PLUS);
        body.addStatement(
                new ThrowStmt(
                        AstBuilders.construct(
                                AstBuilders.type("IllegalArgumentException"), message)));
        return body;
    }

    /**
     * Flags become an {@link java.util.EnumSet} backed wrapper over a nested enum of labels,
     * because the ABI carries them as a label to boolean map where an absent label is false.
     */
    private GeneratedUnit flagsSource(WitInterface iface, WitType declared) {
        String className = Names.type(declared.name());
        if (className.equals(FLAG)) {
            throw new BindgenException(
                    "flags \"" + declared.name() + "\" would collide with its own nested enum");
        }
        GeneratedUnit unit = unitFor(iface);

        ClassOrInterfaceDeclaration type = unit.addClass(className);
        type.setJavadocComment(
                "The WIT flags {@code "
                        + declared.name()
                        + "}, declared by {@code "
                        + iface.name()
                        + "}.");
        type.addMember(flagConstants(declared));

        type.addField(flagSet(unit), "flags", Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);

        ConstructorDeclaration constructor = type.addConstructor(Modifier.Keyword.PRIVATE);
        constructor.addParameter(flagSet(unit), "flags");
        constructor
                .getBody()
                .addStatement(
                        AstBuilders.assign(AstBuilders.thisField("flags"), new NameExpr("flags")));

        type.addMember(factory(unit, className));
        type.addMember(membership());
        type.addMember(reader(unit));
        type.addMember(flagsLowered(unit));
        type.addMember(flagsLifted(unit, className));
        type.addMember(flagsEquals(className));
        type.addMember(
                override(
                        new MethodDeclaration()
                                .setName("hashCode")
                                .setPublic(true)
                                .setType(PrimitiveType.intType())
                                .setBody(
                                        returning(
                                                AstBuilders.call(
                                                        new NameExpr("flags"), "hashCode")))));
        type.addMember(
                override(
                        new MethodDeclaration()
                                .setName("toString")
                                .setPublic(true)
                                .setType(AstBuilders.type("String"))
                                .setBody(
                                        returning(
                                                new BinaryExpr(
                                                        AstBuilders.text(className),
                                                        new NameExpr("flags"),
                                                        BinaryExpr.Operator.PLUS)))));
        return unit;
    }

    /** One constant per label, each carrying the label the ABI knows it by. */
    private EnumDeclaration flagConstants(WitType declared) {
        EnumDeclaration flags = new EnumDeclaration();
        flags.setName(FLAG);
        flags.setPublic(true);
        flags.setJavadocComment("One flag of {@code " + declared.name() + "}.");
        for (String label : ((FlagsType) declared.defValType()).labels()) {
            flags.addEnumConstant(constantOf(label)).addArgument(AstBuilders.text(label));
        }
        flags.addField(
                AstBuilders.type("String"),
                "label",
                Modifier.Keyword.PRIVATE,
                Modifier.Keyword.FINAL);

        ConstructorDeclaration constructor = flags.addConstructor();
        constructor.addParameter(AstBuilders.type("String"), "label");
        constructor
                .getBody()
                .addStatement(
                        AstBuilders.assign(AstBuilders.thisField("label"), new NameExpr("label")));
        return flags;
    }

    /** {@code of(Flag... flags)}, which sets exactly what it is given and nothing else. */
    private MethodDeclaration factory(GeneratedUnit unit, String className) {
        Parameter given = new Parameter(AstBuilders.type(FLAG), "flags");
        given.setVarArgs(true);

        BlockStmt body = new BlockStmt();
        body.addStatement(AstBuilders.declare(flagSet(unit), "set", noneOf(unit)));
        BlockStmt adding = new BlockStmt();
        adding.addStatement(AstBuilders.call(new NameExpr("set"), "add", new NameExpr("flag")));
        body.addStatement(eachFlag(new NameExpr("flags"), adding));
        body.addStatement(
                new ReturnStmt(
                        AstBuilders.construct(AstBuilders.type(className), new NameExpr("set"))));

        MethodDeclaration method = new MethodDeclaration();
        method.setName("of").setPublic(true).setStatic(true).setType(AstBuilders.type(className));
        method.addParameter(given);
        method.setBody(body);
        method.setJavadocComment("The value with exactly {@code flags} set.");
        return method;
    }

    private MethodDeclaration membership() {
        MethodDeclaration method = new MethodDeclaration();
        method.setName("has").setPublic(true).setType(PrimitiveType.booleanType());
        method.addParameter(AstBuilders.type(FLAG), "flag");
        method.setBody(
                returning(
                        AstBuilders.call(new NameExpr("flags"), "contains", new NameExpr("flag"))));
        method.setJavadocComment("Whether {@code flag} is set.");
        return method;
    }

    private MethodDeclaration reader(GeneratedUnit unit) {
        MethodDeclaration method = new MethodDeclaration();
        method.setName("flags")
                .setPublic(true)
                .setType(AstBuilders.generic(unit.use(QualifiedTypes.SET), AstBuilders.type(FLAG)));
        method.setBody(
                returning(
                        AstBuilders.call(
                                unit.useName(QualifiedTypes.ENUM_SET),
                                "copyOf",
                                new NameExpr("flags"))));
        method.setJavadocComment("The flags that are set.");
        return method;
    }

    /** Every label is written, since the ABI packs the bits by reading each of them by name. */
    private MethodDeclaration flagsLowered(GeneratedUnit unit) {
        BlockStmt body = new BlockStmt();
        body.addStatement(
                AstBuilders.declare(
                        labelledBooleans(unit),
                        "value",
                        AstBuilders.construct(
                                AstBuilders.diamond(unit.use(QualifiedTypes.LINKED_HASH_MAP)))));
        BlockStmt putting = new BlockStmt();
        putting.addStatement(
                AstBuilders.call(
                        new NameExpr("value"),
                        "put",
                        AstBuilders.field(new NameExpr("flag"), "label"),
                        AstBuilders.call(new NameExpr("flags"), "contains", new NameExpr("flag"))));
        body.addStatement(eachFlag(AstBuilders.call(AstBuilders.name(FLAG), "values"), putting));
        body.addStatement(new ReturnStmt(new NameExpr("value")));

        MethodDeclaration method = new MethodDeclaration();
        method.setName("toComponent").setPublic(true).setType(labelledBooleans(unit));
        method.setBody(body);
        method.setJavadocComment("These flags as the ABI carries them, which is a map per label.");
        return method;
    }

    /** A label the map does not mention is false, which is where flags differ from a record. */
    private MethodDeclaration flagsLifted(GeneratedUnit unit, String className) {
        BlockStmt body = new BlockStmt();
        body.addStatement(
                AstBuilders.declare(
                        anyMap(unit),
                        "carried",
                        AstBuilders.cast(anyMap(unit), new NameExpr("value"))));
        body.addStatement(AstBuilders.declare(flagSet(unit), "set", noneOf(unit)));

        BlockStmt adding = new BlockStmt();
        adding.addStatement(AstBuilders.call(new NameExpr("set"), "add", new NameExpr("flag")));
        BlockStmt testing = new BlockStmt();
        testing.addStatement(
                new IfStmt(
                        AstBuilders.call(
                                AstBuilders.name("Boolean.TRUE"),
                                "equals",
                                AstBuilders.call(
                                        new NameExpr("carried"),
                                        "get",
                                        AstBuilders.field(new NameExpr("flag"), "label"))),
                        adding,
                        null));
        body.addStatement(eachFlag(AstBuilders.call(AstBuilders.name(FLAG), "values"), testing));
        body.addStatement(
                new ReturnStmt(
                        AstBuilders.construct(AstBuilders.type(className), new NameExpr("set"))));

        MethodDeclaration method = new MethodDeclaration();
        method.setName("fromComponent")
                .setPublic(true)
                .setStatic(true)
                .setType(AstBuilders.type(className));
        method.addParameter(AstBuilders.type("Object"), "value");
        method.setBody(body);
        method.setJavadocComment("The flags a lifted value sets.");
        return method;
    }

    private MethodDeclaration flagsEquals(String className) {
        Expression sameFlags =
                AstBuilders.call(
                        new NameExpr("flags"),
                        "equals",
                        AstBuilders.field(
                                AstBuilders.cast(
                                        AstBuilders.type(className), new NameExpr("other")),
                                "flags"));
        MethodDeclaration method = new MethodDeclaration();
        method.setName("equals").setPublic(true).setType(PrimitiveType.booleanType());
        method.addParameter(AstBuilders.type("Object"), "other");
        method.setBody(
                returning(
                        new BinaryExpr(
                                new InstanceOfExpr(
                                        new NameExpr("other"), AstBuilders.type(className), null),
                                sameFlags,
                                BinaryExpr.Operator.AND)));
        return override(method);
    }

    /** Built fresh each time, since a node may be given to only one parent. */
    private Type flagSet(GeneratedUnit unit) {
        return AstBuilders.generic(unit.use(QualifiedTypes.ENUM_SET), AstBuilders.type(FLAG));
    }

    private Type labelledBooleans(GeneratedUnit unit) {
        return AstBuilders.generic(
                unit.use(QualifiedTypes.MAP),
                AstBuilders.type("String"),
                AstBuilders.type("Boolean"));
    }

    private Type anyMap(GeneratedUnit unit) {
        return AstBuilders.generic(
                unit.use(QualifiedTypes.MAP), new WildcardType(), new WildcardType());
    }

    private Expression noneOf(GeneratedUnit unit) {
        return AstBuilders.call(
                unit.useName(QualifiedTypes.ENUM_SET),
                "noneOf",
                AstBuilders.classLiteral(AstBuilders.type(FLAG)));
    }

    private static ForEachStmt eachFlag(Expression source, BlockStmt body) {
        return new ForEachStmt(
                new VariableDeclarationExpr(new VariableDeclarator(AstBuilders.type(FLAG), "flag")),
                source,
                body);
    }

    private static MethodDeclaration override(MethodDeclaration method) {
        method.addMarkerAnnotation("Override");
        return method;
    }

    private static BlockStmt returning(Expression value) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(value));
        return body;
    }

    /** An imported interface, which the embedder implements. */
    private GeneratedUnit hostSource(WitInterface iface) {
        GeneratedUnit unit = unitFor(iface);
        FunctionBindings bindings = FunctionBindings.forUnit(unit);

        ClassOrInterfaceDeclaration type = unit.addInterface("Host");
        type.setJavadocComment(
                "The WIT interface {@code " + iface.name() + "}, which the embedder implements.");
        for (WitFunction function : iface.functions()) {
            type.addMember(bindings.signature(function, 0));
        }
        for (WitResource resource : iface.resources()) {
            if (resource.constructor() == null) {
                continue;
            }
            MethodDeclaration factory = new MethodDeclaration();
            factory.setName(Names.member(resource.name()));
            factory.setType(AstBuilders.type(Names.type(resource.name())));
            factory.removeBody();
            bindings.addParameters(factory, resource.constructor(), 0);
            factory.setJavadocComment("Makes a {@code " + resource.name() + "}.");
            type.addMember(factory);
        }
        return unit;
    }

    /** A resource an imported interface declares, which the embedder implements. */
    private GeneratedUnit hostResourceSource(WitInterface iface, WitResource resource) {
        String className = Names.type(resource.name());
        GeneratedUnit unit = unitFor(iface);
        FunctionBindings bindings = FunctionBindings.forUnit(unit);

        ClassOrInterfaceDeclaration type = unit.addInterface(className);
        type.setJavadocComment(
                "The WIT resource {@code "
                        + resource.name()
                        + "}, which the embedder implements. A method's borrowed receiver is what"
                        + " Java carries as {@code this}, so it is not a parameter here.");
        for (WitFunction method : resource.methods()) {
            type.addMember(bindings.signature(method, 1));
        }
        type.addMethod("drop", Modifier.Keyword.DEFAULT)
                .setType(new VoidType())
                .setBody(new BlockStmt())
                .setJavadocComment("Called when the guest drops an owned handle to this resource.");
        return unit;
    }

    /** An exported interface, whose functions are narrowed once when the wrapper is built. */
    private GeneratedUnit guestSource(WitInterface iface) {
        GeneratedUnit unit = unitFor(iface);
        FunctionBindings bindings = FunctionBindings.forUnit(unit);

        ClassOrInterfaceDeclaration type = unit.addClass("Guest");
        type.setJavadocComment(
                "The WIT interface {@code " + iface.name() + "}, as the component exports it.");

        for (WitFunction function : iface.functions()) {
            type.addField(
                    unit.use(QualifiedTypes.COMPONENT_FUNCTION),
                    Names.member(function.name()),
                    Modifier.Keyword.PRIVATE,
                    Modifier.Keyword.FINAL);
        }
        for (WitResource resource : iface.resources()) {
            for (WitFunction function : resourceFunctions(resource)) {
                // Read by the resource wrapper, which is a class of its own in this package.
                type.addField(
                        unit.use(QualifiedTypes.COMPONENT_FUNCTION),
                        resourceField(resource, function),
                        Modifier.Keyword.FINAL);
            }
        }

        ConstructorDeclaration constructor = type.addConstructor(Modifier.Keyword.PUBLIC);
        constructor.addParameter(unit.use(QualifiedTypes.COMPONENT_INSTANCE), "instance");
        constructor.setJavadocComment(
                "Built by the world's bindings. Public only because they are another package.");
        BlockStmt body = constructor.getBody();
        for (WitFunction function : iface.functions()) {
            body.addStatement(
                    narrow(
                            Names.member(function.name()),
                            function.name(),
                            bindings.descriptors(function, null, null)));
        }
        for (WitResource resource : iface.resources()) {
            addResourceNarrowing(body, bindings, resource);
        }

        for (WitFunction function : iface.functions()) {
            type.addMember(
                    bindings.callMethod(
                            function,
                            0,
                            AstBuilders.thisField(Names.member(function.name())),
                            List.of()));
        }
        for (WitResource resource : iface.resources()) {
            if (resource.constructor() != null) {
                type.addMember(guestResourceFactory(unit, bindings, resource));
            }
        }
        return unit;
    }

    /**
     * The Canonical ABI names a resource's functions rather than nesting them, and neither a
     * constructor's type nor a method's names the handle, so both are described by hand.
     */
    private void addResourceNarrowing(
            BlockStmt body, FunctionBindings bindings, WitResource resource) {
        if (resource.constructor() != null) {
            body.addStatement(
                    narrow(
                            resourceField(resource, resource.constructor()),
                            "[constructor]" + resource.name(),
                            bindings.descriptors(
                                    resource.constructor(), bindings.resourceDescriptor(), null)));
        }
        for (WitFunction method : resource.methods()) {
            body.addStatement(
                    narrow(
                            resourceField(resource, method),
                            "[method]" + resource.name() + "." + method.name(),
                            bindings.descriptors(method, null, bindings.resourceDescriptor())));
        }
    }

    /** {@code this.<field> = instance.export("<exported>").typed(<descriptors>);} */
    private Statement narrow(String field, String exported, List<Expression> descriptors) {
        Expression export =
                AstBuilders.call(new NameExpr("instance"), "export", AstBuilders.text(exported));
        return AstBuilders.assign(
                AstBuilders.thisField(field), AstBuilders.call(export, "typed", descriptors));
    }

    /**
     * A resource an interface exports is implemented by the guest, so the wrapper holds the handle
     * its constructor returned.
     */
    private GeneratedUnit guestResourceSource(WitInterface iface, WitResource resource) {
        String className = Names.type(resource.name());
        GeneratedUnit unit = unitFor(iface);
        FunctionBindings bindings = FunctionBindings.forUnit(unit);

        ClassOrInterfaceDeclaration type = unit.addClass(className);
        type.addImplementedType("AutoCloseable");
        type.setJavadocComment(
                "The WIT resource {@code "
                        + resource.name()
                        + "}, which {@code "
                        + iface.name()
                        + "} implements. Nothing destroys it on the embedder's behalf, so closing"
                        + " one is what runs the guest's destructor.");

        type.addField(
                AstBuilders.type("Guest"),
                "owner",
                Modifier.Keyword.PRIVATE,
                Modifier.Keyword.FINAL);
        type.addField(
                unit.use(QualifiedTypes.RESOURCE_VALUE),
                "handle",
                Modifier.Keyword.PRIVATE,
                Modifier.Keyword.FINAL);
        type.addField(PrimitiveType.booleanType(), "dropped", Modifier.Keyword.PRIVATE);

        ConstructorDeclaration constructor = type.addConstructor();
        constructor.addParameter(AstBuilders.type("Guest"), "owner");
        constructor.addParameter(unit.use(QualifiedTypes.RESOURCE_VALUE), "handle");
        constructor
                .getBody()
                .addStatement(
                        AstBuilders.assign(AstBuilders.thisField("owner"), new NameExpr("owner")))
                .addStatement(
                        AstBuilders.assign(
                                AstBuilders.thisField("handle"), new NameExpr("handle")));

        for (WitFunction method : resource.methods()) {
            type.addMember(
                    bindings.callMethod(
                            method,
                            1,
                            AstBuilders.field(
                                    new NameExpr("owner"), resourceField(resource, method)),
                            List.of(new NameExpr("handle"))));
        }
        type.addMember(close(unit));
        return unit;
    }

    /** Dropping an owned handle runs the guest's destructor, and doing it twice is harmless. */
    private MethodDeclaration close(GeneratedUnit unit) {
        BlockStmt dropping = new BlockStmt();
        dropping.addStatement(
                AstBuilders.assign(new NameExpr("dropped"), new BooleanLiteralExpr(true)));
        dropping.addStatement(
                AstBuilders.call(
                        unit.useName(QualifiedTypes.GUEST_RESOURCE),
                        "drop",
                        new NameExpr("handle")));

        BlockStmt body = new BlockStmt();
        body.addStatement(
                new IfStmt(
                        new UnaryExpr(
                                new NameExpr("dropped"), UnaryExpr.Operator.LOGICAL_COMPLEMENT),
                        dropping,
                        null));

        MethodDeclaration method = new MethodDeclaration();
        method.setName("close").setType(new VoidType()).setPublic(true).setBody(body);
        method.addMarkerAnnotation("Override");
        method.setJavadocComment(
                "Runs the guest's destructor. Doing so more than once does nothing.");
        return method;
    }

    private MethodDeclaration guestResourceFactory(
            GeneratedUnit unit, FunctionBindings bindings, WitResource resource) {
        String className = Names.type(resource.name());
        WitFunction constructor = resource.constructor();

        Expression made =
                AstBuilders.call(
                        AstBuilders.thisField(resourceField(resource, constructor)),
                        "apply",
                        bindings.callArguments(constructor, 0));
        BlockStmt body = new BlockStmt();
        body.addStatement(
                new ReturnStmt(
                        AstBuilders.construct(
                                AstBuilders.type(className),
                                new ThisExpr(),
                                AstBuilders.cast(
                                        unit.use(QualifiedTypes.RESOURCE_VALUE),
                                        AstBuilders.element(made, 0)))));

        MethodDeclaration factory = new MethodDeclaration();
        factory.setName(Names.member(resource.name())).setPublic(true);
        factory.setType(AstBuilders.type(className));
        bindings.addParameters(factory, constructor, 0);
        factory.setBody(body);
        factory.setJavadocComment("Makes a {@code " + resource.name() + "} inside the component.");
        return factory;
    }

    private GeneratedUnit unitFor(WitInterface iface) {
        return new GeneratedUnit(iface.scope().javaPackage(), generatedBy);
    }

    /** The name of the field holding one of a resource's narrowed functions. */
    private static String resourceField(WitResource resource, WitFunction function) {
        return Names.member(resource.name()) + Names.type(function.name());
    }

    private static List<WitFunction> resourceFunctions(WitResource resource) {
        List<WitFunction> all = new ArrayList<>();
        if (resource.constructor() != null) {
            all.add(resource.constructor());
        }
        all.addAll(resource.methods());
        return all;
    }

    private static String constantOf(String label) {
        return label.toUpperCase().replace('-', '_');
    }
}
