package run.endive.cm.bindgen.it.variants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.cm.bindgen.it.Components;
import run.endive.cm.bindgen.it.variants.example.varianttypes.commands.Command;
import run.endive.cm.bindgen.it.variants.example.varianttypes.commands.Host;
import run.endive.cm.bindgen.it.variants.exports.example.varianttypes.replies.Reply;
import run.endive.cm.runtime.Bindgen;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.runtime.TrapException;

/**
 * A world declaring a variant either side of the boundary, written for this test because none of
 * the bindgen examples declares one.
 *
 * <p>Both variants mix a payload-carrying case with a payload-free one, and the guest traps unless
 * each value it is handed is the one the test says it was handed.
 */
@Bindgen(world = "variant-types")
public class VariantTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/variant-types.wat"),
                        Components.text("/wit/variant-types.wit"),
                        "variant-types");
    }

    /** What the guest sends arrives as the case it named, payload and all. */
    @Test
    void anImportedVariantArrivesAsItsCase() {
        Commands commands = scripted(new Command.Stop(), new Command.Jump(7L));

        instantiate(commands).replies().run();

        assertEquals(List.of(new Command.Speak("go"), new Command.Stop()), commands.seen);
    }

    /** The guest checks each reply, so a case the host did not send would not pass. */
    @Test
    void anImportedVariantTheGuestDoesNotExpectTraps() {
        Commands commands = scripted(new Command.Jump(1L), new Command.Jump(7L));

        VariantTypes bindings = instantiate(commands);

        assertThrows(TrapException.class, () -> bindings.replies().run());
    }

    /** A payload the host sends has to reach the guest, not only the case carrying it. */
    @Test
    void anImportedPayloadTheGuestDoesNotExpectTraps() {
        Commands commands = scripted(new Command.Stop(), new Command.Jump(1L));

        VariantTypes bindings = instantiate(commands);

        assertThrows(TrapException.class, () -> bindings.replies().run());
    }

    /** An exported call carries a payload in and answers with a case carrying none. */
    @Test
    void anExportedCallCarriesAPayloadIn() {
        VariantTypes bindings = instantiate(scripted());

        Reply answer = bindings.replies().echo(new Reply.Text("hello"));

        assertTrue(answer instanceof Reply.Silence, "expected silence, got " + answer);
    }

    /** The other way round, so a payload crosses back out of the guest. */
    @Test
    void anExportedCallCarriesAPayloadOut() {
        VariantTypes bindings = instantiate(scripted());

        Reply answer = bindings.replies().echo(new Reply.Silence());

        assertEquals(new Reply.Text("quiet"), answer);
        assertEquals("quiet", ((Reply.Text) answer).value());
    }

    /** The guest reads the payload it is handed, so a different one traps. */
    @Test
    void anExportedPayloadTheGuestDoesNotExpectTraps() {
        VariantTypes bindings = instantiate(scripted());

        assertThrows(TrapException.class, () -> bindings.replies().echo(new Reply.Text("wrong")));
    }

    /** A case is a class of its own, so nothing but its own kind equals it. */
    @Test
    void aCaseIsToldApartFromTheOthers() {
        Command jump = new Command.Jump(7L);

        assertTrue(jump instanceof Command.Jump);
        assertEquals(7L, ((Command.Jump) jump).value());
        assertEquals("jump(7)", jump.toString());
        assertNotEquals(jump, new Command.Stop());
        assertNotEquals(new Command.Jump(8L), jump);
    }

    private static VariantTypes instantiate(Host commands) {
        return VariantTypes.instantiate(new ComponentStore(), component, () -> commands);
    }

    private static Commands scripted(Command... replies) {
        return new Commands(List.of(replies));
    }

    /** The host side of {@code example:variant-types/commands}, replying from a script. */
    private static final class Commands implements Host {

        private final List<Command> seen = new ArrayList<>();
        private final List<Command> replies;
        private int next;

        Commands(List<Command> replies) {
            this.replies = replies;
        }

        @Override
        public Command handle(Command cmd) {
            seen.add(cmd);
            return replies.get(next++);
        }
    }
}
