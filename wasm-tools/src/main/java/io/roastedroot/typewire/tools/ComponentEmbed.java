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
import java.util.ArrayList;
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
 * Wraps {@code wasm-tools component embed}, which records a world inside a core module as a custom
 * section describing what the module imports and exports.
 *
 * <p>The result is still a core module. {@link ComponentNew} turns one into a component.
 */
public final class ComponentEmbed {

    private ComponentEmbed() {}

    private static final Logger logger =
            new SystemLogger() {
                @Override
                public boolean isLoggable(Logger.Level level) {
                    return false;
                }
            };

    private static final WasmModule MODULE = WasmToolsModule.load();

    /** Embeds the only world the package declares. */
    public static byte[] embed(byte[] module, String wit) {
        return embed(module, wit, "");
    }

    /**
     * @param module a core module, in either the binary or the text format
     * @param wit WIT text declaring the world
     * @param world the world to embed, empty when the package declares exactly one
     */
    public static byte[] embed(byte[] module, String wit, String world) {
        return embed(module, WitInput.of(wit.getBytes(StandardCharsets.UTF_8)), world);
    }

    /**
     * @param module a core module, in either the binary or the text format
     * @param wit a WIT file, or a package directory whose dependencies sit under {@code deps}
     * @param world the world to embed, empty when the package declares exactly one
     * @see <a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/WIT.md#root-package-a-directory">Root Package: A Directory</a>
     */
    public static byte[] embed(byte[] module, Path wit, String world) {
        return embed(module, WitInput.of(wit), world);
    }

    private static byte[] embed(byte[] module, WitInput wit, String world) {
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
            Path witFile = wit.writeTo(inputDir);
            Path moduleFile = inputDir.resolve("module.wasm");
            Files.write(moduleFile, module);
            Path outputFile = inputDir.resolve("output.wasm");

            List<String> args = new ArrayList<>(List.of("wasm-tools", "component", "embed"));
            if (!world.isEmpty()) {
                args.add("--world");
                args.add(world);
            }
            args.add("-o");
            args.add(outputFile.toString());
            args.add(witFile.toString());
            args.add(moduleFile.toString());

            var options =
                    WasiOptions.builder()
                            .withStdin(stdinStream, false)
                            .withStdout(stdoutStream, false)
                            .withStderr(stderrStream, false)
                            .withDirectory(inputDir.toString(), inputDir)
                            .withArguments(args)
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
                        throw new ComponentEmbedException(
                                stdoutStream.toString(StandardCharsets.UTF_8)
                                        + stderrStream.toString(StandardCharsets.UTF_8),
                                e);
                    }
                }
            }

            if (!Files.exists(outputFile)) {
                throw new ComponentEmbedException(
                        "wasm-tools produced no module: "
                                + stdoutStream.toString(StandardCharsets.UTF_8)
                                + stderrStream.toString(StandardCharsets.UTF_8));
            }
            return Files.readAllBytes(outputFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
