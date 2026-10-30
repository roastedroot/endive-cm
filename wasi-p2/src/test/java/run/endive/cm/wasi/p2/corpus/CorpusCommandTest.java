package run.endive.cm.wasi.p2.corpus;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import run.endive.cm.corpus.ComponentCorpus;
import run.endive.cm.parser.ComponentParser;
import run.endive.cm.parser.ComponentValidationException;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.tools.ComponentValidate;
import run.endive.cm.tools.ComponentValidateException;
import run.endive.cm.types.WasmComponent;
import run.endive.cm.wasi.p2.WasiPreview2;

/**
 * Every WASI command in the component corpus, run as its spec describes. The corpus checks each
 * spec against wasmtime, so a pass here means the same exit code and output as wasmtime.
 */
class CorpusCommandTest {

    private static final String SPEC = ".rs.json";

    static Stream<String> programs() {
        return ComponentCorpus.entries().stream()
                .filter(entry -> entry.endsWith(SPEC))
                .map(entry -> entry.substring(0, entry.length() - SPEC.length()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("programs")
    void runsAsSpecified(String name) throws IOException {
        JsonNode spec;
        try (InputStream json = ComponentCorpus.open(name + SPEC)) {
            spec = new ObjectMapper().readTree(json);
        }
        WasmComponent component = parse(ComponentCorpus.readBytes(name + ".rs.wasm"));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try (FileSystem fs = ZeroFs.newFileSystem(Configuration.unix())) {
            WasiPreview2.Builder builder =
                    WasiPreview2.builder()
                            .withArguments(arguments(name, spec))
                            .withStdout(stdout)
                            .withStderr(stderr);
            for (Map.Entry<String, JsonNode> variable : spec.path("env").properties()) {
                builder.withEnvironment(variable.getKey(), variable.getValue().asText());
            }
            if (spec.hasNonNull("root")) {
                builder.withDirectory("/", copyFixture(name, fs));
            }

            int exitCode;
            try (WasiPreview2 wasi = builder.build()) {
                exitCode = wasi.run(new ComponentStore(), component);
            }

            String output =
                    "\nstdout:\n" + stdout.toString(UTF_8) + "\nstderr:\n" + stderr.toString(UTF_8);
            assertEquals(spec.path("exit_code").asInt(0), exitCode, "exit code" + output);
            if (spec.has("stdout")) {
                assertEquals(spec.get("stdout").asText(), stdout.toString(UTF_8), "stdout");
            }
            if (spec.has("stderr")) {
                assertEquals(spec.get("stderr").asText(), stderr.toString(UTF_8), "stderr");
            }
        }
    }

    /** The program's name, then the spec's arguments, as a shell would pass them. */
    private static List<String> arguments(String name, JsonNode spec) {
        List<String> arguments = new ArrayList<>();
        arguments.add(name);
        spec.path("args").forEach(argument -> arguments.add(argument.asText()));
        return arguments;
    }

    /** Copies the program's fixture directory to the root of {@code fs}. */
    private static Path copyFixture(String name, FileSystem fs) throws IOException {
        String prefix = name + ".rs.root/";
        Path root = fs.getPath("/");
        for (String entry : ComponentCorpus.entries()) {
            if (entry.startsWith(prefix)) {
                Path target = root.resolve(entry.substring(prefix.length()));
                Files.createDirectories(target.getParent());
                Files.write(target, ComponentCorpus.readBytes(entry));
            }
        }
        return root;
    }

    private static WasmComponent parse(byte[] bytes) {
        return ComponentParser.builder()
                .withValidation(true)
                .withValidator(CorpusCommandTest::validate)
                .build()
                .parse(() -> new ByteArrayInputStream(bytes));
    }

    private static void validate(InputStream wasm) {
        try {
            ComponentValidate.validate(wasm, "cm-implements");
        } catch (ComponentValidateException e) {
            throw new ComponentValidationException(e.getMessage(), e);
        }
    }
}
