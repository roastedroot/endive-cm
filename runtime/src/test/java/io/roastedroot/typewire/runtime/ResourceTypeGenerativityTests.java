package io.roastedroot.typewire.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

/**
 * Resource types are generative, so declaring one inside a component means every instantiation of
 * that component brings a distinct type into existence. Handles minted against one must not pass
 * the guards of another.
 *
 * <p>The generated spec harness re-parses the component on every command, so its two instantiations
 * never share a declaration and cannot separate identity per instantiation from identity per
 * declaration. One parse instantiated twice is what separates them, and that is what these cases
 * build.
 *
 * @see <a href="https://github.com/WebAssembly/component-model/blob/706074c96bc14cfc58469e1bdc452bb4d91921c7/design/mvp/Explainer.md#type-definitions">Explainer.md, resource type generativity</a>
 */
public class ResourceTypeGenerativityTests {

    /** A component that instantiates one inner component, which declares a resource, twice over. */
    private static final String TWO_INSTANTIATIONS = "/wasmtime-resources/input.16.wasm";

    /**
     * The rule itself. Two instantiations of one declaration are two types, so a handle minted
     * against either is refused by the other's guards.
     */
    @Test
    public void instantiatingOneComponentTwiceCreatesTwoResourceTypes() {
        ComponentInstance root = instantiate(TWO_INSTANTIATIONS);

        ResourceTypeInstance first = declaredResourceType(root.getChildInstance(0));
        ResourceTypeInstance second = declaredResourceType(root.getChildInstance(1));

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(
                first,
                second,
                "each instantiation must declare its own resource type, so that a handle from"
                        + " one is not usable against the other");
    }

    /**
     * Which instance implements each of those types, which is a separate fact from their being
     * distinct. Lowering a {@code borrow} into the implementing component hands over the
     * representation rather than a table index, so a wrong implementer would pass a rep where a
     * handle was expected.
     */
    @Test
    public void eachInstantiationImplementsItsOwnResourceType() {
        ComponentInstance root = instantiate(TWO_INSTANTIATIONS);

        ComponentInstance first = root.getChildInstance(0);
        ComponentInstance second = root.getChildInstance(1);

        assertSame(first.handles(), declaredResourceType(first).handleTable());
        assertSame(second.handles(), declaredResourceType(second).handleTable());
    }

    /** The resource type at index 0 of an instance's own type index space. */
    private static ResourceTypeInstance declaredResourceType(ComponentInstance instance) {
        return instance.requireResourceType(0);
    }

    private static ComponentInstance instantiate(String resourcePath) {
        return ComponentLinker.builder()
                .build()
                .instantiate(new ComponentStore(), TestComponents.parse(loadBytes(resourcePath)));
    }

    private static byte[] loadBytes(String resourcePath) {
        try (InputStream is =
                ResourceTypeGenerativityTests.class.getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Resource not found: " + resourcePath);
            return is.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
