package run.endive.cm.bindgen.it.results;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import run.endive.cm.bindgen.it.Components;
import run.endive.cm.bindgen.it.results.example.resulttypes.parsing.Host;
import run.endive.cm.bindgen.it.results.example.resulttypes.parsing.ParseError;
import run.endive.cm.bindgen.it.results.example.resulttypes.parsing.ParseErrorException;
import run.endive.cm.bindgen.it.results.exports.example.resulttypes.running.Guest;
import run.endive.cm.bindgen.it.results.exports.example.resulttypes.running.RunError;
import run.endive.cm.bindgen.it.results.exports.example.resulttypes.running.RunErrorException;
import run.endive.cm.bindgen.it.results.exports.example.resulttypes.running.RunningResult6Exception;
import run.endive.cm.bindgen.it.results.exports.example.resulttypes.running.RunningResult8Exception;
import run.endive.cm.runtime.Bindgen;
import run.endive.cm.runtime.ComponentStore;
import run.endive.cm.types.WasmComponent;
import run.endive.runtime.TrapException;

/**
 * A world exercising all four shapes of {@code result}, in both directions.
 *
 * <p>The ok payload is the Java return value and the error case is a generated unchecked exception,
 * so {@code run} shows an error crossing each way, from the embedder to the guest through the
 * imported {@code parse} and back out as the exception the guest's own error becomes.
 */
@Bindgen(world = "result-types")
public class ResultTest {

    private static WasmComponent component;

    @BeforeAll
    static void buildComponent() {
        component =
                Components.build(
                        Components.bytes("/result-types.wat"),
                        Components.text("/wit/result-types.wit"),
                        "result-types");
    }

    /** The ok payload crosses into the guest, which checks it and hands back an ok of its own. */
    @Test
    void anOkPayloadCrossesInBothDirections() {
        Guest guest = running(text -> 21L);

        assertEquals(22L, guest.run("21"));
    }

    /** The embedder throws the generated exception, which the guest sees as the error case. */
    @Test
    void anExceptionFromTheEmbedderReachesTheGuestAsAnError() {
        Guest guest =
                running(
                        text -> {
                            throw new ParseErrorException(ParseError.OVERFLOW);
                        });

        RunErrorException thrown = assertThrows(RunErrorException.class, () -> guest.run("big"));
        assertSame(RunError.REFUSED, thrown.error());
    }

    /**
     * Anything else the embedder throws is a bug in the embedder rather than an error the guest
     * asked for, so it propagates instead of arriving as a well formed error case.
     */
    @Test
    void anyOtherExceptionFromTheEmbedderPropagates() {
        Guest guest =
                running(
                        text -> {
                            throw new IllegalStateException("boom");
                        });

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> guest.run("boom"));
        assertEquals("boom", rootCause(thrown).getMessage());
    }

    /** The guest checks the ok payload, so a different one traps. */
    @Test
    void anOkPayloadTheGuestDoesNotExpectTraps() {
        Guest guest = running(text -> 99L);

        assertThrows(TrapException.class, () -> guest.run("99"));
    }

    /** The guest checks the error payload too, so a different case traps. */
    @Test
    void anErrorCaseTheGuestDoesNotExpectTraps() {
        Guest guest =
                running(
                        text -> {
                            throw new ParseErrorException(ParseError.EMPTY);
                        });

        assertThrows(TrapException.class, () -> guest.run("empty"));
    }

    /** {@code result<_, E>} has no ok payload, so the Java method returns nothing. */
    @Test
    void aResultWithNoOkPayloadReturnsNothing() {
        running(text -> 21L).check(7L);
    }

    @Test
    void aResultWithNoOkPayloadStillThrowsItsError() {
        Guest guest = running(text -> 21L);

        RunErrorException thrown = assertThrows(RunErrorException.class, () -> guest.check(8L));
        assertSame(RunError.REFUSED, thrown.error());
    }

    @Test
    void aValueCheckDoesNotExpectTraps() {
        Guest guest = running(text -> 21L);

        assertThrows(TrapException.class, () -> guest.check(9L));
    }

    /** {@code result<T>} carries nothing in its error case, so the exception carries nothing. */
    @Test
    void aResultWithNoErrorPayloadThrowsAnExceptionWithoutOne() {
        Guest guest = running(text -> 21L);

        assertEquals(99L, guest.count(false));
        assertThrows(RunningResult6Exception.class, () -> guest.count(true));
    }

    /** A bare {@code result} carries neither payload, so only the case is left. */
    @Test
    void aBareResultCarriesOnlyItsCase() {
        Guest guest = running(text -> 21L);

        guest.ping(false);
        assertThrows(RunningResult8Exception.class, () -> guest.ping(true));
    }

    private static Guest running(Host host) {
        return ResultTypes.instantiate(new ComponentStore(), component, () -> host).running();
    }

    private static Throwable rootCause(Throwable thrown) {
        Throwable cause = thrown;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
