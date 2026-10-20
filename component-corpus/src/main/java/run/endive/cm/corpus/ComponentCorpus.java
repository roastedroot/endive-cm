package run.endive.cm.corpus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The files this corpus compiled, read from the classpath.
 *
 * <p>Each program {@code <name>} compiles to {@code <name>.<lang>.wasm}. Beside it are its spec,
 * {@code <name>.<lang>.json}, and its fixture directory, {@code <name>.<lang>.root/}, when it has
 * them.
 */
public final class ComponentCorpus {

    private static final String COMPILED = "/compiled/";

    private ComponentCorpus() {}

    /**
     * Opens a compiled file.
     *
     * @param path the file's path within the corpus, such as {@code hello-stdout.rs.wasm}
     * @throws IllegalArgumentException if the corpus has no such file
     */
    public static InputStream open(String path) {
        InputStream stream = ComponentCorpus.class.getResourceAsStream(COMPILED + path);
        if (stream == null) {
            throw new IllegalArgumentException("Corpus file not found: " + path);
        }
        return stream;
    }

    /**
     * Reads a compiled file.
     *
     * @param path the file's path within the corpus, such as {@code hello-stdout.rs.wasm}
     * @throws IllegalArgumentException if the corpus has no such file
     */
    public static byte[] readBytes(String path) {
        try (InputStream stream = open(path)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The path of every compiled file, sorted. */
    public static List<String> entries() {
        try (BufferedReader index =
                new BufferedReader(
                        new InputStreamReader(open("index.txt"), StandardCharsets.UTF_8))) {
            return index.lines()
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toUnmodifiableList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
