package io.roastedroot.typewire.types;

import java.util.Objects;

public interface TypeResolver {

    Type getType(int index);

    default DefValType resolveDefValType(ValType valType) {
        Objects.requireNonNull(valType, "valType");
        if (valType.primValType() != null) {
            return valType.primValType();
        }
        var type = getType(valType.typeIdx());
        if (type == null) {
            throw new IllegalArgumentException(
                    "typeIdx "
                            + valType.typeIdx()
                            + " of ValType must resolve to a Type but got null");
        }
        if (type.defValType() == null) {
            throw new IllegalArgumentException(
                    "typeIdx "
                            + valType.typeIdx()
                            + " of ValType must resolve to a DefValType but got "
                            + type);
        }
        return type.defValType();
    }
}
