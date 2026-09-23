package run.endive.cm.bindgen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/**
 * Generation is checked by compiling an annotated source and comparing the result against a
 * checked-in expected source, the way Endive checks its own processors.
 *
 * <p>WIT is reached through the class path here, because the in-memory file manager behind these
 * compilations has no class output, so Maven has copied no resources there.
 */
class BindgenProcessorTest {

    private static final List<File> WIT_ON_CLASSPATH =
            List.of(new File("src/test/resources"), new File("target/test-classes"));

    @Test
    void inlineWitNeedsNoFile() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.InlineHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(inline = \"package my:project;\\n"
                                        + "world hello-world {\\n"
                                        + "  import name: func() -> string;\\n"
                                        + "  export greet: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class InlineHost {}\n"));

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.HelloWorld")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource("goldens/HelloWorldHost/HelloWorld.java"));
    }

    /**
     * A kind with no binding yet is refused where a function names it, so an interface may declare
     * one that nothing uses.
     */
    @Test
    void declaringAnUnusedTypeOfAnUnboundKindIsAllowed() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.UnusedTypeHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"unused-record\", inline ="
                                        + " \"package my:project;\\n"
                                        + "interface logging {\\n"
                                        + "  record config { verbose: bool }\\n"
                                        + "  log: func(msg: string);\\n"
                                        + "}\\n"
                                        + "world unused-record {\\n"
                                        + "  import logging;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class UnusedTypeHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of("endive.testing.UnusedRecord", "endive.testing.my.project.logging.Host"));
    }

    /** Every world generates a package tree mirroring the WIT ids, which is what this pins. */
    private static void assertGenerated(Compilation compilation, List<String> expected) {
        List<String> actual =
                compilation.generatedSourceFiles().stream()
                        .map(JavaFileObject::getName)
                        .map(BindgenProcessorTest::qualifiedName)
                        .sorted()
                        .collect(Collectors.toList());
        assertEquals(expected.stream().sorted().collect(Collectors.toList()), actual);
    }

    private static String qualifiedName(String path) {
        return path.replace("/SOURCE_OUTPUT/", "").replace(".java", "").replace('/', '.');
    }

    @Test
    void generatesExportedResourceBindings() {
        Compilation compilation = compile("ExportedResourceHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.ExportSomeResources",
                        "endive.testing.exports.example.exportedresources.logging.Guest",
                        "endive.testing.exports.example.exportedresources.logging.Level",
                        "endive.testing.exports.example.exportedresources.logging.Logger"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.ExportSomeResources")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/ExportedResourceHost/ExportSomeResources.java"));
        assertThat(compilation)
                .generatedSourceFile(
                        "endive.testing.exports.example.exportedresources.logging.Guest")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/ExportedResourceHost/exports_example_exportedresources_logging_Guest.java"));
        assertThat(compilation)
                .generatedSourceFile(
                        "endive.testing.exports.example.exportedresources.logging.Level")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/ExportedResourceHost/exports_example_exportedresources_logging_Level.java"));
        assertThat(compilation)
                .generatedSourceFile(
                        "endive.testing.exports.example.exportedresources.logging.Logger")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/ExportedResourceHost/exports_example_exportedresources_logging_Logger.java"));
    }

    /** Flags are declared by two interfaces at once, so each gets a wrapper of its own. */
    @Test
    void generatesFlagsBindings() {
        Compilation compilation = compile("FlagsHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.FlagTypes",
                        "endive.testing.example.flagtypes.permissions.Host",
                        "endive.testing.example.flagtypes.permissions.Permission",
                        "endive.testing.exports.example.flagtypes.runner.Guest",
                        "endive.testing.exports.example.flagtypes.runner.Mode"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.FlagTypes")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource("goldens/FlagsHost/FlagTypes.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.flagtypes.permissions.Host")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/FlagsHost/example_flagtypes_permissions_Host.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.flagtypes.permissions.Permission")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/FlagsHost/example_flagtypes_permissions_Permission.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.exports.example.flagtypes.runner.Guest")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/FlagsHost/exports_example_flagtypes_runner_Guest.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.exports.example.flagtypes.runner.Mode")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/FlagsHost/exports_example_flagtypes_runner_Mode.java"));
    }

    @Test
    void generatesHelloWorldBindings() {
        Compilation compilation = compile("HelloWorldHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(compilation, List.of("endive.testing.HelloWorld"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.HelloWorld")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource("goldens/HelloWorldHost/HelloWorld.java"));
    }

    @Test
    void generatesImportedResourceBindings() {
        Compilation compilation = compile("ImportedResourceHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.ImportSomeResources",
                        "endive.testing.example.importedresources.logging.Host",
                        "endive.testing.example.importedresources.logging.Level",
                        "endive.testing.example.importedresources.logging.Logger"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.ImportSomeResources")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/ImportedResourceHost/ImportSomeResources.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.importedresources.logging.Host")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/ImportedResourceHost/example_importedresources_logging_Host.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.importedresources.logging.Level")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/ImportedResourceHost/example_importedresources_logging_Level.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.importedresources.logging.Logger")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/ImportedResourceHost/example_importedresources_logging_Logger.java"));
    }

    @Test
    void generatesInterfaceImportBindings() {
        Compilation compilation = compile("InterfaceImportsHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.WithImports",
                        "endive.testing.example.interfaceimports.logging.Host",
                        "endive.testing.example.interfaceimports.logging.Level"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.WithImports")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/InterfaceImportsHost/WithImports.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.interfaceimports.logging.Host")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/InterfaceImportsHost/example_interfaceimports_logging_Host.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.interfaceimports.logging.Level")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/InterfaceImportsHost/example_interfaceimports_logging_Level.java"));
    }

    /** A variant is nominal both ways round, so an imported and an exported one look the same. */
    @Test
    void generatesVariantBindings() {
        Compilation compilation = compile("VariantHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.VariantTypes",
                        "endive.testing.example.varianttypes.commands.Command",
                        "endive.testing.example.varianttypes.commands.Host",
                        "endive.testing.exports.example.varianttypes.replies.Guest",
                        "endive.testing.exports.example.varianttypes.replies.Reply"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.VariantTypes")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource("goldens/VariantHost/VariantTypes.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.varianttypes.commands.Command")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/VariantHost/example_varianttypes_commands_Command.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.varianttypes.commands.Host")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/VariantHost/example_varianttypes_commands_Host.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.exports.example.varianttypes.replies.Guest")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/VariantHost/exports_example_varianttypes_replies_Guest.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.exports.example.varianttypes.replies.Reply")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/VariantHost/exports_example_varianttypes_replies_Reply.java"));
    }

    /** A payload of a generic type is the cast Java cannot check, so the case suppresses it. */
    @Test
    void aVariantCaseCarryingAListIsSuppressed() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.ListPayloadHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"list-payload\", inline ="
                                        + " \"package my:project;\\n"
                                        + "interface blobs {\\n"
                                        + "  variant blob { empty, bytes(list<u8>) }\\n"
                                        + "  take: func(b: blob);\\n"
                                        + "}\\n"
                                        + "world list-payload {\\n"
                                        + "  import blobs;\\n"
                                        + "}\\n\")\n"
                                        + "public class ListPayloadHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.my.project.blobs.Blob")
                .contentsAsUtf8String()
                .contains("@SuppressWarnings(\"unchecked\")");
    }

    /** A case named after the variant would be a nested class sharing its enclosing class's name. */
    @Test
    void aVariantCaseNamedAfterItsVariantIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.SelfNamedCaseHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"self-named\", inline ="
                                        + " \"package my:project;\\n"
                                        + "interface shapes {\\n"
                                        + "  variant shape { shape(u32), blank }\\n"
                                        + "  pick: func() -> shape;\\n"
                                        + "}\\n"
                                        + "world self-named {\\n"
                                        + "  import shapes;\\n"
                                        + "}\\n\")\n"
                                        + "public class SelfNamedCaseHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("is named after the variant itself");
    }

    @Test
    void generatesStaticResourceFunctionBindings() {
        Compilation compilation = compile("StaticFunctionHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.StaticFunctions",
                        "endive.testing.example.staticfunctions.hostcounters.Counter",
                        "endive.testing.example.staticfunctions.hostcounters.Host",
                        "endive.testing.exports.example.staticfunctions.guestcounters.Guest",
                        "endive.testing.exports.example.staticfunctions.guestcounters.Tally"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.StaticFunctions")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/StaticFunctionHost/StaticFunctions.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.staticfunctions.hostcounters.Counter")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/StaticFunctionHost/example_staticfunctions_hostcounters_Counter.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.staticfunctions.hostcounters.Host")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/StaticFunctionHost/example_staticfunctions_hostcounters_Host.java"));
        assertThat(compilation)
                .generatedSourceFile(
                        "endive.testing.exports.example.staticfunctions.guestcounters.Guest")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/StaticFunctionHost/exports_example_staticfunctions_guestcounters_Guest.java"));
        assertThat(compilation)
                .generatedSourceFile(
                        "endive.testing.exports.example.staticfunctions.guestcounters.Tally")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/StaticFunctionHost/exports_example_staticfunctions_guestcounters_Tally.java"));
    }

    /** A handle to another resource has no Java type to name, so a static returning one is refused. */
    @Test
    void aStaticReturningAnotherResourcesHandleIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.OtherHandleHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"w\", inline = \"package t:t;\\n"
                                        + "interface i {\\n"
                                        + "  resource a { constructor(); }\\n"
                                        + "  resource b { make: static func() -> a; }\\n"
                                        + "}\\n"
                                        + "world w {\\n"
                                        + "  import i;\\n"
                                        + "}\\n\")\n"
                                        + "public class OtherHandleHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("own is not yet supported");
    }

    @Test
    void generatesOptionBindings() {
        Compilation compilation = compile("OptionHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.OptionTypes",
                        "endive.testing.example.optiontypes.maybe.Host"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.OptionTypes")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource("goldens/OptionHost/OptionTypes.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.optiontypes.maybe.Host")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/OptionHost/example_optiontypes_maybe_Host.java"));
    }

    /**
     * A nullable {@code T} cannot tell {@code some(none)} from {@code none}, so a nested option is
     * refused. The payload is named by index, so one reached through an alias is refused too.
     */
    @Test
    void aNestedOptionIsRefused() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.NestedOptionHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"nested-option\", inline ="
                                        + " \"package my:project;\\n"
                                        + "interface maybe {\\n"
                                        + "  type maybe-num = option<u32>;\\n"
                                        + "  echo: func(value: option<maybe-num>);\\n"
                                        + "}\\n"
                                        + "world nested-option {\\n"
                                        + "  import maybe;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class NestedOptionHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("option<option<T>> is not supported");
    }

    @Test
    void generatesEveryKindOfWorldExport() {
        Compilation compilation = compile("WorldExportKindsHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.WithExports",
                        "endive.testing.exports.environment.Guest",
                        "endive.testing.exports.example.worldexports.units.Guest"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.WithExports")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/WorldExportKindsHost/WithExports.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.exports.environment.Guest")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/WorldExportKindsHost/exports_environment_Guest.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.exports.example.worldexports.units.Guest")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/WorldExportKindsHost/exports_example_worldexports_units_Guest.java"));
    }

    @Test
    void generatesWorldExportBindings() {
        Compilation compilation = compile("WorldExportsHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.HelloWorld",
                        "endive.testing.exports.demo.Guest",
                        "endive.testing.my.project.host.Host"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.HelloWorld")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource("goldens/WorldExportsHost/HelloWorld.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.exports.demo.Guest")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/WorldExportsHost/exports_demo_Guest.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.my.project.host.Host")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/WorldExportsHost/my_project_host_Host.java"));
    }

    @Test
    void generatesWorldImportBindings() {
        Compilation compilation = compile("WorldImportsHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation, List.of("endive.testing.MyWorld", "endive.testing.mycustomhost.Host"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.MyWorld")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource("goldens/WorldImportsHost/MyWorld.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.mycustomhost.Host")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/WorldImportsHost/mycustomhost_Host.java"));
    }

    /** A tuple has no WIT name, so nothing is generated for it and the runtime carries it. */
    @Test
    void generatesTupleBindings() {
        Compilation compilation = compile("TupleHost.java");

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of("endive.testing.TupleTypes", "endive.testing.example.tuples.points.Host"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.TupleTypes")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource("goldens/TupleHost/TupleTypes.java"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.tuples.points.Host")
                .hasSourceEquivalentTo(
                        JavaFileObjects.forResource(
                                "goldens/TupleHost/example_tuples_points_Host.java"));
    }

    @Test
    void aTupleWiderThanTheRuntimeCarriesIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.WideTupleHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"wide\", inline ="
                                        + " \"package t:w;\\n"
                                        + "interface points {\\n"
                                        + "  wide: func() -> tuple<u32, u32, u32, u32, u32, u32,"
                                        + " u32, u32, u32>;\\n"
                                        + "}\\n"
                                        + "world wide {\\n"
                                        + "  import points;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class WideTupleHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("a tuple of 9 elements is not yet supported");
    }

    /** An element is named by its class when a tuple is lifted, so a list has no way through. */
    @Test
    void aTupleElementThatCannotBeNamedByItsClassIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.NestedTupleHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"nested\", inline ="
                                        + " \"package t:n;\\n"
                                        + "interface points {\\n"
                                        + "  nested: func() -> tuple<string, list<u8>>;\\n"
                                        + "}\\n"
                                        + "world nested {\\n"
                                        + "  import points;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class NestedTupleHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining("a tuple element of kind list is not yet supported");
    }

    /**
     * WIT reserves fewer words than Java does, so a name like {@code new} is a WIT name but not a
     * Java one and has to be escaped. Generation used to fail on one with a parser crash.
     */
    @Test
    void witNamesJavaReservesAreEscaped() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.ReservedHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(inline = \"package k:w;\\n"
                                        + "world kw {\\n"
                                        + "  import new: func(class: string) -> string;\\n"
                                        + "  export final: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class ReservedHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.Kw")
                .contentsAsUtf8String()
                .contains("String new_(String class_)");
    }

    @Test
    void aMissingWitFileIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.MissingHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"nowhere\")\n"
                                        + "public class MissingHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("wit/nowhere.wit");
    }

    @Test
    void anUnknownWorldIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.WrongWorldHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"other\", path ="
                                        + " \"wit/hello-world.wit\")\n"
                                        + "public class WrongWorldHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("world \"other\" was not found");
    }

    @Test
    void invalidWitIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.BadWitHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(inline = \"not valid wit {{{\")\n"
                                        + "public class BadWitHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("WIT could not be encoded");
    }

    @Test
    void givingBothInlineAndPathIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.BothHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(inline = \"package a:b;\", path ="
                                        + " \"wit/hello-world.wit\")\n"
                                        + "public class BothHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("only one of inline and path");
    }

    private static Compilation compile(String resource) {
        return compile(JavaFileObjects.forResource(resource));
    }

    private static Compilation compile(javax.tools.JavaFileObject source) {
        return javac().withProcessors(new BindgenProcessor())
                .withClasspathFrom(BindgenProcessorTest.class.getClassLoader())
                .compile(source);
    }
}
