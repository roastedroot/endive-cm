package run.endive.cm.bindgen.it.flags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.cm.bindgen.it.Components;
import run.endive.cm.bindgen.it.flags.example.flagtypes.permissions.Host;
import run.endive.cm.bindgen.it.flags.example.flagtypes.permissions.Permission;
import run.endive.cm.bindgen.it.flags.exports.example.flagtypes.runner.Mode;
import run.endive.cm.runtime.Bindgen;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.runtime.TrapException;

/**
 * Flags in both directions, as an argument the guest is handed and as the value it hands back.
 *
 * <p>The guest traps unless the host grants exactly the complement of what it asked for, so the
 * bits have to cross intact for any of these to pass. An all-false value and an all-true one are
 * each driven both ways, since an absent label and a mispacked bit only show up there.
 */
@Bindgen(world = "flag-types")
public class FlagsTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/flag-types.wat"),
                        Components.text("/wit/flag-types.wit"),
                        "flag-types");
    }

    /** Nothing set crosses into the guest and everything set comes back. */
    @Test
    void allFalseCrossesInAndAllTrueComesBack() {
        Complement host = new Complement();

        Mode granted = run(host, Mode.of());

        assertEquals(List.of(Permission.of()), host.requested);
        assertEquals(Mode.of(Mode.Flag.READ, Mode.Flag.WRITE, Mode.Flag.EXEC), granted);
    }

    /** Everything set crosses into the guest and nothing set comes back. */
    @Test
    void allTrueCrossesInAndAllFalseComesBack() {
        Complement host = new Complement();

        Mode granted = run(host, Mode.of(Mode.Flag.READ, Mode.Flag.WRITE, Mode.Flag.EXEC));

        assertEquals(
                List.of(
                        Permission.of(
                                Permission.Flag.READ, Permission.Flag.WRITE, Permission.Flag.EXEC)),
                host.requested);
        assertEquals(Mode.of(), granted);
    }

    /** One label set tells the first bit from the last, which an all-or-nothing case cannot. */
    @Test
    void oneFlagKeepsItsPlace() {
        Complement host = new Complement();

        Mode granted = run(host, Mode.of(Mode.Flag.WRITE));

        assertEquals(List.of(Permission.of(Permission.Flag.WRITE)), host.requested);
        assertEquals(Mode.of(Mode.Flag.READ, Mode.Flag.EXEC), granted);
        assertTrue(granted.has(Mode.Flag.READ));
        assertFalse(granted.has(Mode.Flag.WRITE));
    }

    /** The guest's trap is live, so flags it was not granted are refused. */
    @Test
    void flagsTheGuestDoesNotExpectTrap() {
        Echo host = new Echo();

        assertThrows(TrapException.class, () -> run(host, Mode.of(Mode.Flag.READ)));
    }

    /** A label the map leaves out is false, which is where flags differ from a record. */
    @Test
    void anAbsentLabelIsFalse() {
        Permission lifted = Permission.fromComponent(Map.of("write", true));

        assertEquals(Permission.of(Permission.Flag.WRITE), lifted);
    }

    /** Every label is written, because the ABI packs the bits by reading each one by name. */
    @Test
    void everyLabelIsCarried() {
        Map<String, Boolean> lowered = Permission.of(Permission.Flag.EXEC).toComponent();

        assertEquals(Map.of("read", false, "write", false, "exec", true), lowered);
    }

    private static Mode run(Host host, Mode requested) {
        return FlagTypes.instantiate(new ComponentStore(), component, () -> host)
                .runner()
                .run(requested);
    }

    /** Grants exactly what was not asked for, which is what the guest checks against. */
    private static final class Complement implements Host {

        private final List<Permission> requested = new ArrayList<>();

        @Override
        public Permission grant(Permission asked) {
            requested.add(asked);
            List<Permission.Flag> granted = new ArrayList<>();
            for (Permission.Flag flag : Permission.Flag.values()) {
                if (!asked.has(flag)) {
                    granted.add(flag);
                }
            }
            return Permission.of(granted.toArray(new Permission.Flag[0]));
        }
    }

    /** Grants what was asked for, which is never what the guest expects. */
    private static final class Echo implements Host {

        @Override
        public Permission grant(Permission asked) {
            return asked;
        }
    }
}
