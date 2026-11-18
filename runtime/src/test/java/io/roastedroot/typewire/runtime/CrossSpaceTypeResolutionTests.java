package io.roastedroot.typewire.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

/**
 * A type carries the index space in which it was written, not the one in which it is used.
 *
 * <p>An inner component exports a {@code record} whose field is an {@code own} of a resource it
 * declares. The outer component aliases that record in at a different index, chosen so that reading
 * the field in the wrong space would find something else entirely.
 */
public class CrossSpaceTypeResolutionTests {

    private static final String CROSS_SPACE = "/type-resolution/cross-space-handle.wat";

    /**
     * Round-trips a handle through the aliased record. The guest mints one, hands it over as the
     * record's field, and gets an index back. Lifting it checks the handle against the resource
     * type the {@code own} resolves to, so a misresolved index traps here rather than quietly
     * admitting a handle of another type.
     */
    @Test
    public void aHandleNestedInAnAliasedTypeResolvesInTheSpaceThatDefinedIt() {
        ComponentInstance instance = instantiate(CROSS_SPACE);

        Object[] result = instance.export("go").apply();

        // The index the record's handle was re-lowered to, which is only reachable if lifting
        // it accepted the handle in the first place.
        assertArrayEquals(new Object[] {1L}, result);
    }

    /** Assembles the {@code .wat} beside this test, so the fixture stays the readable form. */
    private static ComponentInstance instantiate(String resourcePath) {
        try (InputStream is =
                CrossSpaceTypeResolutionTests.class.getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Resource not found: " + resourcePath);
            return ComponentLinker.builder()
                    .build()
                    .instantiate(new ComponentStore(), TestComponents.fromWat(is));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
