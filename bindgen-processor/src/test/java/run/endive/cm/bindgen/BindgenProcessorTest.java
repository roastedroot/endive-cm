package run.endive.cm.bindgen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                .generatedSourceFile("endive.testing.HelloWorldWorld")
                .contentsAsUtf8String()
                .contains("public static HelloWorldWorld instantiate(");
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
                List.of(
                        "endive.testing.UnusedFutureWorld",
                        "endive.testing.my.project.logging.Host"));
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
                .generatedSourceFile("endive.testing.SeamsWorld")
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
                .generatedSourceFile("endive.testing.NestWorld")
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

    /** A resource contributes methods to the interface it belongs to, so their names compete. */
    @Test
    void resourceMethodNamesAreKeptApart() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.ClashHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"clash\", inline ="
                                        + " \"package example:clash;\\n"
                                        + "interface counters {\\n"
                                        + "  resource counter { open: static func() -> u32; }\\n"
                                        + "  resource counter-open { constructor(); }\\n"
                                        + "}\\n"
                                        + "world clash {\\n"
                                        + "  export counters;\\n"
                                        + "}\\n\")\n"
                                        + "public class ClashHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.exports.example.clash.counters.Guest")
                .contentsAsUtf8String()
                .contains("counterOpen2(");
    }

    /**
     * A used type is numbered by the interface declaring it, so what it names has to be declared
     * into the using instance first, even when the using interface never names that itself.
     */
    @Test
    void aUsedTypeDeclaresWhatItNamesFirst() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.UsedRecordHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"used-record\", inline ="
                                        + " \"package example:used;\\n"
                                        + "interface types {\\n"
                                        + "  enum level { debug, info }\\n"
                                        + "  record entry { level: level, code: u32 }\\n"
                                        + "}\\n"
                                        + "interface logging {\\n"
                                        + "  use types.{entry};\\n"
                                        + "  latest: func() -> entry;\\n"
                                        + "}\\n"
                                        + "world used-record {\\n"
                                        + "  import logging;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class UsedRecordHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.UsedRecordWorld")
                .contentsAsUtf8String()
                .contains(
                        "ValType loggingTypesLevel = loggingBuilder.declareType(Type.of("
                                + "EnumType.builder()");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.UsedRecordWorld")
                .contentsAsUtf8String()
                .contains("withLabel(\"level\").withValType(loggingTypesLevel)");
    }

    /** The local derived for a type a used type names stays clear of the user's own type names. */
    @Test
    void aLocalDerivedForAUsedTypeKeepsClearOfTheUsersOwn() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.UsedLocalsHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"used-locals\", inline ="
                                        + " \"package example:used;\\n"
                                        + "interface types {\\n"
                                        + "  enum level { debug, info }\\n"
                                        + "  record entry { level: level, code: u32 }\\n"
                                        + "}\\n"
                                        + "interface logging {\\n"
                                        + "  use types.{entry};\\n"
                                        + "  enum types-level { low, high }\\n"
                                        + "  log: func(e: entry, t: types-level);\\n"
                                        + "}\\n"
                                        + "world used-locals {\\n"
                                        + "  import logging;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class UsedLocalsHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
    }

    /**
     * A type reached through a chain of uses belongs to the interface at the end of the chain, so
     * only that one generates it.
     */
    @Test
    void aTypeUsedThroughAnotherUseKeepsItsDeclaringPackage() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.ChainHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"chain\", inline ="
                                        + " \"package example:chain;\\n"
                                        + "interface base {\\n"
                                        + "  enum level { debug, info }\\n"
                                        + "}\\n"
                                        + "interface middle {\\n"
                                        + "  use base.{level};\\n"
                                        + "  record note { level: level }\\n"
                                        + "}\\n"
                                        + "interface top {\\n"
                                        + "  use middle.{note, level};\\n"
                                        + "  post: func(n: note, l: level);\\n"
                                        + "}\\n"
                                        + "world chain {\\n"
                                        + "  import top;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class ChainHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.ChainWorld",
                        "endive.testing.example.chain.base.Level",
                        "endive.testing.example.chain.middle.Note",
                        "endive.testing.example.chain.top.Host"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.chain.top.Host")
                .contentsAsUtf8String()
                .contains(
                        "void post(endive.testing.example.chain.middle.Note n,"
                                + " endive.testing.example.chain.base.Level l);");
    }

    /**
     * A used resource keeps the runtime type the declaring interface brought into existence, and
     * converts through that interface's {@code Handles}, even under a name of the user's own.
     */
    @Test
    void aUsedResourceKeepsTheDeclaringInterfacesType() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.UsedResourceHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"used-resource\", inline ="
                                        + " \"package example:used;\\n"
                                        + "interface poll {\\n"
                                        + "  resource pollable { ready: func() -> bool; }\\n"
                                        + "}\\n"
                                        + "interface streams {\\n"
                                        + "  use poll.{pollable as ticket};\\n"
                                        + "  subscribe: func() -> ticket;\\n"
                                        + "}\\n"
                                        + "world used-resource {\\n"
                                        + "  import streams;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class UsedResourceHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.used.streams.Host")
                .contentsAsUtf8String()
                .contains("endive.testing.example.used.poll.Pollable subscribe();");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.UsedResourceWorld")
                .contentsAsUtf8String()
                .contains(
                        "HostResource streamsTicket ="
                            + " streamsBuilder.useResource(pollHandles.pollableResourceType());");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.UsedResourceWorld")
                .contentsAsUtf8String()
                .contains("streamsBuilder.addResource(\"ticket\", streamsTicket);");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.UsedResourceWorld")
                .contentsAsUtf8String()
                .contains("pollHandles.ownPollable(streams.subscribe())");
    }

    /** Only an interface's wiring reaches a {@code Handles}, so a world using a resource is refused. */
    @Test
    void aResourceAWorldUsesIsRefused() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.WorldResourceHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"world-resource\", inline ="
                                        + " \"package example:used;\\n"
                                        + "interface poll {\\n"
                                        + "  resource pollable { ready: func() -> bool; }\\n"
                                        + "}\\n"
                                        + "world world-resource {\\n"
                                        + "  use poll.{pollable};\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class WorldResourceHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining(
                        "world \"world-resource\" uses resource \"pollable\" from \"poll\"");
    }

    /** A used result generates its exception into the package of the interface declaring it. */
    @Test
    void aUsedResultIsRefused() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.UsedResultHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"used-result\", inline ="
                                        + " \"package example:used;\\n"
                                        + "interface types {\\n"
                                        + "  type outcome = result<u32, string>;\\n"
                                        + "}\\n"
                                        + "interface runner {\\n"
                                        + "  use types.{outcome};\\n"
                                        + "  run: func() -> outcome;\\n"
                                        + "}\\n"
                                        + "world used-result {\\n"
                                        + "  import runner;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class UsedResultHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("uses result type \"outcome\" from \"types\"");
    }

    /**
     * Interfaces whose results carry the same used error payload throw one exception, generated
     * beside the payload, as {@code wasi:sockets} needs for {@code error-code}.
     */
    @Test
    void aUsedErrorPayloadSharesOneException() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.UsedErrorHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"used-error\", inline ="
                                        + " \"package example:used;\\n"
                                        + "interface network {\\n"
                                        + "  enum error-code { refused, timeout }\\n"
                                        + "}\\n"
                                        + "interface tcp {\\n"
                                        + "  use network.{error-code};\\n"
                                        + "  connect: func() -> result<u32, error-code>;\\n"
                                        + "}\\n"
                                        + "interface udp {\\n"
                                        + "  use network.{error-code as failure};\\n"
                                        + "  send: func() -> result<_, failure>;\\n"
                                        + "}\\n"
                                        + "world used-error {\\n"
                                        + "  import tcp;\\n"
                                        + "  import udp;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class UsedErrorHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.UsedErrorWorld",
                        "endive.testing.example.used.network.ErrorCode",
                        "endive.testing.example.used.network.ErrorCodeException",
                        "endive.testing.example.used.tcp.Host",
                        "endive.testing.example.used.udp.Host"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.UsedErrorWorld")
                .contentsAsUtf8String()
                .doesNotContain("used.tcp.ErrorCodeException");
    }

    /**
     * A used error payload is thrown by the interface returning it and carried as a value by the
     * interface taking it, which generates no exception of its own.
     */
    @Test
    void aUsedErrorPayloadIsThrownWhereReturnedAndAValueElsewhere() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.SharedErrorHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"shared-error\", inline ="
                                        + " \"package example:shared;\\n"
                                        + "interface network {\\n"
                                        + "  enum error-code { refused, timeout }\\n"
                                        + "}\\n"
                                        + "interface tcp {\\n"
                                        + "  use network.{error-code};\\n"
                                        + "  connect: func() -> result<u32, error-code>;\\n"
                                        + "}\\n"
                                        + "interface udp {\\n"
                                        + "  use network.{error-code};\\n"
                                        + "  report: func(outcome: result<_, error-code>);\\n"
                                        + "}\\n"
                                        + "world shared-error {\\n"
                                        + "  import tcp;\\n"
                                        + "  import udp;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class SharedErrorHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.SharedErrorWorld",
                        "endive.testing.example.shared.network.ErrorCode",
                        "endive.testing.example.shared.network.ErrorCodeException",
                        "endive.testing.example.shared.tcp.Host",
                        "endive.testing.example.shared.udp.Host"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.shared.udp.Host")
                .contentsAsUtf8String()
                .contains(
                        "void report(Result<Void, endive.testing.example.shared.network.ErrorCode>"
                                + " outcome);");
    }

    /** Only the host side of a used type is generated so far. */
    @Test
    void anExportedInterfaceUsingTypesIsRefused() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.ExportedUseHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"exported-use\", inline ="
                                        + " \"package example:used;\\n"
                                        + "interface types {\\n"
                                        + "  enum level { debug, info }\\n"
                                        + "}\\n"
                                        + "interface logging {\\n"
                                        + "  use types.{level};\\n"
                                        + "  log: func(level: level);\\n"
                                        + "}\\n"
                                        + "world exported-use {\\n"
                                        + "  export logging;\\n"
                                        + "}\\n\")\n"
                                        + "public class ExportedUseHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining(
                        "exported interface \"example:used/logging\" uses types from elsewhere");
    }

    /** A world declares no Java package for a type of its own to be generated into. */
    @Test
    void aTypeAWorldDeclaresIsRefused() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.WorldTypeHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"world-type\", inline ="
                                        + " \"package example:used;\\n"
                                        + "world world-type {\\n"
                                        + "  type count = u32;\\n"
                                        + "  export go: func() -> count;\\n"
                                        + "}\\n\")\n"
                                        + "public class WorldTypeHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("declares type \"count\" in its own right");
    }

    /**
     * A named primitive is carried as its primitive's Java type, since Java has no alias for a
     * type, so it generates no source of its own. It is still exported from the host instance,
     * since a component using it aliases it by name.
     */
    @Test
    void aNamedPrimitiveBindsAsItsPrimitive() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.NamedPrimitiveHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"clock\", inline ="
                                        + " \"package my:project;\\n"
                                        + "interface monotonic {\\n"
                                        + "  type instant = u64;\\n"
                                        + "  now: func() -> instant;\\n"
                                        + "}\\n"
                                        + "world clock {\\n"
                                        + "  import monotonic;\\n"
                                        + "  export run: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class NamedPrimitiveHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of("endive.testing.ClockWorld", "endive.testing.my.project.monotonic.Host"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.my.project.monotonic.Host")
                .contentsAsUtf8String()
                .contains("BigInteger now()");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.ClockWorld")
                .contentsAsUtf8String()
                .contains("addType(\"instant\"");
    }

    /**
     * A named primitive counts as the primitive it names, so a list of named bytes is still a
     * {@code byte[]} and a record of named numbers is still stored by fixed writes.
     */
    @Test
    void aNamedPrimitiveKeepsTheFastPaths() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.NamedFastPathHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"io\", inline ="
                                        + " \"package my:project;\\n"
                                        + "interface data {\\n"
                                        + "  type octet = u8;\\n"
                                        + "  type instant = u64;\\n"
                                        + "  record stamp { at: instant, seq: u32 }\\n"
                                        + "  read: func(len: u32) -> list<octet>;\\n"
                                        + "  latest: func() -> stamp;\\n"
                                        + "}\\n"
                                        + "world io {\\n"
                                        + "  import data;\\n"
                                        + "  export run: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class NamedFastPathHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.my.project.data.Host")
                .contentsAsUtf8String()
                .contains("byte[] read(Long len)");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.my.project.data.Stamp")
                .contentsAsUtf8String()
                .contains("memory.writeLong(pointer, this.at.longValue())");
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
                                        + "  variant blob { empty, bytes(list<u32>) }\\n"
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

    /** A handle converts through its interface's {@code Handles}, whichever resource returns it. */
    @Test
    void aStaticMayReturnAnotherResourcesHandle() {
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

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.t.t.i.Host")
                .contentsAsUtf8String()
                .contains("A bMake();");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.WWorld")
                .contentsAsUtf8String()
                .contains("iHandles.ownA(i.bMake())");
    }

    /** The guest side reaches no {@code Handles}, so a handle crossing there is still refused. */
    @Test
    void anExportedStaticReturningAnotherResourcesHandleIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.ExportedHandleHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"w\", inline = \"package t:t;\\n"
                                        + "interface i {\\n"
                                        + "  resource a { constructor(); }\\n"
                                        + "  resource b { make: static func() -> a; }\\n"
                                        + "}\\n"
                                        + "world w {\\n"
                                        + "  export i;\\n"
                                        + "}\\n\")\n"
                                        + "public class ExportedHandleHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("own is not yet supported");
    }

    /**
     * An owned handle handed to the host passes ownership with it, so the host takes the object out
     * of its table rather than leaving an entry nothing will drop.
     */
    @Test
    void anOwnedHandleHandedToTheHostIsTaken() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.OwnedArgumentHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"w\", inline = \"package t:t;\\n"
                                        + "interface i {\\n"
                                        + "  resource a {}\\n"
                                        + "  close: func(a: a);\\n"
                                        + "}\\n"
                                        + "world w {\\n"
                                        + "  import i;\\n"
                                        + "}\\n\")\n"
                                        + "public class OwnedArgumentHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.WWorld")
                .contentsAsUtf8String()
                .contains("i.close(iHandles.takeA((ResourceValue) args[0]))");
    }

    /** An exported interface generates no {@code Handles}, so its types carry no handle yet. */
    @Test
    void aTypeCarryingAHandleInAnExportedInterfaceIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.ExportedHandleTypeHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"w\", inline = \"package t:t;\\n"
                                        + "interface i {\\n"
                                        + "  resource a { constructor(); }\\n"
                                        + "  variant outcome { made(a), failed }\\n"
                                        + "}\\n"
                                        + "world w {\\n"
                                        + "  export i;\\n"
                                        + "}\\n\")\n"
                                        + "public class ExportedHandleTypeHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining(
                        "type \"outcome\" of exported interface \"t:t/i\" carries a resource"
                                + " handle");
    }

    /** A world's own functions reach no {@code Handles}, so a type carrying a handle is refused. */
    @Test
    void aWorldFunctionNamingATypeCarryingAHandleIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.WorldHandleTypeHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"w\", inline = \"package t:t;\\n"
                                        + "interface i {\\n"
                                        + "  resource a { constructor(); }\\n"
                                        + "  record held { a: a }\\n"
                                        + "}\\n"
                                        + "world w {\\n"
                                        + "  use i.{held};\\n"
                                        + "  export make: func() -> held;\\n"
                                        + "}\\n\")\n"
                                        + "public class WorldHandleTypeHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining(
                        "type \"held\" carries a resource handle, which only an imported"
                                + " interface's bindings can convert");
    }

    /** {@code Handles} is generated beside the interface's own types, so one named alike clashes. */
    @Test
    void aTypeNamedLikeTheGeneratedHandlesIsReported() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.HandlesClashHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"w\", inline = \"package t:t;\\n"
                                        + "interface i {\\n"
                                        + "  resource a { constructor(); }\\n"
                                        + "  record handles { count: u32 }\\n"
                                        + "  count: func(h: handles) -> u32;\\n"
                                        + "}\\n"
                                        + "world w {\\n"
                                        + "  import i;\\n"
                                        + "}\\n\")\n"
                                        + "public class HandlesClashHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining("would collide with the generated Handles class");
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

    /**
     * A result reached as a value crosses as a {@code Result}, which {@code wasi:cli}'s {@code
     * exit} needs. Only a function's own result is control flow, so a parameter generates no
     * exception.
     */
    @Test
    void aResultParameterBindsAsAResult() {
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
                                        + "  exit: func(status: result);\\n"
                                        + "}\\n"
                                        + "world result-param {\\n"
                                        + "  import calc;\\n"
                                        + "  export go: func(status: result<_, string>);\\n"
                                        + "}\\n\")\n"
                                        + "public class ResultParamHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of("endive.testing.ResultParamWorld", "endive.testing.my.project.calc.Host"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.my.project.calc.Host")
                .contentsAsUtf8String()
                .contains("void report(Result<Long, String> outcome);");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.my.project.calc.Host")
                .contentsAsUtf8String()
                .contains("void exit(Result<Void, Void> status);");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.ResultParamWorld")
                .contentsAsUtf8String()
                .contains("calc.report(Result.fromComponent(args[0], Long.class, String.class));");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.ResultParamWorld")
                .contentsAsUtf8String()
                .contains("calc.exit(Result.fromComponent(args[0], Void.class, Void.class));");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.ResultParamWorld")
                .contentsAsUtf8String()
                .contains("this.go.apply(status.toComponent());");
    }

    /**
     * A result inside another type converts each payload that needs it on its own, in both
     * directions, the way a tuple converts its elements.
     */
    @Test
    void aNestedResultConvertsEachPayloadThatNeedsIt() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.NestedResultHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"nested-result\", inline ="
                                        + " \"package t:r;\\n"
                                        + "interface types {\\n"
                                        + "  enum level { low, high }\\n"
                                        + "  record outcome { name: string, status: result<_,"
                                        + " level> }\\n"
                                        + "  variant event { quiet, done(result<u32>) }\\n"
                                        + "  tally: func(statuses: list<result<u32, level>>)"
                                        + " -> option<result<level, string>>;\\n"
                                        + "  describe: func(o: outcome) -> event;\\n"
                                        + "}\\n"
                                        + "world nested-result {\\n"
                                        + "  import types;\\n"
                                        + "  export go: func();\\n"
                                        + "}\\n\")\n"
                                        + "public class NestedResultHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.NestedResultWorld",
                        "endive.testing.t.r.types.Event",
                        "endive.testing.t.r.types.Host",
                        "endive.testing.t.r.types.Level",
                        "endive.testing.t.r.types.Outcome"));
        assertThat(compilation)
                .generatedSourceFile("endive.testing.NestedResultWorld")
                .contentsAsUtf8String()
                .contains(
                        ".map(element -> Result.fromComponent(element, element1 -> (Long)"
                                + " element1, element1 ->"
                                + " endive.testing.t.r.types.Level.fromComponent(element1)))");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.NestedResultWorld")
                .contentsAsUtf8String()
                .contains(
                        "VariantValue.of(\"some\", some.toComponent(element1 ->"
                                + " element1.toComponent(), element1 -> element1))");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.t.r.types.Outcome")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"status\", status.toComponent(element -> element, element"
                                + " -> element.toComponent()));");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.t.r.types.Outcome")
                .contentsAsUtf8String()
                .contains(
                        "Result.fromComponent(fields.get(\"status\"), element -> (Void) element,"
                                + " element -> Level.fromComponent(element))");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.t.r.types.Event")
                .contentsAsUtf8String()
                .contains(
                        "return new Done(Result.fromComponent(variant.value(), Long.class,"
                                + " Void.class));");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.t.r.types.Event")
                .contentsAsUtf8String()
                .contains("return VariantValue.of(\"done\", value.toComponent());");
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

    /**
     * A record carrying a handle converts it through the {@code Handles} its conversions take,
     * minting one on the way out and taking ownership on the way in. An interface sharing a name
     * with a parameter of {@code instantiate} gives way to it.
     */
    @Test
    void aRecordMayCarryAResourceHandle() {
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

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.my.project.store.Session")
                .contentsAsUtf8String()
                .contains("fields.put(\"c\", storeHandles.ownConn(c));");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.my.project.store.Session")
                .contentsAsUtf8String()
                .contains("storeHandles.takeConn((ResourceValue) fields.get(\"c\"))");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.HandleFieldWorld")
                .contentsAsUtf8String()
                .contains("store_.open().toComponent(store_Handles)");
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

    /**
     * A tuple whose elements convert at the boundary converts each one on its own, in both
     * directions, as {@code wasi:filesystem}'s {@code read} needs for {@code tuple<list<u8>,
     * bool>}.
     */
    @Test
    void aTupleConvertsEachElementThatNeedsIt() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.NestedTupleHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"nested\", inline ="
                                        + " \"package t:n;\\n"
                                        + "interface points {\\n"
                                        + "  enum level { low, high }\\n"
                                        + "  swap: func(pair: tuple<level, list<u8>>)"
                                        + " -> tuple<list<u8>, level>;\\n"
                                        + "}\\n"
                                        + "world nested {\\n"
                                        + "  import points;\\n"
                                        + "  export read: func() -> tuple<list<u8>, bool>;\\n"
                                        + "}\\n\")\n"
                                        + "public class NestedTupleHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.NestedWorld")
                .contentsAsUtf8String()
                .contains(
                        "Tuple2.fromComponent(args[0], element ->"
                                + " endive.testing.t.n.points.Level.fromComponent(element),"
                                + " element -> ByteList.toBytes(element))");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.NestedWorld")
                .contentsAsUtf8String()
                .contains(".toComponent(element -> element, element -> element.toComponent())");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.NestedWorld")
                .contentsAsUtf8String()
                .contains(
                        "Tuple2.fromComponent(this.read.apply()[0], element ->"
                                + " ByteList.toBytes(element), element -> (Boolean) element)");
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
                .generatedSourceFile("endive.testing.KwWorld")
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

    /**
     * A WIT file beside a {@code deps} directory is read as a package directory, so an interface
     * may use a resource another package declares.
     */
    @Test
    void aPackageDirectoryResolvesItsDeps() {
        Compilation compilation = compile(crossPackageHost());

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.clock.monotonic.Host")
                .contentsAsUtf8String()
                .contains("Pollable subscribe(Long millis)");
        assertThat(compilation)
                .generatedSourceFile("endive.testing.example.poll.poll.Pollable")
                .contentsAsUtf8String()
                .contains("Boolean ready()");
    }

    /**
     * WIT arriving inside a dependency jar may be a package directory as well. javac finds a file in
     * a jar only under directories named like Java identifiers, so the jarred copy is renamed.
     */
    @Test
    void aPackageDirectoryInsideAJarIsRead(@TempDir Path temp) throws Exception {
        Path source = Path.of(getClass().getResource("/wit/cross-package").toURI());
        Path jar = temp.resolve("wit.jar");
        try (FileSystem fs =
                FileSystems.newFileSystem(
                        URI.create("jar:" + jar.toUri()), Map.of("create", "true"))) {
            Path target = fs.getPath("/wit/jarred");
            try (Stream<Path> paths = Files.walk(source)) {
                for (Path path : paths.collect(Collectors.toList())) {
                    Path to = target.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(to);
                    } else {
                        Files.copy(path, to);
                    }
                }
            }
        }

        Compilation compilation;
        try (URLClassLoader loader =
                new URLClassLoader(
                        new URL[] {jar.toUri().toURL()},
                        BindgenProcessorTest.class.getClassLoader())) {
            compilation =
                    javac().withProcessors(new BindgenProcessor())
                            .withClasspathFrom(loader)
                            .compile(
                                    JavaFileObjects.forSourceString(
                                            "endive.testing.JarredHost",
                                            "package endive.testing;\n"
                                                    + "import run.endive.cm.runtime.Bindgen;\n"
                                                    + "@Bindgen(world = \"clocks\", path ="
                                                    + " \"wit/jarred/clock.wit\")\n"
                                                    + "public class JarredHost {}\n"));
        }

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation).generatedSourceFile("endive.testing.example.poll.poll.Pollable");
    }

    /** A WIT file with no {@code deps} beside it is read alone, as the files beside it may be. */
    @Test
    void aFileWithoutDepsIsReadAlone() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.AloneHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"clocks\", path ="
                                        + " \"wit/cross-package/deps/poll/poll.wit\")\n"
                                        + "public class AloneHost {}\n"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("world \"clocks\" was not found");
    }

    private static JavaFileObject crossPackageHost() {
        return JavaFileObjects.forSourceString(
                "endive.testing.CrossPackageHost",
                "package endive.testing;\n"
                        + "import run.endive.cm.runtime.Bindgen;\n"
                        + "@Bindgen(world = \"clocks\", path ="
                        + " \"wit/cross-package/clock.wit\")\n"
                        + "public class CrossPackageHost {}\n");
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

    @Test
    void versionedInterfaceGeneratesValidJavaNames() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.VersionedHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"versioned-imports\", path ="
                                        + " \"wit/versioned-imports.wit\")\n"
                                        + "public class VersionedHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.VersionedImportsWorld")
                .contentsAsUtf8String()
                .contains("\"example:versioned-imports/streams@0.2.0\"");

        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.VersionedImportsWorld",
                        "endive.testing.example.versionedimports.streams.Host"));
    }

    @Test
    void versionedExportGeneratesValidJavaNames() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.VersionedExportHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"versioned-exports\", path ="
                                        + " \"wit/versioned-imports.wit\")\n"
                                        + "public class VersionedExportHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation)
                .generatedSourceFile("endive.testing.VersionedExportsWorld")
                .contentsAsUtf8String()
                .contains("\"example:versioned-imports/streams@0.2.0\"");

        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.VersionedExportsWorld",
                        "endive.testing.exports.example.versionedimports.streams.Guest"));
    }

    @Test
    void anonymousResultInVersionedInterfaceHasValidExceptionName() {
        Compilation compilation =
                compile(
                        JavaFileObjects.forSourceString(
                                "endive.testing.VersionedResultHost",
                                "package endive.testing;\n"
                                        + "import run.endive.cm.runtime.Bindgen;\n"
                                        + "@Bindgen(world = \"versioned-results\", path ="
                                        + " \"wit/versioned-results.wit\")\n"
                                        + "public class VersionedResultHost {}\n"));

        assertThat(compilation).succeededWithoutWarnings();
        assertGenerated(
                compilation,
                List.of(
                        "endive.testing.VersionedResultsWorld",
                        "endive.testing.wasi.cli.run.Host",
                        "endive.testing.wasi.cli.run.RunResult0Exception"));
    }

    @Test
    void twoVersionsOfOneInterfaceReportTheConflictingIds() {
        WitInterface first =
                new WitInterface(
                        "wasi:io/streams@0.2.0", List.of(), List.of(), List.of(), new WitScope());
        WitInterface next =
                new WitInterface(
                        "wasi:io/streams@0.3.0", List.of(), List.of(), List.of(), new WitScope());
        WitWorld world =
                new WitWorld(
                        "two-versions",
                        "example:versions/two-versions",
                        List.of(),
                        List.of(first, next),
                        List.of(),
                        List.of());

        BindgenException error =
                assertThrows(
                        BindgenException.class,
                        () ->
                                WorldGenerator.generate(
                                        world, "endive.testing", BindgenProcessor.class.getName()));

        assertTrue(error.getMessage().contains("wasi:io/streams@0.2.0"));
        assertTrue(error.getMessage().contains("wasi:io/streams@0.3.0"));
        assertTrue(error.getMessage().contains("endive.testing.wasi.io.streams"));
    }

    @Test
    void versionedJavaNamesAreStableAcrossPatchVersions() {
        WitInterface first =
                new WitInterface(
                        "wasi:io/streams@0.2.0", List.of(), List.of(), List.of(), new WitScope());
        WitInterface next =
                new WitInterface(
                        "wasi:io/streams@0.2.1", List.of(), List.of(), List.of(), new WitScope());

        assertEquals("streams", first.simpleName());
        assertEquals(first.simpleName(), next.simpleName());
        assertEquals(
                first.javaPackage("endive.testing", false),
                next.javaPackage("endive.testing", false));
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
