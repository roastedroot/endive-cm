package run.endive.cm.bindgen;

import java.util.Objects;
import run.endive.cm.types.FuncType;

/** One function a world imports or exports, under the name the world gives it. */
final class WitFunction {

    private final String resource;
    private final String name;
    private final FuncType type;
    private final WitScope scope;

    WitFunction(String name, FuncType type, WitScope scope) {
        this(null, name, type, scope);
    }

    /**
     * @param resource the resource a method or static function belongs to, or {@code null} for a
     *     free function or a constructor
     */
    WitFunction(String resource, String name, FuncType type, WitScope scope) {
        this.resource = resource;
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    /** The resource a method or static function belongs to, or {@code null} for anything else. */
    String resource() {
        return resource;
    }

    String name() {
        return name;
    }

    /** This function as WIT writes it, with the resource in front of a method or a static. */
    String witName() {
        return resource == null ? name : resource + "." + name;
    }

    FuncType type() {
        return type;
    }

    /** The index space against which this function's value types resolve. */
    WitScope scope() {
        return scope;
    }
}
