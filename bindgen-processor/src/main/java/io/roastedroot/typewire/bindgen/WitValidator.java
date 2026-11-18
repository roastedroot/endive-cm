package io.roastedroot.typewire.bindgen;

import io.roastedroot.typewire.parser.ComponentValidationException;
import io.roastedroot.typewire.parser.Validator;
import io.roastedroot.typewire.tools.ComponentValidate;
import io.roastedroot.typewire.tools.ComponentValidateException;
import java.io.InputStream;

/**
 * Validates an encoded WIT package before it is read.
 *
 * <p>The binary comes from wasm-tools moments earlier, so this is not expected to reject anything.
 * It is here because the parser accepting whatever wasm-tools produces is a rule the project holds
 * itself to, and a version of wasm-tools that encoded something this parser read differently would
 * otherwise surface as a puzzling generation failure rather than as a validation error.
 */
final class WitValidator implements Validator {

    static final WitValidator INSTANCE = new WitValidator();

    private WitValidator() {}

    @Override
    public void validateBinary(InputStream wasm) throws ComponentValidationException {
        try {
            ComponentValidate.validate(wasm, "cm-implements");
        } catch (ComponentValidateException e) {
            throw new ComponentValidationException(e.getMessage(), e);
        }
    }
}
