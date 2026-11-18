package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.types.WasmComponent;
import java.util.Objects;

/**
 * A component as a value, holding its definition together with the scope in which it was written.
 *
 * <p>The definition alone is not enough, because a nested component may reach outward through an
 * {@code alias outer}. What that alias resolves to depends on which instantiation of the enclosing
 * component produced the value, so a component value travels with its scope.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#alias-definitions">Explainer.md, outer aliases</a>
 */
public final class ComponentClosure {

    private final WasmComponent definition;
    private final ComponentInstance definingScope;

    ComponentClosure(WasmComponent definition, ComponentInstance definingScope) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.definingScope = definingScope;
    }

    public WasmComponent definition() {
        return definition;
    }

    /**
     * The instantiation inside which this component was defined, against which its outer aliases
     * resolve. {@code null} for a component the host supplied, which encloses nothing.
     */
    ComponentInstance definingScope() {
        return definingScope;
    }

    @Override
    public String toString() {
        return "component@" + Integer.toHexString(System.identityHashCode(definition));
    }
}
