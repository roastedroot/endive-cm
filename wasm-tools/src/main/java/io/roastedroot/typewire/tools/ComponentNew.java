package io.roastedroot.typewire.tools;

import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

/**
 * Wraps {@code wasm-tools component new}, which builds a component from a core module carrying
 * embedded component type information.
 *
 * <p>The module has to have been through {@link ComponentEmbed}, or through a language toolchain
 * that embeds the same metadata, since that is where the world being implemented is recorded.
 */
public final class ComponentNew {

    private ComponentNew() {}

    private static final Logger logger =
            new SystemLogger() {
                @Override
                public boolean isLoggable(Logger.Level level) {
                    return false;
                }
            };

    private static final WasmModule MODULE = WasmToolsModule.load();

    /**
     * @param module a core module with embedded component types, in either the binary or the text
     *     format
     */
    public static byte[] create(byte[] module) {
        try (var stdinStream = new ByteArrayInputStream(new byte[0]);
                var stdoutStream = new ByteArrayOutputStream();
                var stderrStream = new ByteArrayOutputStream();
                FileSystem fs =
                        ZeroFs.newFileSystem(
                                Configuration.unix().toBuilder()
                                        .setAttributeViews("unix")
                                        .build())) {

            Path inputDir = fs.getPath("input");
            Files.createDirectory(inputDir);
            Path moduleFile = inputDir.resolve("module.wasm");
            Files.write(moduleFile, module);
            Path outputFile = inputDir.resolve("output.wasm");

            var options =
                    WasiOptions.builder()
                            .withStdin(stdinStream, false)
                            .withStdout(stdoutStream, false)
                            .withStderr(stderrStream, false)
                            .withDirectory(inputDir.toString(), inputDir)
                            .withArguments(
                                    List.of(
                                            "wasm-tools",
                                            "component",
                                            "new",
                                            "-o",
                                            outputFile.toString(),
                                            moduleFile.toString()))
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
                        throw new ComponentNewException(
                                stdoutStream.toString(StandardCharsets.UTF_8)
                                        + stderrStream.toString(StandardCharsets.UTF_8),
                                e);
                    }
                }
            }

            if (!Files.exists(outputFile)) {
                throw new ComponentNewException(
                        "wasm-tools produced no component: "
                                + stdoutStream.toString(StandardCharsets.UTF_8)
                                + stderrStream.toString(StandardCharsets.UTF_8));
            }
            return Files.readAllBytes(outputFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
