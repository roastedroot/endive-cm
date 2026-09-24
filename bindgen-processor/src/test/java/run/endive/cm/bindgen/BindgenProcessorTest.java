package run.endive.cm.bindgen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
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
                .contentsAsUtf8String()
                .contains("public static HelloWorld instantiate(");
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
                                        + "@Bindgen(world = \"unused-future\", inline ="
                                        + " \"package my:project;\\n"
                                        + "interface logging {\\n"
                                        + "  type pending = future<u32>;\\n"
                                        + "  log: func(msg: string);\\n"
                                        + "}\\n"
                                        + "world unused-future {\\n"
                                        + "  import logging;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class UnusedTypeHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of("endive.testing.UnusedFuture", "endive.testing.my.project.logging.Host"));
    }

    /**
     * A type that converts at the boundary lowers each of its own members through the same pair,
     * so an {@code option} member crosses as a nested variant rather than as a Java null. Nothing
     * else pins the way one kind composes with another.
     */
    @Test
    void aTypeLowersItsMembersThroughTheirOwnConversions() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.SeamHost",
                                "package endive.testing;\n"
                                    + "import run.endive.cm.runtime.Bindgen;\n"
                                    + "@Bindgen(world = \"seams\", inline = \"package"
                                    + " example:seams;\\n"
                                    + "interface types {\\n"
                                    + "  record profile { name: string, nickname: option<string>"
                                    + " }\\n"
                                    + "  variant event { quiet, noted(option<string>) }\\n"
                                    + "  describe: func(p: profile) -> profile;\\n"
                                    + "  note: func(e: event) -> event;\\n"
                                    + "  batch: func(items: list<profile>) -> list<profile>;\\n"
                                    + "}\\n"
                                    + "world seams {\\n"
                                    + "  import types;\\n"
                                    + "  export go: func();\\n"
                                    + "}\\n"
                                    + "\")\n"
                                    + "public class SeamHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.seams.types.Profile")
                .contentsAsUtf8String()
                .contains("Optional.ofNullable(nickname)");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.seams.types.Event")
                .contentsAsUtf8String()
                .contains("VariantValue.of(\"quiet\", null)");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.seams.types.Event")
                .contentsAsUtf8String()
                .contains("VariantValue.of(\"none\", null)");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.Seams")
                .contentsAsUtf8String()
                .contains("Profile.fromComponent(element)");
    }

    /**
     * A container inside a container converts through a lambda inside a lambda, so the two
     * parameters have to differ or the generated source does not compile.
     */
    @Test
    void nestedContainersDoNotShadowTheirConversionParameters() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.NestHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"nest\", inline = \"package"
                                        + " example:nest;\\n"
                                        + "interface types {\\n"
                                        + "  enum level { low, high }\\n"
                                        + "  grid: func(rows: list<list<level>>) ->"
                                        + " list<list<level>>;\\n"
                                        + "  deep: func(v: option<list<option<u32>>>) ->"
                                        + " option<u32>;\\n"
                                        + "}\\n"
                                        + "world nest {\\n"
                                        + "  import types;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n"
                                        + "\")\n"
                                        + "public class NestHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.Nest")
                .contentsAsUtf8String()
                .contains("element1 ->");
    }

    /** A nested case class stands in for any type of the same name wherever the variant names it. */
    @Test
    void aVariantCaseShadowingAnotherTypeIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.ShadowingCaseHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"shadowing\", inline ="
                                        + " \"package example:shadowing;\\n"
                                        + "interface types {\\n"
                                        + "  record point { x: u32 }\\n"
                                        + "  variant shape { point, circle(point) }\\n"
                                        + "  draw: func(s: shape) -> shape;\\n"
                                        + "}\\n"
                                        + "world shadowing {\\n"
                                        + "  import types;\\n"
                                        + "}\\n\")\n"
                                        + "public class ShadowingCaseHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("which it would shadow inside the variant");
    }

    /**
     * Generated code introduces locals and lambda parameters of its own, and a WIT name is free to
     * be any of them. Shadowing one is either a compile error or, for a record, silently wrong.
     */
    @Test
    void generatedNamesGiveWayToWitNames() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.ShadowHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"shadow\", inline ="
                                        + " \"package example:shadow;\\n"
                                        + "interface types {\\n"
                                        + "  record boxed { fields: string, that: u32, o: bool }\\n"
                                        + "  keep: func(b: boxed) -> boxed;\\n"
                                        + "}\\n"
                                        + "interface ops {\\n"
                                        + "  enum tone { low, high }\\n"
                                        + "  sift: func(element: list<tone>) -> list<tone>;\\n"
                                        + "  pick: func(some: option<u32>) -> option<u32>;\\n"
                                        + "}\\n"
                                        + "world shadow {\\n"
                                        + "  import types;\\n"
                                        + "  export ops;\\n"
                                        + "}\\n\")\n"
                                        + "public class ShadowHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.shadow.types.Boxed")
                .contentsAsUtf8String()
                .contains("Objects.equals(that, that_.that)");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.shadow.types.Boxed")
                .contentsAsUtf8String()
                .contains("fields_.put(\"fields\", fields)");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.exports.example.shadow.ops.Guest")
                .contentsAsUtf8String()
                .contains("element_ ->");
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

    /** A result encodes as control flow, so it says nothing anywhere but a function's result. */
    @Test
    void aResultReachedAsAValueIsRefused() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.ResultParamHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"result-param\", inline ="
                                        + " \"package my:project;\\n"
                                        + "interface calc {\\n"
                                        + "  report: func(outcome: result<u32, string>);\\n"
                                        + "}\\n"
                                        + "world result-param {\\n"
                                        + "  import calc;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class ResultParamHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("only meaningful as a function's own result");
    }

    /** A world declares no Java package for the generated exception to belong to. */
    @Test
    void aResultOnAWorldsOwnFunctionIsRefused() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.WorldResultHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"world-result\", inline ="
                                        + " \"package my:project;\\n"
                                        + "world world-result {\\n"
                                        + "  export go: func() -> result<u32, string>;\\n"
                                        + "}\\n\")\n"
                                        + "public class WorldResultHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("a function a world declares in its own right");
    }

    /** A record's map has to carry a resource by value, which a handle is not. */
    @Test
    void aRecordFieldNamingAResourceHandleIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.HandleFieldHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"handle-field\", inline ="
                                        + " \"package my:project;\\n"
                                        + "interface store {\\n"
                                        + "  resource conn {}\\n"
                                        + "  record session { c: conn }\\n"
                                        + "  open: func() -> session;\\n"
                                        + "}\\n"
                                        + "world handle-field {\\n"
                                        + "  import store;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class HandleFieldHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("names a resource handle");
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
