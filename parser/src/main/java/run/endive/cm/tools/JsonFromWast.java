package run.endive.cm.tools;

import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JsonFromWast {

    private JsonFromWast() {}

    private static final Logger logger =
            new SystemLogger() {
                @Override
                public boolean isLoggable(Logger.Level level) {
                    return false;
                }
            };

    private static final WasmModule MODULE = WasmToolsModule.load();

    public static WastTests exec(InputStream is) {
        try (var stdinStream = new ByteArrayInputStream(new byte[0]);
             var stdoutStream = new ByteArrayOutputStream();
             var stderrStream = new ByteArrayOutputStream()) {

            FileSystem fs =
                    ZeroFs.newFileSystem(
                            Configuration.unix().toBuilder()
                                    .setAttributeViews("unix")
                                    .build());

            Path wastDir = fs.getPath("wast");
            Files.createDirectory(wastDir);
            Path inputFile = wastDir.resolve("input.wast");
            byte[] source = is.readAllBytes();
            Files.write(inputFile, source);

            var options =
                    WasiOptions.builder()
                            .withStdin(stdinStream, false)
                            .withStdout(stdoutStream, false)
                            .withStderr(stderrStream, false)
                            .withDirectory("/", fs.getPath("/work"))
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
                var wastJson = stdoutStream.toString(StandardCharsets.UTF_8);
                return new WastTests(wastJson, fs);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
