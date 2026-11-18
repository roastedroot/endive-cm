package io.roastedroot.typewire.bindgen.it;

import io.roastedroot.typewire.parser.ComponentParser;
import io.roastedroot.typewire.tools.ComponentEmbed;
import io.roastedroot.typewire.tools.ComponentNew;
import io.roastedroot.typewire.types.WasmComponent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Builds the components these tests run against, from a core module written as {@code .wat} and the
 * world it implements.
 *
 * <p>Validation goes through {@link TestValidator}, wired into the parser rather than called
 * separately, so that every parse in this project reaches wasm-tools the same way.
 */
public final class Components {

    private Components() {}

    /**
     * @param wat a core module implementing {@code world}
     * @param wit the WIT declaring it
     */
    public static WasmComponent build(byte[] wat, String wit, String world) {
        byte[] binary = ComponentNew.create(ComponentEmbed.embed(wat, wit, world));
        return ComponentParser.builder()
                .withValidation(true)
                .withValidator(TestValidator.INSTANCE)
                .build()
                .parse(() -> new ByteArrayInputStream(binary));
    }

    /** A classpath resource as text, for a WIT file. */
    public static String text(String path) {
        return new String(bytes(path), StandardCharsets.UTF_8);
    }

    /** A classpath resource as bytes, for a core module. */
    public static byte[] bytes(String path) {
        try (InputStream is = Components.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("resource not found: " + path);
            }
            return is.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
