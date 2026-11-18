package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.parser.ComponentParser;
import io.roastedroot.typewire.parser.Validator;
import io.roastedroot.typewire.types.WasmComponent;
import java.io.ByteArrayInputStream;
import run.endive.tools.wasm.Wat2Wasm;

/**
 * Parses components the way an embedder is expected to, with {@code wasm-tools validate} wired in
 * behind {@link Validator}.
 *
 * <p>Linking assumes it was handed a component that was already validated, because the rules a
 * validator settles, like {@code canon lift}'s callee having the type its signature flattens to,
 * are not re-checked. A test that skipped validation would therefore be exercising the linker
 * against inputs no real caller produces, and could pass on a component nobody could actually run.
 * The generated spec suite wires the same validator, and this is that for the tests written by
 * hand.
 */
final class TestComponents {

    private TestComponents() {}

    /** Validates and parses a component binary. */
    static WasmComponent parse(byte[] wasm) {
        return ComponentParser.builder()
                .withValidation(true)
                .withValidator(SpecTestValidator.INSTANCE)
                .build()
                .parse(() -> new ByteArrayInputStream(wasm));
    }

    /** Assembles component text, then validates and parses what it produced. */
    static WasmComponent fromWat(java.io.InputStream wat) {
        return parse(Wat2Wasm.parse(wat));
    }
}
