package run.endive.cm.tools;

import static java.util.Objects.requireNonNull;

import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import run.endive.log.Logger;
import run.endive.log.SystemLogger;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.tools.wasm.WasmToolsModule;
import run.endive.wasi.WasiExitException;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;
import run.endive.wasm.WasmModule;

public final class JsonFromWast {

    private static final Logger logger =
            new SystemLogger() {
                @Override
                public boolean isLoggable(Logger.Level level) {
                    return false;
                }
            };

    private static final WasmModule MODULE = WasmToolsModule.load();

    private final File file;
    private final File output;

    private JsonFromWast(File file, File output) {
        this.file = file;
        this.output = output;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void process() {
        if (!file.exists()) {
            throw new JsonFromWastException("Input file does not exist: " + file);
        }
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new JsonFromWastException("Failed to create output directory: " + output);
        }

        try (var stdinStream = new ByteArrayInputStream(new byte[0]);
                var stdoutStream = new ByteArrayOutputStream();
                var stderrStream = new ByteArrayOutputStream();
                FileSystem fs =
                        ZeroFs.newFileSystem(
                                Configuration.unix().toBuilder()
                                        .setAttributeViews("unix")
                                        .build())) {

            Path wastDir = fs.getPath("wast");
            Files.createDirectory(wastDir);
            Path inputFile = wastDir.resolve("input.wast");
            Files.write(inputFile, Files.readAllBytes(file.toPath()));

            Path workDir = fs.getPath("/work");
            Files.createDirectories(workDir);

            var options =
                    WasiOptions.builder()
                            .withStdin(stdinStream, false)
                            .withStdout(stdoutStream, false)
                            .withStderr(stderrStream, false)
                            .withDirectory("/", workDir)
                            .withArguments(
                                    List.of(
                                            "wasm-tools",
                                            "json-from-wast",
                                            inputFile.toString(),
                                            "-vv",
                                            "--pretty"))
                            .build();

            try (var wasi =
                    WasiPreview1.builder().withLogger(logger).withOptions(options).build()) {
                var imports = ImportValues.builder().addFunction(wasi.toHostFunctions()).build();
                try {
                    Instance.builder(MODULE)
                            .withMachineFactory(WasmToolsModule::create)
                            .withMemoryFactory(ByteArrayMemory::new)
                            .withImportValues(imports)
                            .build();
                } catch (WasiExitException e) {
                    if (e.exitCode() != 0) {
                        throw new ComponentValidateException(
                                stdoutStream.toString(StandardCharsets.UTF_8)
                                        + stderrStream.toString(StandardCharsets.UTF_8),
                                e);
                    }
                }
            }

            var specJson = stdoutStream.toString(StandardCharsets.UTF_8);
            Files.writeString(output.toPath().resolve("spec.json"), specJson);

            try (Stream<Path> wasmFiles = Files.list(workDir)) {
                wasmFiles
                        .filter(p -> p.toString().endsWith(".wasm"))
                        .forEach(
                                p -> {
                                    try {
                                        Files.copy(
                                                p,
                                                output.toPath().resolve(p.getFileName().toString()),
                                                StandardCopyOption.REPLACE_EXISTING);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                });
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static final class Builder {
        private File file;
        private File output;

        private Builder() {}

        public Builder withFile(File file) {
            this.file = file;
            return this;
        }

        public Builder withOutput(File output) {
            this.output = output;
            return this;
        }

        public JsonFromWast build() {
            requireNonNull(file, "file");
            requireNonNull(output, "output");
            return new JsonFromWast(file, output);
        }
    }
}
