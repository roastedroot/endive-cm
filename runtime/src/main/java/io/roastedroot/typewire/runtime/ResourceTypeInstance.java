package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.HandleTable;
import io.roastedroot.typewire.abi.ResourceTypeRef;
import io.roastedroot.typewire.types.ResourceType;
import io.roastedroot.typewire.types.Type;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A resource type brought into existence by instantiating the component that declares it, or
 * supplied ready-made by the host.
 *
 * <p>{@link ResourceType} is the static declaration, shared by every instantiation. Resource types
 * are generative, so two instantiations declare two distinct types. That distinction lives here,
 * one instance per declaration per instantiation, compared by reference identity.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#type-definitions">Explainer.md, resource type generativity</a>
 */
final class ResourceTypeInstance implements ResourceTypeRef {

    private final Type type;
    private final ResourceType declared;
    private final ComponentInstance impl;
    private final IntConsumer hostDtor;
    private CoreFunction<?> dtor;

    ResourceTypeInstance(Type type, ComponentInstance impl, IntConsumer hostDtor) {
        this.type = Objects.requireNonNull(type, "type");
        this.declared = Objects.requireNonNull(type.resourceType(), "resourceType");
        this.impl = impl;
        this.hostDtor = hostDtor;
    }

    /**
     * {@code null} for a resource type the host defines, whose handles therefore always go through
     * a table.
     */
    @Override
    public HandleTable handleTable() {
        return impl == null ? null : impl.handles();
    }

    /** The static declaration this is an instantiation of. */
    ResourceType declared() {
        return declared;
    }

    /**
     * The declaration as it appears in a type index space, so that whoever receives this identity
     * can record the pair together.
     */
    Type type() {
        return type;
    }

    /**
     * Whether dropping the last owning handle of this type runs anything. A guest-declared type
     * names its destructor by core function index and a host-defined type carries a callback. An
     * imported type has neither.
     */
    boolean hasDtor() {
        return hostDtor != null || (impl != null && declared.hasDtor());
    }

    /**
     * Destroys the resource behind {@code rep}. Resolved on first use, because a resource type may
     * name a core function defined after the type section.
     */
    void runDtor(int rep) {
        if (hostDtor != null) {
            hostDtor.accept(rep);
            return;
        }
        if (dtor == null) {
            dtor = impl.getCoreFunction((int) declared.dtor());
        }
        dtor.apply(rep);
    }

    @Override
    public String toString() {
        return "resource type " + declared.rep() + "@" + Integer.toHexString(hashCode());
    }
}
