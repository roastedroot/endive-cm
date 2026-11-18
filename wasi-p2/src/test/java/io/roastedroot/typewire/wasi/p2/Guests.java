package io.roastedroot.typewire.wasi.p2;

import io.roastedroot.typewire.parser.ComponentParser;
import io.roastedroot.typewire.parser.ComponentValidationException;
import io.roastedroot.typewire.tools.ComponentEmbed;
import io.roastedroot.typewire.tools.ComponentNew;
import io.roastedroot.typewire.tools.ComponentValidate;
import io.roastedroot.typewire.tools.ComponentValidateException;
import io.roastedroot.typewire.types.WasmComponent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Builds the guest components these tests run, each from a core module written as
 * {@code guests/<name>.wat} and a world {@code <name>} in {@code guests/<name>.wit}.
 *
 * <p>A guest world includes the host's own world, so its package directory is assembled with every
 * package the host reads placed under {@code deps}, flattened the way wasm-tools expects.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/main/design/mvp/WIT.md#root-package-a-directory">Root Package: A Directory</a>
 */
public final class Guests {

    private static final String HOST_WIT = "/wit/typewire_wasi@0.2.12.wit";

    private Guests() {}

    /**
     * The guest {@code name}, built and validated.
     *
     * @param temp a directory the guest's package directory may be written into
     */
    public static WasmComponent build(String name, Path temp) {
        try {
            Path wit = packageDirectory(name, temp);
            byte[] module = resource("/guests/" + name + ".wat");
            byte[] binary = ComponentNew.create(ComponentEmbed.embed(module, wit, name));
            return ComponentParser.builder()
                    .withValidation(true)
                    .withValidator(Guests::validate)
                    .build()
                    .parse(() -> new ByteArrayInputStream(binary));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path packageDirectory(String name, Path temp) throws IOException {
        Path deps = Files.createDirectories(temp.resolve("deps"));
        Path hostWit = hostWit();
        copyWitFiles(hostWit, deps);
        copyWitFiles(hostWit.resolve("deps"), deps);
        Files.write(temp.resolve(name + ".wit"), resource("/guests/" + name + ".wit"));
        return temp;
    }

    private static void copyWitFiles(Path from, Path to) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(from, "*.wit")) {
            for (Path file : files) {
                Files.copy(file, to.resolve(file.getFileName().toString()));
            }
        }
    }

    /** The host's WIT directory, which is inside a jar when another module runs these guests. */
    private static Path hostWit() throws IOException {
        try {
            URI uri = Guests.class.getResource(HOST_WIT).toURI();
            if ("jar".equals(uri.getScheme())) {
                try {
                    FileSystems.newFileSystem(uri, Map.of());
                } catch (FileSystemAlreadyExistsException e) {
                    // Already open for an earlier guest.
                }
            }
            return Path.of(uri).getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream is = Guests.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("resource not found: " + path);
            }
            return is.readAllBytes();
        }
    }

    private static void validate(InputStream wasm) {
        try {
            ComponentValidate.validate(wasm, "cm-implements");
        } catch (ComponentValidateException e) {
            throw new ComponentValidationException(e.getMessage(), e);
        }
    }
}
