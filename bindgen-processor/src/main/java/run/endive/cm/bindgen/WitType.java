package run.endive.cm.bindgen;

import java.util.Objects;
import run.endive.cm.types.DefValType;

/**
 * A type an interface declares, under the name its export gives it.
 *
 * <p>The index is the slot the type occupies in the interface's {@link WitScope}, which is how a
 * function type names it and how a generated exception for an anonymous type is told apart from
 * another.
 */
final class WitType {

    private final String name;
    private final int index;
    private final DefValType defValType;

    WitType(String name, int index, DefValType defValType) {
        this.name = Objects.requireNonNull(name, "name");
        this.index = index;
        this.defValType = Objects.requireNonNull(defValType, "defValType");
    }

    String name() {
        return name;
    }

    int index() {
        return index;
    }

    DefValType defValType() {
        return defValType;
    }

    DefValType.Kind kind() {
        return defValType.kind();
    }
}
