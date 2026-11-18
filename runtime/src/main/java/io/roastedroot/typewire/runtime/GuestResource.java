package io.roastedroot.typewire.runtime;

import io.roastedroot.typewire.abi.ResourceTypeRef;
import io.roastedroot.typewire.abi.ResourceValue;

/**
 * Drops an owned handle to a resource a component implements.
 *
 * <p>Lifting an {@code own} out of a component takes the handle out of that component's table, so
 * what the embedder holds afterwards is the last thing naming the resource. Nothing will destroy it
 * on the embedder's behalf, which is why dropping one is something the embedder has to do.
 *
 * @see HostResource for a resource the embedder implements rather than one it holds
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/CanonicalABI.md#canon-resourcedrop">CanonicalABI.md, canon resource.drop</a>
 */
public final class GuestResource {

    private GuestResource() {}

    /**
     * Runs the destructor of whoever implements {@code handle}'s resource type. Dropping a resource
     * type with no destructor does nothing, which is what the specification says it means.
     *
     * @throws LinkageException if the handle is borrowed rather than owned, since only an owner may
     *     drop
     */
    public static void drop(ResourceValue handle) {
        if (!handle.own()) {
            throw new LinkageException("only an owned handle may be dropped");
        }
        ResourceTypeRef resourceType = handle.resourceType();
        if (resourceType instanceof ResourceTypeInstance) {
            ResourceTypeInstance instance = (ResourceTypeInstance) resourceType;
            if (instance.hasDtor()) {
                instance.runDtor(handle.rep());
            }
        }
    }
}
