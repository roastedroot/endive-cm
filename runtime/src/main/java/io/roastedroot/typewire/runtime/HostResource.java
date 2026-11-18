package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.ResourceTypeRef;
import io.roastedroot.typewire.types.ValType;
import java.util.Objects;

/**
 * A resource type the host implements, together with the value types naming a handle to it.
 *
 * <p>Resource types are generative, so this stands for one type rather than for a declaration that
 * many instantiations share. Two calls to {@link HostInstance.Builder#declareResource} produce two
 * types however alike their declarations look.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#type-definitions">Explainer.md, resource type generativity</a>
 */
public final class HostResource {

    private final ResourceTypeInstance type;
    private final ValType own;
    private final ValType borrow;

    HostResource(ResourceTypeInstance type, ValType own, ValType borrow) {
        this.type = Objects.requireNonNull(type, "type");
        this.own = Objects.requireNonNull(own, "own");
        this.borrow = Objects.requireNonNull(borrow, "borrow");
    }

    /**
     * The runtime identity of this type, for building a {@link io.roastedroot.typewire.abi.ResourceValue}
     * that carries one of its handles.
     */
    public ResourceTypeRef type() {
        return type;
    }

    /** The {@code own} of this resource type, as a function type names it. */
    public ValType own() {
        return own;
    }

    /** The {@code borrow} of this resource type, as a function type names it. */
    public ValType borrow() {
        return borrow;
    }

    ResourceTypeInstance instance() {
        return type;
    }
}
