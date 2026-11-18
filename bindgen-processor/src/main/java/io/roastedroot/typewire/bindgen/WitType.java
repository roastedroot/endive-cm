package io.roastedroot.typewire.bindgen;

import io.roastedroot.typewire.types.DefValType;
import java.util.Objects;

/**
 * A type an interface declares, under the name its export gives it.
 *
 * <p>Only a named type is carried here, since a name is what a generated Java type needs. A type
 * written anonymously, such as a list or an option, is reached through the {@link WitScope}
 * instead.
 */
final class WitType {

    private final String name;
    private final DefValType defValType;

    WitType(String name, DefValType defValType) {
        this.name = Objects.requireNonNull(name, "name");
        this.defValType = Objects.requireNonNull(defValType, "defValType");
    }

    String name() {
        return name;
    }

    DefValType defValType() {
        return defValType;
    }

    DefValType.Kind kind() {
        return defValType.kind();
    }
}
