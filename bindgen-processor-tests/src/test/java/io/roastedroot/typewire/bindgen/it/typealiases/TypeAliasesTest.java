package io.roastedroot.typewire.bindgen.it.typealiases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.bindgen.it.Components;
import io.roastedroot.typewire.bindgen.it.typealiases.example.typealiases.clock.Stamp;
import io.roastedroot.typewire.runtime.Bindgen;
import io.roastedroot.typewire.runtime.ComponentStore;
import io.roastedroot.typewire.types.WasmComponent;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.runtime.TrapException;

/**
 * A world whose interfaces name primitive types, written for this test because none of the
 * bindgen! examples declares one. WASI declares many, such as {@code instant} and {@code duration}.
 *
 * <p>The guest traps unless each named value holds what these tests say the host handed over, so a
 * passing call shows the values crossed rather than only that a call happened.
 */
@Bindgen(world = "type-aliases")
public class TypeAliasesTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/type-aliases.wat"),
                        Components.text("/wit/type-aliases.wit"),
                        "type-aliases");
    }

    /**
     * Named primitives cross in both directions, as arguments, as results, as a record field, and
     * through an interface using them from another.
     */
    @Test
    void namedPrimitivesCrossInBothDirections() {
        assertEquals(BigInteger.valueOf(105), instantiate(new Clock(100)).run());
    }

    /** The guest traps on an instant it was not promised, so the one above really arrived. */
    @Test
    void anInstantTheGuestDoesNotExpectTraps() {
        TypeAliasesWorld bindings = instantiate(new Clock(101));

        assertThrows(TrapException.class, bindings::run);
    }

    private static TypeAliasesWorld instantiate(Clock clock) {
        return TypeAliasesWorld.instantiate(
                new ComponentStore(),
                component,
                new TypeAliasesWorld.Imports() {
                    @Override
                    public io.roastedroot.typewire.bindgen.it.typealiases.example.typealiases.clock
                                    .Host
                            clock() {
                        return clock;
                    }

                    @Override
                    public io.roastedroot.typewire.bindgen.it.typealiases.example.typealiases.timer
                                    .Host
                            timer() {
                        return after -> clock.now().add(after);
                    }
                });
    }

    /** The host side of {@code example:type-aliases/clock}, stopped at one instant. */
    private static final class Clock
            implements io.roastedroot.typewire.bindgen.it.typealiases.example.typealiases.clock
                    .Host {

        private final BigInteger now;

        Clock(long now) {
            this.now = BigInteger.valueOf(now);
        }

        @Override
        public BigInteger now() {
            return now;
        }

        @Override
        public BigInteger elapsed(BigInteger since) {
            return now.subtract(since);
        }

        @Override
        public Stamp latest() {
            return new Stamp(now, 7L);
        }

        @Override
        public String describe(BigInteger at) {
            return "at " + at;
        }

        @Override
        public List<BigInteger> history() {
            return List.of(now);
        }
    }
}
