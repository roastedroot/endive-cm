package io.roastedroot.typewire.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * WIT handed to wasm-tools, written into the directory wasm-tools is given before it runs.
 *
 * <p>A package spread across a directory keeps its layout, so the packages it depends on are found
 * under {@code deps} the way wasm-tools resolves them.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/WIT.md#root-package-a-directory">Root Package: A Directory</a>
 */
@FunctionalInterface
interface WitInput {

    /** Writes the WIT under {@code dir} and returns the path wasm-tools reads it from. */
    Path writeTo(Path dir) throws IOException;

    /** WIT text, or the binary encoding of a package. */
    static WitInput of(byte[] wit) {
        return dir -> Files.write(dir.resolve("input.wit"), wit);
    }

    /**
     * A WIT file, or a package directory holding its own files and a {@code deps} directory. The
     * source may sit on any file system, such as one opened over a jar.
     */
    static WitInput of(Path source) {
        return dir -> {
            if (!Files.isDirectory(source)) {
                return Files.copy(source, dir.resolve(source.getFileName().toString()));
            }
            Path target = dir.resolve("wit");
            try (Stream<Path> paths = Files.walk(source)) {
                paths.forEach(
                        path -> copy(path, target.resolve(source.relativize(path).toString())));
            }
            return target;
        };
    }

    private static void copy(Path from, Path to) {
        try {
            if (Files.isDirectory(from)) {
                Files.createDirectories(to);
            } else {
                Files.copy(from, to);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
