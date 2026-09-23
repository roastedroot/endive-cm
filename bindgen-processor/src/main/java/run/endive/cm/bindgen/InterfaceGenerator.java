package run.endive.cm.bindgen;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.type.WildcardType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import run.endive.cm.types.Case;
import run.endive.cm.types.DefValType;
import run.endive.cm.types.EnumType;
import run.endive.cm.types.FlagsType;
import run.endive.cm.types.LabelValType;
import run.endive.cm.types.RecordType;
import run.endive.cm.types.ResultType;
import run.endive.cm.types.VariantType;

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

    /** The name a variant case gives the payload it carries, since a WIT case payload has none. */
    private static final String VALUE = "value";

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
                case VARIANT:
                    sources.add(variantSource(iface, declared));
                    break;
                case RECORD:
                    sources.add(recordSource(iface, declared));
                    break;
                default:
                    break;
            }
        }
        sources.addAll(exceptionSources(iface));
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
     * One exception per {@code result} the interface declares, since a result is encoded as
     * control flow rather than as a value.
     *
     * <p>A result is written anonymously, so the scope is walked rather than the named types, and
     * two results sharing an error payload share the exception named after it.
     */
    private List<GeneratedUnit> exceptionSources(WitInterface iface) {
        List<GeneratedUnit> sources = new ArrayList<>();
        Set<String> named = new HashSet<>();
        WitScope scope = iface.scope();
        for (int i = 0; i < scope.size(); i++) {
            run.endive.cm.types.Type declared = scope.at(i);
            if (declared == null
                    || declared.defValType() == null
                    || declared.defValType().kind() != DefValType.Kind.RESULT) {
                continue;
            }
            String className = WitTypes.exceptionName(scope, i);
            if (named.add(className)) {
                sources.add(exceptionSource(iface, className, (ResultType) declared.defValType()));
            }
        }
        return sources;
    }

    /**
     * The error case of a {@code result}, as an unchecked exception carrying the error payload so
     * that a bound signature stays free of {@code throws}.
     */
    private GeneratedUnit exceptionSource(WitInterface iface, String className, ResultType result) {
        GeneratedUnit unit = unitFor(iface);
        WitTypes types = new WitTypes(unit);

        ClassOrInterfaceDeclaration type = unit.addClass(className);
        type.addExtendedType("RuntimeException");
        type.setJavadocComment(
                "The error case of a WIT result declared by {@code " + iface.name() + "}.");
        type.addFieldWithInitializer(
                PrimitiveType.longType(),
                "serialVersionUID",
                new LongLiteralExpr("1L"),
                Modifier.Keyword.PRIVATE,
                Modifier.Keyword.STATIC,
                Modifier.Keyword.FINAL);

        if (!result.hasError()) {
            type.addConstructor(Modifier.Keyword.PUBLIC)
                    .getBody()
                    .addStatement(superCall(AstBuilders.text(iface.name() + " returned an error")));
            return unit;
        }

        type.addField(
                types.javaType(result.error(), iface.scope()),
                "error",
                Modifier.Keyword.PRIVATE,
                Modifier.Keyword.FINAL);

        ConstructorDeclaration constructor = type.addConstructor(Modifier.Keyword.PUBLIC);
        constructor.addParameter(types.javaType(result.error(), iface.scope()), "error");
        constructor
                .getBody()
                .addStatement(
                        superCall(
                                AstBuilders.call(
                                        AstBuilders.name("String"),
                                        "valueOf",
                                        new NameExpr("error"))))
                .addStatement(
                        AstBuilders.assign(AstBuilders.thisField("error"), new NameExpr("error")));

        MethodDeclaration accessor =
                type.addMethod("error", Modifier.Keyword.PUBLIC)
                        .setType(types.javaType(result.error(), iface.scope()));
        BlockStmt read = new BlockStmt();
        read.addStatement(new ReturnStmt(new NameExpr("error")));
        accessor.setBody(read);
        accessor.setJavadocComment("The error payload the failing side supplied.");
        return unit;
    }

    private static Statement superCall(Expression message) {
        return new ExplicitConstructorInvocationStmt(false, null, NodeList.nodeList(message));
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

    /**
     * A variant becomes an abstract base class with one nested class per case, told apart with
     * {@code instanceof}. The base carries nothing, so a payload is a field on the case alone.
     */
    private GeneratedUnit variantSource(WitInterface iface, WitType declared) {
        String className = Names.type(declared.name());
        GeneratedUnit unit = unitFor(iface);
        WitTypes types = new WitTypes(unit);

        ClassOrInterfaceDeclaration type = unit.addAbstractClass(className);
        type.setJavadocComment(
                "The WIT variant {@code "
                        + declared.name()
                        + "}, declared by {@code "
                        + iface.name()
                        + "}. A value is one of the nested case classes, told apart with {@code"
                        + " instanceof}.");
        type.addConstructor(Modifier.Keyword.PRIVATE);

        MethodDeclaration lowered =
                type.addMethod("toComponent", Modifier.Keyword.PUBLIC, Modifier.Keyword.ABSTRACT)
                        .setType(unit.use(QualifiedTypes.VARIANT_VALUE));
        lowered.removeBody();
        lowered.setJavadocComment("This case as the ABI carries it.");

        MethodDeclaration lifted =
                type.addMethod("fromComponent", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC)
                        .setType(AstBuilders.type(className));
        lifted.addParameter(AstBuilders.type("Object"), VALUE);
        lifted.setBody(matchCase(unit, types, iface, declared));
        lifted.setJavadocComment("The case a lifted value names.");

        for (Case declaredCase : cases(declared)) {
            type.addMember(caseSource(unit, types, iface, className, declaredCase));
        }
        return unit;
    }

    /** The label is all a lifted value carries of its case, so matching one is a switch on it. */
    private BlockStmt matchCase(
            GeneratedUnit unit, WitTypes types, WitInterface iface, WitType declared) {
        BlockStmt body = new BlockStmt();
        body.addStatement(
                AstBuilders.declare(
                        unit.use(QualifiedTypes.VARIANT_VALUE),
                        "variant",
                        AstBuilders.cast(
                                unit.use(QualifiedTypes.VARIANT_VALUE), new NameExpr(VALUE))));

        NodeList<SwitchEntry> entries = new NodeList<>();
        for (Case declaredCase : cases(declared)) {
            List<Expression> payload = new ArrayList<>();
            if (declaredCase.hasValType()) {
                payload.add(
                        types.fromComponent(
                                AstBuilders.call(new NameExpr("variant"), VALUE),
                                declaredCase.valType(),
                                iface.scope()));
            }
            entries.add(
                    entry(
                            AstBuilders.text(declaredCase.label()),
                            new ReturnStmt(
                                    AstBuilders.construct(
                                            AstBuilders.type(Names.type(declaredCase.label())),
                                            payload.toArray(new Expression[0])))));
        }
        Expression message =
                new BinaryExpr(
                        AstBuilders.text("unknown " + declared.name() + ": "),
                        AstBuilders.call(new NameExpr("variant"), "label"),
                        BinaryExpr.Operator.PLUS);
        entries.add(
                entry(
                        null,
                        new ThrowStmt(
                                AstBuilders.construct(
                                        AstBuilders.type("IllegalArgumentException"), message))));
        body.addStatement(
                new SwitchStmt(AstBuilders.call(new NameExpr("variant"), "label"), entries));
        return body;
    }

    /** One arm of the label switch, or its default arm when {@code label} is {@code null}. */
    private static SwitchEntry entry(Expression label, Statement statement) {
        SwitchEntry entry = new SwitchEntry();
        if (label != null) {
            entry.setLabels(NodeList.nodeList(label));
        }
        entry.setStatements(NodeList.nodeList(statement));
        return entry;
    }

    /**
     * One case of a variant. Which case a value is comes from its Java class rather than from
     * whether a payload is present, so a case carrying {@code none} stays distinct from a case
     * carrying nothing at all.
     */
    private ClassOrInterfaceDeclaration caseSource(
            GeneratedUnit unit,
            WitTypes types,
            WitInterface iface,
            String baseName,
            Case declaredCase) {
        String className = Names.type(declaredCase.label());
        if (className.equals(baseName)) {
            throw new BindgenException(
                    "variant case \""
                            + declaredCase.label()
                            + "\" is named after the variant itself, which Java forbids for a"
                            + " nested class");
        }
        for (WitType declared : iface.types()) {
            // A nested case shadows a type of the same name, so the case class would stand in
            // for it wherever the variant names it.
            if (!declared.name().equals(baseName)
                    && className.equals(Names.type(declared.name()))) {
                throw new BindgenException(
                        "variant case \""
                                + declaredCase.label()
                                + "\" has the Java name of type \""
                                + declared.name()
                                + "\", which it would shadow inside the variant");
            }
        }

        ClassOrInterfaceDeclaration type = new ClassOrInterfaceDeclaration();
        type.setName(className).setPublic(true).setStatic(true).setFinal(true);
        type.addExtendedType(baseName);
        type.setJavadocComment("The case {@code " + declaredCase.label() + "}.");

        Expression payload = new NullLiteralExpr();
        if (declaredCase.hasValType()) {
            Type carried = types.javaType(declaredCase.valType(), iface.scope());
            type.addField(carried, VALUE, Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);

            ConstructorDeclaration constructor = type.addConstructor(Modifier.Keyword.PUBLIC);
            constructor.addParameter(carried.clone(), VALUE);
            constructor
                    .getBody()
                    .addStatement(
                            AstBuilders.assign(AstBuilders.thisField(VALUE), new NameExpr(VALUE)));

            type.addMethod(VALUE, Modifier.Keyword.PUBLIC)
                    .setType(carried.clone())
                    .setBody(returning(new NameExpr(VALUE)))
                    .setJavadocComment("The payload this case carries.");
            payload = types.toComponent(new NameExpr(VALUE), declaredCase.valType(), iface.scope());
        }

        type.addMethod("toComponent", Modifier.Keyword.PUBLIC)
                .setType(unit.use(QualifiedTypes.VARIANT_VALUE))
                .setBody(
                        returning(
                                AstBuilders.call(
                                        unit.useName(QualifiedTypes.VARIANT_VALUE),
                                        "of",
                                        AstBuilders.text(declaredCase.label()),
                                        payload)))
                .addMarkerAnnotation("Override");

        type.addMember(equalsMethod(unit, className, declaredCase.hasValType()));
        type.addMember(hashCodeMethod(unit, declaredCase));
        type.addMember(toStringMethod(declaredCase));
        return type;
    }

    private MethodDeclaration equalsMethod(
            GeneratedUnit unit, String className, boolean hasPayload) {
        Expression sameCase = new InstanceOfExpr(new NameExpr("o"), AstBuilders.type(className));
        BlockStmt body = new BlockStmt();
        if (hasPayload) {
            BlockStmt mismatched = new BlockStmt();
            mismatched.addStatement(new ReturnStmt(new BooleanLiteralExpr(false)));
            body.addStatement(
                    new IfStmt(
                            new UnaryExpr(
                                    new EnclosedExpr(sameCase),
                                    UnaryExpr.Operator.LOGICAL_COMPLEMENT),
                            mismatched,
                            null));
            body.addStatement(
                    new ReturnStmt(
                            AstBuilders.call(
                                    unit.useName(QualifiedTypes.OBJECTS),
                                    "equals",
                                    new NameExpr(VALUE),
                                    AstBuilders.field(
                                            AstBuilders.cast(
                                                    AstBuilders.type(className), new NameExpr("o")),
                                            VALUE))));
        } else {
            body.addStatement(new ReturnStmt(sameCase));
        }

        MethodDeclaration method = new MethodDeclaration();
        method.setName("equals").setType(PrimitiveType.booleanType()).setPublic(true);
        method.addParameter(AstBuilders.type("Object"), "o");
        method.setBody(body);
        method.addMarkerAnnotation("Override");
        return method;
    }

    private MethodDeclaration hashCodeMethod(GeneratedUnit unit, Case declaredCase) {
        Expression hash =
                declaredCase.hasValType()
                        ? AstBuilders.call(
                                unit.useName(QualifiedTypes.OBJECTS),
                                "hashCode",
                                new NameExpr(VALUE))
                        : AstBuilders.call(AstBuilders.text(declaredCase.label()), "hashCode");

        MethodDeclaration method = new MethodDeclaration();
        method.setName("hashCode").setType(PrimitiveType.intType()).setPublic(true);
        method.setBody(returning(hash));
        method.addMarkerAnnotation("Override");
        return method;
    }

    private MethodDeclaration toStringMethod(Case declaredCase) {
        Expression described = AstBuilders.text(declaredCase.label());
        if (declaredCase.hasValType()) {
            described =
                    new BinaryExpr(
                            new BinaryExpr(
                                    AstBuilders.text(declaredCase.label() + "("),
                                    new NameExpr(VALUE),
                                    BinaryExpr.Operator.PLUS),
                            AstBuilders.text(")"),
                            BinaryExpr.Operator.PLUS);
        }

        MethodDeclaration method = new MethodDeclaration();
        method.setName("toString").setType(AstBuilders.type("String")).setPublic(true);
        method.setBody(returning(described));
        method.addMarkerAnnotation("Override");
        return method;
    }

    private static List<Case> cases(WitType declared) {
        return ((VariantType) declared.defValType()).cases();
    }

    /**
     * A record is carried as a map keyed by field label, so it converts at the boundary. Every
     * field is written, because a label the map leaves out is stored as a null field rather than
     * reported.
     */
    private GeneratedUnit recordSource(WitInterface iface, WitType declared) {
        String className = Names.type(declared.name());
        GeneratedUnit unit = unitFor(iface);
        WitTypes types = FunctionBindings.forUnit(unit).types();
        WitScope scope = iface.scope();
        RecordType record = (RecordType) declared.defValType();
        types.requireNoHandles(record, scope);

        ClassOrInterfaceDeclaration type = unit.addClass(className);
        type.setJavadocComment(
                "The WIT record {@code "
                        + declared.name()
                        + "}, declared by {@code "
                        + iface.name()
                        + "}.");

        for (LabelValType field : record.fields()) {
            type.addField(
                    types.javaType(field.valType(), scope),
                    Names.member(field.label()),
                    Modifier.Keyword.PRIVATE,
                    Modifier.Keyword.FINAL);
        }

        ConstructorDeclaration constructor = type.addConstructor(Modifier.Keyword.PUBLIC);
        for (LabelValType field : record.fields()) {
            String member = Names.member(field.label());
            constructor.addParameter(types.javaType(field.valType(), scope), member);
            constructor
                    .getBody()
                    .addStatement(
                            AstBuilders.assign(
                                    AstBuilders.thisField(member), new NameExpr(member)));
        }

        for (LabelValType field : record.fields()) {
            String member = Names.member(field.label());
            BlockStmt read = new BlockStmt();
            read.addStatement(new ReturnStmt(new NameExpr(member)));
            type.addMethod(member, Modifier.Keyword.PUBLIC)
                    .setType(types.javaType(field.valType(), scope))
                    .setBody(read);
        }

        type.addMember(lowerRecord(unit, types, scope, record));
        type.addMember(liftRecord(unit, types, scope, record, className));
        type.addMember(recordEquals(unit, record, className));
        type.addMember(recordHashCode(unit, record));
        type.addMember(recordToString(record, className));
        return unit;
    }

    /** {@code toComponent}, which writes every field under the label the ABI knows it by. */
    private MethodDeclaration lowerRecord(
            GeneratedUnit unit, WitTypes types, WitScope scope, RecordType record) {
        BlockStmt body = new BlockStmt();
        body.addStatement(
                AstBuilders.declare(
                        mapOfObject(unit),
                        "fields",
                        AstBuilders.construct(
                                AstBuilders.diamond(unit.use(QualifiedTypes.LINKED_HASH_MAP)))));
        for (LabelValType field : record.fields()) {
            body.addStatement(
                    AstBuilders.call(
                            new NameExpr("fields"),
                            "put",
                            AstBuilders.text(field.label()),
                            types.toComponent(
                                    new NameExpr(Names.member(field.label())),
                                    field.valType(),
                                    scope)));
        }
        body.addStatement(new ReturnStmt(new NameExpr("fields")));

        MethodDeclaration method = new MethodDeclaration();
        method.setName("toComponent").setPublic(true).setType(mapOfObject(unit)).setBody(body);
        method.setJavadocComment(
                "This record as the ABI carries it, which is a map keyed by field label.");
        return method;
    }

    /** {@code fromComponent}, which reads every field back by that same label. */
    private MethodDeclaration liftRecord(
            GeneratedUnit unit,
            WitTypes types,
            WitScope scope,
            RecordType record,
            String className) {
        BlockStmt body = new BlockStmt();
        body.addStatement(
                AstBuilders.declare(
                        mapOfAnything(unit),
                        "fields",
                        AstBuilders.cast(mapOfAnything(unit), new NameExpr("value"))));
        List<Expression> arguments = new ArrayList<>();
        for (LabelValType field : record.fields()) {
            arguments.add(
                    types.fromComponent(
                            AstBuilders.call(
                                    new NameExpr("fields"), "get", AstBuilders.text(field.label())),
                            field.valType(),
                            scope));
        }
        body.addStatement(
                new ReturnStmt(AstBuilders.construct(AstBuilders.type(className), arguments)));

        MethodDeclaration method = new MethodDeclaration();
        method.setName("fromComponent")
                .setPublic(true)
                .setStatic(true)
                .setType(AstBuilders.type(className))
                .setBody(body);
        method.addParameter(AstBuilders.type("Object"), "value");
        method.setJavadocComment("The record a lifted value carries.");
        return method;
    }

    private MethodDeclaration recordEquals(
            GeneratedUnit unit, RecordType record, String className) {
        BlockStmt mismatched = new BlockStmt();
        mismatched.addStatement(new ReturnStmt(new BooleanLiteralExpr(false)));

        BlockStmt body = new BlockStmt();
        body.addStatement(
                new IfStmt(
                        new UnaryExpr(
                                new EnclosedExpr(
                                        new InstanceOfExpr(
                                                new NameExpr("o"), AstBuilders.type(className))),
                                UnaryExpr.Operator.LOGICAL_COMPLEMENT),
                        mismatched,
                        null));
        body.addStatement(
                AstBuilders.declare(
                        AstBuilders.type(className),
                        "that",
                        AstBuilders.cast(AstBuilders.type(className), new NameExpr("o"))));
        List<Expression> comparisons = new ArrayList<>();
        for (LabelValType field : record.fields()) {
            String member = Names.member(field.label());
            comparisons.add(
                    AstBuilders.call(
                            unit.useName(QualifiedTypes.OBJECTS),
                            "equals",
                            new NameExpr(member),
                            AstBuilders.field(new NameExpr("that"), member)));
        }
        body.addStatement(new ReturnStmt(AstBuilders.join(comparisons, BinaryExpr.Operator.AND)));

        MethodDeclaration method = new MethodDeclaration();
        method.setName("equals").setPublic(true).setType(PrimitiveType.booleanType()).setBody(body);
        method.addParameter(AstBuilders.type("Object"), "o");
        method.addMarkerAnnotation("Override");
        return method;
    }

    private MethodDeclaration recordHashCode(GeneratedUnit unit, RecordType record) {
        List<Expression> members = new ArrayList<>();
        for (LabelValType field : record.fields()) {
            members.add(new NameExpr(Names.member(field.label())));
        }
        BlockStmt body = new BlockStmt();
        body.addStatement(
                new ReturnStmt(
                        AstBuilders.call(unit.useName(QualifiedTypes.OBJECTS), "hash", members)));

        MethodDeclaration method = new MethodDeclaration();
        method.setName("hashCode").setPublic(true).setType(PrimitiveType.intType()).setBody(body);
        method.addMarkerAnnotation("Override");
        return method;
    }

    private MethodDeclaration recordToString(RecordType record, String className) {
        List<Expression> parts = new ArrayList<>();
        parts.add(AstBuilders.text(className + "{"));
        String separator = "";
        for (LabelValType field : record.fields()) {
            String member = Names.member(field.label());
            parts.add(AstBuilders.text(separator + member + "="));
            parts.add(new NameExpr(member));
            separator = ", ";
        }
        parts.add(AstBuilders.text("}"));

        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(AstBuilders.join(parts, BinaryExpr.Operator.PLUS)));

        MethodDeclaration method = new MethodDeclaration();
        method.setName("toString")
                .setPublic(true)
                .setType(AstBuilders.type("String"))
                .setBody(body);
        method.addMarkerAnnotation("Override");
        return method;
    }

    private ClassOrInterfaceType mapOfObject(GeneratedUnit unit) {
        return AstBuilders.generic(
                unit.use(QualifiedTypes.MAP),
                AstBuilders.type("String"),
                AstBuilders.type("Object"));
    }

    /** {@code Map<?, ?>}, which reads a lifted record without an unchecked cast. */
    private ClassOrInterfaceType mapOfAnything(GeneratedUnit unit) {
        return AstBuilders.generic(
                unit.use(QualifiedTypes.MAP), new WildcardType(), new WildcardType());
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
            if (resource.constructor() != null) {
                MethodDeclaration factory =
                        hostSignature(
                                bindings,
                                resource,
                                resource.constructor(),
                                Names.member(resource.name()));
                factory.setJavadocComment("Makes a {@code " + resource.name() + "}.");
                type.addMember(factory);
            }
            for (WitFunction function : resource.statics()) {
                MethodDeclaration method =
                        hostSignature(
                                bindings,
                                resource,
                                function,
                                Names.qualifiedMember(resource.name(), function.name()));
                method.setJavadocComment(
                        "The static {@code " + resource.name() + "." + function.name() + "}.");
                type.addMember(method);
            }
        }
        return unit;
    }

    /**
     * One signature on a {@code Host}, named by the caller because neither a constructor nor a
     * static is called what its own WIT name says. A returned handle is named by hand too, since
     * an {@code own} has no Java type of its own.
     */
    private MethodDeclaration hostSignature(
            FunctionBindings bindings, WitResource resource, WitFunction function, String name) {
        if (!resource.returnsOwnHandle(function)) {
            return bindings.signature(function, 0).setName(name);
        }
        MethodDeclaration method = new MethodDeclaration();
        method.setName(name);
        method.setType(AstBuilders.type(Names.type(resource.name())));
        method.removeBody();
        return bindings.addParameters(method, function, 0);
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
        ResourceFields fields = new ResourceFields(iface);
        for (WitResource resource : iface.resources()) {
            for (WitFunction function : resourceFunctions(resource)) {
                // Read by the resource wrapper, which is a class of its own in this package.
                type.addField(
                        unit.use(QualifiedTypes.COMPONENT_FUNCTION),
                        fields.of(function),
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
            addResourceNarrowing(body, bindings, fields, resource);
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
            WitFunction maker = resource.constructor();
            if (maker != null) {
                MethodDeclaration factory =
                        guestFactory(
                                unit,
                                bindings,
                                resource,
                                maker,
                                fields.of(maker),
                                Names.member(resource.name()));
                factory.setJavadocComment(
                        "Makes a {@code " + resource.name() + "} inside the component.");
                type.addMember(factory);
            }
            for (WitFunction function : resource.statics()) {
                type.addMember(guestStatic(unit, bindings, fields, resource, function));
            }
        }
        return unit;
    }

    /**
     * A static has no handle to hold, so it belongs on the {@code Guest} rather than on the
     * wrapper. One handing back an {@code own} to its own resource wraps it the way a constructor
     * does, and one handing back an ordinary value is called like any other export.
     */
    private MethodDeclaration guestStatic(
            GeneratedUnit unit,
            FunctionBindings bindings,
            ResourceFields fields,
            WitResource resource,
            WitFunction function) {
        String name = Names.qualifiedMember(resource.name(), function.name());
        MethodDeclaration method =
                resource.returnsOwnHandle(function)
                        ? guestFactory(
                                unit, bindings, resource, function, fields.of(function), name)
                        : bindings.callMethod(
                                        function,
                                        0,
                                        AstBuilders.thisField(fields.of(function)),
                                        List.of())
                                .setName(name);
        method.setJavadocComment(
                "The static {@code " + resource.name() + "." + function.name() + "}.");
        return method;
    }

    /**
     * The Canonical ABI names a resource's functions rather than nesting them, and no function's
     * own type names the handle it takes or returns, so each one is described by hand.
     */
    private void addResourceNarrowing(
            BlockStmt body,
            FunctionBindings bindings,
            ResourceFields fields,
            WitResource resource) {
        if (resource.constructor() != null) {
            body.addStatement(
                    narrow(
                            fields.of(resource.constructor()),
                            "[constructor]" + resource.name(),
                            bindings.descriptors(
                                    resource.constructor(), bindings.resourceDescriptor(), null)));
        }
        for (WitFunction method : resource.methods()) {
            body.addStatement(
                    narrow(
                            fields.of(method),
                            "[method]" + resource.name() + "." + method.name(),
                            bindings.descriptors(method, null, bindings.resourceDescriptor())));
        }
        for (WitFunction function : resource.statics()) {
            Expression result =
                    resource.returnsOwnHandle(function) ? bindings.resourceDescriptor() : null;
            body.addStatement(
                    narrow(
                            fields.of(function),
                            "[static]" + resource.name() + "." + function.name(),
                            bindings.descriptors(function, result, null)));
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

        ResourceFields fields = new ResourceFields(iface);

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
                            AstBuilders.field(new NameExpr("owner"), fields.of(method)),
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

    /** A call handing back an {@code own} handle, which the wrapper is built around. */
    private MethodDeclaration guestFactory(
            GeneratedUnit unit,
            FunctionBindings bindings,
            WitResource resource,
            WitFunction function,
            String field,
            String name) {
        String className = Names.type(resource.name());

        Expression made =
                AstBuilders.call(
                        AstBuilders.thisField(field), "apply", bindings.callArguments(function, 0));
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
        factory.setName(name).setPublic(true);
        factory.setType(AstBuilders.type(className));
        bindings.addParameters(factory, function, 0);
        factory.setBody(body);
        return factory;
    }

    private GeneratedUnit unitFor(WitInterface iface) {
        return new GeneratedUnit(iface.scope().javaPackage(), generatedBy);
    }

    private static List<WitFunction> resourceFunctions(WitResource resource) {
        List<WitFunction> all = new ArrayList<>();
        if (resource.constructor() != null) {
            all.add(resource.constructor());
        }
        all.addAll(resource.methods());
        all.addAll(resource.statics());
        return all;
    }

    /**
     * Names the field holding each of an interface's narrowed resource functions. A constructor is
     * named after its resource and everything else after itself, and a name two functions would
     * otherwise share is numbered apart.
     */
    private static final class ResourceFields {

        private final Map<WitFunction, String> names = new IdentityHashMap<>();

        ResourceFields(WitInterface iface) {
            Set<String> taken = new HashSet<>();
            for (WitResource resource : iface.resources()) {
                for (WitFunction function : resourceFunctions(resource)) {
                    String base = Names.qualifiedMember(resource.name(), function.name());
                    String name = base;
                    int next = 2;
                    while (!taken.add(name)) {
                        name = base + next;
                        next++;
                    }
                    names.put(function, name);
                }
            }
        }

        String of(WitFunction function) {
            return names.get(function);
        }
    }

    private static String constantOf(String label) {
        return label.toUpperCase().replace('-', '_');
    }
}
