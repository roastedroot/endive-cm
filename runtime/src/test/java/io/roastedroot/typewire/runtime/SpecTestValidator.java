package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.parser.ComponentValidationException;
import io.roastedroot.typewire.parser.Validator;
import io.roastedroot.typewire.tools.ComponentValidate;
import io.roastedroot.typewire.tools.ComponentValidateException;
import java.io.InputStream;

public final class SpecTestValidator implements Validator {

    public static final SpecTestValidator INSTANCE = new SpecTestValidator();

    private SpecTestValidator() {}

    @Override
    public void validateBinary(InputStream wasm) throws ComponentValidationException {
        try {
            ComponentValidate.validate(wasm, "cm-implements");
        } catch (ComponentValidateException e) {
            throw new ComponentValidationException(e.getMessage(), e);
        }
    }
}
