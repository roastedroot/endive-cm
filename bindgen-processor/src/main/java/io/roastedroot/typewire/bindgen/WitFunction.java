package io.roastedroot.typewire.bindgen;

import io.roastedroot.typewire.types.FuncType;
import java.util.Objects;

/** One function a world imports or exports, under the name the world gives it. */
final class WitFunction {

    private final String name;
    private final FuncType type;
    private final WitScope scope;

    WitFunction(String name, FuncType type, WitScope scope) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    String name() {
        return name;
    }

    FuncType type() {
        return type;
    }

    /** The index space against which this function's value types resolve. */
    WitScope scope() {
        return scope;
    }
}
