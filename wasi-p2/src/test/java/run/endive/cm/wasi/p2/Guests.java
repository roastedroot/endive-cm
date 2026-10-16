package run.endive.cm.wasi.p2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import run.endive.cm.parser.ComponentParser;
import run.endive.cm.parser.ComponentValidationException;
import run.endive.cm.tools.ComponentEmbed;
import run.endive.cm.tools.ComponentNew;
import run.endive.cm.tools.ComponentValidate;
import run.endive.cm.tools.ComponentValidateException;
import run.endive.cm.types.WasmComponent;

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

    private static final String HOST_WIT = "/wit/endive_wasi@0.2.12.wit";

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

    private static Path hostWit() {
        try {
            return Path.of(Guests.class.getResource(HOST_WIT).toURI()).getParent();
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
