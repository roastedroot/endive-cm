package run.endive.cm.bindgen;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import run.endive.cm.types.DefValType;
import run.endive.cm.types.Type;

/**
 * Generates the bindings for one world.
 *
 * <p>The world becomes a class of its own, and each interface it names becomes a Java package
 * mirroring the WIT id, holding the interface plus whatever types it declares. Exports sit under an
 * {@code exports} package, which is what lets a world import and export one name at once.
 */
final class WorldGenerator {

    /** The local {@code instantiate} collects what satisfies each of the world's imports. */
    private static final String VALUES = "values";

    private final WitWorld world;
    private final String base;
    private final String generatedBy;

    private WorldGenerator(WitWorld world, String base, String generatedBy) {
        this.world = world;
        this.base = base;
        this.generatedBy = generatedBy;
    }

    static List<GeneratedUnit> generate(WitWorld world, String base, String generatedBy) {
        return new WorldGenerator(world, base, generatedBy).sources();
    }

    private List<GeneratedUnit> sources() {
        List<GeneratedUnit> sources = new ArrayList<>();
        InterfaceGenerator interfaces = new InterfaceGenerator(generatedBy);
        for (WitInterface imported : world.importedInterfaces()) {
            imported.scope().withJavaPackage(imported.javaPackage(base, false));
        }
        for (WitInterface exported : world.exportedInterfaces()) {
            exported.scope().withJavaPackage(exported.javaPackage(base, true));
        }
        for (WitInterface imported : world.importedInterfaces()) {
            sources.addAll(interfaces.sources(imported, false));
        }
        for (WitInterface exported : world.exportedInterfaces()) {
            sources.addAll(interfaces.sources(exported, true));
        }
        sources.add(worldSource());
        return sources;
    }

    private GeneratedUnit worldSource() {
        String className = Names.type(world.name());
        GeneratedUnit unit = new GeneratedUnit(base, generatedBy);
        FunctionBindings bindings = FunctionBindings.forUnit(unit);

        ClassOrInterfaceDeclaration type = unit.addClass(className);
        type.setJavadocComment("Bindings for the WIT world {@code " + world.qualifiedName() + "}.");

        // Only an import needs its type written out, since an export's is read off the instance.
        for (WitFunction imported : world.imports()) {
            type.addFieldWithInitializer(
                    unit.use(QualifiedTypes.FUNC_TYPE),
                    constantName(imported.name()),
                    bindings.funcType(imported, 0, null, Map.of()),
                    Modifier.Keyword.PRIVATE,
                    Modifier.Keyword.STATIC,
                    Modifier.Keyword.FINAL);
        }
        type.addMember(importsInterface(bindings));

        type.addField(
                unit.use(QualifiedTypes.COMPONENT_INSTANCE),
                "instance",
                Modifier.Keyword.PRIVATE,
                Modifier.Keyword.FINAL);
        for (WitFunction exported : world.exports()) {
            type.addField(
                    unit.use(QualifiedTypes.COMPONENT_FUNCTION),
                    fieldName(exported.name()),
                    Modifier.Keyword.PRIVATE,
                    Modifier.Keyword.FINAL);
        }
        for (WitInterface exported : world.exportedInterfaces()) {
            type.addField(
                    AstBuilders.type(guestType(exported)),
                    fieldName(exported.simpleName()),
                    Modifier.Keyword.PRIVATE,
                    Modifier.Keyword.FINAL);
        }

        addConstructor(type, unit, bindings);
        addInstantiate(type, unit, bindings, className);

        MethodDeclaration accessor =
                type.addMethod("instance", Modifier.Keyword.PUBLIC)
                        .setType(unit.use(QualifiedTypes.COMPONENT_INSTANCE));
        accessor.setBody(returning(new NameExpr("instance")));
        accessor.setJavadocComment("The component instance behind these bindings.");

        for (WitFunction exported : world.exports()) {
            type.addMember(
                    bindings.callMethod(
                            exported,
                            0,
                            AstBuilders.thisField(fieldName(exported.name())),
                            List.of()));
        }
        for (WitInterface exported : world.exportedInterfaces()) {
            MethodDeclaration reader =
                    type.addMethod(Names.member(exported.simpleName()), Modifier.Keyword.PUBLIC)
                            .setType(AstBuilders.type(guestType(exported)));
            reader.setBody(returning(new NameExpr(fieldName(exported.simpleName()))));
            reader.setJavadocComment("The exported interface {@code " + exported.name() + "}.");
        }
        return unit;
    }

    /** One embedder object may implement the world and its imported interfaces together. */
    private ClassOrInterfaceDeclaration importsInterface(FunctionBindings bindings) {
        ClassOrInterfaceDeclaration imports = new ClassOrInterfaceDeclaration();
        imports.setInterface(true).setName("Imports").setPublic(true);
        imports.setJavadocComment("The world's imports, which the embedder implements.");
        for (WitFunction imported : world.imports()) {
            imports.addMember(bindings.signature(imported, 0));
        }
        for (WitInterface imported : world.importedInterfaces()) {
            MethodDeclaration reader = new MethodDeclaration();
            reader.setName(Names.member(imported.simpleName()));
            reader.setType(AstBuilders.type(hostType(imported)));
            reader.removeBody();
            reader.setJavadocComment("The imported interface {@code " + imported.name() + "}.");
            imports.addMember(reader);
        }
        return imports;
    }

    /** Each export is narrowed once here, so a mismatch fails at instantiation, not at a call. */
    private void addConstructor(
            ClassOrInterfaceDeclaration type, GeneratedUnit unit, FunctionBindings bindings) {
        ConstructorDeclaration constructor = type.addConstructor(Modifier.Keyword.PRIVATE);
        constructor.addParameter(unit.use(QualifiedTypes.COMPONENT_INSTANCE), "instance");
        BlockStmt body = constructor.getBody();
        body.addStatement(
                AstBuilders.assign(AstBuilders.thisField("instance"), new NameExpr("instance")));
        for (WitFunction exported : world.exports()) {
            Expression export =
                    AstBuilders.call(
                            new NameExpr("instance"), "export", AstBuilders.text(exported.name()));
            body.addStatement(
                    AstBuilders.assign(
                            AstBuilders.thisField(fieldName(exported.name())),
                            AstBuilders.call(
                                    export, "typed", bindings.descriptors(exported, null, null))));
        }
        for (WitInterface exported : world.exportedInterfaces()) {
            Expression instance =
                    AstBuilders.call(
                            new NameExpr("instance"),
                            "exportedInstance",
                            AstBuilders.text(exported.name()));
            body.addStatement(
                    AstBuilders.assign(
                            AstBuilders.thisField(fieldName(exported.simpleName())),
                            AstBuilders.construct(
                                    AstBuilders.type(guestType(exported)), instance)));
        }
    }

    private void addInstantiate(
            ClassOrInterfaceDeclaration type,
            GeneratedUnit unit,
            FunctionBindings bindings,
            String className) {
        MethodDeclaration method =
                type.addMethod("instantiate", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC)
                        .setType(AstBuilders.type(className));
        method.addParameter(unit.use(QualifiedTypes.COMPONENT_STORE), "store");
        method.addParameter(unit.use(QualifiedTypes.WASM_COMPONENT), "component");
        method.addParameter(AstBuilders.type("Imports"), "imports");
        method.setJavadocComment(
                "Instantiates {@code component}, satisfying its imports with {@code imports}.");
        if (usesKind(DefValType.Kind.LIST)) {
            // A list arrives raw, and only the generated cast names its element type.
            method.addSingleMemberAnnotation(SuppressWarnings.class, AstBuilders.text("unchecked"));
        }

        BlockStmt body = new BlockStmt();
        body.addStatement(
                AstBuilders.declare(
                        AstBuilders.generic(
                                unit.use(QualifiedTypes.MAP),
                                AstBuilders.type("String"),
                                AstBuilders.type("Object")),
                        VALUES,
                        AstBuilders.construct(
                                AstBuilders.diamond(unit.use(QualifiedTypes.LINKED_HASH_MAP)))));
        for (WitFunction imported : world.imports()) {
            Expression function =
                    AstBuilders.call(
                            unit.useName(QualifiedTypes.HOST_FUNCTION),
                            "of",
                            new NameExpr("store"),
                            new NameExpr(constantName(imported.name())),
                            bindings.importLambda(new NameExpr("imports"), imported, 0));
            body.addStatement(
                    AstBuilders.call(
                            new NameExpr(VALUES),
                            "put",
                            AstBuilders.text(imported.name()),
                            function));
        }
        HostWiring wiring = new HostWiring(unit, bindings);
        for (WitInterface imported : world.importedInterfaces()) {
            wiring.addTo(body, imported, VALUES);
        }

        Expression linker =
                AstBuilders.call(unit.useName(QualifiedTypes.COMPONENT_LINKER), "builder");
        Expression instance =
                AstBuilders.call(
                        AstBuilders.call(linker, "build"),
                        "instantiate",
                        new NameExpr("store"),
                        new NameExpr("component"),
                        new NameExpr(VALUES));
        body.addStatement(
                new ReturnStmt(AstBuilders.construct(AstBuilders.type(className), instance)));
        method.setBody(body);
    }

    private static BlockStmt returning(Expression value) {
        BlockStmt body = new BlockStmt();
        body.addStatement(new ReturnStmt(value));
        return body;
    }

    /** Whether any imported interface declares a type of {@code kind}. */
    private boolean usesKind(DefValType.Kind kind) {
        for (WitInterface imported : world.importedInterfaces()) {
            for (Type declared : HostWiring.compoundTypes(imported).values()) {
                if (declared.defValType().kind() == kind) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A field named like the first segment of a qualified type shadows it, so the field is renamed
     * rather than the reference, which Java gives no way to write unambiguously.
     */
    private String fieldName(String witName) {
        String name = Names.member(witName);
        return shadowedSegments().contains(name) ? name + "_" : name;
    }

    private Set<String> shadowedSegments() {
        Set<String> segments = new HashSet<>();
        for (WitInterface imported : world.importedInterfaces()) {
            segments.add(firstSegment(hostType(imported)));
        }
        for (WitInterface exported : world.exportedInterfaces()) {
            segments.add(firstSegment(guestType(exported)));
        }
        return segments;
    }

    private static String firstSegment(String qualified) {
        int dot = qualified.indexOf('.');
        return dot < 0 ? qualified : qualified.substring(0, dot);
    }

    private static String hostType(WitInterface imported) {
        return imported.scope().javaPackage() + ".Host";
    }

    private static String guestType(WitInterface exported) {
        return exported.scope().javaPackage() + ".Guest";
    }

    private static String constantName(String witName) {
        return witName.toUpperCase().replace('-', '_') + "_FUNC";
    }
}
