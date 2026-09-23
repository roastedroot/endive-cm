package run.endive.cm.bindgen;

import java.util.List;
import java.util.Objects;
import run.endive.cm.types.DefValType;
import run.endive.cm.types.FuncType;
import run.endive.cm.types.OwnType;
import run.endive.cm.types.Type;

/**
 * A resource an interface declares, together with the functions reaching it.
 *
 * <p>The Canonical ABI names those functions rather than nesting them, so an interface exports
 * {@code [constructor]file}, {@code [method]file.get-name} and {@code [static]file.open} alongside
 * its ordinary functions. Reading one back means splitting those names apart again.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#import-and-export-definitions">Explainer.md, resource method names</a>
 */
final class WitResource {

    private final String name;
    private final int typeIndex;
    private final WitFunction constructor;
    private final List<WitFunction> methods;
    private final List<WitFunction> statics;

    /**
     * @param typeIndex where the resource sits in the declaring interface's type index space,
     *     which is what an {@code own} or a {@code borrow} names it by
     */
    WitResource(
            String name,
            int typeIndex,
            WitFunction constructor,
            List<WitFunction> methods,
            List<WitFunction> statics) {
        this.name = Objects.requireNonNull(name, "name");
        this.typeIndex = typeIndex;
        this.constructor = constructor;
        this.methods = List.copyOf(methods);
        this.statics = List.copyOf(statics);
    }

    String name() {
        return name;
    }

    /** {@code null} when the resource is only ever handed over rather than made by the host. */
    WitFunction constructor() {
        return constructor;
    }

    /** Each method's first parameter is the borrowed receiver, which Java carries as {@code this}. */
    List<WitFunction> methods() {
        return methods;
    }

    /** A static function reaches the resource without a receiver, so it takes no handle. */
    List<WitFunction> statics() {
        return statics;
    }

    /**
     * Whether {@code function} hands back an {@code own} handle to this resource, which is what
     * makes a static behave like a constructor on both sides of the boundary.
     */
    boolean returnsOwnHandle(WitFunction function) {
        FuncType type = function.type();
        if (!type.hasResult() || type.result().primValType() != null) {
            return false;
        }
        Type declared = function.scope().at(type.result().typeIdx());
        DefValType defined = declared == null ? null : declared.defValType();
        return defined instanceof OwnType && ((OwnType) defined).typeIdx() == typeIndex;
    }
}
