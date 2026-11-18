package io.roastedroot.typewire.tools;

import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * Wraps {@code wasm-tools component wit}, which reads a WIT package and writes it back out in
 * either form.
 *
 * <p>Input may be WIT text or the binary encoding of a package, since wasm-tools tells them apart
 * by content rather than by file name.
 */
public final class WitParser {
    private WitParser() {}

    private static final Logger logger =
            new SystemLogger() {
                @Override
                public boolean isLoggable(Logger.Level level) {
                    return false;
                }
            };
    private static final WasmModule MODULE = WasmToolsModule.load();

    /** The written form of a WIT package. */
    private enum Form {
        TEXT,
        BINARY
    }

    public static String parse(File file) {
        try (var is = new FileInputStream(file)) {
            return parse(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String parse(String wit) {
        try (var is = new ByteArrayInputStream(wit.getBytes(StandardCharsets.UTF_8))) {
            return parse(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String parse(InputStream is) {
        return new String(run(read(is), Form.TEXT), StandardCharsets.UTF_8);
    }

    /**
     * Encodes a WIT package into its binary form, which is a component whose exports name the
     * package's worlds and interfaces and carry their types.
     *
     * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Binary.md">Binary.md</a>
     */
    public static byte[] encode(File file) {
        try (var is = new FileInputStream(file)) {
            return encode(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] encode(String wit) {
        try (var is = new ByteArrayInputStream(wit.getBytes(StandardCharsets.UTF_8))) {
            return encode(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] encode(InputStream is) {
        return run(read(is), Form.BINARY);
    }

    /**
     * Encodes a WIT file, or a package directory whose dependencies sit under {@code deps}.
     *
     * @param wit a file or directory on any file system, such as one opened over a jar
     * @see <a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/WIT.md#root-package-a-directory">Root Package: A Directory</a>
     */
    public static byte[] encode(Path wit) {
        return run(WitInput.of(wit), Form.BINARY);
    }

    private static WitInput read(InputStream is) {
        try {
            return WitInput.of(is.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Text goes to stdout and binary goes to a file, so the requested form decides both the command
     * and where to read the result.
     */
    private static byte[] run(WitInput input, Form form) {
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
            Path inputFile = input.writeTo(inputDir);
            Path outputFile = inputDir.resolve("output.wasm");

            List<String> args = new ArrayList<>(List.of("wasm-tools", "component", "wit"));
            if (form == Form.BINARY) {
                args.add("--wasm");
                args.add("-o");
                args.add(outputFile.toString());
            }
            args.add(inputFile.toString());

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
                        throw new WitParseException(
                                stdoutStream.toString(StandardCharsets.UTF_8)
                                        + stderrStream.toString(StandardCharsets.UTF_8),
                                e);
                    }
                }
            }

            if (form == Form.TEXT) {
                return stdoutStream.toByteArray();
            }
            if (!Files.exists(outputFile)) {
                throw new WitParseException(
                        "wasm-tools produced no binary: "
                                + stdoutStream.toString(StandardCharsets.UTF_8)
                                + stderrStream.toString(StandardCharsets.UTF_8));
            }
            return Files.readAllBytes(outputFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
